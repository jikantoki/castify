<template lang="pug">
.explore-page-root
  //- ページヘッダー
  .explore-header.px-4.pt-4.pb-2
    .d-flex.align-center.justify-space-between.mb-2
      .header-title-wrap
        h1.text-h5.font-weight-bold おすすめチャンネル
        p.text-caption.text-medium-emphasis 公式・厳選されたラジオ番組
      v-btn(
        icon="mdi-refresh"
        variant="tonal"
        density="comfortable"
        :loading="stationsStore.isLoadingRecommended"
        @click="refresh"
      )

    //- 検索入力
    v-text-field.mt-2(
      v-model="searchQuery"
      prepend-inner-icon="mdi-magnify"
      placeholder="チャンネル名・楽曲・アーティストで検索..."
      variant="outlined"
      density="compact"
      rounded="pill"
      hide-details
      clearable
    )

    //- ドメイン切り替えチップス
    .domain-chips-wrap.mt-3
      v-chip(
        :color="selectedDomain === 'all' ? 'primary' : undefined"
        :variant="selectedDomain === 'all' ? 'flat' : 'outlined'"
        size="small"
        @click="selectedDomain = 'all'"
      ) すべて ({{ stationsStore.recommendedStations.length }})

      v-chip.ml-2(
        v-for="dom in stationsStore.recommendedDomains"
        :key="dom"
        :color="selectedDomain === dom ? 'primary' : undefined"
        :variant="selectedDomain === dom ? 'flat' : 'outlined'"
        size="small"
        @click="selectedDomain = dom"
      ) {{ getDomainLabel(dom) }}

  //- メインスクロールエリア
  .explore-content.px-4.pb-16
    //- ローディング中
    v-row(v-if="stationsStore.isLoadingRecommended && stationsStore.recommendedStations.length === 0")
      v-col(cols="12" sm="6" md="4" v-for="i in 4" :key="i")
        v-skeleton-loader(type="card-avatar, text, text")

    //- チャンネル一覧
    v-row.mt-2(v-else-if="filteredStations.length > 0")
      v-col(
        cols="6"
        sm="4"
        md="3"
        v-for="channel in filteredStations"
        :key="channel.id"
      )
        v-card.station-card(
          variant="elevated"
          elevation="2"
          rounded="xl"
          :class="{ 'playing-active': isCurrentlyPlaying(channel) }"
          @click="playStation(channel)"
        )
          .station-card-inner
            //- 1. 現在再生中のアルバムアート
            .art-container
              v-img.card-art(
                :src="getChannelArtwork(channel)"
                cover
                aspect-ratio="1"
                error-icon="mdi-radio-tower"
              )
                template(#placeholder)
                  .d-flex.align-center.justify-center.fill-height
                    v-progress-circular(indeterminate color="primary" size="20")

              //- ライブステータスバッジ
              .status-badge(:class="channel.nowPlaying?.is_online !== false ? 'online' : 'offline'")
                span.dot
                span {{ channel.nowPlaying?.is_online !== false ? 'LIVE' : 'OFF' }}

              //- リスナー数
              .listener-badge
                v-icon(size="12" color="white") mdi-account-group
                span {{ channel.nowPlaying?.listeners?.current ?? 0 }}

              //- 再生オーバーレイボタン
              .play-overlay
                v-btn(
                  :icon="isCurrentlyPlaying(channel) && playerStore.isPlaying ? 'mdi-pause' : 'mdi-play'"
                  color="primary"
                  size="large"
                  elevation="6"
                  @click.stop="togglePlayStation(channel)"
                )

            //- 2. アルバムアートの下: 曲名・アーティスト・チャンネル情報
            .station-meta-box.pa-3
              //- 曲名（太字・一番大きく表示）
              .song-title.font-weight-bold.text-truncate(title="曲名")
                | {{ channel.nowPlaying?.now_playing?.song?.title || '配信中' }}

              //- アーティスト名（その下に表示）
              .song-artist.text-caption.text-medium-emphasis.text-truncate.mt-1(title="アーティスト")
                | {{ channel.nowPlaying?.now_playing?.song?.artist || 'アーティスト不明' }}

              //- チャンネル名
              .channel-name.text-caption.text-disabled.text-truncate.mt-1
                v-icon.mr-1(size="12") mdi-radio-tower
                | {{ channel.station.name }}

              //- フッターアクション: ドメイン & プレイリスト追加ボタン
              .d-flex.align-center.justify-space-between.mt-2.pt-1.border-top-thin(@click.stop)
                v-chip(
                  size="x-small"
                  color="primary"
                  variant="tonal"
                  density="compact"
                ) {{ getDomainLabel(channel.domain) }}

                v-btn(
                  :icon="isInPlaylist(channel) ? 'mdi-playlist-check' : 'mdi-playlist-plus'"
                  :color="isInPlaylist(channel) ? 'primary' : 'medium-emphasis'"
                  variant="text"
                  density="compact"
                  size="small"
                  @click.stop="togglePlaylist(channel)"
                )

    //- 該当なし
    .empty-state.text-center.py-12(v-else)
      v-icon(size="64" color="medium-emphasis" icon="mdi-radio-tower-off")
      h3.text-h6.mt-3 チャンネルが見つかりませんでした
      p.text-caption.text-medium-emphasis 検索条件を変更するか、再読み込みをお試しください。
      v-btn.mt-4(
        color="primary"
        variant="tonal"
        prepend-icon="mdi-refresh"
        @click="refresh"
      ) 再読み込み
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useStationsStore } from '@/stores/stations'
import { usePlayerStore } from '@/stores/player'
import type { ChannelItem } from '@/types/azuracast'
import { getChannelArtUrl } from '@/utils/azuracast'

