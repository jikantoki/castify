<template lang="pug">
.home-page-root
  //- ヘッダーバー
  .home-header.px-4.pt-4.pb-1
    .d-flex.align-center.justify-space-between
      .brand-wrap.d-flex.align-center.gap-2
        img(src="/icon.png" style="height: 3em; border-radius: 9999px;")
        div
          h1.text-h6.font-weight-bold Castify
          p.text-caption.text-primary.font-weight-medium.mb-2 つなぐ、ひろがる、あなただけの電波。

  //- メインコンテンツ
  .home-content.px-4.pb-16
    //- Web版プロモーションバナー（PC: PWA, スマホ: アプリストア）
    AppPromotionBanner

    //- カスタムドメイン管理バー（カスタムドメインがある場合）
    .custom-domains-bar.mb-4(v-if="stationsStore.customDomains.length > 0")
      .text-caption.text-medium-emphasis.mb-1 登録済みカスタムドメイン
      .d-flex.flex-wrap.gap-2
        v-chip(
          v-for="domain in stationsStore.customDomains"
          :key="domain"
          size="small"
          closable
          variant="tonal"
          color="secondary"
          @click:close="confirmRemoveDomain(domain)"
        )
          v-icon.mr-1(size="14") mdi-server
          | {{ getDomainLabel(domain) }}

    ClientOnly
      //- プレイリストヘッダー
      .d-flex.align-center.justify-space-between.mt-2.mb-3(style="flex-direction: column")
        .d-flex.align-center.gap-2
          v-icon(icon="mdi-playlist-music" color="primary")
          h2.text-subtitle-1.font-weight-bold マイプレイリスト ({{ stationsStore.playlist.length }})
        .d-flex.align-center.gap-2
          //- 順番変更画面への遷移ボタン
          v-btn(
            v-if="stationsStore.playlist.length > 0"
            variant="tonal"
            color="primary"
            density="compact"
            size="small"
            rounded="pill"
            prepend-icon="mdi-swap-vertical"
            @click="$router.push('/playlist/edit')"
          ) 順番変更
          //- グリッド / リスト切り替え
          v-btn-toggle(
            v-if="stationsStore.playlist.length > 0"
            v-model="viewMode"
            mandatory
            density="compact"
            rounded="pill"
            variant="outlined"
            color="primary"
            divided
          )
            v-btn(value="grid" icon="mdi-view-grid" size="x-small" title="グリッド表示" aria-label="グリッド表示")
            v-btn(value="list" icon="mdi-view-list" size="x-small" title="リスト表示" aria-label="リスト表示")
          v-btn(
            v-if="stationsStore.playlist.length > 0"
            variant="text"
            density="compact"
            size="small"
            prepend-icon="mdi-refresh"
            @click="refreshPlaylist"
          ) 更新

      //- プレイリスト一覧（グリッド表示: おすすめページに合わせて cols="6" sm="4" md="2"）
      v-row.mt-1(
        v-if="stationsStore.playlist.length > 0 && viewMode === 'grid'"
        key="playlist-grid"
      )
        v-col(
          cols="6"
          sm="4"
          md="2"
          v-for="item in stationsStore.playlist"
          :key="item.id"
        )
          v-card.playlist-channel-card(
            variant="elevated"
            elevation="2"
            rounded="xl"
            :class="{ 'active-playing': isCurrentlyPlaying(item) }"
            @click="playItem(item)"
          )
            //- 現在再生中のアルバムアートワーク
            .channel-art-container
              v-img.channel-art(
                :src="getItemArtwork(item)"
                cover
                aspect-ratio="1"
                error-icon="mdi-radio-tower"
              )
                template(#placeholder)
                  .d-flex.align-center.justify-center.fill-height
                    v-progress-circular(indeterminate color="primary" size="20")

              //- ライブステータスバッジ (おすすめページとサイズ・位置を統一)
              .channel-status-pill(:class="item.nowPlaying?.is_online !== false ? 'online' : 'offline'")
                span.dot
                span {{ item.nowPlaying?.is_online !== false ? 'LIVE' : 'OFF' }}

              //- リスナー数 (おすすめページとサイズ・位置を統一)
              .channel-listener-pill
                v-icon(size="12" color="white") mdi-account-group
                span {{ item.nowPlaying?.listeners?.current ?? 0 }}

              //- 再生オーバーレイ
              .channel-play-overlay
                v-btn(
                  :icon="isCurrentlyPlaying(item) && playerStore.isPlaying ? 'mdi-pause' : 'mdi-play'"
                  color="primary"
                  size="large"
                  elevation="6"
                  @click.stop="togglePlayItem(item)"
                )

            //- アルバムアートの下: 曲名・アーティスト・チャンネル情報
            .channel-meta-box.pa-3
              //- 1. 曲名（最も大きく太字で表示）
              .song-title.font-weight-bold.text-truncate(title="曲名")
                | {{ item.nowPlaying?.now_playing?.song?.title || '配信中' }}

              //- 2. アーティスト名（その下に表示）
              .song-artist.text-caption.text-medium-emphasis.text-truncate.mt-1(title="アーティスト")
                | {{ item.nowPlaying?.now_playing?.song?.artist || 'アーティスト不明' }}

              //- 3. チャンネル名 & ドメイン
              .channel-name.text-caption.text-disabled.text-truncate.mt-1
                v-icon.mr-1(size="12") mdi-radio-tower
                | {{ item.station.name }}

              //- アクションボタン
              .d-flex.align-center.justify-space-between.mt-2.pt-1.border-top-thin(@click.stop)
                v-chip(
                  size="x-small"
                  color="secondary"
                  variant="tonal"
                  density="compact"
                  class="domain-chip"
                ) {{ getDomainLabel(item.domain) }}
                .d-flex.align-center.gap-1
                  v-btn(
                    :icon="isFavorite(item) ? 'mdi-heart' : 'mdi-heart-outline'"
                    :color="isFavorite(item) ? 'error' : 'medium-emphasis'"
                    variant="text"
                    density="compact"
                    size="small"
                    :title="isFavorite(item) ? 'お気に入り解除' : 'お気に入りに追加'"
                    @click="toggleFavorite(item)"
                  )

      //- プレイリスト一覧（リスト表示）
      .playlist-list-wrap.mt-1(
        v-else-if="stationsStore.playlist.length > 0 && viewMode === 'list'"
        key="playlist-list"
      )
        v-card.playlist-list-card.mb-2(
          v-for="item in stationsStore.playlist"
          :key="item.id"
          variant="elevated"
          elevation="1"
          rounded="xl"
          :class="{ 'active-playing': isCurrentlyPlaying(item) }"
          @click="playItem(item)"
        )
          .d-flex.align-center.pa-3.gap-3
            //- アートワーク（サムネイル 56x56）
            .list-art-box
              v-img.list-art-img(
                :src="getItemArtwork(item)"
                cover
                aspect-ratio="1"
                error-icon="mdi-radio-tower"
              )
                template(#placeholder)
                  .d-flex.align-center.justify-center.fill-height
                    v-progress-circular(indeterminate color="primary" size="18")

              //- ホバー/タップ用 再生オーバーレイ
              .list-play-overlay
                v-btn(
                  :icon="isCurrentlyPlaying(item) && playerStore.isPlaying ? 'mdi-pause' : 'mdi-play'"
                  color="primary"
                  size="x-small"
                  elevation="3"
                  @click.stop="togglePlayItem(item)"
                )

            //- メタ情報エリア
            .list-meta-box.flex-grow-1.min-width-0
              .d-flex.align-center.gap-2
                //- LIVE ステータスインジケータードット
                span.list-status-dot(:class="item.nowPlaying?.is_online !== false ? 'online' : 'offline'" :title="item.nowPlaying?.is_online !== false ? 'LIVE' : 'OFFLINE'")
                //- 曲名
                .song-title.font-weight-bold.text-truncate(title="曲名")
                  | {{ item.nowPlaying?.now_playing?.song?.title || '配信中' }}

              //- アーティスト名
              .song-artist.text-caption.text-medium-emphasis.text-truncate.mt-1(title="アーティスト")
                | {{ item.nowPlaying?.now_playing?.song?.artist || 'アーティスト不明' }}

              //- チャンネル名 & ドメイン & リスナー
              .d-flex.align-center.gap-2.mt-1.flex-wrap
                .channel-name.text-caption.text-disabled.text-truncate
                  v-icon.mr-1(size="12") mdi-radio-tower
                  | {{ item.station.name }}
                v-chip(
                  size="x-small"
                  color="secondary"
                  variant="tonal"
                  density="compact"
                  class="domain-chip"
                ) {{ getDomainLabel(item.domain) }}
                span.list-listeners.text-caption.text-disabled.d-none.d-sm-inline-flex.align-center.gap-1(v-if="item.nowPlaying?.listeners?.current !== undefined")
                  v-icon(size="12") mdi-account-group
                  | {{ item.nowPlaying.listeners.current }}

            //- 右側アクションボタン
            .list-actions.d-flex.align-center.gap-1.flex-shrink-0(@click.stop)
              v-btn(
                :icon="isCurrentlyPlaying(item) && playerStore.isPlaying ? 'mdi-pause-circle' : 'mdi-play-circle'"
                color="primary"
                variant="text"
                size="default"
                density="comfortable"
                :title="isCurrentlyPlaying(item) && playerStore.isPlaying ? '一時停止' : '再生'"
                @click.stop="togglePlayItem(item)"
              )
              v-btn(
                :icon="isFavorite(item) ? 'mdi-heart' : 'mdi-heart-outline'"
                :color="isFavorite(item) ? 'error' : 'medium-emphasis'"
                variant="text"
                density="compact"
                size="small"
                :title="isFavorite(item) ? 'お気に入り解除' : 'お気に入りに追加'"
                @click="toggleFavorite(item)"
              )

      //- 空状態
      v-card.empty-playlist-card.pa-6.text-center.rounded-xl.mt-2(
        v-else-if="stationsStore.isInitialized"
        key="playlist-empty"
        variant="outlined"
      )
        v-avatar(size="64" color="primary" variant="tonal" class="mb-3")
          v-icon(size="36") mdi-playlist-plus
        h3.text-h6.font-weight-bold プレイリストが空です
        p.text-body-2.text-medium-emphasis.mt-2(style="max-width: 380px; margin: 0 auto;")
          | 好きなAzuraCastサーバーのドメインを追加するか、「おすすめ」からチャンネルを追加して、あなただけのラジオプレイリストを作成しましょう！

        .d-flex.justify-center.gap-3.mt-6
          v-btn(
            color="primary"
            rounded="pill"
            prepend-icon="mdi-plus"
            elevation="2"
            @click="openAddDomainDialog"
          ) ドメインを追加
          v-btn(
            color="secondary"
            variant="tonal"
            rounded="pill"
            prepend-icon="mdi-compass"
            @click="$router.push('/explore')"
          ) おすすめを探す

      //- 初期化中フォールバック
      template(#fallback)
        .d-flex.align-center.justify-space-between.mt-2.mb-3
          .d-flex.align-center.gap-2
            v-icon(icon="mdi-playlist-music" color="primary")
            h2.text-subtitle-1.font-weight-bold マイプレイリスト
        v-row.mt-1
          v-col(cols="6" sm="4" md="2" v-for="i in 4" :key="i")
            v-skeleton-loader(type="card-avatar, text, text")

  //- フローティング追加ボタン (FAB: 再生バーMiniPlayerに引っかからない右下配置)
  v-btn.fab-add-button(
    icon
    elevation="6"
    :class="{ 'has-mini-player': playerStore.hasCurrentChannel, 'android-15': settingsStore.hidden?.isAndroid15OrHigher }"
    title="チャンネル・ドメインを追加"
    aria-label="チャンネル・ドメインを追加"
    @click="openAddDomainDialog"
    style="background: rgba(var(--v-theme-primary), 0.5);"
  )
    v-icon(size="28") mdi-plus

  //- ================= チャンネル・ドメイン追加ダイアログ =================
  v-dialog(
    v-model="addDomainDialog"
    max-width="500"
  )
    v-card.rounded-xl.pa-4
      v-card-title.d-flex.align-center.justify-space-between.pa-0.mb-2
        .d-flex.align-center.gap-2
          v-icon(color="primary" icon="mdi-server-plus")
          span.font-weight-bold チャンネル・ドメイン追加
        v-btn(
          icon="mdi-close"
          variant="text"
          density="comfortable"
          @click="addDomainDialog = false"
        )

      v-card-text.pa-0.my-2
        p.text-body-2.text-medium-emphasis.mb-3
          | 好きなAzuraCastサーバーのドメインまたはURLを入力してください。

        v-text-field(
          v-model="inputDomain"
          label="AzuraCast ドメイン / URL *"
          placeholder="例: radio.example.com または https://..."
          variant="outlined"
          density="comfortable"
          rounded="lg"
          prepend-inner-icon="mdi-web"
          :error-messages="domainError"
          @keyup.enter="submitAddDomain"
        )

        v-text-field.mt-1(
          v-model="inputSlug"
          label="チャンネルスラッグ（オプション）"
          placeholder="例: secret-radio, lofi など"
          variant="outlined"
          density="comfortable"
          rounded="lg"
          prepend-inner-icon="mdi-radio-tower"
          hint="未入力の場合はサーバー内の全ステーションを一括取得します"
          persistent-hint
          @keyup.enter="submitAddDomain"
        )

        //- 非公開配信・限定チャンネル向け説明カード
        v-alert.mt-3.mb-2(
          type="info"
          variant="tonal"
          density="comfortable"
          rounded="lg"
          icon="mdi-information-outline"
        )
          .text-caption
            span.font-weight-bold.d-block.mb-1 非公開・限定チャンネルの追加について
            | サーバー全体の公開一覧には表示されない非公開配信・限定チャンネルを登録したい場合は、上記にチャンネルスラッグを入力してください。指定したスラッグのチャンネルのみを直接検索してプレイリストに追加します。

        //- 入力例
        .examples.mt-3
          .text-caption.text-disabled おすすめサーバー例:
          .d-flex.flex-wrap.gap-1.mt-1
            v-chip(
              size="x-small"
              variant="outlined"
              @click="inputDomain = 'https://radio.enoki.xyz'"
            ) radio.enoki.xyz
            v-chip(
              size="x-small"
              variant="outlined"
              @click="inputDomain = 'https://cast.architectural-n.net'"
            ) cast.architectural-n.net

      v-card-actions.pa-0.mt-4.justify-end
        v-btn(
          variant="text"
          @click="addDomainDialog = false"
        ) キャンセル
        v-btn(
          color="primary"
          variant="flat"
          rounded="pill"
          prepend-icon="mdi-check"
          :loading="stationsStore.isLoadingCustom"
          @click="submitAddDomain"
        ) 追加する
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { Toast } from '@capacitor/toast'
import { useStationsStore } from '@/stores/stations'
import { usePlayerStore } from '@/stores/player'
import { useSettingsStore } from '@/stores/settings'
import type { ChannelItem } from '@/types/azuracast'
import { getChannelArtUrl } from '@/utils/azuracast'
import AppPromotionBanner from '@/components/common/AppPromotionBanner.vue'

const router = useRouter()
const stationsStore = useStationsStore()
const playerStore = usePlayerStore()
const settingsStore = useSettingsStore()

const viewMode = computed<'grid' | 'list'>({
  get: () => settingsStore.display?.viewMode || 'grid',
  set: (val: 'grid' | 'list') => {
    settingsStore.setViewMode(val)
  },
})

const addDomainDialog = ref(false)
const inputDomain = ref('')
const inputSlug = ref('')
const domainError = ref('')

function onVisibilityChange() {
  if (typeof document === 'undefined') return
  if (document.visibilityState === 'visible') {
    if (stationsStore.playlist.length > 0) {
      stationsStore.refreshPlaylistNowPlaying()
    }
    stationsStore.startPlaylistAutoRefresh()
  } else {
    stationsStore.stopPlaylistAutoRefresh()
  }
}

onMounted(async () => {
  // localStorage から確実に最新のプレイリストをロード
  stationsStore.initFromStorage()

  // プレイリストが存在する場合は最新曲情報・アルバムアートを更新
  if (stationsStore.playlist.length > 0) {
    stationsStore.refreshPlaylistNowPlaying()
  }

  // 10秒に1回、プレイリストの再生内容を自動更新
  stationsStore.startPlaylistAutoRefresh()

  if (typeof document !== 'undefined') {
    document.addEventListener('visibilitychange', onVisibilityChange)
  }

  // おすすめステーションが未ロードの場合はバックグラウンドで取得
  if (stationsStore.recommendedStations.length === 0) {
    await stationsStore.loadRecommendedStations()
  }
})

onUnmounted(() => {
  stationsStore.stopPlaylistAutoRefresh()
  if (typeof document !== 'undefined') {
    document.removeEventListener('visibilitychange', onVisibilityChange)
  }
})

function openAddDomainDialog() {
  inputDomain.value = ''
  inputSlug.value = ''
  domainError.value = ''
  addDomainDialog.value = true
}

async function submitAddDomain() {
  if (!inputDomain.value.trim()) {
    domainError.value = 'ドメインを入力してください'
    return
  }

  domainError.value = ''
  const result = await stationsStore.addCustomDomain(
    inputDomain.value,
    inputSlug.value,
  )
  if (result.success) {
    addDomainDialog.value = false
    await Toast.show({ text: result.message })
  } else {
    domainError.value = result.message
    await Toast.show({ text: result.message })
  }
}

async function confirmRemoveDomain(domain: string) {
  if (confirm(`${domain} と関連チャンネルをプレイリストから削除しますか？`)) {
    stationsStore.removeCustomDomain(domain)
    await Toast.show({ text: `${domain} を削除しました` })
  }
}

async function refreshPlaylist() {
  await stationsStore.refreshPlaylistNowPlaying()
  await Toast.show({ text: 'プレイリストの情報を更新しました' })
}

function getDomainLabel(domain: string): string {
  try {
    return new URL(domain).hostname
  } catch {
    return domain
  }
}

function getItemArtwork(item: ChannelItem): string {
  return getChannelArtUrl(item)
}

function isCurrentlyPlaying(item: ChannelItem): boolean {
  return playerStore.currentChannel?.id === item.id
}

function isFavorite(item: ChannelItem): boolean {
  return stationsStore.isFavorite(item.id)
}

async function toggleFavorite(item: ChannelItem) {
  const willBeFav = !stationsStore.isFavorite(item.id)
  stationsStore.toggleFavorite(item.id)
  await Toast.show({
    text: willBeFav
      ? `${item.station.name} をお気に入りに追加しました`
      : `${item.station.name} をお気に入りから解除しました`,
  })
}

async function playItem(item: ChannelItem) {
  await playerStore.playChannel(item, stationsStore.playlist)
  router.push('/player')
}

async function togglePlayItem(item: ChannelItem) {
  if (isCurrentlyPlaying(item)) {
    await playerStore.togglePlay()
  } else {
    await playerStore.playChannel(item, stationsStore.playlist)
  }
}
</script>

<style scoped lang="scss">
.home-page-root {
  height: 100%;
  height: 100dvh;
  max-height: 100%;
  overflow-y: auto;
  overflow-x: hidden;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior-y: contain;
  box-sizing: border-box;

  scrollbar-width: thin;
  scrollbar-color: rgba(var(--v-theme-on-surface), 0.2) transparent;

  &::-webkit-scrollbar {
    width: 6px;
  }
  &::-webkit-scrollbar-track {
    background: transparent;
  }
  &::-webkit-scrollbar-thumb {
    background: rgba(var(--v-theme-on-surface), 0.2);
    border-radius: 3px;
  }
  &::-webkit-scrollbar-thumb:hover {
    background: rgba(var(--v-theme-on-surface), 0.4);
  }
}

.home-content {
  padding-bottom: 160px !important;
}

.gap-1 {
  gap: 4px;
}
.gap-2 {
  gap: 8px;
}
.gap-3 {
  gap: 12px;
}

.playlist-channel-card {
  cursor: pointer;
  background-color: rgb(var(--v-theme-surface));
  border: 1px solid rgba(var(--v-theme-on-surface), 0.08);
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
  overflow: hidden;
  height: 100%;
  max-width: 100%;
  display: flex;
  flex-direction: column;

  &:hover {
    transform: translateY(-3px);
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.25);
  }

  &.active-playing {
    border: 1.5px solid rgb(var(--v-theme-primary));
    box-shadow: 0 0 16px rgba(var(--v-theme-primary), 0.35);
  }
}

.channel-art-container {
  position: relative;
  width: 100%;
  max-width: 100%;
  aspect-ratio: 1;
  overflow: hidden;

  .channel-art {
    width: 100%;
    height: 100%;
    transition: transform 0.3s ease;
  }

  &:hover .channel-art {
    transform: scale(1.04);
  }

  .channel-status-pill {
    position: absolute;
    top: 8px;
    left: 8px;
    padding: 2px 8px;
    border-radius: 9999px;
    font-size: 0.65rem;
    font-weight: 800;
    letter-spacing: 0.05em;
    display: flex;
    align-items: center;
    gap: 4px;
    backdrop-filter: blur(4px);

    &.online {
      background: rgba(var(--v-theme-primary), 0.9);
      color: #ffffff;
      .dot {
        width: 6px;
        height: 6px;
        border-radius: 50%;
        background: #ffffff;
      }
    }

    &.offline {
      background: rgba(120, 120, 120, 0.85);
      color: #ffffff;
      .dot {
        width: 6px;
        height: 6px;
        border-radius: 50%;
        background: #cccccc;
      }
    }
  }

  .channel-listener-pill {
    position: absolute;
    top: 8px;
    right: 8px;
    background: rgba(0, 0, 0, 0.65);
    backdrop-filter: blur(4px);
    padding: 2px 8px;
    border-radius: 9999px;
    font-size: 0.7rem;
    color: #ffffff;
    display: flex;
    align-items: center;
    gap: 4px;
  }

  .channel-play-overlay {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    background: rgba(0, 0, 0, 0.25);
    opacity: 0;
    transition: opacity 0.2s ease;
  }

  &:hover .channel-play-overlay {
    opacity: 1;
  }
}

.channel-meta-box {
  flex-grow: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-between;

  .song-title {
    font-size: 0.95rem;
    line-height: 1.3;
    color: rgb(var(--v-theme-on-surface));
  }

  .song-artist {
    font-size: 0.8rem;
    line-height: 1.2;
  }

  .channel-name {
    font-size: 0.72rem;
    display: flex;
    align-items: center;
  }
}

.border-top-thin {
  border-top: 1px solid rgba(var(--v-theme-on-surface), 0.08);
}

.domain-chip {
  max-width: 90px;
}

/* ================= リスト表示用スタイル ================= */
.playlist-list-wrap {
  display: flex;
  flex-direction: column;
}

.playlist-list-card {
  cursor: pointer;
  background-color: rgb(var(--v-theme-surface));
  border: 1px solid rgba(var(--v-theme-on-surface), 0.08);
  transition: transform 0.15s ease, box-shadow 0.15s ease,
    border-color 0.15s ease, background-color 0.15s ease;
  overflow: hidden;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);
    background-color: rgba(var(--v-theme-on-surface), 0.02);
  }

  &.active-playing {
    border: 1.5px solid rgb(var(--v-theme-primary));
    box-shadow: 0 0 16px rgba(var(--v-theme-primary), 0.25);
    background-color: rgba(var(--v-theme-primary), 0.04);
  }
}

