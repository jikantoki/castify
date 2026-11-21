import { defineNuxtPlugin } from '#app'
import { useStationsStore } from '@/stores/stations'
import { usePlayerStore } from '@/stores/player'
import { CastifyPlayer, isNativePlayerAvailable, toNativeChannel } from '@/utils/nativePlayer'

/**
 * Android アプリ専用:
 * マイプレイリストとおすすめをネイティブ側に同期し、Android Auto の一覧に表示できるようにする。
 * （stations-init.client.ts で localStorage から復元した後に実行されるよう、ファイル名順で後ろに置いている）
 */
export default defineNuxtPlugin((nuxtApp) => {
  if (!isNativePlayerAvailable()) return

  const pinia = nuxtApp.$pinia
  if (!pinia) return

  const stationsStore = useStationsStore(pinia)
  const playerStore = usePlayerStore(pinia)

  let timer: ReturnType<typeof setTimeout> | null = null
  let lastPayload = ''

  const sync = () => {
    timer = null
    const playlist = stationsStore.playlist.map(toNativeChannel)
    const recommended = stationsStore.recommendedStations.map(toNativeChannel)
    const payload = {
      playlist,
      // おすすめが未取得のときは送らない（ネイティブ側の保存内容を維持）
      ...(recommended.length > 0 ? { recommended } : {}),
    }
    const json = JSON.stringify(payload)
    if (json === lastPayload) return
    lastPayload = json
    CastifyPlayer.syncLibrary(payload).catch((err) => {
      lastPayload = ''
      console.warn('[native-sync] syncLibrary failed:', err)
    })
  }

  const schedule = () => {
    if (timer) clearTimeout(timer)
    timer = setTimeout(sync, 1000)
  }

  stationsStore.$subscribe(schedule)
  schedule()

  // 車側・通知側の操作をアプリの UI に反映
  playerStore.initNativeBridge()
})
