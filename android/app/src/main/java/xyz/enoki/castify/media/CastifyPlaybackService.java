package xyz.enoki.castify.media;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.session.LibraryResult;
import androidx.media3.session.MediaLibraryService;
import androidx.media3.session.MediaSession;
import androidx.media3.session.SessionError;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import xyz.enoki.castify.MainActivity;
import xyz.enoki.castify.R;

/**
 * Castify のネイティブ再生サービス。
 *
 * - ExoPlayer でライブストリームを再生（WebView を開いていなくても再生できる）
 * - MediaLibrarySession で Android Auto にブラウズツリー（マイプレイリスト / おすすめ）を公開
 * - 通知・ロック画面・Bluetooth ボタン・音声コマンドも Media3 がこのセッション経由で処理する
 */
@OptIn(markerClass = UnstableApi.class)
public class CastifyPlaybackService extends MediaLibraryService implements ChannelRepository.Listener {

    private static final String TAG = "CastifyPlayback";
    private static final long NOW_PLAYING_POLL_MS = 15_000;
    private static final long PLAYLIST_MAX_AGE_MS = 30_000;
    private static final long RECOMMENDED_MAX_AGE_MS = 5 * 60_000;
    private static final long FIRST_LOAD_TIMEOUT_MS = 8_000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ListeningExecutorService io;
    private ScheduledExecutorService timeoutScheduler;

    private ChannelRepository repo;
    private MediaItemFactory items;
    private ExoPlayer exoPlayer;
    private MediaLibrarySession session;

    private volatile boolean playlistRefreshing;
    private volatile boolean recommendedRefreshing;
    @Nullable private volatile List<Channel> lastSearchResult;

    // ---------------------------------------------------------------------
    // ライフサイクル
    // ---------------------------------------------------------------------

    @Override
    public void onCreate() {
        super.onCreate();
        io = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(3));
        timeoutScheduler = Executors.newSingleThreadScheduledExecutor();
        repo = ChannelRepository.get(this);
        items = new MediaItemFactory(this);

        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(AzuraCastClient.USER_AGENT)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(10_000)
                .setReadTimeoutMs(15_000);

