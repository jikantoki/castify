<template lang="pug">
Transition(name="player-slide")
  .full-player-overlay(
    v-if="playerStore.isFullPlayerOpen"
    :style="backdropStyle"
    @touchstart="onTouchStart"
    @touchmove="onTouchMove"
    @touchend="onTouchEnd"
    @touchcancel="onTouchEnd"
    @mousedown="onMouseDown"
  )
    .full-player-sheet(
      :style="sheetStyle"
      :class="{ 'android-15-padding': settings.hidden.isAndroid15OrHigher }"
    )
      //- 背景のアンビエントグロー効果
      .ambient-background(:style="ambientStyle")

      //- 上部スワイプ用ドラッグインジケーター（下に引けることを示すピルバー）
      .swipe-handle-bar(@click="dismiss")
        .swipe-handle-pill

      //- メインコンテナ（スクロール可能）
      .player-container(ref="containerRef")
        //- トップナビゲーションバー
        .player-header
          v-btn(
            icon="mdi-chevron-down"
            variant="text"
            density="comfortable"
            size="large"
            title="閉じる"
            aria-label="閉じる"
            @click="dismiss"
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

        //- ボトムアクションバー
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
import { ref, computed, onUnmounted } from 'vue'
import { Toast } from '@capacitor/toast'
import { Share } from '@capacitor/share'
import { usePlayerStore } from '@/stores/player'
import { useStationsStore } from '@/stores/stations'
import { useSettingsStore } from '@/stores/settings'
import StationDetailModal from '@/components/player/StationDetailModal.vue'
import { getStationShareUrl } from '@/utils/url'

const playerStore = usePlayerStore()
const stationsStore = useStationsStore()
const settings = useSettingsStore()

const detailModalOpen = ref(false)
const containerRef = ref<HTMLElement | null>(null)

// スワイプダウン（ジェスチャー）管理用の状態
const dragY = ref(0)
const isDragging = ref(false)
const isClosing = ref(false)

let startY = 0
let startX = 0
let startTime = 0
let isTracking = false
let isIntentDetermined = false

// ================= スワイプダウンジェスチャー =================

function onTouchStart(e: TouchEvent) {
  // 詳細モーダルが開いている場合は無効化
  if (detailModalOpen.value) return

  // スライダーやボタン、入力要素を操作しているときはスワイプジェスチャーを無視
  const target = e.target as HTMLElement | null
  if (target?.closest('.v-slider, .v-btn, button, input, .v-dialog, .v-overlay')) {
    return
  }

  // 上部ドラッグバーの場合はスクロール位置に関係なく常にドラッグ許可
  const isDragHandle = Boolean(target?.closest('.swipe-handle-bar'))
  const scrollEl = containerRef.value
  if (!isDragHandle && scrollEl && scrollEl.scrollTop > 5) {
    return
  }

  const touch = e.touches[0]
  startY = touch.clientY
  startX = touch.clientX
  startTime = Date.now()
  isTracking = true
  isIntentDetermined = false
  isDragging.value = false
}

function onTouchMove(e: TouchEvent) {
  if (!isTracking) return

  const touch = e.touches[0]
  const deltaY = touch.clientY - startY
  const deltaX = touch.clientX - startX

  if (!isIntentDetermined) {
    // 6px 以上動いた時点で方向を判定
    if (Math.abs(deltaY) > 6 || Math.abs(deltaX) > 6) {
      isIntentDetermined = true
      // 縦方向の動きであり、かつ下向き（deltaY > 0）であること
      if (Math.abs(deltaY) > Math.abs(deltaX) && deltaY > 0) {
        const scrollEl = containerRef.value
        if (!scrollEl || scrollEl.scrollTop <= 2) {
          isDragging.value = true
        } else {
          isTracking = false
          return
        }
      } else {
        isTracking = false
        return
      }
    }
  }

  if (isDragging.value) {
    if (deltaY > 0) {
      dragY.value = deltaY
      // 画面のバウンススクロールを防止
      if (e.cancelable) e.preventDefault()
    } else {
      dragY.value = 0
    }
  }
}

function onTouchEnd() {
  if (!isTracking && !isDragging.value) return
  isTracking = false

  if (isDragging.value) {
    const elapsed = Date.now() - startTime
    const currentY = dragY.value
    const velocity = currentY / Math.max(elapsed, 1)

    // しきい値: 100px 以上下方向にドラッグ、または素早い下スワイプ（速度 > 0.35 かつ 30px 以上）
    if (currentY > 100 || (velocity > 0.35 && currentY > 30)) {
      dismiss()
    } else {
      resetPosition()
    }
  }
}

// マウスドラッグ対応（PCブラウザ検証・操作用）
function onMouseDown(e: MouseEvent) {
  if (detailModalOpen.value) return
  const target = e.target as HTMLElement | null
  if (target?.closest('.v-slider, .v-btn, button, input, .v-dialog, .v-overlay')) {
    return
  }

  const isDragHandle = Boolean(target?.closest('.swipe-handle-bar'))
  const scrollEl = containerRef.value
  if (!isDragHandle && scrollEl && scrollEl.scrollTop > 5) {
    return
  }

  startY = e.clientY
  startX = e.clientX
  startTime = Date.now()
  isTracking = true
  isIntentDetermined = false
  isDragging.value = false

  window.addEventListener('mousemove', onMouseMove)
  window.addEventListener('mouseup', onMouseUp)
}

function onMouseMove(e: MouseEvent) {
  if (!isTracking) return
  const deltaY = e.clientY - startY
  const deltaX = e.clientX - startX

  if (!isIntentDetermined) {
    if (Math.abs(deltaY) > 6 || Math.abs(deltaX) > 6) {
      isIntentDetermined = true
      if (Math.abs(deltaY) > Math.abs(deltaX) && deltaY > 0) {
        isDragging.value = true
      } else {
        isTracking = false
        cleanupMouseListeners()
        return
      }
    }
  }

  if (isDragging.value) {
    if (deltaY > 0) {
      dragY.value = deltaY
    } else {
      dragY.value = 0
    }
  }
}

function onMouseUp() {
  cleanupMouseListeners()
  if (!isTracking && !isDragging.value) return
  isTracking = false

  if (isDragging.value) {
    const elapsed = Date.now() - startTime
    const currentY = dragY.value
    const velocity = currentY / Math.max(elapsed, 1)

    if (currentY > 100 || (velocity > 0.35 && currentY > 30)) {
      dismiss()
    } else {
      resetPosition()
    }
  }
}

function cleanupMouseListeners() {
  window.removeEventListener('mousemove', onMouseMove)
  window.removeEventListener('mouseup', onMouseUp)
}

onUnmounted(() => {
  cleanupMouseListeners()
})

// スムーズに閉じる（元の画面に戻る）
function dismiss() {
  if (isClosing.value) return
  isClosing.value = true
  isDragging.value = false

  const targetH = typeof window !== 'undefined' ? window.innerHeight : 900
  dragY.value = targetH

  window.setTimeout(() => {
    playerStore.closeFullPlayer()
    isClosing.value = false
    dragY.value = 0
  }, 260)
}

function resetPosition() {
  dragY.value = 0
  isDragging.value = false
}

// スタイル計算
const sheetStyle = computed(() => {
  if (isDragging.value) {
    return {
      transform: `translate3d(0, ${dragY.value}px, 0)`,
      transition: 'none',
      borderTopLeftRadius: '24px',
      borderTopRightRadius: '24px',
    }
  }
  if (isClosing.value) {
    return {
      transform: `translate3d(0, ${dragY.value}px, 0)`,
      transition: 'transform 0.26s cubic-bezier(0.32, 0.72, 0, 1)',
      borderTopLeftRadius: '24px',
      borderTopRightRadius: '24px',
    }
  }
  return {
    transform: 'translate3d(0, 0, 0)',
    transition: 'transform 0.28s cubic-bezier(0.25, 1, 0.5, 1)',
  }
})

const backdropStyle = computed(() => {
  if (isDragging.value && dragY.value > 0) {
    const opacity = Math.max(0.1, 1 - dragY.value / 350)
    return { backgroundColor: `rgba(0, 0, 0, ${0.65 * opacity})` }
  }
  if (isClosing.value) {
    return { backgroundColor: 'rgba(0, 0, 0, 0)', transition: 'background-color 0.25s ease' }
  }
  return { backgroundColor: 'rgba(0, 0, 0, 0.65)' }
})

// ================= プレイヤー関連ロジック =================

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

function openDetailModal() {
  detailModalOpen.value = true
}

async function shareCurrentStation() {
  if (!playerStore.currentChannel) {
    await Toast.show({ text: 'ステーションが選択されていません' })
    return
  }

  const station = playerStore.currentChannel.station
  const title = station?.name || currentStationName.value || 'Castify'
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
.full-player-overlay {
  position: fixed;
  inset: 0;
  width: 100vw;
  height: 100vh;
  height: 100dvh;
  z-index: 500;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  overflow: hidden;
  user-select: none;
  touch-action: pan-y;
}

.full-player-sheet {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  background-color: #121212;
  color: #ffffff;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-shadow: 0 -8px 32px rgba(0, 0, 0, 0.7);
  will-change: transform;

  &.android-15-padding {
    padding-bottom: 16px;
  }
}

// 上部のピル型ドラッグハンドルバー
.swipe-handle-bar {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 100%;
  padding-top: 10px;
  padding-bottom: 6px;
  flex-shrink: 0;
  cursor: grab;
  z-index: 3;

  .swipe-handle-pill {
    width: 40px;
    height: 4.5px;
    border-radius: 9999px;
    background: rgba(255, 255, 255, 0.35);
    transition: background 0.2s ease, width 0.2s ease;
  }

  &:hover .swipe-handle-pill {
    background: rgba(255, 255, 255, 0.6);
    width: 48px;
  }
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
  pointer-events: none;
}

.player-container {
  position: relative;
  z-index: 2;
  height: 100%;
  max-width: 480px;
  width: 100%;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 4px 20px 24px;
  overflow-y: auto;
  overflow-x: hidden;
  box-sizing: border-box;
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

// トランジションアニメーション（下からのスライドイン・スライドアウト）
.player-slide-enter-active,
.player-slide-leave-active {
  transition: transform 0.3s cubic-bezier(0.22, 1, 0.36, 1), opacity 0.25s ease;
}

.player-slide-enter-from,
.player-slide-leave-to {
  transform: translate3d(0, 100%, 0);
  opacity: 0;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}
</style>
