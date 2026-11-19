<template lang="pug">
div
  //- ================= ボトムナビゲーションバー =================
  .store-bottom-nav
    .nav-buttons
      //- 1. プレイリスト (ホーム)
      .nav-btn(
        :class="{ active: isHomeActive }"
        v-ripple
        @click="goTo('/')"
      )
        v-icon {{ isHomeActive ? 'mdi-playlist-music' : 'mdi-playlist-music-outline' }}
        span プレイリスト

      //- 2. おすすめ
      .nav-btn(
        :class="{ active: isExploreActive }"
        v-ripple
        @click="goTo('/explore')"
      )
        v-icon {{ isExploreActive ? 'mdi-compass' : 'mdi-compass-outline' }}
        span おすすめ

      //- 3. 設定
      .nav-btn(
        :class="{ active: isSettingsActive }"
        v-ripple
        @click="goTo('/settings')"
      )
        v-icon {{ isSettingsActive ? 'mdi-cog' : 'mdi-cog-outline' }}
        span 設定

      //- 4. その他
      .nav-btn(
        :class="{ active: optionsDialog }"
        v-ripple
        @click="openOptions"
      )
        v-icon mdi-dots-horizontal
        span その他

    .bottom-android-15-or-higher(v-if="settings.hidden.isAndroid15OrHigher")

  //- ================= その他 / ようこそダイアログ =================
  v-dialog.options-dialog(
    v-model="optionsDialog"
    :fullscreen="isMobile"
    :transition="isMobile ? 'dialog-bottom-transition' : 'dialog-transition'"
    :max-width="isMobile ? undefined : 480"
    :width="isMobile ? '100%' : '90%'"
    scrollable
    teleport="body"
  )
    v-card.options-dialog-card(:class="{ 'is-pc': !isMobile }")
      .top-android-15-or-higher(v-if="settings.hidden.isAndroid15OrHigher && isMobile")
      v-card-actions.dialog-header
        .d-flex.align-center.gap-2.ml-2
          v-icon(color="primary" icon="mdi-radio-tower")
          p.dialog-title.font-weight-bold Castify へようこそ
        v-spacer
        v-btn.dialog-close-btn(
          icon="mdi-close"
          variant="text"
          density="comfortable"
          @click="optionsDialog = false"
        )

      v-card-text.dialog-content
        //- アプリブランディング / キャッチフレーズ
        .account-details
          .account-img-wrap
            img.account-img(
              src="/icon.png"
              alt="Castify Logo"
              onerror="this.src='/favicon.ico'"
            )
          .account-info
            h2.brand-title Castify
            p.brand-catchphrase つなぐ、ひろがる、あなただけの電波。
            p.brand-subtext AzuraCast Internet Radio Player
            .d-flex.justify-center.gap-1.mt-2
              v-chip(
                size="x-small"
                color="primary"
                variant="flat"
              ) v1.0.0
              v-chip(
                size="x-small"
                color="secondary"
                variant="outlined"
              ) Nuxt 4 × Capacitor

        v-divider.my-3

        //- メニューリスト
        v-list.options-list(density="comfortable")
          v-list-item.item(
            prepend-icon="mdi-playlist-music-outline"
            title="マイプレイリスト"
            subtitle="登録したチャンネルの一覧と再生"
            @click="goTo('/')"
          )
          v-list-item.item(
            prepend-icon="mdi-swap-vertical"
            title="プレイリストの並び替え・削除"
            subtitle="チャンネルの順番変更や整理"
            @click="goTo('/playlist/edit')"
          )
          v-list-item.item(
            prepend-icon="mdi-compass-outline"
            title="おすすめチャンネル"
            subtitle="厳選されたラジオ番組"
            @click="goTo('/explore')"
          )
          v-list-item.item(
            prepend-icon="mdi-cog-outline"
            title="アプリ設定"
            subtitle="テーマ切り替えや動作環境設定"
            @click="goTo('/settings')"
          )
          v-list-item.item(
            prepend-icon="mdi-school-outline"
            title="チュートリアル"
            subtitle="アプリの使い方・基本操作ガイド"
            @click="goTo('/tutorial?from=options')"
          )
          v-list-item.item(
            prepend-icon="mdi-information-outline"
            title="About Castify"
            subtitle="アプリの機能や技術情報"
            @click="goTo('/about')"
          )
          v-list-item.item(
            prepend-icon="mdi-file-document-outline"
            title="利用規約"
            subtitle="利用規約を確認 (enoki.xyz)"
            @click="openUrl('https://enoki.xyz/terms')"
          )
          v-list-item.item(
            prepend-icon="mdi-shield-lock-outline"
            title="プライバシーポリシー"
            subtitle="プライバシーポリシーを確認 (enoki.xyz)"
            @click="openUrl('https://enoki.xyz/privacy')"
          )
          v-list-item.item(
            prepend-icon="mdi-share-variant-outline"
            title="アプリをシェア"
            subtitle="友だちにCastifyを共有"
            @click="shareSite"
          )
          v-list-item.item(
            v-if="isWeb && isMobileDevice"
            prepend-icon="mdi-google-play"
            title="公式Androidアプリ"
            subtitle="Google Play で入手"
            @click="openUrl('https://play.google.com/store/apps/details?id=xyz.enoki.castify')"
          )
          v-list-item.item(
            v-if="isWeb && !isMobileDevice"
            prepend-icon="mdi-laptop"
            title="デスクトップ版をインストール"
            subtitle="PWAアプリとして追加"
            @click="promptPwaInstall"
          )
