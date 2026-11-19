import { ref } from 'vue'

// グローバルで利用可能な deferredPrompt
export const pwaInstallPrompt = ref<any>(null)
export const isPwaInstalled = ref(false)

export default defineNuxtPlugin(() => {
  if (typeof window === 'undefined') return

  // 既にスタンドアロンモードかどうか
  if (
    window.matchMedia('(display-mode: standalone)').matches ||
    (window.navigator as any).standalone === true
  ) {
    isPwaInstalled.value = true
  }

  // PWAインストールプロンプトの捕捉
  window.addEventListener('beforeinstallprompt', (e) => {
    e.preventDefault()
    pwaInstallPrompt.value = e
  })

  // インストール完了イベント
  window.addEventListener('appinstalled', () => {
    isPwaInstalled.value = true
    pwaInstallPrompt.value = null
  })
})
