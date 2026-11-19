<template lang="pug">
v-dialog(
  v-model="modelValueInternal"
  :max-width="isMobile ? undefined : 640"
  :fullscreen="isMobile"
  :transition="isMobile ? 'dialog-bottom-transition' : 'dialog-transition'"
  scrollable
)
  v-card.detail-modal-card
    //- ヘッダー
    v-card-title.d-flex.align-center.justify-space-between.py-3.px-4.border-b
      .d-flex.align-center.gap-2
        v-icon(color="primary" icon="mdi-radio-tower")
        span.font-weight-bold.text-subtitle-1 チャンネル・配信詳細情報
      v-btn(
        icon="mdi-close"
        variant="text"
        density="comfortable"
        @click="close"
      )

    //- タブ選択
    v-tabs(
      v-model="activeTab"
      color="primary"
      density="comfortable"
      grow
    )
      v-tab(value="now")
        v-icon.mr-1(size="18") mdi-broadcast
        | 配信中
      v-tab(value="next")
        v-icon.mr-1(size="18") mdi-skip-next-circle
        | 次の配信
      v-tab(value="history")
        v-icon.mr-1(size="18") mdi-history
        | 履歴
      v-tab(value="info")
        v-icon.mr-1(size="18") mdi-information-outline
        | 局情報

    //- コンテンツ
    v-card-text.pa-4.modal-body(style="max-height: 75vh; overflow-y: auto;max-width: 100dvw;")
      v-window(v-model="activeTab")

        //- 1. 現在の配信 (now)
        v-window-item(value="now")
          .now-playing-tab
            //- ライブ配信バッジ
            v-alert.mb-4(
              v-if="liveInfo.is_live"
              color="error"
              variant="tonal"
              density="comfortable"
              prepend-icon="mdi-record-rec"
            )
              .font-weight-bold 生放送中！
              .text-caption 配信者: {{ liveInfo.streamer_name || 'DJ' }}

            //- 現在の曲カード
            .d-flex.align-center.gap-4.mb-4.pa-3.rounded-lg.bg-surface-variant(style="background: rgba(var(--v-theme-on-surface), 0.2);")
              v-avatar(size="80" rounded="lg" elevation="2")
                v-img(
                  :src="currentSong.art || '/thumbnail.jpg'"
                  cover
                )
              .flex-grow-1(style="min-width: 0;")
                .text-caption.text-primary.font-weight-bold NOW PLAYING
                .text-h6.font-weight-bold.text-truncate(style="overflow: hidden") {{ currentSong.title || 'タイトルなし' }}
                .text-subtitle-2.text-medium-emphasis.text-truncate(style="overflow: hidden") {{ currentSong.artist || 'アーティスト不明' }}
                .text-caption.text-disabled.text-truncate(v-if="currentSong.album" style="overflow: hidden;") {{ currentSong.album }}

            //- 詳細スペックリスト
            v-list(density="compact" lines="one" bg-color="transparent")
              v-list-item(prepend-icon="mdi-music-box-outline")
                v-list-item-title ジャンル
                template(#append)
                  span.text-caption.text-medium-emphasis(style="max-width: 50vw; max-height: 8em; overflow: auto;") {{ currentSong.genre || '未設定' }}
              v-list-item(prepend-icon="mdi-account-group-outline")
                v-list-item-title リスナー数
                template(#append)
                  v-chip(size="x-small" color="primary" variant="flat") {{ listeners.current }} 人
              v-list-item(prepend-icon="mdi-timer-sand")
                v-list-item-title 再生時間
                template(#append)
                  span.text-caption.font-mono {{ formatDuration(currentSongItem?.elapsed || 0) }} / {{ formatDuration(currentSongItem?.duration || 0) }}

            //- 歌詞（もしあれば）
            .lyrics-box.mt-4(v-if="currentSong.lyrics")
              .text-subtitle-2.font-weight-bold.mb-2 歌詞
              .lyrics-content.pa-3.rounded.bg-surface-variant.text-body-2(style="white-space: pre-wrap;")
                | {{ currentSong.lyrics }}

        //- 2. 次の配信内容 (next)
        v-window-item(value="next")
          .next-playing-tab
            .mb-3.text-caption.text-medium-emphasis
              | AzuraCastのキューに基づき、次に再生が予定されている楽曲です。

            div(v-if="nextSong")
              .d-flex.align-center.gap-4.pa-3.rounded-lg.bg-surface-variant.mb-4(style="background: rgba(var(--v-theme-on-surface), 0.2);")
                v-avatar(size="72" rounded="lg" elevation="2")
                  v-img(
                    :src="nextSong.art || '/thumbnail.jpg'"
                    cover
                  )
                .flex-grow-1(style="min-width: 0;")
                  .text-caption.text-secondary.font-weight-bold NEXT TRACK
                  .text-subtitle-1.font-weight-bold.text-truncate {{ nextSong.title }}
                  .text-body-2.text-medium-emphasis.text-truncate {{ nextSong.artist }}
                  .text-caption.text-disabled(v-if="playingNextItem?.duration")
                    | 長さ: {{ formatDuration(playingNextItem.duration) }}

              v-list(density="compact" bg-color="transparent")
                v-list-item(v-if="nextSong.album" prepend-icon="mdi-album")
                  v-list-item-title アルバム
                  template(#append)
                    span.text-caption {{ nextSong.album }}
                v-list-item(v-if="nextSong.genre" prepend-icon="mdi-tag-outline")
                  v-list-item-title ジャンル
                  template(#append)
                    span.text-caption(style="max-width: 50vw; max-height: 8em; overflow: auto;")  {{ nextSong.genre }}
                v-list-item(v-if="playingNextItem?.playlist" prepend-icon="mdi-playlist-music")
                  v-list-item-title プレイリスト
                  template(#append)
                    span.text-caption {{ playingNextItem.playlist }}

            div.text-center.py-8(v-else)
              v-icon(size="48" color="medium-emphasis" icon="mdi-help-circle-outline")
              p.text-body-2.text-medium-emphasis.mt-2 次の配信内容は未定、または設定されていません

        //- 3. 過去の配信履歴 (history)
        v-window-item(value="history")
          .history-tab
            .d-flex.align-center.justify-space-between.mb-2
              span.text-caption.text-medium-emphasis 最近再生された楽曲（直近）
              span.text-caption.text-medium-emphasis {{ songHistory.length }} 件

            div(v-if="songHistory.length > 0")
              v-list(lines="two" density="comfortable" bg-color="transparent")
                v-list-item.history-item.mb-2.rounded-lg(
                  v-for="(item, index) in songHistory"
                  :key="item.sh_id || index"
                )
                  template(#prepend)
                    v-avatar(size="44" rounded="md")
                      v-img(:src="item.song?.art || '/thumbnail.jpg'" cover)
                  v-list-item-title.font-weight-medium.text-truncate
                    | {{ item.song?.title || 'タイトル不明' }}
                  v-list-item-subtitle.text-truncate
                    | {{ item.song?.artist || 'アーティスト不明' }}
                  template(#append)
                    .text-right
                      .text-caption.text-medium-emphasis {{ formatTime(item.played_at) }}
                      .text-caption.text-disabled(v-if="item.duration") {{ formatDuration(item.duration) }}
            div.text-center.py-8(v-else)
              v-icon(size="48" color="medium-emphasis" icon="mdi-history")
              p.text-body-2.text-medium-emphasis.mt-2 再生履歴はありません

        //- 4. ステーション情報 (info)
        v-window-item(value="info")
          .station-info-tab
            .station-hero.text-center.mb-4
              v-avatar(size="64" color="primary" variant="tonal" class="mb-2")
                v-icon(size="36") mdi-radio-tower
              h3.text-h6.font-weight-bold {{ station.name }}
              p.text-caption.text-medium-emphasis {{ station.description || '説明文はありません' }}

            v-list(density="compact" bg-color="transparent")
              v-list-item(prepend-icon="mdi-web")
                v-list-item-title 配信元ドメイン
                template(#append)
                  a.text-caption.text-primary.text-decoration-none(
                    :href="domain"
                    target="_blank"
                    rel="noopener noreferrer"
                  ) {{ domain }}
              v-list-item(v-if="station.public_player_url" prepend-icon="mdi-open-in-new")
                v-list-item-title 公開プレイヤー
                template(#append)
                  a.text-caption.text-primary.text-decoration-none(
                    :href="station.public_player_url"
                    target="_blank"
                    rel="noopener noreferrer"
                  ) リンクを開く
              v-list-item(prepend-icon="mdi-tune-vertical")
                v-list-item-title フロントエンド / バックエンド
                template(#append)
                  span.text-caption {{ station.frontend }} / {{ station.backend }}
              v-list-item(prepend-icon="mdi-clock-outline")
                v-list-item-title タイムゾーン
                template(#append)
                  span.text-caption {{ station.timezone || 'Asia/Tokyo' }}

            //- マウントポイント一覧
            .mt-4(v-if="station.mounts && station.mounts.length > 0")
              .text-subtitle-2.font-weight-bold.mb-2 マウントポイント (音質)
              v-chip.mr-2.mb-2(
                v-for="mount in station.mounts"
                :key="mount.id"
                size="small"
                variant="outlined"
                :color="mount.is_default ? 'primary' : undefined"
              )
                | {{ mount.name }} ({{ mount.bitrate }}kbps {{ mount.format }})

    //- フッター
    v-card-actions.py-2.px-4.border-t.justify-end
      v-btn(
        color="primary"
        variant="tonal"
        @click="close"
      ) 閉じる
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type {
  AzuraCastNowPlaying,
  AzuraCastStation,
  AzuraCastSong,
  AzuraCastLive,
} from '@/types/azuracast'

const props = defineProps<{
  modelValue: boolean
  nowPlayingDetail?: AzuraCastNowPlaying | null
  domain?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const activeTab = ref('now')

const modelValueInternal = computed({
  get: () => props.modelValue,
  set: (val: boolean) => emit('update:modelValue', val),
})

const isMobile = computed(() => {
  if (typeof window === 'undefined') return false
  return window.innerWidth < 600
})

const np = computed(() => props.nowPlayingDetail)
const station = computed<Partial<AzuraCastStation>>(
  () => np.value?.station || {},
)
const currentSongItem = computed(() => np.value?.now_playing)
const currentSong = computed<Partial<AzuraCastSong>>(
  () => np.value?.now_playing?.song || {},
)
const liveInfo = computed<Partial<AzuraCastLive>>(
  () => np.value?.live || { is_live: false, streamer_name: '' },
)
const listeners = computed(
  () => np.value?.listeners || { current: 0, unique: 0, total: 0 },
)
const playingNextItem = computed(() => np.value?.playing_next)
const nextSong = computed<Partial<AzuraCastSong> | null>(
  () => np.value?.playing_next?.song || null,
)
const songHistory = computed(() => np.value?.song_history || [])

function close() {
  modelValueInternal.value = false
}

function formatDuration(sec: number): string {
  if (!sec || isNaN(sec)) return '0:00'
  const m = Math.floor(sec / 60)
  const s = Math.floor(sec % 60)
  return `${m}:${s < 10 ? '0' : ''}${s}`
}

function formatTime(timestamp: number): string {
  if (!timestamp) return ''
  const d = new Date(timestamp * 1000)
  const h = d.getHours()
  const m = d.getMinutes()
  return `${h < 10 ? '0' : ''}${h}:${m < 10 ? '0' : ''}${m}`
}
</script>

<style scoped>
.detail-modal-card {
  background-color: rgb(var(--v-theme-surface));
  border-radius: 16px;
  overflow: hidden;
}
.history-item {
  background-color: rgba(var(--v-theme-on-surface), 0.04);
  transition: background-color 0.2s ease;
}
.history-item:hover {
  background-color: rgba(var(--v-theme-on-surface), 0.08);
}
.border-b {
  border-bottom: 1px solid rgba(var(--v-theme-on-surface), 0.1);
}
.border-t {
  border-top: 1px solid rgba(var(--v-theme-on-surface), 0.1);
}
.font-mono {
  font-family: monospace;
}
.gap-2 {
  gap: 8px;
}
.gap-4 {
  gap: 16px;
}
</style>
