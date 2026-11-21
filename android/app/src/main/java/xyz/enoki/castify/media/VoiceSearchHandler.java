package xyz.enoki.castify.media;

import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;

/**
 * スマホ側の音声コマンド（MEDIA_PLAY_FROM_SEARCH インテント）を再生サービスに渡す。
 * 例: 「OK Google、Castify で ○○ を再生して」
 * （Android Auto 上の音声コマンドは Media3 がセッション経由で直接処理する）
 */
public final class VoiceSearchHandler {

    private static final String TAG = "CastifyVoiceSearch";

    private VoiceSearchHandler() {}

    /** @return インテントを処理した場合 true */
    public static boolean handle(@NonNull Context context, @Nullable Intent intent) {
        if (intent == null || !MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH.equals(intent.getAction())) {
            return false;
        }
        String query = intent.getStringExtra(SearchManager.QUERY);
        Context app = context.getApplicationContext();
        SessionToken token = new SessionToken(app, new ComponentName(app, CastifyPlaybackService.class));
        ListenableFuture<MediaController> future = new MediaController.Builder(app, token)
                .setApplicationLooper(Looper.getMainLooper())
                .buildAsync();
        future.addListener(() -> {
            try {
                MediaController controller = future.get();
                MediaItem request = new MediaItem.Builder()
                        .setRequestMetadata(new MediaItem.RequestMetadata.Builder()
                                .setSearchQuery(query != null ? query : "")
                                .setExtras(intent.getExtras())
                                .build())
                        .build();
                controller.setMediaItem(request);
                controller.prepare();
                controller.play();
            } catch (Exception e) {
                Log.w(TAG, "voice search failed", e);
            }
            // コマンド送信後に接続を解放（再生自体はサービス側で継続する）
            new Handler(Looper.getMainLooper()).postDelayed(
                    () -> MediaController.releaseFuture(future), 5_000);
        }, ContextCompat.getMainExecutor(app));
        // インテントを消費したことにして、次回以降の再処理を防ぐ
        intent.setAction(Intent.ACTION_MAIN);
        return true;
    }
}
