import { defineStore } from 'pinia'

export const useSettingsStore = defineStore('settings', {
  state: () => ({
    /** 表示と言語設定 */
    display: {
      /** テーマ */
      theme: 'system' as 'system' | 'light' | 'dark',
      /** 言語 */
      language: '日本語' as const,
      /** Android等でアプリ起動中に画面をオフにしない */
      keepScreenOn: true,
      /** チャンネル一覧の表示形式 (grid / list) */
      viewMode: 'grid' as 'grid' | 'list',
    },
    /** 通知設定 */
    notification: {},
    /** 開発者オプション */
    developerOptions: {
      /** 開発者オプションが有効 */
      enabled: false,
      /** ステータスバーのノッチの切り欠き */
      statusBarNotch: 'default' as 'default' | 'true' | 'false',
    },
    /** 画面からは見えない設定 */
    hidden: {
      /** AndroidかつVer.15以上で自動true */
      isAndroid15OrHigher: false,
    },
    /** 初回起動チュートリアル閲覧フラグ */
    hasSeenTutorial: false,
  }),
  actions: {
    /** チュートリアル完了状態にする */
    setTutorialCompleted(completed = true) {
      this.hasSeenTutorial = completed
    },
    /** チャンネル一覧の表示形式 (grid / list) を切り替え */
    setViewMode(mode: 'grid' | 'list') {
      if (!this.display) {
        this.display = {
          theme: 'system',
          language: '日本語',
          keepScreenOn: true,
          viewMode: 'grid',
        }
      }
      this.display.viewMode = mode
    },
  },
  persist: true,
})
