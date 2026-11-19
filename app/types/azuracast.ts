export interface AzuraCastMount {
  id: number
  name: string
  url: string
  bitrate: number
  format: string
  listeners: {
    total: number
    unique: number
    current: number
  }
  path: string
  is_default: boolean
}

export interface AzuraCastStation {
  id: number
  name: string
  shortcode: string
  description: string
  frontend: string
  backend: string
  timezone: string
  listen_url: string
  url: string
  public_player_url?: string
  playlist_pls_url?: string
  playlist_m3u_url?: string
  is_public?: boolean
  requests_enabled?: boolean
  mounts?: AzuraCastMount[]
  remotes?: any[]
  hls_enabled?: boolean
  hls_is_default?: boolean
  hls_url?: string | null
  hls_listeners?: number
}

export interface AzuraCastSong {
  id: string
  text: string
  artist: string
  title: string
  album: string
  genre: string
  art: string
  lyrics?: string
  isrc?: string
  custom_fields?: Record<string, any>
}

export interface AzuraCastLive {
  is_live: boolean
  streamer_name: string
  broadcast_start?: number | null
  art?: string | null
}

export interface AzuraCastNowPlayingSongItem {
  sh_id?: number
  played_at: number
  duration: number
  playlist?: string
  playlist_chain?: any
  playlist_source?: string
  streamer?: string
  is_request?: boolean
  song: AzuraCastSong
  elapsed?: number
  remaining?: number
}

export interface AzuraCastPlayingNext {
  cued_at?: number
  played_at?: number
  duration?: number
  playlist?: string
  playlist_chain?: any
  playlist_source?: string
  is_request?: boolean
  song: AzuraCastSong
}

export interface AzuraCastSongHistoryItem {
  sh_id: number
  played_at: number
  duration: number
  playlist?: string
  playlist_chain?: any
  playlist_source?: string
  streamer?: string
  is_request?: boolean
  song: AzuraCastSong
}

export interface AzuraCastNowPlaying {
  station: AzuraCastStation
  listeners: {
    total: number
    unique: number
    current: number
  }
  live: AzuraCastLive
  now_playing: AzuraCastNowPlayingSongItem
  playing_next?: AzuraCastPlayingNext | null
  song_history: AzuraCastSongHistoryItem[]
  is_online: boolean
  cache?: any
}

export interface ChannelItem {
  id: string // `${domain}#${station.id}`
  domain: string
  station: AzuraCastStation
  nowPlaying?: AzuraCastNowPlaying
  isCustom?: boolean
  isFavorite?: boolean
  addedAt?: number
}
