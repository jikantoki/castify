import type {
  AzuraCastNowPlaying,
  AzuraCastStation,
  ChannelItem,
} from '@/types/azuracast'

/**
 * ユーザーが入力したドメイン文字列を正規化する
 * 例: "radio.enoki.xyz" -> "https://radio.enoki.xyz"
 * 例: "http://example.com/radio/" -> "http://example.com"
 */
export function normalizeDomain(input: string): string {
  let cleaned = input.trim()
  if (!cleaned) return ''

  // プロトコルが付いていなければ https:// を補完
  if (!/^https?:\/\//i.test(cleaned)) {
    cleaned = 'https://' + cleaned
  }

  try {
    const url = new URL(cleaned)
    // origin 部分（protocol + host + port）のみを取得
    return url.origin.replace(/\/+$/, '')
  } catch {
    // URLパース失敗時は最低限末尾スラッシュを削除
    return cleaned.replace(/\/+$/, '')
  }
}

/**
 * ストリームURLの補正
 * .onion アドレスや相対パス、不正なURLを検出した場合、正規のAzuraCastストリームURLに修復
 */
export function fixStreamUrl(
  listenUrl: string | undefined | null,
  domain: string,
  shortcode: string
): string {
  const normDomain = normalizeDomain(domain)

  if (!listenUrl || listenUrl.includes('.onion')) {
    // AzuraCast のデフォルトマウントパス
    return `${normDomain}/listen/${shortcode}/radio.mp3`
  }

  if (listenUrl.startsWith('/')) {
    return `${normDomain}${listenUrl}`
  }

  // ページが HTTPS の場合、ストリームが HTTP だと Mixed Content ブロックされることがあるため HTTPS へ変換
  if (normDomain.startsWith('https://') && listenUrl.startsWith('http://') && !listenUrl.includes('localhost')) {
    try {
      const u = new URL(listenUrl)
      if (u.hostname === new URL(normDomain).hostname) {
        return listenUrl.replace(/^http:/, 'https:')
      }
    } catch {
      // ignore
    }
  }

  return listenUrl
}

/**
 * アートワークURLの補正
 */
export function fixArtUrl(
  artUrl: string | undefined | null,
  domain: string
): string {
  if (!artUrl) return '/thumbnail.jpg'

  const normDomain = normalizeDomain(domain)
  if (artUrl.includes('.onion')) {
    try {
      const u = new URL(artUrl)
      return `${normDomain}${u.pathname}${u.search}`
    } catch {
      return '/thumbnail.jpg'
    }
  }

  if (artUrl.startsWith('/')) {
    return `${normDomain}${artUrl}`
  }

  return artUrl
}

/**
 * チャンネルのサムネイル画像を決定する（全チャンネルで確実に表示するためのフォールバック付き）
 */
export function getChannelArtUrl(channel: ChannelItem | null | undefined): string {
  if (!channel) return '/thumbnail.jpg'

  const domain = channel.domain || ''

  // 1. 現在オンエア中の曲のアートワーク
  const currentArt = channel.nowPlaying?.now_playing?.song?.art
  if (currentArt && currentArt.trim() && !currentArt.endsWith('/thumbnail.jpg')) {
    return fixArtUrl(currentArt, domain)
  }

  // 2. 次回予定曲のアートワーク
  const nextArt = channel.nowPlaying?.playing_next?.song?.art
  if (nextArt && nextArt.trim() && !nextArt.endsWith('/thumbnail.jpg')) {
    return fixArtUrl(nextArt, domain)
  }

  // 3. 直近の履歴曲のアートワーク
  if (channel.nowPlaying?.song_history && channel.nowPlaying.song_history.length > 0) {
    for (const sh of channel.nowPlaying.song_history) {
      if (sh.song?.art && sh.song.art.trim() && !sh.song.art.endsWith('/thumbnail.jpg')) {
        return fixArtUrl(sh.song.art, domain)
      }
    }
  }

  // 4. liveのart
  const liveArt = channel.nowPlaying?.live?.art
  if (liveArt && liveArt.trim()) {
    return fixArtUrl(liveArt, domain)
  }

  // 5. ステーションごとのフォールバック
  const shortcode = channel.station?.shortcode || ''
  const normDomain = normalizeDomain(domain)
  if (normDomain && shortcode) {
    if (shortcode === 'neruru-radio') {
      return `${normDomain}/static/uploads/neruru-radio/album_art.1795069862.jpg`
    }
    if (shortcode === 'lofi') {
      return `${normDomain}/static/uploads/lofi/album_art.1790098211.png`
    }
    if (shortcode === 'nerurus_room_rest') {
      return `${normDomain}/static/uploads/nerurus_room_rest/album_art.1794852265.png`
    }
  }

  return '/thumbnail.jpg'
}

