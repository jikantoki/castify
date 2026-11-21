package xyz.enoki.castify.media;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaConstants;

import java.util.ArrayList;
import java.util.List;

import xyz.enoki.castify.R;

/**
 * Channel から Media3 の MediaItem（ブラウズ一覧用 / 再生用）を組み立てるヘルパー。
 */
@OptIn(markerClass = UnstableApi.class)
public final class MediaItemFactory {

    public static final String ROOT_ID = "castify:root";
    public static final String PLAYLIST_ID = "castify:playlist";
    public static final String RECOMMENDED_ID = "castify:recommended";
    public static final String EMPTY_ID_PREFIX = "castify:empty:";

    private final Context context;

    public MediaItemFactory(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    @NonNull
    public MediaItem root() {
        Bundle extras = new Bundle();
        // チャンネルはリスト表示、フォルダ（タブ）もリスト表示
        extras.putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM);
        extras.putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM);
        return new MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(new MediaMetadata.Builder()
                        .setTitle(context.getString(R.string.app_name))
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .setExtras(extras)
                        .build())
                .build();
    }

    @NonNull
    public MediaItem playlistFolder() {
        return folder(PLAYLIST_ID, R.string.auto_tab_playlist, R.drawable.ic_auto_playlist);
    }

    @NonNull
    public MediaItem recommendedFolder() {
        return folder(RECOMMENDED_ID, R.string.auto_tab_recommended, R.drawable.ic_auto_recommended);
    }

    @NonNull
    private MediaItem folder(String id, int titleRes, @DrawableRes int iconRes) {
        return new MediaItem.Builder()
                .setMediaId(id)
                .setMediaMetadata(new MediaMetadata.Builder()
                        .setTitle(context.getString(titleRes))
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                        .setArtworkUri(resourceUri(iconRes))
                        .build())
                .build();
    }

    /** 一覧が空のときに表示する案内用アイテム（タップしても何も起きない） */
    @NonNull
    public MediaItem emptyMessage(@NonNull String parentId, int titleRes, int subtitleRes) {
        return new MediaItem.Builder()
                .setMediaId(EMPTY_ID_PREFIX + parentId)
                .setMediaMetadata(new MediaMetadata.Builder()
                        .setTitle(context.getString(titleRes))
                        .setSubtitle(context.getString(subtitleRes))
                        .setIsBrowsable(false)
                        .setIsPlayable(false)
                        .setArtworkUri(resourceUri(R.drawable.ic_auto_info))
                        .build())
                .build();
    }

    /** ブラウズ一覧用（局名 + 現在の曲） */
    @NonNull
    public MediaItem browsable(@NonNull Channel ch) {
        String line = ch.nowPlayingLine();
        return new MediaItem.Builder()
                .setMediaId(ch.id)
                .setMediaMetadata(new MediaMetadata.Builder()
                        .setTitle(ch.name)
                        .setSubtitle(line)
                        .setArtist(line)
                        .setStation(ch.name)
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                        .setArtworkUri(artworkUri(ch.artUrl != null ? ch.artUrl : ch.songArtUrl))
                        .build())
                .build();
    }

    @NonNull
    public List<MediaItem> browsable(@NonNull List<Channel> list) {
        List<MediaItem> items = new ArrayList<>(list.size());
        for (Channel ch : list) items.add(browsable(ch));
        return items;
    }

    /** 再生用（URI 付き。メタデータは再生画面向けに曲名・アーティストを入れる） */
    @NonNull
    public MediaItem playable(@NonNull Channel ch) {
        return new MediaItem.Builder()
                .setMediaId(ch.id)
                .setUri(ch.streamUrl)
                .setMediaMetadata(nowPlayingMetadata(ch))
                .build();
    }

    @NonNull
    public List<MediaItem> playable(@NonNull List<Channel> list) {
        List<MediaItem> items = new ArrayList<>(list.size());
        for (Channel ch : list) items.add(playable(ch));
        return items;
    }

    @NonNull
    public MediaMetadata nowPlayingMetadata(@NonNull Channel ch) {
        String title = ch.songTitle != null ? ch.songTitle : ch.name;
        String artist;
        if (ch.songArtist != null) {
            artist = ch.songArtist;
        } else if (ch.isLive && ch.streamerName != null) {
            artist = "LIVE: " + ch.streamerName;
        } else {
            artist = ch.songTitle != null ? ch.name : ch.description;
        }
        return new MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(ch.songAlbum != null ? ch.songAlbum : ch.name)
                .setStation(ch.name)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                .setArtworkUri(artworkUri(ch.displayArtUrl()))
                .build();
    }

    @NonNull
    private Uri artworkUri(@Nullable String url) {
        Uri content = ArtworkProvider.toContentUri(context, url);
        return content != null ? content : resourceUri(R.drawable.castify_artwork);
    }

    @NonNull
    private Uri resourceUri(@DrawableRes int res) {
        return new Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(context.getResources().getResourcePackageName(res))
                .appendPath(context.getResources().getResourceTypeName(res))
                .appendPath(context.getResources().getResourceEntryName(res))
                .build();
    }
}
