<template lang="pug">
.player-page-root
  //- 背景のアンビエントグロー効果
  .ambient-background(:style="ambientStyle")

  //- メインコンテナ
  .player-container(:class="{ 'android-15-padding': settings.hidden.isAndroid15OrHigher }")
    //- トップナビゲーションバー
    .player-header
      v-btn(
        icon="mdi-chevron-down"
        variant="text"
        density="comfortable"
        size="large"
        @click="goBack"
      )
      .header-title-wrap
        .header-sub-text 再生中のチャンネル
        .header-main-text.text-truncate {{ currentStationName }}
      .header-actions.d-flex.align-center
        v-btn(
          icon="mdi-information-outline"
          variant="text"
          density="comfortable"
          size="large"
          title="詳細情報"
          aria-label="詳細情報"
          @click="openDetailModal"
        )
        v-btn(
          icon="mdi-share-variant-outline"
          variant="text"
          density="comfortable"
          size="large"
          title="チャンネルを共有"
          aria-label="チャンネルを共有"
          @click="shareCurrentStation"
        )

    //- 巨大アートワークセクション
    .artwork-section
      .artwork-wrapper
        img.main-art(
          :src="playerStore.currentArtwork"
          alt="Album Art"
          onerror="this.src='/thumbnail.jpg'"
        )
        //- ライブバッジ
        .live-badge(v-if="playerStore.isLive")
          span.live-dot
          span.live-text LIVE
        .listeners-badge
          v-icon(size="14" color="white") mdi-account-group
          span {{ playerStore.listenerCount }}

    //- トラック情報セクション
    .track-info-section
      .track-text-wrap
        h1.track-title.text-truncate {{ playerStore.currentSongTitle }}
        p.track-artist.text-truncate {{ playerStore.currentArtist || currentStationName }}
        p.track-album.text-truncate(v-if="playerStore.currentAlbum") {{ playerStore.currentAlbum }}
      .track-action-wrap
        v-btn(
          :icon="isFavorite ? 'mdi-heart' : 'mdi-heart-outline'"
          :color="isFavorite ? 'error' : 'white'"
          variant="text"
          size="large"
          @click="toggleFavorite"
        )

    //- プログレスバーセクション
    .progress-section
      .progress-bar-wrap
        v-slider(
          :model-value="playerStore.progressPercent"
          color="primary"
          track-color="rgba(255, 255, 255, 0.2)"
          density="compact"
          hide-details
          readonly
        )
      .progress-time-row
        span.time-text {{ formatDuration(playerStore.liveElapsed) }}
        span.time-text.font-weight-bold(v-if="playerStore.liveDuration > 0")
          | {{ formatDuration(playerStore.liveDuration) }}
        span.time-text.live-pill(v-else)
          | ライブ配信中

    //- メインコントローラー（Spotify風UI × 共通アクセントカラー）
    .controls-section
      //- シャッフル
      v-btn(
        icon="mdi-shuffle-variant"
        variant="text"
        size="default"
        color="rgba(255, 255, 255, 0.7)"
        @click="playRandomChannel"
      )

      //- 前のチャンネル
      v-btn(
        icon="mdi-skip-previous"
        variant="text"
        size="x-large"
        color="white"
        @click="playerStore.prevChannel"
      )

      //- 巨大な再生 / 一時停止ボタン（共通アクセントカラー）
      v-btn.play-pause-btn(
        :icon="playerStore.isPlaying ? 'mdi-pause' : 'mdi-play'"
        color="primary"
        size="x-large"
        elevation="8"
        :loading="playerStore.isLoading"
        @click="playerStore.togglePlay"
      )

      //- 次のチャンネル
      v-btn(
        icon="mdi-skip-next"
        variant="text"
        size="x-large"
        color="white"
        @click="playerStore.nextChannel"
      )

      //- プレイリストに追加ボタン
      v-btn(
        :icon="isInPlaylist ? 'mdi-playlist-check' : 'mdi-playlist-plus'"
        :color="isInPlaylist ? 'primary' : 'rgba(255, 255, 255, 0.7)'"
        variant="text"
        size="default"
        @click="togglePlaylist"
      )

    //- ボトムアクションバー（ナビバーの上に収まる安全エリア）
    .bottom-tools-section
      //- 音量コントロール
      .volume-control-wrap
        v-btn(
          :icon="volumeIcon"
          variant="text"
          density="comfortable"
          size="small"
          color="rgba(255, 255, 255, 0.7)"
          @click="playerStore.toggleMute"
        )
        v-slider.volume-slider(
          :model-value="playerStore.isMuted ? 0 : playerStore.volume"
          min="0"
          max="1"
          step="0.05"
          color="primary"
          track-color="rgba(255, 255, 255, 0.2)"
          density="compact"
          hide-details
          @update:model-value="onVolumeChange"
        )

      //- 詳細情報ボタン
      v-btn.detail-info-btn(
        prepend-icon="mdi-format-list-bulleted-type"
        rounded="pill"
        variant="tonal"
        color="white"
        size="small"
        @click="openDetailModal"
      )
        | 詳細情報・履歴

  //- 配信詳細情報・過去履歴・次配信確認モーダル
  StationDetailModal(
    v-model="detailModalOpen"
    :now-playing-detail="playerStore.nowPlayingDetail"
    :domain="playerStore.currentChannel?.domain"
  )
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { Toast } from '@capacitor/toast'
import { Share } from '@capacitor/share'
import { usePlayerStore } from '@/stores/player'
import { useStationsStore } from '@/stores/stations'
import { useSettingsStore } from '@/stores/settings'
import StationDetailModal from '@/components/player/StationDetailModal.vue'
import { getStationShareUrl } from '@/utils/url'

