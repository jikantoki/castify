package xyz.enoki.castify.media;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * マイプレイリスト・おすすめ・直近の再生キューを保持するリポジトリ（プロセス内シングルトン）。
 *
 * WebView の localStorage はネイティブから読めないため、JS 側から CastifyPlayer.syncLibrary() で
 * 内容を受け取り SharedPreferences に保存する。アプリを開いていない状態で Android Auto から
 * 起動された場合も、ここに保存された内容で一覧を表示・再生できる。
 */
public final class ChannelRepository {

    public interface Listener {
        /** プレイリスト・おすすめの内容が変わった（一覧の再描画が必要） */
        void onLibraryChanged();
    }

    private static final String PREFS = "castify_media_library";
    private static final String KEY_PLAYLIST = "playlist";
    private static final String KEY_RECOMMENDED = "recommended";
    private static final String KEY_LAST_QUEUE = "last_queue";
    private static final String KEY_LAST_PLAYED = "last_played_id";

    private static volatile ChannelRepository instance;

    private final SharedPreferences prefs;
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    private List<Channel> playlist;
    private List<Channel> recommended;
    private List<Channel> lastQueue;
    @Nullable private String lastPlayedId;
    private long playlistUpdatedAt;
    private long recommendedUpdatedAt;

    @NonNull
    public static ChannelRepository get(@NonNull Context context) {
        if (instance == null) {
            synchronized (ChannelRepository.class) {
                if (instance == null) instance = new ChannelRepository(context.getApplicationContext());
            }
        }
        return instance;
    }

    private ChannelRepository(Context appContext) {
        prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        playlist = load(KEY_PLAYLIST);
        recommended = load(KEY_RECOMMENDED);
        lastQueue = load(KEY_LAST_QUEUE);
        lastPlayedId = prefs.getString(KEY_LAST_PLAYED, null);
    }

    public void addListener(@NonNull Listener l) {
        listeners.addIfAbsent(l);
    }

    public void removeListener(@NonNull Listener l) {
        listeners.remove(l);
    }

    // ---------------------------------------------------------------------
    // 読み出し
    // ---------------------------------------------------------------------

    @NonNull
    public synchronized List<Channel> getPlaylist() {
        return playlist;
    }

    @NonNull
    public synchronized List<Channel> getRecommended() {
        return recommended;
    }

    @Nullable
    public synchronized String getLastPlayedId() {
        return lastPlayedId;
    }

    public synchronized boolean isPlaylistStale(long maxAgeMs) {
        return SystemClock.elapsedRealtime() - playlistUpdatedAt > maxAgeMs || playlistUpdatedAt == 0;
    }

    public synchronized boolean isRecommendedStale(long maxAgeMs) {
        return SystemClock.elapsedRealtime() - recommendedUpdatedAt > maxAgeMs || recommendedUpdatedAt == 0;
    }

    /** ID からチャンネルを探す（プレイリスト → おすすめ → 直近キューの順） */
    @Nullable
    public synchronized Channel find(@Nullable String id) {
        if (id == null) return null;
        for (List<Channel> list : sources()) {
            for (Channel ch : list) {
                if (ch.id.equals(id)) return ch;
            }
        }
        return null;
    }

    /**
     * 指定チャンネルを含む再生キューを返す。
     * 車の画面で 1 局選んだときに、前後スキップでプレイリスト内を移動できるようにするため。
     */
    @NonNull
    public synchronized List<Channel> queueFor(@NonNull Channel channel) {
        for (List<Channel> list : sources()) {
            for (Channel ch : list) {
                if (ch.id.equals(channel.id)) return list;
            }
        }
        return Collections.singletonList(channel);
    }

    /** 直近に再生したチャンネル（なければプレイリスト先頭 → おすすめ先頭） */
    @Nullable
    public synchronized Channel resumeCandidate() {
        Channel last = find(lastPlayedId);
        if (last != null) return last;
        if (!playlist.isEmpty()) return playlist.get(0);
        if (!recommended.isEmpty()) return recommended.get(0);
        return null;
    }

    /**
     * 音声コマンド・検索用。局名 → 説明 → 曲名/アーティストの順に部分一致で探す。
     */
    @NonNull
    public synchronized List<Channel> search(@Nullable String rawQuery) {
        String q = normalize(rawQuery);
        List<Channel> exact = new ArrayList<>();
        List<Channel> nameHit = new ArrayList<>();
        List<Channel> otherHit = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (q.isEmpty()) return exact;
        for (List<Channel> list : sources()) {
            for (Channel ch : list) {
                if (!seen.add(ch.id)) continue;
                String name = normalize(ch.name);
                if (name.equals(q)) {
                    exact.add(ch);
                } else if (!name.isEmpty() && (name.contains(q) || q.contains(name))) {
                    nameHit.add(ch);
                } else if (normalize(ch.description).contains(q)
                        || normalize(ch.songTitle).contains(q)
                        || normalize(ch.songArtist).contains(q)
                        || normalize(ch.shortcode).contains(q)) {
                    otherHit.add(ch);
                }
            }
        }
        exact.addAll(nameHit);
        exact.addAll(otherHit);
        return exact;
    }