</template>

<script lang="ts">
import { Share } from '@capacitor/share'
import { Toast } from '@capacitor/toast'
import { Browser } from '@capacitor/browser'
import { Capacitor } from '@capacitor/core'
import { getAppBaseUrl } from '@/utils/url'
import { pwaInstallPrompt, isPwaInstalled } from '@/plugins/pwa-prompt.client'
import { useSettingsStore } from '@/stores/settings'
import { useMyProfileStore } from '@/stores/myProfile'
import { useNavigationStore } from '@/stores/navigation'

export default {
  name: 'BottomNav',
  props: {
    modelValue: {
      type: Boolean,
      default: false,
    },
  },
  emits: ['update:modelValue', 'tabClick'],
  data() {
    return {
      settings: useSettingsStore(),
      myProfile: useMyProfileStore(),
      navStore: useNavigationStore(),
    }
  },
  watch: {
    modelValue(val: boolean) {
      this.navStore.optionsDialog = val
    },
  },
  computed: {
    optionsDialog: {
      get(): boolean {
        return this.navStore.optionsDialog || this.modelValue
      },
      set(val: boolean) {
        this.navStore.optionsDialog = val
        this.$emit('update:modelValue', val)
      },
    },
    currentPath(): string {
      return this.$route.path
    },
    isHomeActive(): boolean {
      return this.currentPath === '/'
    },
    isExploreActive(): boolean {
      return this.currentPath.startsWith('/explore')
    },
    isPlayerActive(): boolean {
      return this.currentPath.startsWith('/player')
    },
    isSettingsActive(): boolean {
      return this.currentPath.startsWith('/settings')
    },
    isMobile(): boolean {
      return Boolean(this.$vuetify?.display?.xs)
    },
    isWeb(): boolean {
      return !Capacitor.isNativePlatform() && !isPwaInstalled.value
    },
    isMobileDevice(): boolean {
      if (typeof window === 'undefined') return false
      const ua = navigator.userAgent || ''
      return (
        /Android|iPhone|iPad|iPod|Mobile/i.test(ua) ||
        Boolean(this.$vuetify?.display?.smAndDown)
      )
    },
  },
  methods: {
    openOptions() {
      this.optionsDialog = true
      this.navStore.openOptions()
    },
    goTo(path: string) {
      this.optionsDialog = false
      this.$emit('tabClick', path)
      if (this.currentPath !== path) {
        this.$router.push(path)
      }
    },
    async openUrl(url: string) {
      this.optionsDialog = false
      try {
        if (Capacitor.isNativePlatform()) {
          await Browser.open({ url })
        } else {
          window.open(url, '_blank', 'noopener,noreferrer')
        }
      } catch {
        window.open(url, '_blank', 'noopener,noreferrer')
      }
    },
    async promptPwaInstall() {
      this.optionsDialog = false
      if (pwaInstallPrompt.value) {
        pwaInstallPrompt.value.prompt()
        await pwaInstallPrompt.value.userChoice
        pwaInstallPrompt.value = null
      } else {
        await Toast.show({
          text: 'ブラウザのアドレスバーのインストールボタンから追加できます',
        })
      }
    },
    async shareSite() {
      this.optionsDialog = false
      const title = 'Castify - つなぐ、ひろがる、あなただけの電波。'
      const text =
        'AzuraCastのラジオチャンネルをSpotify風UIで快適に聴ける次世代プレイヤー'
      const url = getAppBaseUrl()
      try {
        await Share.share({
          title,
          text,
          url,
          dialogTitle: 'Castifyをシェア',
        })
      } catch (e) {
        if (navigator.clipboard) {
          await navigator.clipboard.writeText(url)
          await Toast.show({ text: 'リンクをコピーしました' })
        }
      }
    },
  },
}
</script>

