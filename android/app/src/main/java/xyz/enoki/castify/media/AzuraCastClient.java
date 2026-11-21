package xyz.enoki.castify.media;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * AzuraCast の Now Playing API をネイティブ側から叩くクライアント。
 * app/utils/azuracast.ts の URL 補正ロジック（fixStreamUrl / fixArtUrl / getChannelArtUrl）を移植している。
 */
public final class AzuraCastClient {

    private static final String TAG = "CastifyAzuraCast";
    private static final int CONNECT_TIMEOUT_MS = 4000;
    private static final int READ_TIMEOUT_MS = 6000;
    private static final int MAX_BODY_BYTES = 4 * 1024 * 1024;
    public static final String USER_AGENT = "Castify/1.0 (Android; Media3)";

    /** JS 側の DEFAULT_RECOMMENDED_DOMAINS と同じ */
    public static final String[] DEFAULT_RECOMMENDED_DOMAINS = {
        "https://radio.enoki.xyz",
        "https://cast.architectural-n.net",
    };

    private AzuraCastClient() {}

    /** {domain}/api/nowplaying から全ステーションを取得 */
    @NonNull
    public static List<Channel> fetchDomain(@NonNull String domain) throws IOException {
        String norm = normalizeDomain(domain);
        String body = httpGet(norm + "/api/nowplaying");
        List<Channel> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(body);
            for (int i = 0; i < arr.length(); i++) {
                Channel ch = parseNowPlaying(arr.optJSONObject(i), norm);
                if (ch != null) list.add(ch);
            }
        } catch (Exception e) {
            throw new IOException("Invalid nowplaying response from " + norm, e);
        }
        return list;
    }

    /** {domain}/api/nowplaying/{shortcode} から単一ステーションを取得 */
    @Nullable
    public static Channel fetchStation(@NonNull Channel base) throws IOException {
        String norm = normalizeDomain(base.domain);
        if (norm.isEmpty() || base.shortcode.isEmpty()) return null;
        String body = httpGet(norm + "/api/nowplaying/" + Uri.encode(base.shortcode));
        try {
            return parseNowPlaying(new JSONObject(body), norm);
        } catch (Exception e) {
            throw new IOException("Invalid nowplaying response for " + base.id, e);
        }
    }

    /** おすすめドメインのチャンネルをまとめて取得（失敗したドメインはスキップ） */
    @NonNull
    public static List<Channel> fetchRecommended() {
        List<Channel> all = new ArrayList<>();
        for (String domain : DEFAULT_RECOMMENDED_DOMAINS) {
            try {
                all.addAll(fetchDomain(domain));
            } catch (IOException e) {
                Log.w(TAG, "recommended fetch failed: " + domain, e);
            }
        }
        return all;
    }

    // ---------------------------------------------------------------------
    // パース
    // ---------------------------------------------------------------------

    @Nullable
    static Channel parseNowPlaying(@Nullable JSONObject np, @NonNull String normDomain) {
        if (np == null) return null;
        JSONObject station = np.optJSONObject("station");
        if (station == null) return null;

        String stationId = station.optString("id", "");
        String shortcode = station.optString("shortcode", "");
        if (shortcode.isEmpty()) shortcode = stationId;
        if (stationId.isEmpty()) return null;

        String listenUrl = optString(station, "listen_url");
        if (listenUrl == null) {
            JSONArray mounts = station.optJSONArray("mounts");
            if (mounts != null && mounts.length() > 0) {
                JSONObject m = mounts.optJSONObject(0);
                if (m != null) listenUrl = optString(m, "url");
            }
        }
        String streamUrl = fixStreamUrl(listenUrl, normDomain, shortcode);

        JSONObject nowPlaying = np.optJSONObject("now_playing");
        JSONObject song = nowPlaying != null ? nowPlaying.optJSONObject("song") : null;
        JSONObject live = np.optJSONObject("live");

        String songTitle = song != null ? optString(song, "title") : null;
        String songArtist = song != null ? optString(song, "artist") : null;
        String songAlbum = song != null ? optString(song, "album") : null;
        String songArt = song != null ? usableArt(optString(song, "art"), normDomain) : null;

        boolean isLive = live != null && live.optBoolean("is_live", false);
        String streamerName = live != null ? optString(live, "streamer_name") : null;

        return new Channel(
                normDomain + "#" + stationId,
                normDomain,
                shortcode,
                station.optString("name", shortcode),
                optString(station, "description"),
                streamUrl,
                channelArtUrl(np, normDomain, shortcode),
                songTitle,
                songArtist,
                songAlbum,
                songArt,
                isLive,
                streamerName);
    }

    /** getChannelArtUrl() の移植 */
    @Nullable
    private static String channelArtUrl(JSONObject np, String normDomain, String shortcode) {
        String art = songArt(np.optJSONObject("now_playing"), normDomain);
        if (art != null) return art;
        art = songArt(np.optJSONObject("playing_next"), normDomain);
        if (art != null) return art;
        JSONArray history = np.optJSONArray("song_history");
        if (history != null) {
            for (int i = 0; i < history.length(); i++) {
                art = songArt(history.optJSONObject(i), normDomain);
                if (art != null) return art;
            }
        }
        JSONObject live = np.optJSONObject("live");
        if (live != null) {
            art = usableArt(optString(live, "art"), normDomain);
            if (art != null) return art;
        }
        switch (shortcode) {
            case "neruru-radio":
                return normDomain + "/static/uploads/neruru-radio/album_art.1795069862.jpg";
            case "lofi":
                return normDomain + "/static/uploads/lofi/album_art.1790098211.png";
            case "nerurus_room_rest":
                return normDomain + "/static/uploads/nerurus_room_rest/album_art.1794852265.png";
            default:
                return null;
        }
    }

    @Nullable
    private static String songArt(@Nullable JSONObject holder, String normDomain) {
        if (holder == null) return null;
        JSONObject song = holder.optJSONObject("song");
        if (song == null) return null;
        return usableArt(optString(song, "art"), normDomain);
    }

    @Nullable
    private static String usableArt(@Nullable String raw, String normDomain) {
        String fixed = fixArtUrl(raw, normDomain);
        if (fixed == null || fixed.endsWith("/thumbnail.jpg")) return null;
        return fixed;
    }

    // ---------------------------------------------------------------------
    // URL 補正（azuracast.ts と同じ挙動）
    // ---------------------------------------------------------------------

    @NonNull
    public static String normalizeDomain(@Nullable String input) {
        if (input == null) return "";
        String cleaned = input.trim();
        if (cleaned.isEmpty()) return "";
        if (!cleaned.matches("(?i)^https?://.*")) cleaned = "https://" + cleaned;
        try {
            URL url = new URL(cleaned);
            String origin = url.getProtocol() + "://" + url.getHost();
            if (url.getPort() != -1 && url.getPort() != url.getDefaultPort()) {
                origin += ":" + url.getPort();
            }
            return origin;
        } catch (Exception e) {
            return cleaned.replaceAll("/+$", "");
        }
    }

    @NonNull
    static String fixStreamUrl(@Nullable String listenUrl, String domain, String shortcode) {
        String normDomain = normalizeDomain(domain);
        if (listenUrl == null || listenUrl.isEmpty() || listenUrl.contains(".onion")) {
            return normDomain + "/listen/" + shortcode + "/radio.mp3";
        }
        if (listenUrl.startsWith("/")) return normDomain + listenUrl;
        if (normDomain.startsWith("https://")
                && listenUrl.startsWith("http://")
                && !listenUrl.contains("localhost")) {
            try {
                String streamHost = new URL(listenUrl).getHost();
                String domainHost = new URL(normDomain).getHost();
                if (streamHost.equals(domainHost)) {
                    return "https://" + listenUrl.substring("http://".length());
                }
            } catch (Exception ignored) {
                // そのまま返す
            }
        }
        return listenUrl;
    }

    @Nullable
    static String fixArtUrl(@Nullable String artUrl, String domain) {
        if (artUrl == null || artUrl.trim().isEmpty()) return null;
        String normDomain = normalizeDomain(domain);
        if (artUrl.contains(".onion")) {
            try {
                URL u = new URL(artUrl);
                return normDomain + u.getPath() + (u.getQuery() != null ? "?" + u.getQuery() : "");
            } catch (Exception e) {
                return null;
            }
        }
        if (artUrl.startsWith("/")) return normDomain + artUrl;
        return artUrl;
    }

    // ---------------------------------------------------------------------
    // HTTP
    // ---------------------------------------------------------------------

    @NonNull
    private static String httpGet(@NonNull String endpoint) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", USER_AGENT);
        try {
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code + " for " + endpoint);
            }
            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int total = 0;
                int n;
                while ((n = in.read(buf)) != -1) {
                    total += n;
                    if (total > MAX_BODY_BYTES) throw new IOException("Response too large");
                    out.write(buf, 0, n);
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            conn.disconnect();
        }
    }

    @Nullable
    private static String optString(JSONObject o, String key) {
        if (o == null || !o.has(key) || o.isNull(key)) return null;
        String s = o.optString(key, "").trim();
        return s.isEmpty() ? null : s;
    }
}