        exoPlayer = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(this).setDataSourceFactory(http))
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(), /* handleAudioFocus= */ true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build();
        // 前後スキップでキューの端から反対側へ回り込む（JS 版の挙動と同じ）
        exoPlayer.setRepeatMode(Player.REPEAT_MODE_ALL);
        exoPlayer.addListener(playerListener);

        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent sessionActivity = PendingIntent.getActivity(
                this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        session = new MediaLibrarySession.Builder(this, new LivePlayer(exoPlayer), new LibraryCallback())
                .setSessionActivity(sessionActivity)
                .build();

        repo.addListener(this);
    }

    @Nullable
    @Override
    public MediaLibrarySession onGetSession(@NonNull MediaSession.ControllerInfo controllerInfo) {
        return session;
    }

    @Override
    public void onTaskRemoved(@Nullable Intent rootIntent) {
        // 再生していないときにアプリをスワイプで閉じたらサービスも終了する
        Player player = session != null ? session.getPlayer() : null;
        if (player == null || !player.getPlayWhenReady() || player.getMediaItemCount() == 0) {
            stopSelf();
        }
    }

    @Override
    public void onDestroy() {
        repo.removeListener(this);
        mainHandler.removeCallbacksAndMessages(null);
        if (session != null) {
            session.getPlayer().release();
            session.release();
            session = null;
        }
        io.shutdownNow();
        timeoutScheduler.shutdownNow();
        super.onDestroy();
    }

    /** JS 側の同期やネイティブ側の更新で一覧が変わったら Android Auto に再取得させる */
    @Override
    public void onLibraryChanged() {
        mainHandler.post(() -> {
            if (session == null) return;
            session.notifyChildrenChanged(MediaItemFactory.PLAYLIST_ID,
                    Math.max(1, repo.getPlaylist().size()), null);
            session.notifyChildrenChanged(MediaItemFactory.RECOMMENDED_ID,
                    Math.max(1, repo.getRecommended().size()), null);
        });
    }

    // ---------------------------------------------------------------------
    // 再生中チャンネルの曲情報ポーリング
    // ---------------------------------------------------------------------

    private final Runnable pollTask = new Runnable() {
        @Override
        public void run() {
            refreshCurrentNowPlaying();
            mainHandler.postDelayed(this, NOW_PLAYING_POLL_MS);
        }
    };

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            mainHandler.removeCallbacks(pollTask);
            if (isPlaying) mainHandler.post(pollTask);
        }

        @Override
        public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
            if (mediaItem != null) repo.setLastPlayedId(mediaItem.mediaId);
            // 局が変わったらすぐに最新の曲情報を取りに行く
            mainHandler.removeCallbacks(pollTask);
            mainHandler.post(pollTask);
        }

        @Override
        public void onPlayerError(@NonNull PlaybackException error) {
            Log.w(TAG, "playback error: " + error.getErrorCodeName(), error);
        }
    };

    private void refreshCurrentNowPlaying() {
        MediaItem current = exoPlayer.getCurrentMediaItem();
        if (current == null) return;
        Channel ch = repo.find(current.mediaId);
        if (ch == null) return;
        io.execute(() -> {
            try {
                Channel fresh = AzuraCastClient.fetchStation(ch);
                if (fresh == null) return;
                Channel merged = ch.withNowPlaying(fresh);
                repo.updateNowPlaying(Collections.singletonList(merged), false);
                mainHandler.post(() -> applyMetadata(merged));
            } catch (Exception e) {
                Log.d(TAG, "now playing refresh failed: " + e.getMessage());
            }
        });
    }

    /** 再生を止めずにメタデータだけ差し替える（同じ URI なら ExoPlayer は再準備しない） */
    private void applyMetadata(@NonNull Channel ch) {
        if (exoPlayer == null) return;
        int index = exoPlayer.getCurrentMediaItemIndex();
        MediaItem current = exoPlayer.getCurrentMediaItem();
        if (current == null || !current.mediaId.equals(ch.id)) return;
        MediaItem updated = current.buildUpon().setMediaMetadata(items.nowPlayingMetadata(ch)).build();
        if (!updated.mediaMetadata.equals(current.mediaMetadata)) {
            exoPlayer.replaceMediaItem(index, updated);
        }
    }

    // ---------------------------------------------------------------------
    // 一覧データの取得
    // ---------------------------------------------------------------------

    /** プレイリストの曲情報を裏で更新（終わったら notifyChildrenChanged される） */
    private void refreshPlaylistInBackground() {
        if (playlistRefreshing || !repo.isPlaylistStale(PLAYLIST_MAX_AGE_MS)) return;
        List<Channel> playlist = repo.getPlaylist();
        if (playlist.isEmpty()) return;
        playlistRefreshing = true;
        io.execute(() -> {
            try {
                List<String> domains = new ArrayList<>();
                for (Channel ch : playlist) {
                    if (!domains.contains(ch.domain)) domains.add(ch.domain);
                }
                List<Channel> fresh = new ArrayList<>();
                for (String d : domains) {
                    try {
                        fresh.addAll(AzuraCastClient.fetchDomain(d));
                    } catch (Exception e) {
                        Log.d(TAG, "playlist refresh failed for " + d + ": " + e.getMessage());
                    }
                }
                repo.updateNowPlaying(fresh, true);
            } finally {
                playlistRefreshing = false;
            }
        });
    }

    /** おすすめ一覧を取得。キャッシュが空のときだけ呼び出し側が待つ */
    private ListenableFuture<List<Channel>> loadRecommended() {
        if (!repo.getRecommended().isEmpty()) {
            if (repo.isRecommendedStale(RECOMMENDED_MAX_AGE_MS) && !recommendedRefreshing) {
                recommendedRefreshing = true;
                io.execute(() -> {
                    try {
                        repo.setRecommended(AzuraCastClient.fetchRecommended());
                    } finally {
                        recommendedRefreshing = false;
                    }
                });
            }
            return Futures.immediateFuture(repo.getRecommended());
        }
        ListenableFuture<List<Channel>> fetch = io.submit(() -> {
            List<Channel> list = AzuraCastClient.fetchRecommended();
            repo.setRecommended(list);
            return list;
        });
        ListenableFuture<List<Channel>> withTimeout =
                Futures.withTimeout(fetch, FIRST_LOAD_TIMEOUT_MS, TimeUnit.MILLISECONDS, timeoutScheduler);
        return Futures.catching(withTimeout, Exception.class,
                e -> Collections.<Channel>emptyList(), MoreExecutors.directExecutor());
    }

    private static <T> List<T> page(List<T> list, int page, int pageSize) {
        if (pageSize <= 0 || pageSize == Integer.MAX_VALUE) return list;
        long from = (long) page * pageSize;
        if (from >= list.size()) return Collections.emptyList();
        int to = (int) Math.min(list.size(), from + pageSize);
        return list.subList((int) from, to);
    }

    // ---------------------------------------------------------------------
    // 再生キューの解決
    // ---------------------------------------------------------------------

    /** MediaItem（ID または音声検索クエリ）から対象チャンネルを決める */
    @Nullable
    private Channel resolve(@NonNull MediaItem requested) {
        Channel byId = repo.find(requested.mediaId);
        if (byId != null) return byId;
        CharSequence query = requested.requestMetadata.searchQuery;
        if (query != null) {
            List<Channel> hits = repo.search(query.toString());
            if (!hits.isEmpty()) return hits.get(0);
            // 「Castify で何か再生して」のようにクエリが曖昧な場合は前回の局を流す
            return repo.resumeCandidate();
        }
        return null;
    }

    @NonNull
    private MediaSession.MediaItemsWithStartPosition queueStartingAt(@NonNull Channel ch) {
        List<Channel> queue = repo.queueFor(ch);
        int index = 0;
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).id.equals(ch.id)) {
                index = i;
                break;
            }
        }
        return new MediaSession.MediaItemsWithStartPosition(items.playable(queue), index, C.TIME_UNSET);
    }

    // ---------------------------------------------------------------------
    // MediaLibrarySession.Callback
    // ---------------------------------------------------------------------

    private final class LibraryCallback implements MediaLibrarySession.Callback {

        @NonNull
        @Override
        public ListenableFuture<LibraryResult<MediaItem>> onGetLibraryRoot(
                @NonNull MediaLibrarySession session,
                @NonNull MediaSession.ControllerInfo browser,
                @Nullable LibraryParams params) {
            return Futures.immediateFuture(LibraryResult.ofItem(items.root(), params));
        }

        @NonNull
        @Override
        public ListenableFuture<LibraryResult<MediaItem>> onGetItem(
                @NonNull MediaLibrarySession session,
                @NonNull MediaSession.ControllerInfo browser,
                @NonNull String mediaId) {
            switch (mediaId) {
                case MediaItemFactory.ROOT_ID:
                    return Futures.immediateFuture(LibraryResult.ofItem(items.root(), null));
                case MediaItemFactory.PLAYLIST_ID:
                    return Futures.immediateFuture(LibraryResult.ofItem(items.playlistFolder(), null));
                case MediaItemFactory.RECOMMENDED_ID:
                    return Futures.immediateFuture(LibraryResult.ofItem(items.recommendedFolder(), null));
                default:
                    Channel ch = repo.find(mediaId);
                    if (ch == null) {
                        return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE));
                    }
                    return Futures.immediateFuture(LibraryResult.ofItem(items.browsable(ch), null));
            }
        }

        @NonNull
        @Override
        public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> onGetChildren(
                @NonNull MediaLibrarySession session,
                @NonNull MediaSession.ControllerInfo browser,
                @NonNull String parentId,
                int page,
                int pageSize,
                @Nullable LibraryParams params) {
            switch (parentId) {
                case MediaItemFactory.ROOT_ID:
                    return Futures.immediateFuture(LibraryResult.ofItemList(
                            ImmutableList.of(items.playlistFolder(), items.recommendedFolder()), params));

                case MediaItemFactory.PLAYLIST_ID: {
                    refreshPlaylistInBackground();
                    List<Channel> playlist = repo.getPlaylist();
                    List<MediaItem> list = playlist.isEmpty()
                            ? Collections.singletonList(items.emptyMessage(parentId,
                                    R.string.auto_playlist_empty_title, R.string.auto_playlist_empty_subtitle))
                            : items.browsable(page(playlist, page, pageSize));
                    return Futures.immediateFuture(LibraryResult.ofItemList(list, params));
                }

                case MediaItemFactory.RECOMMENDED_ID:
                    return Futures.transform(loadRecommended(), recommended -> {
                        List<MediaItem> list = recommended.isEmpty()
                                ? Collections.singletonList(items.emptyMessage(parentId,
                                        R.string.auto_recommended_empty_title, R.string.auto_recommended_empty_subtitle))
                                : items.browsable(page(recommended, page, pageSize));
                        return LibraryResult.ofItemList(list, params);
                    }, MoreExecutors.directExecutor());

                default:
                    return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params));
            }
        }

        @NonNull
        @Override
        public ListenableFuture<LibraryResult<Void>> onSearch(
                @NonNull MediaLibrarySession session,
                @NonNull MediaSession.ControllerInfo browser,
                @NonNull String query,
                @Nullable LibraryParams params) {
            List<Channel> result = repo.search(query);
            lastSearchResult = result;
            session.notifySearchResultChanged(browser, query, result.size(), params);
            return Futures.immediateFuture(LibraryResult.ofVoid());
        }

        @NonNull
        @Override
        public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> onGetSearchResult(
                @NonNull MediaLibrarySession session,
                @NonNull MediaSession.ControllerInfo browser,
                @NonNull String query,
                int page,
                int pageSize,
                @Nullable LibraryParams params) {
            List<Channel> result = lastSearchResult;
            if (result == null) result = repo.search(query);
            return Futures.immediateFuture(
                    LibraryResult.ofItemList(items.browsable(page(result, page, pageSize)), params));
        }

        @NonNull
        @Override
        public ListenableFuture<List<MediaItem>> onAddMediaItems(
                @NonNull MediaSession mediaSession,
                @NonNull MediaSession.ControllerInfo controller,
                @NonNull List<MediaItem> mediaItems) {
            List<MediaItem> resolved = new ArrayList<>();
            for (MediaItem requested : mediaItems) {
                Channel ch = resolve(requested);
                if (ch != null) resolved.add(items.playable(ch));
            }
            return Futures.immediateFuture(resolved);
        }

        @NonNull
        @Override
        public ListenableFuture<MediaSession.MediaItemsWithStartPosition> onSetMediaItems(
                @NonNull MediaSession mediaSession,
                @NonNull MediaSession.ControllerInfo controller,
                @NonNull List<MediaItem> mediaItems,
                int startIndex,
                long startPositionMs) {
            if (mediaItems.size() == 1) {
                // 車の画面で 1 局選んだ / 音声で 1 局指定した → その局を含む一覧をキューにする
                Channel ch = resolve(mediaItems.get(0));
                if (ch == null) {
                    return Futures.immediateFailedFuture(new IllegalArgumentException("Unknown media item"));
                }
                return Futures.immediateFuture(queueStartingAt(ch));
            }
            List<MediaItem> resolved = new ArrayList<>();
            int newStart = 0;
            for (int i = 0; i < mediaItems.size(); i++) {
                Channel ch = resolve(mediaItems.get(i));
                if (ch == null) continue;
                if (i == startIndex) newStart = resolved.size();
                resolved.add(items.playable(ch));
            }
            if (resolved.isEmpty()) {
                return Futures.immediateFailedFuture(new IllegalArgumentException("No playable items"));
            }
            return Futures.immediateFuture(
                    new MediaSession.MediaItemsWithStartPosition(resolved, newStart, C.TIME_UNSET));
        }

        /**
         * 通知の再生ボタンや Bluetooth の再生ボタンから再開されたとき（ユーザー操作時のみ呼ばれる）。
         * ライブ配信なので前回の局を「今の放送」から再生する。
         */
        @NonNull
        @Override
        public ListenableFuture<MediaSession.MediaItemsWithStartPosition> onPlaybackResumption(
                @NonNull MediaSession mediaSession,
                @NonNull MediaSession.ControllerInfo controller,
                boolean isForPlayback) {
            Channel ch = repo.resumeCandidate();
            if (ch == null) {
                return Futures.immediateFailedFuture(new UnsupportedOperationException("Nothing to resume"));
            }
            return Futures.immediateFuture(queueStartingAt(ch));
        }
    }

    // ---------------------------------------------------------------------
    // ライブ配信向けの Player ラッパー
    // ---------------------------------------------------------------------

    /**
     * 一時停止から再開したとき、溜まった古いバッファではなく「今の放送」から再接続する。
     * （JS 版で再開時にキャッシュバスター付き URL へ付け替えていたのと同じ狙い）
     */
    private static final class LivePlayer extends ForwardingPlayer {
        private static final long RECONNECT_AFTER_PAUSE_MS = 3_000;
        private long pausedAt;

        LivePlayer(Player player) {
            super(player);
        }

        @Override
        public void play() {
            Player p = getWrappedPlayer();
            if (pausedAt > 0
                    && SystemClock.elapsedRealtime() - pausedAt > RECONNECT_AFTER_PAUSE_MS
                    && p.getPlaybackState() != Player.STATE_IDLE
                    && p.getMediaItemCount() > 0) {
                p.seekToDefaultPosition();
            }
            pausedAt = 0;
            super.play();
        }

        @Override
        public void pause() {
            pausedAt = SystemClock.elapsedRealtime();
            super.pause();
        }

        @Override
        public void setPlayWhenReady(boolean playWhenReady) {
            if (playWhenReady) {
                play();
            } else {
                pause();
            }
        }
    }
}