.list-art-box {
  position: relative;
  width: 56px;
  height: 56px;
  flex-shrink: 0;
  border-radius: 10px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);

  .list-art-img {
    width: 100%;
    height: 100%;
    transition: transform 0.3s ease;
  }

  &:hover .list-art-img {
    transform: scale(1.05);
  }

  .list-play-overlay {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    background: rgba(0, 0, 0, 0.3);
    opacity: 0;
    transition: opacity 0.2s ease;
  }

  &:hover .list-play-overlay {
    opacity: 1;
  }
}

.list-meta-box {
  min-width: 0;

  .song-title {
    font-size: 0.95rem;
    line-height: 1.3;
    color: rgb(var(--v-theme-on-surface));
  }

  .song-artist {
    font-size: 0.8rem;
    line-height: 1.2;
  }

  .channel-name {
    font-size: 0.72rem;
    display: flex;
    align-items: center;
  }
}

.list-status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;

  &.online {
    background-color: rgb(var(--v-theme-primary));
    box-shadow: 0 0 6px rgb(var(--v-theme-primary));
  }

  &.offline {
    background-color: #888888;
  }
}

.min-width-0 {
  min-width: 0;
}

.empty-playlist-card {
  background-color: rgba(var(--v-theme-surface), 0.5);
  border-style: dashed;
}

.fab-add-button {
  position: fixed;
  right: 20px;
  bottom: calc(56px + 20px);
  width: 56px;
  height: 56px;
  border-radius: 50% !important;
  z-index: 99;
  box-shadow: 0 4px 16px rgba(var(--v-theme-primary), 0.45),
    0 8px 24px rgba(0, 0, 0, 0.3) !important;
  transition: bottom 0.3s cubic-bezier(0.4, 0, 0.2, 1), transform 0.2s ease,
    box-shadow 0.2s ease !important;

  &:hover {
    transform: scale(1.08);
    box-shadow: 0 6px 20px rgba(var(--v-theme-primary), 0.6),
      0 10px 28px rgba(0, 0, 0, 0.4) !important;
  }

  &:active {
    transform: scale(0.95);
  }

  &.android-15 {
    bottom: calc(56px + 16px + 20px);
  }

  &.has-mini-player {
    bottom: calc(56px + 10px + 64px + 16px);

    &.android-15 {
      bottom: calc(56px + 16px + 10px + 64px + 16px);
    }
  }
}
</style>
