import { Capacitor, registerPlugin } from '@capacitor/core'
import type { PluginListenerHandle } from '@capacitor/core'
import type { ChannelItem } from '@/types/azuracast'
import { fixArtUrl, fixStreamUrl, getChannelArtUrl } from '@/utils/azuracast'

/**
 * Android ネイティブ再生（Media3 / Android Auto）とのブリッジ。
 * 実装: android/app/src/main/java/xyz/enoki/castify/media/CastifyPlayerPlugin.java
 *
 * Web / PWA / iOS では使われず、従来どおり HTMLAudioElement で再生する。
 */

/** ネイティブ側に渡すチャンネル情報（Channel.java と対応） */
export interface NativeChannel {
  id: string
  domain: string
  shortcode: string
  name: string
  description?: string
  streamUrl: string
  artUrl?: string
  songTitle?: string
  songArtist?: string
  songAlbum?: string
  songArtUrl?: string
  isLive: boolean
  streamerName?: string
}

/** ネイティブ側の再生状態（stateChange イベント / 各メソッドの戻り値） */
export interface NativePlayerState {
  connected: boolean
  isPlaying?: boolean
  playWhenReady?: boolean
  isLoading?: boolean
  volume?: number
  currentIndex?: number
  channelId?: string
  title?: string
  artist?: string
  queueIds?: string[]
  errorCode?: string
  errorMessage?: string
}

export interface CastifyPlayerPlugin {
  syncLibrary(options: {
    playlist: NativeChannel[]
    recommended?: NativeChannel[]
  }): Promise<{ playlist: number; recommended: number }>
  play(options: { queue: NativeChannel[]; startIndex: number }): Promise<NativePlayerState>
  pause(): Promise<NativePlayerState>
  resume(): Promise<NativePlayerState>
  stop(): Promise<NativePlayerState>
  next(): Promise<NativePlayerState>
  previous(): Promise<NativePlayerState>
  setVolume(options: { volume: number }): Promise<NativePlayerState>
  getState(): Promise<NativePlayerState>
  addListener(
    eventName: 'stateChange',
    listener: (state: NativePlayerState) => void
  ): Promise<PluginListenerHandle>
}

export const CastifyPlayer = registerPlugin<CastifyPlayerPlugin>('CastifyPlayer')

/** Android アプリ上でネイティブ再生エンジンが使えるか */
export function isNativePlayerAvailable(): boolean {
  if (typeof window === 'undefined') return false
  return Capacitor.getPlatform() === 'android' && Capacitor.isPluginAvailable('CastifyPlayer')
}

/** チャンネルのストリーム URL を解決（Web 版・ネイティブ版で共通） */
export function resolveStreamUrl(channel: ChannelItem): string {
  const rawUrl =
    channel.nowPlaying?.station?.listen_url ||
    channel.station.listen_url ||
    channel.station.mounts?.[0]?.url
  const shortcode = channel.station.shortcode || String(channel.station.id)
  return fixStreamUrl(rawUrl, channel.domain, shortcode)
}

function absoluteUrl(url: string | undefined | null): string | undefined {
  if (!url) return undefined
  if (!/^https?:\/\//i.test(url)) return undefined
  if (url.endsWith('/thumbnail.jpg')) return undefined
  return url
}

/** ChannelItem をネイティブ用の軽量な形に変換 */
export function toNativeChannel(channel: ChannelItem): NativeChannel {
  const np = channel.nowPlaying
  const song = np?.now_playing?.song
  const shortcode = channel.station.shortcode || String(channel.station.id)
  return {
    id: channel.id,
    domain: channel.domain,
    shortcode,
    name: channel.station.name || shortcode,
    description: channel.station.description || undefined,
    streamUrl: resolveStreamUrl(channel),
    artUrl: absoluteUrl(getChannelArtUrl(channel)),
    songTitle: song?.title || undefined,
    songArtist: song?.artist || undefined,
    songAlbum: song?.album || undefined,
    songArtUrl: song?.art ? absoluteUrl(fixArtUrl(song.art, channel.domain)) : undefined,
    isLive: np?.live?.is_live || false,
    streamerName: np?.live?.streamer_name || undefined,
  }
}