const router = useRouter()
const stationsStore = useStationsStore()
const playerStore = usePlayerStore()

const searchQuery = ref('')
const selectedDomain = ref<string>('all')

onMounted(async () => {
  await stationsStore.loadRecommendedStations()
})

async function refresh() {
  await stationsStore.loadRecommendedStations(true)
}

const filteredStations = computed(() => {
  let list = stationsStore.recommendedStations

  if (selectedDomain.value !== 'all') {
    list = list.filter((ch) => ch.domain === selectedDomain.value)
  }

  const query = searchQuery.value.trim().toLowerCase()
  if (!query) return list

  return list.filter((ch) => {
    const nameMatch = ch.station.name.toLowerCase().includes(query)
    const titleMatch = ch.nowPlaying?.now_playing?.song?.title
      ?.toLowerCase()
      .includes(query)
    const artistMatch = ch.nowPlaying?.now_playing?.song?.artist
      ?.toLowerCase()
      .includes(query)
    return nameMatch || titleMatch || artistMatch
  })
})

function getDomainLabel(domain: string): string {
  if (domain.includes('radio.enoki.xyz')) return 'enoki.xyz'
  if (domain.includes('cast.architectural-n.net')) return 'architectural-n'
  try {
    return new URL(domain).hostname
  } catch {
    return domain
  }
}

function getChannelArtwork(channel: ChannelItem): string {
  return getChannelArtUrl(channel)
}

function isCurrentlyPlaying(channel: ChannelItem): boolean {
  return playerStore.currentChannel?.id === channel.id
}

function isInPlaylist(channel: ChannelItem): boolean {
  return stationsStore.isInPlaylist(channel.id)
}

function togglePlaylist(channel: ChannelItem) {
  if (isInPlaylist(channel)) {
    stationsStore.removeFromPlaylist(channel.id)
  } else {
    stationsStore.addToPlaylist(channel)
  }
}

async function playStation(channel: ChannelItem) {
  await playerStore.playChannel(channel, stationsStore.recommendedStations)
  router.push('/player')
}

async function togglePlayStation(channel: ChannelItem) {
  if (isCurrentlyPlaying(channel)) {
    await playerStore.togglePlay()
  } else {
    await playerStore.playChannel(channel, stationsStore.recommendedStations)
  }
}
</script>

<style scoped lang="scss">
.explore-page-root {
  min-height: 100%;
  padding-bottom: 90px;
}

.domain-chips-wrap {
  display: flex;
  overflow-x: auto;
  padding-bottom: 4px;
  scrollbar-width: none;
  &::-webkit-scrollbar {
    display: none;
  }
}

.station-card {
  cursor: pointer;
  background-color: rgb(var(--v-theme-surface));
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
  overflow: hidden;
  border: 1px solid rgba(var(--v-theme-on-surface), 0.08);

  &:hover {
    transform: translateY(-3px);
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.25);
  }

  &.playing-active {
    border: 1.5px solid rgb(var(--v-theme-primary));
  }
}

.art-container {
  position: relative;
  width: 100%;
  overflow: hidden;

  .card-art {
    transition: transform 0.3s ease;
  }

  &:hover .card-art {
    transform: scale(1.04);
  }

  .status-badge {
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

  .listener-badge {
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

  .play-overlay {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    background: rgba(0, 0, 0, 0.25);
    opacity: 0;
    transition: opacity 0.2s ease;
  }

  &:hover .play-overlay {
    opacity: 1;
  }
}

.station-meta-box {
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
</style>
