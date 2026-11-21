package xyz.enoki.castify.media;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Web 上のアートワーク画像を content:// URI として提供する ContentProvider。
 *
 * Android Auto はブラウズ一覧の画像を ContentResolver 経由で読み込むため、http(s) の URL を
 * そのまま渡しても表示されない。そこで画像をアプリのキャッシュに保存してから返す。
 * 任意 URL の取得に悪用されないよう、リポジトリに登録済みのホストだけを許可している。
 */
public class ArtworkProvider extends ContentProvider {

    private static final String TAG = "CastifyArtwork";
    private static final long MAX_AGE_MS = 6L * 60 * 60 * 1000; // 6時間
    private static final int MAX_BYTES = 5 * 1024 * 1024;

    @NonNull
    public static String authority(@NonNull Context context) {
        return context.getPackageName() + ".artwork";
    }

    /** http(s) の画像 URL を content:// URI に変換する。使えない URL なら null */
    @Nullable
    public static Uri toContentUri(@NonNull Context context, @Nullable String url) {
        if (url == null) return null;
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("https://") && !lower.startsWith("http://")) return null;
        String encoded = Base64.encodeToString(
                url.getBytes(StandardCharsets.UTF_8),
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        return new Uri.Builder()
                .scheme("content")
                .authority(authority(context))
                .appendPath(encoded)
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        Context context = getContext();
        if (context == null) throw new FileNotFoundException("No context");
        if (!"r".equals(mode)) throw new FileNotFoundException("Read only");

        String url = decode(uri);
        if (url == null) throw new FileNotFoundException("Invalid uri");
        Uri parsed = Uri.parse(url);
        String scheme = parsed.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            throw new FileNotFoundException("Unsupported scheme");
        }
        if (!ChannelRepository.get(context).isKnownHost(parsed.getHost())) {
            throw new FileNotFoundException("Host not allowed");
        }

        File dir = new File(context.getCacheDir(), "artwork");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File file = new File(dir, sha1(url));
        boolean fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < MAX_AGE_MS;
        if (!fresh) {
            try {
                download(url, dir, file);
            } catch (IOException e) {
                Log.w(TAG, "artwork download failed: " + url, e);
                if (!file.exists()) throw new FileNotFoundException("Download failed");
                // 古いキャッシュがあればそれを返す
            }
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    private static void download(String url, File dir, File target) throws IOException {
        File tmp = File.createTempFile("art", ".tmp", dir);
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(4000);
        conn.setReadTimeout(6000);
        conn.setRequestProperty("User-Agent", AzuraCastClient.USER_AGENT);
        try {
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
            try (InputStream in = conn.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[16 * 1024];
                int total = 0;
                int n;
                while ((n = in.read(buf)) != -1) {
                    total += n;
                    if (total > MAX_BYTES) throw new IOException("Image too large");
                    out.write(buf, 0, n);
                }
            }
            if (!tmp.renameTo(target)) {
                //noinspection ResultOfMethodCallIgnored
                target.delete();
                if (!tmp.renameTo(target)) throw new IOException("rename failed");
            }
        } finally {
            conn.disconnect();
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    @Nullable
    private static String decode(Uri uri) {
        String seg = uri.getLastPathSegment();
        if (seg == null) return null;
        try {
            return new String(Base64.decode(seg, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING),
                    StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String sha1(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return "image/*";
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        return 0;
    }
}
