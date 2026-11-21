package xyz.enoki.castify.media;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * ネイティブ側で扱う 1 チャンネル（AzuraCast ステーション）の情報。
 * JS 側の ChannelItem から必要な項目だけを抜き出した軽量モデル。
 */
public final class Channel {

    /** JS 側と同じ ID 形式: "{正規化ドメイン}#{station.id}" */
    @NonNull public final String id;
    @NonNull public final String domain;
    @NonNull public final String shortcode;
    @NonNull public final String name;
    @Nullable public final String description;
    @NonNull public final String streamUrl;
    /** ステーションのフォールバック画像（曲のアートワークが無いときに使う） */
    @Nullable public final String artUrl;
    @Nullable public final String songTitle;
    @Nullable public final String songArtist;
    @Nullable public final String songAlbum;
    @Nullable public final String songArtUrl;
    public final boolean isLive;
    @Nullable public final String streamerName;

    public Channel(
            @NonNull String id,
            @NonNull String domain,
            @NonNull String shortcode,
            @NonNull String name,
            @Nullable String description,
            @NonNull String streamUrl,
            @Nullable String artUrl,
            @Nullable String songTitle,
            @Nullable String songArtist,
            @Nullable String songAlbum,
            @Nullable String songArtUrl,
            boolean isLive,
            @Nullable String streamerName) {
        this.id = id;
        this.domain = domain;
        this.shortcode = shortcode;
        this.name = name;
        this.description = emptyToNull(description);
        this.streamUrl = streamUrl;
        this.artUrl = absoluteOrNull(artUrl);
        this.songTitle = emptyToNull(songTitle);
        this.songArtist = emptyToNull(songArtist);
        this.songAlbum = emptyToNull(songAlbum);
        this.songArtUrl = absoluteOrNull(songArtUrl);
        this.isLive = isLive;
        this.streamerName = emptyToNull(streamerName);
    }

    /** 再生画面に出すアートワーク（曲 → ステーションの順） */
    @Nullable
    public String displayArtUrl() {
        return songArtUrl != null ? songArtUrl : artUrl;
    }

    /** 一覧のサブタイトル（「曲名 - アーティスト」） */
    @Nullable
    public String nowPlayingLine() {
        if (isLive && streamerName != null && songTitle == null) {
            return "LIVE: " + streamerName;
        }
        if (songTitle != null && songArtist != null) return songTitle + " - " + songArtist;
        if (songTitle != null) return songTitle;
        return description;
    }

    /** 曲情報だけを差し替えた新しいインスタンスを返す（ID・ストリームURLは維持） */
    @NonNull
    public Channel withNowPlaying(@NonNull Channel fresh) {
        return new Channel(
                id,
                domain,
                shortcode,
                fresh.name,
                fresh.description != null ? fresh.description : description,
                streamUrl,
                fresh.artUrl != null ? fresh.artUrl : artUrl,
                fresh.songTitle,
                fresh.songArtist,
                fresh.songAlbum,
                fresh.songArtUrl,
                fresh.isLive,
                fresh.streamerName);
    }

    /** 曲情報（メタデータ）が同じかどうか */
    public boolean sameNowPlaying(@NonNull Channel other) {
        return Objects.equals(songTitle, other.songTitle)
                && Objects.equals(songArtist, other.songArtist)
                && Objects.equals(songAlbum, other.songAlbum)
                && Objects.equals(songArtUrl, other.songArtUrl)
                && isLive == other.isLive
                && Objects.equals(streamerName, other.streamerName)
                && Objects.equals(name, other.name);
    }

    // ---------------------------------------------------------------------
    // JSON 変換（SharedPreferences 保存用 / JS ブリッジ用）
    // ---------------------------------------------------------------------

    @NonNull
    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("domain", domain);
            o.put("shortcode", shortcode);
            o.put("name", name);
            o.putOpt("description", description);
            o.put("streamUrl", streamUrl);
            o.putOpt("artUrl", artUrl);
            o.putOpt("songTitle", songTitle);
            o.putOpt("songArtist", songArtist);
            o.putOpt("songAlbum", songAlbum);
            o.putOpt("songArtUrl", songArtUrl);
            o.put("isLive", isLive);
            o.putOpt("streamerName", streamerName);
        } catch (JSONException ignored) {
            // put は null 以外の値では例外を投げない
        }
        return o;
    }

    /** JSON から復元する。必須項目が欠けていれば null */
    @Nullable
    public static Channel fromJson(@Nullable JSONObject o) {
        if (o == null) return null;
        String id = o.optString("id", "");
        String streamUrl = o.optString("streamUrl", "");
        if (id.isEmpty() || streamUrl.isEmpty()) return null;
        String name = o.optString("name", "");
        return new Channel(
                id,
                o.optString("domain", ""),
                o.optString("shortcode", ""),
                name.isEmpty() ? "Castify" : name,
                optString(o, "description"),
                streamUrl,
                optString(o, "artUrl"),
                optString(o, "songTitle"),
                optString(o, "songArtist"),
                optString(o, "songAlbum"),
                optString(o, "songArtUrl"),
                o.optBoolean("isLive", false),
                optString(o, "streamerName"));
    }

    @NonNull
    public static List<Channel> listFromJson(@Nullable JSONArray arr) {
        List<Channel> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            Channel ch = fromJson(arr.optJSONObject(i));
            if (ch != null) list.add(ch);
        }
        return list;
    }

    @NonNull
    public static JSONArray listToJson(@NonNull List<Channel> list) {
        JSONArray arr = new JSONArray();
        for (Channel ch : list) arr.put(ch.toJson());
        return arr;
    }

    @Nullable
    private static String optString(JSONObject o, String key) {
        if (!o.has(key) || o.isNull(key)) return null;
        return emptyToNull(o.optString(key, null));
    }

    @Nullable
    private static String emptyToNull(@Nullable String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** "/thumbnail.jpg" のようなアプリ内相対パスはネイティブでは使えないので捨てる */
    @Nullable
    private static String absoluteOrNull(@Nullable String url) {
        String u = emptyToNull(url);
        if (u == null) return null;
        String lower = u.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return null;
        if (lower.endsWith("/thumbnail.jpg")) return null;
        return u;
    }
}
