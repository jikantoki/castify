import { defineStore } from 'pinia'
import type { ChannelItem, AzuraCastNowPlaying } from '@/types/azuracast'
import {
  fetchSingleStationNowPlaying,
  fixStreamUrl,
  fixArtUrl,
  getChannelArtUrl,
} from '@/utils/azuracast'
import { useStationsStore } from '@/stores/stations'

let globalAudio: HTMLAudioElement | null = null

function getAudio(): HTMLAudioElement | null {
  if (typeof window === 'undefined') return null
  if (!globalAudio) {
    globalAudio = new Audio()
    globalAudio.preload = 'none'
  }
  return globalAudio
}

interface PlayerState {
  currentChannel: ChannelItem | null
  nowPlayingDetail: AzuraCastNowPlaying | null
  isPlaying: boolean
  isLoading: boolean
  volume: number
  isMuted: boolean
  liveElapsed: number
  liveDuration: number
  currentStreamUrl: string
  error: string | null
  playQueue: ChannelItem[]
  currentQueueIndex: number
}

export const usePlayerStore = defineStore('player', {
  state: (): PlayerState => ({
    currentChannel: null,
    nowPlayingDetail: null,
    isPlaying: false,
    isLoading: false,
    volume: 1,
    isMuted: false,
    liveElapsed: 0,
    liveDuration: 0,
    currentStreamUrl: '',
    error: null,
    playQueue: [],
    currentQueueIndex: -1,
  }),

  getters: {
    hasCurrentChannel(state): boolean {
      return state.currentChannel !== null
    },

    currentSongTitle(state): string {
      return (
        state.nowPlayingDetail?.now_playing?.song?.title ||
        state.currentChannel?.nowPlaying?.now_playing?.song?.title ||
        state.currentChannel?.station?.name ||
        '放送局未選択'
      )
    },

    currentArtist(state): string {
      return (
        state.nowPlayingDetail?.now_playing?.song?.artist ||
        state.currentChannel?.nowPlaying?.now_playing?.song?.artist ||
        state.currentChannel?.station?.description ||
        ''
      )
    },

    currentAlbum(state): string {
      return (
        state.nowPlayingDetail?.now_playing?.song?.album ||
        state.currentChannel?.station?.name ||
        ''
      )
    },

    currentArtwork(state): string {
      const art =
        state.nowPlayingDetail?.now_playing?.song?.art ||
        state.currentChannel?.nowPlaying?.now_playing?.song?.art
      if (art && art.trim() && !art.endsWith('/thumbnail.jpg')) {
        return fixArtUrl(art, state.currentChannel?.domain || '')
      }
      return getChannelArtUrl(state.currentChannel)
    },

    isLive(state): boolean {
      return (
        state.nowPlayingDetail?.live?.is_live ||
        state.currentChannel?.nowPlaying?.live?.is_live ||
        false
      )
    },

    liveStreamerName(state): string {
      return (
        state.nowPlayingDetail?.live?.streamer_name ||
        state.currentChannel?.nowPlaying?.live?.streamer_name ||
        ''
      )
    },

    listenerCount(state): number {
      return (
        state.nowPlayingDetail?.listeners?.current ??
        state.currentChannel?.nowPlaying?.listeners?.current ??
        0
      )
    },

    progressPercent(state): number {
      if (state.liveDuration <= 0) return 0
      const pct = (state.liveElapsed / state.liveDuration) * 100
      return Math.min(Math.max(pct, 0), 100)
    },
  },

  actions: {
    /**
     * オーディオ初期化とイベントリスナーの設定
     */
    initAudio() {
      if (typeof window === 'undefined') return
      const audio = getAudio()
      if (!audio) return

      // イベントリスナーの多重登録を防ぐため、一度リセット
      audio.onplaying = () => {
        this.isPlaying = true
        this.isLoading = false
        this.error = null
        this.updateMediaSessionPlaybackState('playing')
      }

      audio.onpause = () => {
        this.isPlaying = false
        this.updateMediaSessionPlaybackState('paused')
      }

      audio.onwaiting = () => {
        this.isLoading = true
      }

      audio.oncanplay = () => {
        this.isLoading = false
      }

      audio.onerror = (e) => {
        console.error('オーディオ再生エラー:', e)
        this.isPlaying = false
        this.isLoading = false
        this.error = 'ストリームの再生に失敗しました。一時的にオフラインの可能性があります。'
        this.updateMediaSessionPlaybackState('none')
      }

      audio.volume = this.isMuted ? 0 : this.volume

      this.setupMediaSessionActionHandlers()
      this.startElapsedTimer()
    },

    /**
     * チャンネルを再生する
     * @param channel 再生対象のチャンネル
     * @param queue 再生コンテキストのキュー（プレイリストまたはおすすめ一覧など）
     */
    async playChannel(channel: ChannelItem, queue?: ChannelItem[]) {
      this.initAudio()
      const audio = getAudio()
      if (!audio) return

      this.error = null
      this.isLoading = true
      this.currentChannel = channel
      this.nowPlayingDetail = channel.nowPlaying || null

      if (queue && queue.length > 0) {
        this.playQueue = [...queue]
        this.currentQueueIndex = this.playQueue.findIndex((item) => item.id === channel.id)
      } else {
        const stationsStore = useStationsStore()
        const defaultQueue =
          stationsStore.playlist.length > 0
            ? stationsStore.playlist
            : stationsStore.recommendedStations
        this.playQueue = [...defaultQueue]
        this.currentQueueIndex = this.playQueue.findIndex((item) => item.id === channel.id)
      }

      // ストリームURLの解決
      const rawUrl =
        channel.nowPlaying?.station?.listen_url ||
        channel.station.listen_url ||
        channel.station.mounts?.[0]?.url
      const shortcode = channel.station.shortcode || String(channel.station.id)
      const streamUrl = fixStreamUrl(rawUrl, channel.domain, shortcode)

      // キャッシュバスターを付与してライブストリームの最新バッファを取得
      const cacheBustUrl = streamUrl.includes('?')
        ? `${streamUrl}&_cb=${Date.now()}`
        : `${streamUrl}?_cb=${Date.now()}`

      this.currentStreamUrl = cacheBustUrl

      try {
        audio.src = cacheBustUrl
        audio.load()
        await audio.play()
        this.isPlaying = true
        this.isLoading = false
      } catch (err: any) {
        console.error('Play failed:', err)
        this.isPlaying = false
        this.isLoading = false
        this.error = 'ストリームの再生を開始できませんでした'
      }

      // リアルタイム詳細情報の取得 & MediaSession 設定
      await this.refreshNowPlayingDetail()
      this.updateMediaSession()
      this.startPolling()
    },

    /**
     * 再生/一時停止トグル
     */
    async togglePlay() {
      const audio = getAudio()
      if (!audio) return

      if (!this.currentChannel) {
        // 再生対象が未選択の場合、プレイリストの先頭またはおすすめを再生
        const stationsStore = useStationsStore()
        const target =
          stationsStore.playlist[0] || stationsStore.recommendedStations[0]
        if (target) {
          await this.playChannel(target)
        }
        return
      }

      if (this.isPlaying) {
        this.pause()
      } else {
        this.resume()
      }
    },

    /**
     * 一時停止
     */
    pause() {
      const audio = getAudio()
      if (audio) {
        audio.pause()
      }
      this.isPlaying = false
      this.updateMediaSessionPlaybackState('paused')
    },

    /**
     * 再生再開
     */
    async resume() {
      const audio = getAudio()
      if (!audio) return

      this.error = null
      this.isLoading = true
      try {
        // ライブストリームの場合、一時停止後に再開するときは最新ストリームへ再接続
        if (this.currentStreamUrl) {
          const base = this.currentStreamUrl.split('?')[0]
          audio.src = `${base}?_cb=${Date.now()}`
          audio.load()
        }
        await audio.play()
        this.isPlaying = true
        this.isLoading = false
        this.updateMediaSessionPlaybackState('playing')
      } catch (err: any) {
        console.error('Resume failed:', err)
        this.isLoading = false
        this.isPlaying = false
        this.error = '再開に失敗しました'
      }
    },

    /**
     * 停止
     */
    stop() {
      const audio = getAudio()
      if (audio) {
        audio.pause()
        audio.src = ''
      }
      this.isPlaying = false
      this.isLoading = false
      this.updateMediaSessionPlaybackState('none')
    },

    /**
     * 音量設定 (0 - 1)
     */
    setVolume(vol: number) {
      this.volume = Math.max(0, Math.min(1, vol))
      this.isMuted = this.volume === 0
      const audio = getAudio()
      if (audio) {
        audio.volume = this.volume
      }
    },

    /**
     * ミュート切り替え
     */
    toggleMute() {
      this.isMuted = !this.isMuted
      const audio = getAudio()
      if (audio) {
        audio.volume = this.isMuted ? 0 : this.volume
      }
    },

    /**
     * 次のチャンネルへ
     */
    async nextChannel() {
      if (this.playQueue.length === 0) return
      let nextIdx = this.currentQueueIndex + 1
      if (nextIdx >= this.playQueue.length) {
        nextIdx = 0
      }
      const nextCh = this.playQueue[nextIdx]
      if (nextCh) {
        await this.playChannel(nextCh, this.playQueue)
      }
    },

    /**
     * 前のチャンネルへ
     */
    async prevChannel() {
      if (this.playQueue.length === 0) return
      let prevIdx = this.currentQueueIndex - 1
      if (prevIdx < 0) {
        prevIdx = this.playQueue.length - 1
      }
      const prevCh = this.playQueue[prevIdx]
      if (prevCh) {
        await this.playChannel(prevCh, this.playQueue)
      }
    },

    /**
     * Media Session API のハンドラーを登録（ブラウザ & Android両対応）
     */
    setupMediaSessionActionHandlers() {
      if (typeof window === 'undefined' || !('mediaSession' in navigator)) {
        return
      }

      try {
        navigator.mediaSession.setActionHandler('play', () => {
          this.resume()
        })
        navigator.mediaSession.setActionHandler('pause', () => {
          this.pause()
        })
        navigator.mediaSession.setActionHandler('previoustrack', () => {
          this.prevChannel()
        })
        navigator.mediaSession.setActionHandler('nexttrack', () => {
          this.nextChannel()
        })
        navigator.mediaSession.setActionHandler('stop', () => {
          this.stop()
        })
      } catch (err) {
        console.warn('MediaSession handler registration error:', err)
      }
    },

    /**
     * Media Session メタデータの更新
     */
    updateMediaSession() {
      if (typeof window === 'undefined' || !('mediaSession' in navigator)) {
        return
      }

      const title = this.currentSongTitle
      const artist = this.currentArtist || this.currentChannel?.station?.name || 'Castify'
      const album = this.currentAlbum || this.currentChannel?.station?.name || 'Castify Radio'
      const artworkUrl = this.currentArtwork

      try {
        navigator.mediaSession.metadata = new MediaMetadata({
          title,
          artist,
          album,
          artwork: [
            { src: artworkUrl, sizes: '96x96', type: 'image/jpeg' },
            { src: artworkUrl, sizes: '128x128', type: 'image/jpeg' },
            { src: artworkUrl, sizes: '192x192', type: 'image/jpeg' },
            { src: artworkUrl, sizes: '256x256', type: 'image/jpeg' },
            { src: artworkUrl, sizes: '384x384', type: 'image/jpeg' },
            { src: artworkUrl, sizes: '512x512', type: 'image/jpeg' },
          ],
        })
        this.updateMediaSessionPlaybackState(this.isPlaying ? 'playing' : 'paused')
      } catch (err) {
        console.warn('Failed to update MediaMetadata:', err)
      }
    },

    /**
     * MediaSession の再生状態を更新
     */
    updateMediaSessionPlaybackState(state: 'playing' | 'paused' | 'none') {
      if (typeof window === 'undefined' || !('mediaSession' in navigator)) {
        return
      }
      try {
        navigator.mediaSession.playbackState = state
      } catch (err) {
        // ignore
      }
    },

    /**
     * 現在再生中ステーションの最新 NowPlaying 詳細を取得
     */
    async refreshNowPlayingDetail() {
      if (!this.currentChannel) return
      try {
        const shortcode =
          this.currentChannel.station.shortcode ||
          String(this.currentChannel.station.id)
        const detail = await fetchSingleStationNowPlaying(
          this.currentChannel.domain,
          shortcode
        )
        const oldTitle = this.currentSongTitle
        this.nowPlayingDetail = detail
        this.currentChannel.nowPlaying = detail

        if (detail.now_playing) {
          this.liveElapsed = detail.now_playing.elapsed || 0
          this.liveDuration = detail.now_playing.duration || 0
        }

        // 曲が変わっていたら MediaSession を即座に更新
        if (this.currentSongTitle !== oldTitle) {
          this.updateMediaSession()
        }

        // stationsStore の playlist にも最新情報を同期
        const stationsStore = useStationsStore()
        const targetId = this.currentChannel.id
        const pIdx = stationsStore.playlist.findIndex((item) => item.id === targetId)
        if (pIdx >= 0) {
          stationsStore.playlist[pIdx] = {
            ...stationsStore.playlist[pIdx],
            nowPlaying: detail,
            station: detail.station || stationsStore.playlist[pIdx].station,
          }
        }
      } catch (e) {
        console.warn('NowPlaying detail refresh error:', e)
      }
    },

    /**
     * 定期ポーリング（10秒ごと）
     */
    startPolling() {
      if (typeof window === 'undefined') return
      // 既存のタイマーをクリア
      if ((window as any).__castify_poll_timer) {
        clearInterval((window as any).__castify_poll_timer)
      }

      ;(window as any).__castify_poll_timer = setInterval(async () => {
        if (this.isPlaying && this.currentChannel) {
          await this.refreshNowPlayingDetail()
        }
      }, 10000)
    },

    /**
     * 1秒ごとの経過時間インクリメント
     */
    startElapsedTimer() {
      if (typeof window === 'undefined') return
      if ((window as any).__castify_elapsed_timer) {
        clearInterval((window as any).__castify_elapsed_timer)
      }

      ;(window as any).__castify_elapsed_timer = setInterval(() => {
        if (this.isPlaying && this.liveDuration > 0) {
          if (this.liveElapsed < this.liveDuration) {
            this.liveElapsed += 1
          }
        }
      }, 1000)
    },
  },

  persist: {
    pick: ['volume', 'isMuted'],
  },
})
