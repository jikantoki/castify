<template lang="pug">
.mini-player-wrapper(
  v-if="playerStore.hasCurrentChannel"
  :class="{ 'android-15': settings.hidden.isAndroid15OrHigher }"
  @click="openFullPlayer"
)
  //- 最上部の薄いプログレスバー
  .mini-progress-bar
    .mini-progress-fill(:style="{ width: `${playerStore.progressPercent}%` }")

  .mini-player-content
    //- 左側: アートワーク
    .mini-art-wrap
      img.mini-art(
        :src="playerStore.currentArtwork"
        alt="Art"
        onerror="this.src='/thumbnail.jpg'"
      )
      .mini-live-indicator(v-if="playerStore.isLive")
        span.pulse-dot

    //- 中央: タイトル & アーティスト
    .mini-info
      .mini-title.text-truncate {{ playerStore.currentSongTitle }}
      .mini-artist.text-truncate {{ playerStore.currentArtist || playerStore.currentChannel?.station?.name }}

    //- 右側: コントロールボタン群（バブリング抑止）
    .mini-actions(@click.stop)
      //- お気に入りボタン
      v-btn(
        :icon="isFav ? 'mdi-heart' : 'mdi-heart-outline'"
        :color="isFav ? 'error' : undefined"
        variant="text"
        density="comfortable"
        size="small"
        @click="toggleFav"
      )

      //- 前の曲
      v-btn(
        icon="mdi-skip-previous"
        variant="text"
        density="comfortable"
        size="small"
        @click="playerStore.prevChannel"
      )

      //- 再生 / 一時停止
      v-btn(
        :icon="playerStore.isPlaying ? 'mdi-pause' : 'mdi-play'"
        color="primary"
        variant="flat"
        density="comfortable"
        size="small"
        :loading="playerStore.isLoading"
        @click="playerStore.togglePlay"
      )

      //- 次の曲
      v-btn(
        icon="mdi-skip-next"
        variant="text"
        density="comfortable"
        size="small"
        @click="playerStore.nextChannel"
      )
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { usePlayerStore } from '@/stores/player'
import { useStationsStore } from '@/stores/stations'
import { useSettingsStore } from '@/stores/settings'

const playerStore = usePlayerStore()
const stationsStore = useStationsStore()
const settings = useSettingsStore()

const isFav = computed(() => {
  if (!playerStore.currentChannel) return false
  return stationsStore.isFavorite(playerStore.currentChannel.id)
})

function toggleFav() {
  if (playerStore.currentChannel) {
    stationsStore.toggleFavorite(playerStore.currentChannel.id)
  }
}

function openFullPlayer() {
  playerStore.openFullPlayer()
}
</script>

<style scoped lang="scss">
.mini-player-wrapper {
  position: fixed;
  bottom: calc(56px + 10px); /* ナビゲーションバーの上に浮かせる */
  left: 50%;
  transform: translateX(-50%);
  width: 95%;
  max-width: 500px;
  height: 64px;
  border-radius: 16px;
  background: rgba(24, 24, 28, 0.94);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.12);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.5), 0 2px 8px rgba(0, 0, 0, 0.25);
  display: flex;
  flex-direction: column;
  z-index: 100;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  user-select: none;
  overflow: hidden;

  &.android-15 {
    bottom: calc(56px + 16px + 10px);
  }

  &:hover {
    background: rgba(34, 34, 40, 0.98);
    transform: translateX(-50%) translateY(-2px);
    box-shadow: 0 14px 36px rgba(0, 0, 0, 0.6), 0 4px 12px rgba(0, 0, 0, 0.3);
  }
}

.mini-progress-bar {
  width: 100%;
  height: 2.5px;
  background: rgba(255, 255, 255, 0.15);
  overflow: hidden;
}

.mini-progress-fill {
  height: 100%;
  background: rgb(var(--v-theme-primary));
  transition: width 0.3s linear;
}

.mini-player-content {
  display: flex;
  align-items: center;
  height: calc(100% - 2.5px);
  padding: 0 14px;
  gap: 12px;
}

.mini-art-wrap {
  position: relative;
  width: 44px;
  height: 44px;
  flex-shrink: 0;

  .mini-art {
    width: 100%;
    height: 100%;
    object-fit: cover;
    border-radius: 10px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.4);
  }

  .mini-live-indicator {
    position: absolute;
    top: -2px;
    right: -2px;
    background: #ff334b;
    border-radius: 9999px;
    width: 10px;
    height: 10px;
    border: 1.5px solid #121212;
  }
}

.mini-info {
  flex-grow: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;

  .mini-title {
    font-size: 0.9rem;
    font-weight: 700;
    color: #ffffff;
    line-height: 1.25;
  }

  .mini-artist {
    font-size: 0.75rem;
    color: rgba(255, 255, 255, 0.72);
    line-height: 1.2;
    margin-top: 2px;
  }
}

.mini-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.pulse-dot {
  display: block;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: #ff334b;
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0% {
    transform: scale(0.9);
    opacity: 1;
  }
  50% {
    transform: scale(1.4);
    opacity: 0.5;
  }
  100% {
    transform: scale(0.9);
    opacity: 1;
  }
}
</style>
