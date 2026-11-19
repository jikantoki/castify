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

const STORAGE_KEY_PLAYLIST = 'castify_playlist_v1'
const STORAGE_KEY_DOMAINS = 'castify_custom_domains_v1'
const STORAGE_KEY_FAVORITES = 'castify_favorites_v1'

/**
 * 永続化保存用にチャンネルデータを軽量化（巨大な過去履歴などを削る）
 */
function sanitizeChannelForStorage(ch: ChannelItem): ChannelItem {
  return {
    id: ch.id,
    domain: ch.domain,
    station: ch.station,
    isCustomDomain: ch.isCustomDomain,
    addedAt: ch.addedAt,
    isFavorite: ch.isFavorite,
    nowPlaying: ch.nowPlaying
      ? {
          ...ch.nowPlaying,
          // 過去履歴はローカルストレージ肥大化を防ぐため直近3件のみ保持
          song_history: (ch.nowPlaying.song_history || []).slice(0, 3),
        }
      : null,
  }
}

/**
 * localStorage への直接書き込み
 */
function saveStateToStorage(customDomains: string[], playlist: ChannelItem[], favorites: string[]) {
  if (typeof window === 'undefined') return
  try {
    localStorage.setItem(STORAGE_KEY_DOMAINS, JSON.stringify(customDomains))
    localStorage.setItem(STORAGE_KEY_FAVORITES, JSON.stringify(favorites))
    const sanitized = playlist.map(sanitizeChannelForStorage)
    localStorage.setItem(STORAGE_KEY_PLAYLIST, JSON.stringify(sanitized))
  } catch (err) {
    console.warn('[stationsStore] Failed to save state to localStorage:', err)
  }
}

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
  isInitialized: boolean
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
    isInitialized: false,
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
     * localStorage からプレイリスト・カスタムドメイン・お気に入りを復元
     */
    initFromStorage() {
      if (typeof window === 'undefined') return

      try {
        // 1. カスタムドメイン復元
        const rawDomains = localStorage.getItem(STORAGE_KEY_DOMAINS)
        if (rawDomains) {
          const parsed = JSON.parse(rawDomains)
          if (Array.isArray(parsed) && parsed.length > 0) {
            this.customDomains = Array.from(new Set([...this.customDomains, ...parsed]))
          }
        }

        // 2. お気に入り復元
        const rawFavorites = localStorage.getItem(STORAGE_KEY_FAVORITES)
        if (rawFavorites) {
          const parsed = JSON.parse(rawFavorites)
          if (Array.isArray(parsed) && parsed.length > 0) {
            this.favorites = Array.from(new Set([...this.favorites, ...parsed]))
          }
        }

        // 3. プレイリスト復元
        const rawPlaylist = localStorage.getItem(STORAGE_KEY_PLAYLIST)
        if (rawPlaylist) {
          const parsed = JSON.parse(rawPlaylist)
          if (Array.isArray(parsed) && parsed.length > 0) {
            // 既存のプレイリストと重複排除しながら結合または設定
            const map = new Map<string, ChannelItem>()
            for (const item of parsed) {
              if (item && item.id) {
                map.set(item.id, item)
              }
            }
            for (const item of this.playlist) {
              if (item && item.id) {
                map.set(item.id, item)
              }
            }
            this.playlist = Array.from(map.values())
          }
        }

        this.isInitialized = true
      } catch (err) {
        console.warn('[stationsStore] initFromStorage error:', err)
      }
    },

    /**
     * 現在の状態を localStorage に即時永続化
     */
    saveToStorage() {
      saveStateToStorage(this.customDomains, this.playlist, this.favorites)
    },

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

        // 保存
        this.saveToStorage()

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
      this.saveToStorage()
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
        this.saveToStorage()
      }
    },

    /**
     * チャンネルをプレイリストから削除
     */
    removeFromPlaylist(channelId: string) {
      this.playlist = this.playlist.filter((item) => item.id !== channelId)
      this.saveToStorage()
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
      this.saveToStorage()
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
      // 最新の nowPlaying 情報を保存
      this.saveToStorage()
    },
  },

  persist: {
    key: 'castify_stations_pinia',
    pick: ['customDomains', 'playlist', 'favorites'],
  },
})
