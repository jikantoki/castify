import { Capacitor } from '@capacitor/core'

let wakeLockSentinel: any = null

/**
 * 画面常時点灯（スリープ防止）を要求する
 */
export async function requestKeepScreenOn(): Promise<boolean> {
  if (typeof window === 'undefined') return false

  // Web標準 Screen Wake Lock API
  if ('wakeLock' in navigator) {
    try {
      if (wakeLockSentinel && !wakeLockSentinel.released) {
        return true
      }
      wakeLockSentinel = await (navigator as any).wakeLock.request('screen')
      wakeLockSentinel.addEventListener('release', () => {
        wakeLockSentinel = null
      })
      return true
    } catch (err) {
      console.warn('Wake Lock request error:', err)
    }
  }

  return false
}

/**
 * 画面常時点灯を解除する
 */
export async function releaseKeepScreenOn(): Promise<void> {
  if (wakeLockSentinel) {
    try {
      await wakeLockSentinel.release()
      wakeLockSentinel = null
    } catch (err) {
      console.warn('Wake Lock release error:', err)
    }
  }
}

/**
 * 設定に応じて画面常時点灯を適用する
 */
export async function applyKeepScreenOn(enabled: boolean): Promise<void> {
  if (typeof window === 'undefined') return

  const isAndroid = Capacitor.getPlatform() === 'android'

  // Androidに限り、または設定がONの場合に適用
  if (enabled) {
    await requestKeepScreenOn()
  } else {
    await releaseKeepScreenOn()
  }
}

/**
 * アプリ復帰時の自動再取得イベントリスナーを初期化
 */
export function initKeepScreenOnWatcher(getEnabled: () => boolean): void {
  if (typeof window === 'undefined') return

  document.addEventListener('visibilitychange', async () => {
    if (document.visibilityState === 'visible' && getEnabled()) {
      await requestKeepScreenOn()
    }
  })
}
