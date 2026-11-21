package xyz.enoki.castify;

import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;
import com.getcapacitor.BridgeActivity;
import xyz.enoki.castify.media.CastifyPlayerPlugin;
import xyz.enoki.castify.media.VoiceSearchHandler;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // ネイティブ再生（Media3 / Android Auto）用のローカルプラグインを登録
        registerPlugin(CastifyPlayerPlugin.class);
        super.onCreate(savedInstanceState);
        // Androidでアプリ起動中は画面をオフにしない
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // 「OK Google、Castify で○○を再生」で起動された場合
        VoiceSearchHandler.handle(this, getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        VoiceSearchHandler.handle(this, intent);
    }
}