const router = useRouter()
const playerStore = usePlayerStore()
const stationsStore = useStationsStore()
const settings = useSettingsStore()

const detailModalOpen = ref(false)

const currentStationName = computed(() => {
  return playerStore.currentChannel?.station?.name || 'ステーション未選択'
})

const isFavorite = computed(() => {
  if (!playerStore.currentChannel) return false
  return stationsStore.isFavorite(playerStore.currentChannel.id)
})

const isInPlaylist = computed(() => {
  if (!playerStore.currentChannel) return false
  return stationsStore.isInPlaylist(playerStore.currentChannel.id)
})

const ambientStyle = computed(() => {
  const art = playerStore.currentArtwork
  return {
    backgroundImage: `url(${art})`,
  }
})

const volumeIcon = computed(() => {
  if (playerStore.isMuted || playerStore.volume === 0) return 'mdi-volume-off'
  if (playerStore.volume < 0.5) return 'mdi-volume-medium'
  return 'mdi-volume-high'
})

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/')
  }
}

function openDetailModal() {
  detailModalOpen.value = true
}

async function shareCurrentStation() {
  if (!playerStore.currentChannel) {
    await Toast.show({ text: 'ステーションが選択されていません' })
    return
  }

  const station = playerStore.currentChannel.station
  // 共有タイトル: チャンネル名
  const title = station?.name || currentStationName.value || 'Castify'
  // 共有URL: https://ドメイン/public/チャンネルスラッグ/
  const shareUrl = getStationShareUrl(playerStore.currentChannel.domain, station?.shortcode)

  try {
    await Share.share({
      title,
      text: `${title} を聴こう`,
      url: shareUrl,
      dialogTitle: `${title} を共有`,
    })
  } catch (e) {
    if (navigator.clipboard) {
      await navigator.clipboard.writeText(shareUrl)
      await Toast.show({ text: '共有リンクをコピーしました' })
    }
  }
}

async function toggleFavorite() {
  if (playerStore.currentChannel) {
    const id = playerStore.currentChannel.id
    const name = playerStore.currentChannel.station.name
    const willBeFav = !stationsStore.isFavorite(id)
    stationsStore.toggleFavorite(id)
    await Toast.show({
      text: willBeFav
        ? `${name} をお気に入りに追加しました`
        : `${name} をお気に入りから解除しました`,
    })
  }
}

async function togglePlaylist() {
  if (!playerStore.currentChannel) return
  const name = playerStore.currentChannel.station.name
  if (isInPlaylist.value) {
    stationsStore.removeFromPlaylist(playerStore.currentChannel.id)
    await Toast.show({ text: `${name} をプレイリストから削除しました` })
  } else {
    stationsStore.addToPlaylist(playerStore.currentChannel)
    await Toast.show({ text: `${name} をプレイリストに追加しました` })
  }
}

function onVolumeChange(val: number) {
  playerStore.setVolume(val)
}

function playRandomChannel() {
  const all = [...stationsStore.playlist, ...stationsStore.recommendedStations]
  if (all.length > 0) {
    const random = all[Math.floor(Math.random() * all.length)]
    playerStore.playChannel(random)
  }
}

function formatDuration(sec: number): string {
  if (!sec || isNaN(sec)) return '0:00'
  const m = Math.floor(sec / 60)
  const s = Math.floor(sec % 60)
  return `${m}:${s < 10 ? '0' : ''}${s}`
}
</script>

