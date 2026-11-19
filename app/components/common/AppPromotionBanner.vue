<template lang="pug">
ClientOnly
  .app-promotion-banner(v-if="shouldShow")
    v-card.promotion-card(
      variant="tonal"
      rounded="xl"
      elevation="0"
      :class="isMobile ? 'mobile-mode' : 'desktop-mode'"
    )
      .d-flex.align-start.justify-space-between.pa-4
        .d-flex.align-center.gap-3.flex-grow-1.mr-2
          //- アイコン
          .promo-icon-box
            v-icon(v-if="isMobile" size="28" color="primary") mdi-google-play
            v-icon(v-else size="28" color="primary") mdi-laptop

          //- テキスト
          .promo-text-wrap
            .d-flex.align-center.gap-2.flex-wrap
              h3.text-subtitle-1.font-weight-bold.text-truncate
                | {{ isMobile ? 'Castify 公式Androidアプリ' : 'Castify をデスクトップにインストール' }}
              v-chip(
                size="x-small"
                color="primary"
                variant="flat"
              ) {{ isMobile ? 'Google Play' : 'PWA' }}
            p.text-caption.text-medium-emphasis.mt-1.mb-0
              | {{ isMobile ? 'バックグラウンド再生や通知機能に完全対応。Google Play から公式アプリをダウンロード！' : 'ブラウザ不要でワンクリック起動。より快適にラジオをお楽しみいただけます。' }}

        //- 閉じるボタン
        v-btn(
          icon="mdi-close"
          variant="text"
          density="compact"
          size="small"
          title="閉じる"
          aria-label="閉じる"
          @click="dismiss"
        )

      //- アクションボタンバー
      .px-4.pb-3.pt-0.d-flex.align-center.gap-2.flex-wrap
        template(v-if="isMobile")
          v-btn(
            color="primary"
            variant="flat"
            rounded="pill"
            size="small"
            prepend-icon="mdi-google-play"
            @click="openAppStore"
          ) Google Play で入手
        template(v-else)
          v-btn(
            color="primary"
            variant="flat"
            rounded="pill"
            size="small"
            prepend-icon="mdi-download"
            @click="triggerPwaInstall"
          ) アプリをインストール
          v-btn(
            v-if="!pwaInstallPrompt"
            variant="text"
            rounded="pill"
            size="small"
            color="secondary"
            prepend-icon="mdi-help-circle-outline"
            @click="showPwaGuide = true"
          ) インストール方法

    //- PWA 手動インストール案内ダイアログ
    v-dialog(v-model="showPwaGuide" max-width="420")
      v-card(rounded="xl")
        v-card-title.d-flex.align-center.gap-2.pt-4
          v-icon(color="primary") mdi-laptop
          span デスクトップアプリの追加方法
        v-card-text.py-3
          p.text-body-2.mb-3 ブラウザの機能を使って Castify をデスクトップアプリ（PWA）としてインストールできます。
          v-list(density="compact")
            v-list-item(prepend-icon="mdi-google-chrome")
              v-list-item-title Chrome / Edge
              v-list-item-subtitle アドレスバー右端の「インストール」アイコンをクリックするか、メニュー（︙）から「Castify をインストール」を選択
            v-list-item(prepend-icon="mdi-apple-safari")
              v-list-item-title Safari (macOS Sonoma以降)
              v-list-item-subtitle 「ファイル」メニュー &gt;「Dockに追加...」を選択
        v-card-actions.justify-end.pa-4
          v-btn(color="primary" variant="tonal" rounded="pill" @click="showPwaGuide = false") 閉じる
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useDisplay } from 'vuetify'
import { Capacitor } from '@capacitor/core'
import { Toast } from '@capacitor/toast'
import { pwaInstallPrompt, isPwaInstalled } from '@/plugins/pwa-prompt.client'

const display = useDisplay()

const isMounted = ref(false)
const isDismissed = ref(false)
const showPwaGuide = ref(false)

const STORAGE_KEY_DISMISSED = 'castify_promo_banner_dismissed_v1'
const PLAY_STORE_URL =
  'https://play.google.com/store/apps/details?id=xyz.enoki.castify'

onMounted(() => {
  isMounted.value = true
  if (typeof window !== 'undefined') {
    const val = localStorage.getItem(STORAGE_KEY_DISMISSED)
    if (val === '1') {
      isDismissed.value = true
    }
  }
})

// モバイル端末判定（スマホブラウザ判定）
const isMobile = computed(() => {
  if (typeof window === 'undefined') return false
  const ua = navigator.userAgent || ''
  const isMobileUa = /Android|iPhone|iPad|iPod|Mobile/i.test(ua)
  return isMobileUa || (display.smAndDown.value && 'ontouchstart' in window)
})

// 表示すべきかどうかの判定
const shouldShow = computed(() => {
  if (!isMounted.value) return false
  // ネイティブアプリ（APKなど）内では一切表示しない
  if (Capacitor.isNativePlatform()) return false
  // 既にPWAスタンドアロンで動いている場合は非表示
  if (isPwaInstalled.value) return false
  // ユーザーが閉じた場合は非表示
  if (isDismissed.value) return false

  return true
})

function dismiss() {
  isDismissed.value = true
  try {
    localStorage.setItem(STORAGE_KEY_DISMISSED, '1')
  } catch {
    // ignore
  }
}

async function triggerPwaInstall() {
  if (pwaInstallPrompt.value) {
    pwaInstallPrompt.value.prompt()
    const choice = await pwaInstallPrompt.value.userChoice
    if (choice?.outcome === 'accepted') {
      dismiss()
    }
    pwaInstallPrompt.value = null
  } else {
    showPwaGuide.value = true
  }
}

function openAppStore() {
  try {
    window.open(PLAY_STORE_URL, '_blank', 'noopener,noreferrer')
  } catch {
    window.location.href = PLAY_STORE_URL
  }
}
</script>

<style scoped lang="scss">
.app-promotion-banner {
  margin-bottom: 16px;
}

.promotion-card {
  border: 1px solid rgba(var(--v-theme-primary), 0.25);
  background: linear-gradient(
    135deg,
    rgba(var(--v-theme-primary), 0.08) 0%,
    rgba(var(--v-theme-surface), 0.95) 100%
  );
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.06);
  transition: all 0.2s ease;

  &.mobile-mode {
    border-color: rgba(var(--v-theme-primary), 0.3);
  }
}

.promo-icon-box {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background-color: rgba(var(--v-theme-primary), 0.12);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.gap-2 {
  gap: 8px;
}
.gap-3 {
  gap: 12px;
}
</style>
