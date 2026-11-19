import { defineNuxtPlugin } from '#app'
import { useStationsStore } from '@/stores/stations'

export default defineNuxtPlugin((nuxtApp) => {
  const pinia = nuxtApp.$pinia
  if (!pinia) return

  const stationsStore = useStationsStore(pinia)

  // クライアント起動直後に localStorage からプレイリスト・ドメイン・お気に入りを復元
  stationsStore.initFromStorage()

  // ストアの状態変更を監視し、自動で localStorage に永続化
  stationsStore.$subscribe((_mutation, state) => {
    stationsStore.saveToStorage()
  })
})
