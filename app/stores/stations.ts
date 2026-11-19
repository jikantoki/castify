import { defineStore } from 'pinia'
import type { ChannelItem } from '@/types/azuracast'
import {
  fetchNowPlayingApi,
  convertNowPlayingToChannelItem,
  normalizeDomain,
} from '@/utils/azuracast'

export const DEFAULT_RECOMMENDED_DOMAINS = [
  'https://radio.enoki.xyz',
  'https://cast.architectural-n.net',
]

interface StationsState {
  recommendedDomains: string[]
  customDomains: string[]
  playlist: ChannelItem[]
  favorites: string[]
  recommendedStations: ChannelItem[]
  isLoadingRecommended: boolean
  isLoadingCustom: boolean
  errorMessage: string | null
  lastUpdated: number
}

export const useStationsStore = defineStore('stations', {
  state: (): StationsState => ({
    recommendedDomains: [...DEFAULT_RECOMMENDED_DOMAINS],
    customDomains: [],
    playlist: [],
    favorites: [],
    recommendedStations: [],
    isLoadingRecommended: false,
    isLoadingCustom: false,
    errorMessage: null,
    lastUpdated: 0,
  }),

  getters: {
    favoriteChannels(state): ChannelItem[] {
      const favSet = new Set(state.favorites)
      // playlist または recommendedStations から検索
      const all = [...state.playlist, ...state.recommendedStations]
      const seen = new Set<string>()
      const result: ChannelItem[] = []
      for (const ch of all) {
        if (favSet.has(ch.id) && !seen.has(ch.id)) {
          seen.add(ch.id)
          result.push({ ...ch, isFavorite: true })
        }
      }
      return result
    },

    isFavorite: (state) => (channelId: string): boolean => {
      return state.favorites.includes(channelId)
    },

    isInPlaylist: (state) => (channelId: string): boolean => {
      return state.playlist.some((item) => item.id === channelId)
    },

    allDomains(state): string[] {
      return Array.from(new Set([...state.recommendedDomains, ...state.customDomains]))
    },
  },

  actions: {
    /**
     * おすすめドメインからチャンネル全件を取得
     */
    async loadRecommendedStations(force = false) {
      // 5分以内のキャッシュがあり force でなければスキップ可能
      if (!force && this.recommendedStations.length > 0 && Date.now() - this.lastUpdated < 300000) {
        return
      }

      this.isLoadingRecommended = true
      this.errorMessage = null

      const results: ChannelItem[] = []

      for (const domain of this.recommendedDomains) {
        try {
          const npList = await fetchNowPlayingApi(domain)
          for (const np of npList) {
            results.push(convertNowPlayingToChannelItem(np, domain, false))
          }
        } catch (err: any) {
          console.error(`おすすめステーション取得失敗 (${domain}):`, err)
        }
      }

      this.recommendedStations = results
      this.lastUpdated = Date.now()
      this.isLoadingRecommended = false
    },

    /**
     * 好きなAzuraCastドメインを入力して、そのドメインの全チャンネルをプレイリストに追加
     */
    async addCustomDomain(domainInput: string): Promise<{ success: boolean; message: string; count: number }> {
      const normDomain = normalizeDomain(domainInput)
      if (!normDomain) {
        return { success: false, message: '有効なURLまたはドメイン名を入力してください', count: 0 }
      }

      this.isLoadingCustom = true
      this.errorMessage = null

      try {
        const npList = await fetchNowPlayingApi(normDomain)
        if (npList.length === 0) {
          this.isLoadingCustom = false
          return { success: false, message: 'ステーションが見つかりませんでした', count: 0 }
        }

        // customDomains に登録（重複防止）
        if (!this.customDomains.includes(normDomain) && !this.recommendedDomains.includes(normDomain)) {
          this.customDomains.push(normDomain)
        }

        let addedCount = 0
        for (const np of npList) {
          const channel = convertNowPlayingToChannelItem(np, normDomain, true)
          const existingIndex = this.playlist.findIndex((item) => item.id === channel.id)
          if (existingIndex >= 0) {
            // 既存ステーションを更新
            this.playlist[existingIndex] = {
              ...this.playlist[existingIndex],
              ...channel,
            }
          } else {
            this.playlist.push(channel)
            addedCount++
          }
        }

        this.isLoadingCustom = false
        return {
          success: true,
          message: `${normDomain} から ${npList.length} 件のチャンネルを取得し、プレイリストに追加しました！`,
          count: npList.length,
        }
      } catch (err: any) {
        this.isLoadingCustom = false
        const msg = err?.message || 'ドメインの検証・接続に失敗しました'
        this.errorMessage = msg
        return { success: false, message: msg, count: 0 }
      }
    },

    /**
     * カスタムドメインを削除し、関連チャンネルもプレイリストから削除
     */
    removeCustomDomain(domain: string) {
      const normDomain = normalizeDomain(domain)
      this.customDomains = this.customDomains.filter((d) => d !== normDomain)
      this.playlist = this.playlist.filter((item) => item.domain !== normDomain)
    },

    /**
     * チャンネルをプレイリストに追加
     */
    addToPlaylist(channel: ChannelItem) {
      if (!this.playlist.some((item) => item.id === channel.id)) {
        this.playlist.push({
          ...channel,
          addedAt: Date.now(),
        })
      }
    },

    /**
     * チャンネルをプレイリストから削除
     */
    removeFromPlaylist(channelId: string) {
      this.playlist = this.playlist.filter((item) => item.id !== channelId)
    },

    /**
     * お気に入りの切り替え
     */
    toggleFavorite(channelId: string) {
      const index = this.favorites.indexOf(channelId)
      if (index >= 0) {
        this.favorites.splice(index, 1)
      } else {
        this.favorites.push(channelId)
      }
    },

    /**
     * プレイリスト内の全チャンネルの最新 now_playing 情報を再取得
     */
    async refreshPlaylistNowPlaying() {
      // プレイリストに含まれるドメイン一覧
      const domains = Array.from(new Set(this.playlist.map((ch) => ch.domain)))
      for (const dom of domains) {
        try {
          const npList = await fetchNowPlayingApi(dom)
          const map = new Map(npList.map((np) => [np.station.id, np]))
          for (let i = 0; i < this.playlist.length; i++) {
            if (this.playlist[i].domain === dom) {
              const fresh = map.get(this.playlist[i].station.id)
              if (fresh) {
                this.playlist[i].nowPlaying = fresh
                this.playlist[i].station = fresh.station
              }
            }
          }
        } catch (e) {
          console.error(`プレイリスト更新エラー (${dom}):`, e)
        }
      }
    },
  },

  persist: {
    // customDomains, playlist, favorites をローカルストレージに永続化
    pick: ['customDomains', 'playlist', 'favorites'],
  },
})
