package xyz.enoki.castify.media;

import android.content.ComponentName;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * WebView（Nuxt）から CastifyPlaybackService を操作するための Capacitor プラグイン。
 *
 * JS 側からは registerPlugin('CastifyPlayer') で呼び出す（app/utils/nativePlayer.ts）。
 * 再生状態が変わるたびに "stateChange" イベントを JS に通知するので、
 * Android Auto や通知から操作された場合もアプリの UI が追従する。
 */
@CapacitorPlugin(name = "CastifyPlayer")
public class CastifyPlayerPlugin extends Plugin {

    private static final String TAG = "CastifyPlayerPlugin";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ChannelRepository repo;
    @Nullable private ListenableFuture<MediaController> controllerFuture;
    @Nullable private MediaController controller;

    private interface ControllerAction {
        void run(@NonNull MediaController controller);
    }

    @Override
    public void load() {
        super.load();
        repo = ChannelRepository.get(getContext());
        mainHandler.post(this::connect);
    }

    @Override
    protected void handleOnDestroy() {
        mainHandler.post(() -> {
            if (controllerFuture != null) MediaController.releaseFuture(controllerFuture);
            controllerFuture = null;
            controller = null;
        });
        super.handleOnDestroy();
    }

    private void connect() {
        if (controllerFuture != null) return;
        SessionToken token = new SessionToken(getContext(),
                new ComponentName(getContext(), CastifyPlaybackService.class));
        ListenableFuture<MediaController> future = new MediaController.Builder(getContext(), token)
                .setApplicationLooper(Looper.getMainLooper())
                .buildAsync();
        controllerFuture = future;
        future.addListener(() -> {
            try {
                controller = future.get();
                controller.addListener(new Player.Listener() {
                    @Override
                    public void onEvents(@NonNull Player player, @NonNull Player.Events events) {
                        emitState();
                    }
                });
                emitState();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "MediaController connection failed", e);
                controllerFuture = null;
            }
        }, ContextCompat.getMainExecutor(getContext()));
    }

    /** コントローラ接続後にメインスレッドで実行する */
    private void withController(@NonNull PluginCall call, @NonNull ControllerAction action) {
        mainHandler.post(() -> {
            if (controller != null && controller.isConnected()) {
                runAction(call, controller, action);
                return;
            }
            controller = null;
            controllerFuture = null;
            connect();
            ListenableFuture<MediaController> future = controllerFuture;
            if (future == null) {
                call.reject("Player service unavailable");
                return;
            }
            future.addListener(() -> {
                if (controller != null) {
                    runAction(call, controller, action);
                } else {
                    call.reject("Player service unavailable");
                }
            }, ContextCompat.getMainExecutor(getContext()));
        });
    }

    private void runAction(PluginCall call, MediaController c, ControllerAction action) {
        try {
            action.run(c);
            call.resolve(buildState());
        } catch (Exception e) {
            Log.e(TAG, "player command failed", e);
            call.reject(e.getMessage() != null ? e.getMessage() : "Player command failed", e);
        }
    }

    // ---------------------------------------------------------------------
    // JS から呼ばれるメソッド
    // ---------------------------------------------------------------------

    /** マイプレイリストとおすすめをネイティブ側（Android Auto 用）に保存する */
    @PluginMethod
    public void syncLibrary(PluginCall call) {
        JSArray playlist = call.getArray("playlist", new JSArray());
        JSArray recommended = call.getArray("recommended", null);
        List<Channel> p = Channel.listFromJson(playlist);
        List<Channel> r = recommended != null ? Channel.listFromJson(recommended) : null;
        repo.setLibrary(p, r);
        JSObject ret = new JSObject();
        ret.put("playlist", p.size());
        ret.put("recommended", r != null ? r.size() : -1);
        call.resolve(ret);
    }

    /** queue[startIndex] から再生を開始する */
    @PluginMethod
    public void play(PluginCall call) {
        JSArray queueJson = call.getArray("queue", new JSArray());
        List<Channel> queue = Channel.listFromJson(queueJson);
        if (queue.isEmpty()) {
            call.reject("queue is empty");
            return;
        }
        int startIndex = Math.max(0, Math.min(queue.size() - 1, call.getInt("startIndex", 0)));
        repo.setLastQueue(queue);

        List<MediaItem> mediaItems = new ArrayList<>(queue.size());
        for (Channel ch : queue) {
            mediaItems.add(new MediaItem.Builder()
                    .setMediaId(ch.id)
                    .setMediaMetadata(new MediaMetadata.Builder().setTitle(ch.name).build())
                    .build());
        }
        withController(call, c -> {
            c.setMediaItems(mediaItems, startIndex, C.TIME_UNSET);
            c.prepare();
            c.play();
        });
    }

    @PluginMethod
    public void pause(PluginCall call) {
        withController(call, Player::pause);
    }

    @PluginMethod
    public void resume(PluginCall call) {
        withController(call, c -> {
            if (c.getPlaybackState() == Player.STATE_IDLE) c.prepare();
            c.play();
        });
    }

    @PluginMethod
    public void stop(PluginCall call) {
        withController(call, c -> {
            c.pause();
            c.stop();
        });
    }

    @PluginMethod
    public void next(PluginCall call) {
        withController(call, c -> {
            c.seekToNextMediaItem();
            if (c.getPlaybackState() == Player.STATE_IDLE) c.prepare();
            c.play();
        });
    }

    @PluginMethod
    public void previous(PluginCall call) {
        withController(call, c -> {
            c.seekToPreviousMediaItem();
            if (c.getPlaybackState() == Player.STATE_IDLE) c.prepare();
            c.play();
        });
    }

    @PluginMethod
    public void setVolume(PluginCall call) {
        Double v = call.getDouble("volume", 1.0);
        float volume = (float) Math.max(0.0, Math.min(1.0, v != null ? v : 1.0));
        withController(call, c -> c.setVolume(volume));
    }

    @PluginMethod
    public void getState(PluginCall call) {
        withController(call, c -> { /* 状態を返すだけ */ });
    }

    // ---------------------------------------------------------------------
    // 状態通知
    // ---------------------------------------------------------------------

    private void emitState() {
        notifyListeners("stateChange", buildState());
    }

    @NonNull
    private JSObject buildState() {
        JSObject s = new JSObject();
        MediaController c = controller;
        s.put("connected", c != null && c.isConnected());
        if (c == null) return s;

        int state = c.getPlaybackState();
        PlaybackException error = c.getPlayerError();
        s.put("isPlaying", c.isPlaying());
        s.put("playWhenReady", c.getPlayWhenReady());
        s.put("isLoading", state == Player.STATE_BUFFERING
                || (c.getPlayWhenReady() && state == Player.STATE_IDLE && error == null && c.getMediaItemCount() > 0));
        s.put("volume", c.getVolume());
        s.put("currentIndex", c.getCurrentMediaItemIndex());

        MediaItem current = c.getCurrentMediaItem();
        if (current != null) {
            s.put("channelId", current.mediaId);
            MediaMetadata md = c.getMediaMetadata();
            if (md.title != null) s.put("title", md.title.toString());
            if (md.artist != null) s.put("artist", md.artist.toString());
        }

        JSArray queueIds = new JSArray();
        for (int i = 0; i < c.getMediaItemCount(); i++) {
            queueIds.put(c.getMediaItemAt(i).mediaId);
        }
        s.put("queueIds", queueIds);

        if (error != null) {
            s.put("errorCode", error.getErrorCodeName());
            s.put("errorMessage", error.getMessage());
        }
        return s;
    }
}