<style scoped lang="scss">
.store-bottom-nav {
  position: fixed;
  bottom: 0;
  left: 0;
  width: 100%;
  height: 56px;
  background-color: rgb(var(--v-theme-surface));
  border-top: 1px solid rgba(var(--v-theme-on-surface), 0.1);
  display: flex;
  flex-direction: column;
  z-index: 101;

  .nav-buttons {
    display: flex;
    justify-content: space-around;
    align-items: center;
    height: 100%;

    .nav-btn {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      flex: 1;
      height: 100%;
      cursor: pointer;
      color: rgba(var(--v-theme-on-surface), 0.6);
      transition: color 0.2s ease;

      span {
        font-size: 0.68rem;
        margin-top: 2px;
      }

      &.active {
        color: rgb(var(--v-theme-primary));
        font-weight: bold;
      }
    }
  }
}

.options-dialog-card {
  width: 100%;
  background-color: rgb(var(--v-theme-surface)) !important;
  color: rgb(var(--v-theme-on-surface));
  display: flex;
  flex-direction: column;

  &.is-pc {
    border-radius: 20px !important;
    max-height: 85vh;
    box-shadow: 0 16px 48px rgba(0, 0, 0, 0.3) !important;
    border: 1px solid rgba(var(--v-theme-on-surface), 0.1);

    .dialog-header {
      padding: 16px 16px 12px 20px;
      flex-shrink: 0;
      border-bottom: 1px solid rgba(var(--v-theme-on-surface), 0.08);
    }

    .dialog-content {
      padding: 20px;
      overflow-y: auto;
    }
  }

  &:not(.is-pc) {
    min-height: 100vh;
    height: 100%;
    border-radius: 0 !important;

    .dialog-header {
      padding: 12px 12px 12px 16px;
      flex-shrink: 0;
      border-bottom: 1px solid rgba(var(--v-theme-on-surface), 0.08);
    }

    .dialog-content {
      padding: 16px;
      overflow-y: auto;
    }
  }

  .dialog-title {
    font-size: 1.15rem;
    margin: 0;
  }

  .account-details {
    display: flex;
    flex-direction: column;
    align-items: center;
    text-align: center;
    padding: 8px 0;

    .account-img-wrap {
      width: 72px;
      height: 72px;
      border-radius: 50%;
      overflow: hidden;
      margin-bottom: 10px;
      box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);
      border: 2px solid rgb(var(--v-theme-primary));

      .account-img {
        width: 100%;
        height: 100%;
        object-fit: cover;
      }
    }

    .account-info {
      .brand-title {
        font-size: 1.35rem;
        font-weight: 800;
        margin: 0;
        line-height: 1.2;
      }

      .brand-catchphrase {
        font-size: 0.85rem;
        font-weight: 600;
        color: rgb(var(--v-theme-primary));
        margin: 4px 0 0;
      }

      .brand-subtext {
        font-size: 0.75rem;
        color: rgba(var(--v-theme-on-surface), 0.6);
        margin: 2px 0 0;
      }
    }
  }

  .options-list {
    background: transparent;
    padding: 0;

    .item {
      border-radius: 12px;
      margin-bottom: 6px;
      cursor: pointer;
      transition: background-color 0.15s ease;

      &:hover {
        background-color: rgba(var(--v-theme-on-surface), 0.06);
      }
    }
  }
}

.bottom-android-15-or-higher {
  height: 16px;
}
.top-android-15-or-higher {
  height: 40px;
}
.gap-1 {
  gap: 4px;
}
.gap-2 {
  gap: 8px;
}
</style>
