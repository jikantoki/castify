<template lang="pug">
v-card.about-page-root(
  style="width: 100%; height: 100%;"
  :class="settings.hidden.isAndroid15OrHigher ? 'top-android-15-or-higher' : ''"
)
  //- トップバー
  v-card-actions.about-page-header
    v-btn(
      icon="mdi-arrow-left"
      variant="text"
      @click="handleBack"
    )
    p.header-title About Castify
    v-spacer
    v-btn(
      icon="mdi-share-variant"
      variant="text"
      @click="sharePage"
    )

  //- メインスクロールエリア
  v-card-text.about-page-content
    AboutView(initialTab="features")
</template>

<script lang="ts">
import { Share } from '@capacitor/share'
import { Toast } from '@capacitor/toast'
import { getAppShareUrl } from '@/utils/url'
import AboutView from '@/components/about/AboutView.vue'
import { useSettingsStore } from '@/stores/settings'
import Setup from '@/js/setup'
import mixins from '@/mixins/mixins'

export default {
  name: 'AboutPage',
  components: {
    AboutView,
  },
  mixins: [mixins],
  data() {
    return {
      settings: useSettingsStore(),
    }
  },
  setup() {
    Setup.setTitle('About')
    Setup.setDescription(
      'つなぐ、ひろがる、あなただけの電波。AzuraCastインターネットラジオプレイヤー「Castify」の概要と機能一覧です。',
    )
  },
  async mounted() {
    this.setTitle('About')
    const developerOptions = localStorage.getItem('developerOptions')
    if (developerOptions) {
      try {
        const options = JSON.parse(developerOptions)
        if (options.statusBarNotch !== undefined) {
          this.settings.hidden.isAndroid15OrHigher = options.statusBarNotch
        }
      } catch {
        // ignore
      }
    }
  },
  methods: {
    handleBack() {
      if (window.history.length > 1) {
        this.$router.back()
      } else {
        this.$router.push('/')
      }
    },
    async sharePage() {
      const shareUrl = getAppShareUrl('/about')
      try {
        await Share.share({
          title: 'About - Castify',
          text: 'このアプリについて',
          url: shareUrl,
          dialogTitle: '共有',
        })
      } catch {
        if (navigator.clipboard) {
          await navigator.clipboard.writeText(shareUrl)
          await Toast.show({ text: 'URLをコピーしました' })
        }
      }
    },
  },
}
</script>

<style lang="scss" scoped>
.about-page-root {
  display: flex;
  flex-direction: column;
  height: 100%;
  height: 100dvh;
  position: relative;
  overflow: hidden;

  .about-page-header {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    z-index: 10;
    background: rgba(var(--v-theme-surface), 0.95);
    backdrop-filter: blur(8px);
    border-bottom: 1px solid rgba(var(--v-theme-on-surface), 0.08);

    .header-title {
      font-size: 1.1em;
      font-weight: 700;
      margin: 0;
    }
  }

  .about-page-content {
    margin-top: 56px;
    height: calc(100% - 56px);
    overflow-y: auto;
    padding: 16px;
  }
}
</style>
