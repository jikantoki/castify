import { Capacitor } from '@capacitor/core'

/**
 * Castify 公式本番URL
 */
export const CASTIFY_OFFICIAL_URL = 'https://castify.enoki.xyz'

/**
 * アプリのベースURLを取得する
 * APK環境（Capacitorネイティブ）やローカル開発環境（localhost）では
 * https://localhost にならないよう公式URL https://castify.enoki.xyz を返す
 */
export function getAppBaseUrl(): string {
  if (typeof window === 'undefined') {
    return CASTIFY_OFFICIAL_URL
  }

  // ネイティブ環境またはlocalhost時は公式URLに統一
  if (Capacitor.isNativePlatform() || window.location.origin.includes('localhost')) {
    return CASTIFY_OFFICIAL_URL
  }

  return window.location.origin || CASTIFY_OFFICIAL_URL
}

/**
 * アプリ内の特定ページの共有URLを取得する
 */
export function getAppShareUrl(path = ''): string {
  const base = getAppBaseUrl()
  const cleanPath = path ? (path.startsWith('/') ? path : `/${path}`) : ''
  return `${base}${cleanPath}`
}

/**
 * チャンネル（AzuraCastステーション）の共有URLを生成する
 * 形式: https://ドメイン/public/チャンネルスラッグ/
 * @param domain ステーションの配信元ドメイン（例: "https://radio.enoki.xyz" または "radio.enoki.xyz"）
 * @param shortcode チャンネルスラッグ（ステーションのショートコード）
 */
export function getStationShareUrl(domain: string | undefined | null, shortcode: string | undefined | null): string {
  let host = domain ? domain.trim() : ''
  if (!host) {
    host = 'radio.enoki.xyz'
  }

  try {
    if (host.includes('://')) {
      host = new URL(host).host
    } else {
      host = host.split('/')[0]
    }
  } catch {
    host = host.replace(/^https?:\/\//i, '').replace(/\/+$/, '')
  }

  const slug = (shortcode || '').trim()
  return `https://${host}/public/${slug}/`
}