<style scoped lang="scss">
.player-page-root {
  position: relative;
  width: 100%;
  height: 100vh;
  height: 100dvh;
  background-color: #121212;
  color: #ffffff;
  overflow: hidden;
  user-select: none;
}

.ambient-background {
  position: absolute;
  inset: -20px;
  background-size: cover;
  background-position: center;
  filter: blur(50px) brightness(0.25);
  transform: scale(1.15);
  opacity: 0.85;
  z-index: 1;
}

.player-container {
  position: relative;
  z-index: 2;
  height: 100%;
  max-width: 480px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 12px 20px 24px;
  overflow-y: auto;
  overflow-x: hidden;
  box-sizing: border-box;

  &.android-15-padding {
    padding-bottom: calc(24px + 16px);
  }
}

.player-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 44px;
  flex-shrink: 0;

  .header-actions {
    display: flex;
    align-items: center;
    gap: 2px;
    flex-shrink: 0;
  }

  .header-title-wrap {
    text-align: center;
    min-width: 0;
    flex-grow: 1;
    margin: 0 8px;

    .header-sub-text {
      font-size: 0.68rem;
      text-transform: uppercase;
      letter-spacing: 0.1em;
      color: rgba(255, 255, 255, 0.6);
    }

    .header-main-text {
      font-size: 0.9rem;
      font-weight: 700;
      color: #ffffff;
    }
  }
}

.artwork-section {
  display: flex;
  justify-content: center;
  align-items: center;
  flex-grow: 1;
  min-height: 0;
  padding: 8px 0;

  .artwork-wrapper {
    position: relative;
    /* 画面高に合わせて可変 */
    width: min(64vw, 270px);
    height: min(64vw, 270px);
    max-height: 38vh;
    max-width: 38vh;

    .main-art {
      width: 100%;
      height: 100%;
      object-fit: cover;
      border-radius: 14px;
      box-shadow: 0 10px 30px rgba(0, 0, 0, 0.7);
    }

    .live-badge {
      position: absolute;
      top: 10px;
      left: 10px;
      background: rgba(230, 30, 50, 0.9);
      padding: 3px 8px;
      border-radius: 9999px;
      display: flex;
      align-items: center;
      gap: 5px;
      backdrop-filter: blur(4px);

      .live-dot {
        width: 7px;
        height: 7px;
        border-radius: 50%;
        background: #ffffff;
        animation: blink 1.2s infinite;
      }

      .live-text {
        font-size: 0.7rem;
        font-weight: 800;
        letter-spacing: 0.05em;
        color: #ffffff;
      }
    }

    .listeners-badge {
      position: absolute;
      bottom: 10px;
      right: 10px;
      background: rgba(0, 0, 0, 0.65);
      backdrop-filter: blur(4px);
      padding: 3px 8px;
      border-radius: 9999px;
      font-size: 0.7rem;
      font-weight: 600;
      display: flex;
      align-items: center;
      gap: 5px;
    }
  }
}

.track-info-section {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
  flex-shrink: 0;

  .track-text-wrap {
    min-width: 0;
    flex-grow: 1;

    .track-title {
      font-size: 1.25rem;
      font-weight: 800;
      color: #ffffff;
      margin: 0;
      line-height: 1.25;
    }

    .track-artist {
      font-size: 0.92rem;
      color: rgba(255, 255, 255, 0.75);
      margin: 2px 0 0;
    }

    .track-album {
      font-size: 0.75rem;
      color: rgba(255, 255, 255, 0.5);
      margin: 1px 0 0;
    }
  }

  .track-action-wrap {
    flex-shrink: 0;
    margin-left: 8px;
  }
}

.progress-section {
  margin-bottom: 8px;
  flex-shrink: 0;

  .progress-bar-wrap {
    margin: -8px 0;
  }

  .progress-time-row {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .time-text {
      font-size: 0.72rem;
      font-family: monospace;
      color: rgba(255, 255, 255, 0.6);
    }

    .live-pill {
      color: rgb(var(--v-theme-primary));
      font-size: 0.7rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }
  }
}

.controls-section {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 8px;
  flex-shrink: 0;

  .play-pause-btn {
    width: 62px !important;
    height: 62px !important;
    border-radius: 50%;
    transition: transform 0.15s ease;

    &:active {
      transform: scale(0.95);
    }
  }
}

.bottom-tools-section {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 8px;
  flex-shrink: 0;

  .volume-control-wrap {
    display: flex;
    align-items: center;
    gap: 6px;
    flex-grow: 1;
    max-width: 160px;

    .volume-slider {
      flex-grow: 1;
    }
  }

  .detail-info-btn {
    font-size: 0.72rem;
    font-weight: 700;
  }
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}
</style>