/**
 * NowPlaying オブジェクト内の各種URLを安全なURLに正規化
 */
export function sanitizeNowPlaying(
  np: AzuraCastNowPlaying,
  domain: string
): AzuraCastNowPlaying {
  const normDomain = normalizeDomain(domain)
  const shortcode = np.station.shortcode || String(np.station.id)

  // station.listen_url の補正
  np.station.listen_url = fixStreamUrl(np.station.listen_url, normDomain, shortcode)

  // mounts の補正
  if (np.station.mounts && np.station.mounts.length > 0) {
    np.station.mounts = np.station.mounts.map((m) => ({
      ...m,
      url: fixStreamUrl(m.url, normDomain, shortcode),
    }))
  }

  // now_playing.song.art の補正
  if (np.now_playing?.song) {
    np.now_playing.song.art = fixArtUrl(np.now_playing.song.art, normDomain)
  }

  // playing_next.song.art の補正
  if (np.playing_next?.song) {
    np.playing_next.song.art = fixArtUrl(np.playing_next.song.art, normDomain)
  }

  // song_history の補正
  if (np.song_history && np.song_history.length > 0) {
    np.song_history = np.song_history.map((sh) => ({
      ...sh,
      song: {
        ...sh.song,
        art: fixArtUrl(sh.song?.art, normDomain),
      },
    }))
  }

  return np
}

/**
 * ドメインから /api/nowplaying を取得
 */
export async function fetchNowPlayingApi(
  domain: string
): Promise<AzuraCastNowPlaying[]> {
  const normDomain = normalizeDomain(domain)
  const endpoint = `${normDomain}/api/nowplaying`

  const response = await fetch(endpoint, {
    headers: {
      Accept: 'application/json',
    },
  })

  if (!response.ok) {
    throw new Error(`APIリクエスト失敗: ${response.status} ${response.statusText}`)
  }

  const data = (await response.json()) as AzuraCastNowPlaying[]
  if (!Array.isArray(data)) {
    throw new Error('予期しないデータ構造です（配列ではありません）')
  }

  return data.map((np) => sanitizeNowPlaying(np, normDomain))
}

/**
 * ドメインとステーションID/ショートコードから単一ステーションの /api/nowplaying/{station} を取得
 */
export async function fetchSingleStationNowPlaying(
  domain: string,
  stationIdOrShortcode: string | number
): Promise<AzuraCastNowPlaying> {
  const normDomain = normalizeDomain(domain)
  const endpoint = `${normDomain}/api/nowplaying/${stationIdOrShortcode}`

  const response = await fetch(endpoint, {
    headers: {
      Accept: 'application/json',
    },
  })

  if (!response.ok) {
    throw new Error(`APIリクエスト失敗: ${response.status} ${response.statusText}`)
  }

  const data = (await response.json()) as AzuraCastNowPlaying
  return sanitizeNowPlaying(data, normDomain)
}

/**
 * NowPlaying から ChannelItem に変換するヘルパー
 */
export function convertNowPlayingToChannelItem(
  np: AzuraCastNowPlaying,
  domain: string,
  isCustom = false
): ChannelItem {
  const normDomain = normalizeDomain(domain)
  return {
    id: `${normDomain}#${np.station.id}`,
    domain: normDomain,
    station: np.station,
    nowPlaying: np,
    isCustom,
    addedAt: Date.now(),
  }
}
