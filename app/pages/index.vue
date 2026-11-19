<template lang="pug">
.home-page-root
  //- ヘッダーバー
  .home-header.px-4.pt-4.pb-3
    .d-flex.align-center.justify-space-between
      .brand-wrap.d-flex.align-center.gap-2
        v-avatar(color="primary" size="36")
          v-icon(color="white" icon="mdi-radio-tower")
        div
          h1.text-h6.font-weight-bold Castify
          p.text-caption.text-primary.font-weight-medium つなぐ、ひろがる、あなただけの電波。
      .actions-wrap
        v-btn(
          prepend-icon="mdi-plus"
          color="primary"
          variant="flat"
          rounded="pill"
          size="small"
          elevation="2"
          @click="openAddDomainDialog"
        ) ドメインを追加

  //- メインコンテンツ
  .home-content.px-4.pb-16
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

    //- プレイリストヘッダー
    .d-flex.align-center.justify-space-between.mt-2.mb-3
      .d-flex.align-center.gap-2
        v-icon(icon="mdi-playlist-music" color="primary")
        h2.text-subtitle-1.font-weight-bold マイプレイリスト ({{ stationsStore.playlist.length }})
      v-btn(
        v-if="stationsStore.playlist.length > 0"
        variant="text"
        density="compact"
        size="small"
        prepend-icon="mdi-refresh"
        @click="refreshPlaylist"
      ) 更新

    //- プレイリスト一覧（アルバムアートを上に、曲名・アーティストを下に表示）
    v-row.mt-1(v-if="stationsStore.playlist.length > 0")
      v-col(
        cols="6"
        sm="4"
        md="3"
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

            //- ライブステータスバッジ
            .channel-status-pill(:class="item.nowPlaying?.is_online !== false ? 'online' : 'offline'")
              span.dot
              span {{ item.nowPlaying?.is_online !== false ? 'LIVE' : 'OFF' }}

            //- リスナー数
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
              ) {{ getDomainLabel(item.domain) }}
              .d-flex.align-center.gap-1
                v-btn(
                  :icon="isFavorite(item) ? 'mdi-heart' : 'mdi-heart-outline'"
                  :color="isFavorite(item) ? 'error' : 'medium-emphasis'"
                  variant="text"
                  density="compact"
                  size="small"
                  @click="toggleFavorite(item)"
                )
                v-btn(
                  icon="mdi-delete-outline"
                  variant="text"
                  density="compact"
                  size="small"
                  color="medium-emphasis"
                  @click="removeItem(item)"
                )

    //- 空状態
    v-card.empty-playlist-card.pa-6.text-center.rounded-xl.mt-2(
      v-else
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

  //- ================= ドメイン追加ダイアログ =================
  v-dialog(
    v-model="addDomainDialog"
    max-width="480"
  )
    v-card.rounded-xl.pa-4
      v-card-title.d-flex.align-center.justify-space-between.pa-0.mb-2
        .d-flex.align-center.gap-2
          v-icon(color="primary" icon="mdi-server-plus")
          span.font-weight-bold AzuraCast ドメイン追加
        v-btn(
          icon="mdi-close"
          variant="text"
          density="comfortable"
          @click="addDomainDialog = false"
        )

      v-card-text.pa-0.my-2
        p.text-body-2.text-medium-emphasis.mb-3
          | 好きなAzuraCastサーバーのドメインまたはURLを入力してください。そのサーバーで公開されている全てのチャンネルを取得し、プレイリストに登録します。

        v-text-field(
          v-model="inputDomain"
          label="AzuraCast ドメイン / URL"
          placeholder="例: radio.example.com または https://..."
          variant="outlined"
          density="comfortable"
          rounded="lg"
          prepend-inner-icon="mdi-web"
          :error-messages="domainError"
          @keyup.enter="submitAddDomain"
        )

        //- 入力例
        .examples.mt-2
          .text-caption.text-disabled 例:
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

  //- 通知スナックバー
  v-snackbar(
    v-model="snackbar"
    :color="snackbarColor"
    timeout="3500"
    rounded="pill"
  )
    | {{ snackbarText }}
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useStationsStore } from '@/stores/stations'
import { usePlayerStore } from '@/stores/player'
import type { ChannelItem } from '@/types/azuracast'
import { getChannelArtUrl } from '@/utils/azuracast'

const router = useRouter()
const stationsStore = useStationsStore()
const playerStore = usePlayerStore()

const addDomainDialog = ref(false)
const inputDomain = ref('')
const domainError = ref('')

const snackbar = ref(false)
const snackbarText = ref('')
const snackbarColor = ref('success')

onMounted(async () => {
  // localStorage から確実に最新のプレイリストをロード
  stationsStore.initFromStorage()

  // プレイリストが存在する場合は最新曲情報・アルバムアートを更新
  if (stationsStore.playlist.length > 0) {
    stationsStore.refreshPlaylistNowPlaying()
  }

  // おすすめステーションが未ロードの場合はバックグラウンドで取得
  if (stationsStore.recommendedStations.length === 0) {
    await stationsStore.loadRecommendedStations()
  }
})

function openAddDomainDialog() {
  inputDomain.value = ''
  domainError.value = ''
  addDomainDialog.value = true
}

async function submitAddDomain() {
  if (!inputDomain.value.trim()) {
    domainError.value = 'ドメインを入力してください'
    return
  }

  domainError.value = ''
  const result = await stationsStore.addCustomDomain(inputDomain.value)
  if (result.success) {
    addDomainDialog.value = false
    snackbarText.value = result.message
    snackbarColor.value = 'success'
    snackbar.value = true
  } else {
    domainError.value = result.message
  }
}

function confirmRemoveDomain(domain: string) {
  if (confirm(`${domain} と関連チャンネルをプレイリストから削除しますか？`)) {
    stationsStore.removeCustomDomain(domain)
    snackbarText.value = `${domain} を削除しました`
    snackbarColor.value = 'info'
    snackbar.value = true
  }
}

async function refreshPlaylist() {
  await stationsStore.refreshPlaylistNowPlaying()
  snackbarText.value = 'プレイリストの情報を更新しました'
  snackbarColor.value = 'success'
  snackbar.value = true
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

function toggleFavorite(item: ChannelItem) {
  stationsStore.toggleFavorite(item.id)
}

function removeItem(item: ChannelItem) {
  stationsStore.removeFromPlaylist(item.id)
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
  min-height: 100%;
  padding-bottom: 90px;
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
  overflow: hidden;

  .channel-art {
    transition: transform 0.3s ease;
  }

  &:hover .channel-art {
    transform: scale(1.04);
  }

  .channel-status-pill {
    position: absolute;
    top: 6px;
    left: 6px;
    padding: 2px 6px;
    border-radius: 9999px;
    font-size: 0.62rem;
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
        width: 5px;
        height: 5px;
        border-radius: 50%;
        background: #ffffff;
      }
    }

    &.offline {
      background: rgba(120, 120, 120, 0.85);
      color: #ffffff;
      .dot {
        width: 5px;
        height: 5px;
        border-radius: 50%;
        background: #cccccc;
      }
    }
  }

  .channel-listener-pill {
    position: absolute;
    top: 6px;
    right: 6px;
    background: rgba(0, 0, 0, 0.65);
    backdrop-filter: blur(4px);
    padding: 2px 6px;
    border-radius: 9999px;
    font-size: 0.65rem;
    color: #ffffff;
    display: flex;
    align-items: center;
    gap: 3px;
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

.empty-playlist-card {
  background-color: rgba(var(--v-theme-surface), 0.5);
  border-style: dashed;
}
</style>
