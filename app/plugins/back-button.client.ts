import { defineNuxtPlugin } from '#app'
import { Capacitor } from '@capacitor/core'
import { App } from '@capacitor/app'
import { useNavigationStore } from '@/stores/navigation'
import { useSettingsStore } from '@/stores/settings'
import { usePlayerStore } from '@/stores/player'

export default defineNuxtPlugin((nuxtApp) => {
  if (typeof window === 'undefined') return

  const router = nuxtApp.$router

  // Android実機 / Capacitorネイティブ環境でのバックボタン（戻る操作）ハンドリング
  if (Capacitor.isNativePlatform()) {
    App.addListener('backButton', () => {
      const pinia = nuxtApp.$pinia
      const navStore = useNavigationStore(pinia)
      const settingsStore = useSettingsStore(pinia)

      // 1. 「Castify へようこそ」ダイアログ（その他ダイアログ）が開いている場合
      if (navStore.optionsDialog) {
        navStore.closeOptions()
        return
      }

      // 2. フルスクリーンプレイヤーが開いている場合
      const playerStore = usePlayerStore(pinia)
      if (playerStore.isFullPlayerOpen) {
        playerStore.closeFullPlayer()
        return
      }

      const currentRoute = router.currentRoute.value

      // 2. チュートリアル画面（Castify へようこそ）が開いている場合
      if (currentRoute.path === '/tutorial') {
        const event = new CustomEvent('castify:backbutton', { cancelable: true })
        window.dispatchEvent(event)
        if (event.defaultPrevented) {
          return
        }

        // イベントがキャンセルされなかった場合のフォールバック: ようこそ画面を閉じる
        settingsStore.setTutorialCompleted(true)
        const from = currentRoute.query.from
        if (from === 'settings' || from === 'options') {
          if (window.history.length > 1) {
            router.back()
          } else {
            router.replace('/settings')
          }
        } else {
          router.replace('/')
        }
        return
      }

      // 3. 通常の画面遷移
      if (currentRoute.path === '/') {
        // ホーム画面でこれ以上戻れない場合はアプリ終了
        App.exitApp()
      } else {
        router.back()
      }
    })
  }

  // Webブラウザ環境でも popstate 時に「Castify へようこそ」ダイアログが開いていれば閉じる
  window.addEventListener('popstate', () => {
    const pinia = nuxtApp.$pinia
    if (!pinia) return
    const navStore = useNavigationStore(pinia)
    if (navStore.optionsDialog) {
      navStore.closeOptions()
    }
    const playerStore = usePlayerStore(pinia)
    if (playerStore.isFullPlayerOpen) {
      playerStore.closeFullPlayer()
    }
  })
})