    /** アートワーク取得を許可するホストか（ArtworkProvider が任意 URL のプロキシにならないように） */
    public synchronized boolean isKnownHost(@Nullable String host) {
        if (host == null) return false;
        for (List<Channel> list : sources()) {
            for (Channel ch : list) {
                if (host.equalsIgnoreCase(hostOf(ch.domain))
                        || host.equalsIgnoreCase(hostOf(ch.artUrl))
                        || host.equalsIgnoreCase(hostOf(ch.songArtUrl))) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------
    // 書き込み
    // ---------------------------------------------------------------------

    /** JS 側からプレイリストとおすすめを同期（recommended が null なら据え置き） */
    public void setLibrary(@NonNull List<Channel> newPlaylist, @Nullable List<Channel> newRecommended) {
        boolean changed;
        synchronized (this) {
            changed = !sameContent(playlist, newPlaylist)
                    || (newRecommended != null && !sameContent(recommended, newRecommended));
            playlist = Collections.unmodifiableList(new ArrayList<>(newPlaylist));
            playlistUpdatedAt = SystemClock.elapsedRealtime();
            save(KEY_PLAYLIST, playlist);
            if (newRecommended != null && !newRecommended.isEmpty()) {
                recommended = Collections.unmodifiableList(new ArrayList<>(newRecommended));
                recommendedUpdatedAt = SystemClock.elapsedRealtime();
                save(KEY_RECOMMENDED, recommended);
            }
        }
        if (changed) notifyChanged();
    }

    public void setRecommended(@NonNull List<Channel> list) {
        boolean changed;
        synchronized (this) {
            if (list.isEmpty()) return;
            changed = !sameContent(recommended, list);
            recommended = Collections.unmodifiableList(new ArrayList<>(list));
            recommendedUpdatedAt = SystemClock.elapsedRealtime();
            save(KEY_RECOMMENDED, recommended);
        }
        if (changed) notifyChanged();
    }

    /** ネイティブ側で取得した最新の曲情報を反映する（ID が一致するものだけ） */
    public void updateNowPlaying(@NonNull List<Channel> freshList, boolean isPlaylistRefresh) {
        boolean changed = false;
        synchronized (this) {
            List<Channel> p = merge(playlist, freshList);
            List<Channel> r = merge(recommended, freshList);
            List<Channel> q = merge(lastQueue, freshList);
            if (p != playlist) { playlist = p; changed = true; save(KEY_PLAYLIST, p); }
            if (r != recommended) { recommended = r; changed = true; save(KEY_RECOMMENDED, r); }
            if (q != lastQueue) { lastQueue = q; save(KEY_LAST_QUEUE, q); }
            if (isPlaylistRefresh) playlistUpdatedAt = SystemClock.elapsedRealtime();
        }
        if (changed) notifyChanged();
    }

    /** アプリ内（探索画面など）から再生したキューを保存 */
    public synchronized void setLastQueue(@NonNull List<Channel> queue) {
        lastQueue = Collections.unmodifiableList(new ArrayList<>(queue));
        save(KEY_LAST_QUEUE, lastQueue);
    }

    public synchronized void setLastPlayedId(@Nullable String id) {
        if (id == null || id.equals(lastPlayedId)) return;
        lastPlayedId = id;
        prefs.edit().putString(KEY_LAST_PLAYED, id).apply();
    }

    // ---------------------------------------------------------------------
    // 内部処理
    // ---------------------------------------------------------------------

    private List<List<Channel>> sources() {
        List<List<Channel>> s = new ArrayList<>(3);
        s.add(playlist);
        s.add(recommended);
        s.add(lastQueue);
        return s;
    }

    private void notifyChanged() {
        for (Listener l : listeners) l.onLibraryChanged();
    }

    /** 変化がなければ同じインスタンスを返す */
    private static List<Channel> merge(List<Channel> base, List<Channel> fresh) {
        boolean changed = false;
        List<Channel> out = new ArrayList<>(base.size());
        for (Channel ch : base) {
            Channel replacement = ch;
            for (Channel f : fresh) {
                if (f.id.equals(ch.id)) {
                    if (!ch.sameNowPlaying(f)) {
                        replacement = ch.withNowPlaying(f);
                        changed = true;
                    }
                    break;
                }
            }
            out.add(replacement);
        }
        return changed ? Collections.unmodifiableList(out) : base;
    }

    private static boolean sameContent(List<Channel> a, List<Channel> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            Channel x = a.get(i);
            Channel y = b.get(i);
            if (!x.id.equals(y.id) || !x.streamUrl.equals(y.streamUrl) || !x.sameNowPlaying(y)) return false;
        }
        return true;
    }

    private List<Channel> load(String key) {
        String raw = prefs.getString(key, null);
        if (raw == null) return Collections.emptyList();
        try {
            return Collections.unmodifiableList(Channel.listFromJson(new JSONArray(raw)));
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private void save(String key, List<Channel> list) {
        prefs.edit().putString(key, Channel.listToJson(list).toString()).apply();
    }

    @NonNull
    static String normalize(@Nullable String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return n.replaceAll("[\\s\\p{Punct}　・]+", "");
    }

    @Nullable
    private static String hostOf(@Nullable String url) {
        if (url == null) return null;
        try {
            return Uri.parse(url).getHost();
        } catch (Exception e) {
            return null;
        }
    }
}
