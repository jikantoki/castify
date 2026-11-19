<template lang="pug">
.tutorial-page-root(
  :class="settings.hidden.isAndroid15OrHigher ? 'top-android-15-or-higher' : ''"
)
  //- トップヘッダー
  .tutorial-header.d-flex.align-center.justify-space-between.px-4.py-3
    .d-flex.align-center.gap-2
      v-btn(
        v-if="isFromSettings || currentPage > 0"
        icon="mdi-arrow-left"
        variant="text"
        density="comfortable"
        @click="handleBackOrPrev"
        title="戻る"
      )
      .header-step-indicator
        span.font-weight-bold {{ currentPage + 1 }}
        span.text-medium-emphasis.mx-1 /
        span.text-medium-emphasis 4

    .d-flex.align-center.gap-2
      //- 初回時はスキップボタン、設定からの場合は閉じるボタン
      v-btn(
        v-if="!isFromSettings"
        variant="text"
        density="comfortable"
        size="small"
        color="medium-emphasis"
        rounded="pill"
        @click="finishTutorial"
      ) スキップ
      v-btn(
        v-else
        icon="mdi-close"
        variant="text"
        density="comfortable"
        @click="finishTutorial"
        title="閉じる"
      )

  //- スライドメインコンテンツ
  .tutorial-content-wrap
    v-window.tutorial-window(
      v-model="currentPage"
      touch
      continuous
    )
      //- ================= PAGE 1: ようこそ Castify へ =================
      v-window-item(:value="0")
        .tutorial-slide.px-6.py-4
          .tutorial-slide-inner.d-flex.flex-column.align-center.text-center
            .slide-visual.mb-6
              .icon-halo-bg.color-primary
                v-avatar(size="100" color="primary" elevation="6")
                  v-icon(size="54" color="white") mdi-radio-tower
              .brand-badge.mt-3
                v-chip(color="primary" variant="flat" size="small") AzuraCast Radio Player

            h1.text-h5.font-weight-black.mb-2 Castify へようこそ
            p.text-subtitle-2.text-primary.font-weight-bold.mb-4 つなぐ、ひろがる、あなただけの電波。

            p.text-body-2.text-medium-emphasis.mb-6.lead-description
              | Castify は、世界中の AzuraCast インターネットラジオを
              | 最高の音質と洗練された UI で楽しむための音楽プレイヤーです。
              | 会員登録は不要。開いた瞬間からあなただけの電波がひろがります。

            .features-summary-list.w-100
              .feature-row
                .feature-icon-wrap.color-teal
                  v-icon(size="20") mdi-lightning-bolt
                .feature-text
                  span.font-weight-bold すぐに聴ける
                  span.caption-desc アプリを開くだけで厳選ラジオ番組を即時ストリーミング
              .feature-row
                .feature-icon-wrap.color-indigo
                  v-icon(size="20") mdi-music-note-eighth
                .feature-text
                  span.font-weight-bold 途切れない高音質
                  span.caption-desc 画面遷移やアプリ操作中も音声が止まらない快適設計
              .feature-row
                .feature-icon-wrap.color-purple
                  v-icon(size="20") mdi-shield-check-outline
                .feature-text
                  span.font-weight-bold 完全オープン & 自由
                  span.caption-desc 広告なし・ログイン不要で自分のサーバーも自由に追加可能

      //- ================= PAGE 2: チャンネルを探す・聴く =================
      v-window-item(:value="1")
        .tutorial-slide.px-6.py-4
          .tutorial-slide-inner.d-flex.flex-column.align-center.text-center
            .slide-visual.mb-5
              .mockup-card-container
                .mockup-radio-card
                  .mockup-art-box
                    v-icon(size="42" color="white") mdi-disc-player
                    .mockup-live-pill
                      span.dot
                      span LIVE
                    .mockup-listener-pill
                      v-icon(size="12" color="white") mdi-account-group
                      span 42
                  .mockup-info-box.text-left
                    .mockup-title.text-truncate 流れている楽曲タイトル
                    .mockup-artist.text-truncate アーティスト名 • 番組名
                    .mockup-bar

            h2.text-h5.font-weight-black.mb-2 おすすめチャンネルを探索
            p.text-subtitle-2.text-secondary.font-weight-bold.mb-3 ワンタップで広がる音楽の世界

            p.text-body-2.text-medium-emphasis.mb-6.lead-description
              | 「おすすめ」タブには、公式＆厳選された AzuraCast ステーションがラインナップ。
              | オンエア中の曲名やジャケット写真、リスナー数がリアルタイムに更新されます。

            .features-summary-list.w-100
              .feature-row
                .feature-icon-wrap.color-amber
                  v-icon(size="20") mdi-compass-outline
                .feature-text
                  span.font-weight-bold リアルタイム探索
                  span.caption-desc チャンネル名や楽曲名からサクサク検索できます
              .feature-row
                .feature-icon-wrap.color-cyan
                  v-icon(size="20") mdi-play-circle-outline
                .feature-text
                  span.font-weight-bold ワンタップ即再生
                  span.caption-desc 気になる番組をタップするだけで、すぐにライブ再生開始
              .feature-row
                .feature-icon-wrap.color-green
                  v-icon(size="20") mdi-dock-bottom
                .feature-text
                  span.font-weight-bold スマートミニプレイヤー
                  span.caption-desc どのページを閲覧していても下部でスムーズに操作可能

      //- ================= PAGE 3: カスタムドメイン追加 =================
      v-window-item(:value="2")
        .tutorial-slide.px-6.py-4
          .tutorial-slide-inner.d-flex.flex-column.align-center.text-center
            .slide-visual.mb-5
              .domain-flow-visual
                .flow-node
                  v-avatar(size="54" color="primary" variant="tonal")
                    v-icon(size="28") mdi-server
                  span.flow-label 外部サーバー
                v-icon.flow-arrow(color="primary" size="24") mdi-arrow-right-thick
                .flow-node
                  v-avatar(size="54" color="secondary" variant="flat")
                    v-icon(size="28" color="white") mdi-link-variant-plus
                  span.flow-label URL登録
                v-icon.flow-arrow(color="secondary" size="24") mdi-arrow-right-thick
                .flow-node
                  v-avatar(size="54" color="teal" variant="tonal")
                    v-icon(size="28") mdi-playlist-check
                  span.flow-label 全局インポート

            h2.text-h5.font-weight-black.mb-2 好きなサーバーを追加
            p.text-subtitle-2.text-primary.font-weight-bold.mb-3 あなたの電波をつなげるカスタム機能

            p.text-body-2.text-medium-emphasis.mb-6.lead-description
              | ご自身で運用している AzuraCast サーバーや、お気に入りの配信局 URL（例: 
              code https://radio.example.com
              | ）を入力するだけで、全ステーションを自動で取得・登録できます。

            .features-summary-list.w-100
              .feature-row
                .feature-icon-wrap.color-blue
                  v-icon(size="20") mdi-server-plus
                .feature-text
                  span.font-weight-bold ドメイン入力だけ
                  span.caption-desc 面倒な設定は不要。URLを入力すれば自動で解析・連携
              .feature-row
                .feature-icon-wrap.color-pink
                  v-icon(size="20") mdi-folder-music-outline
                .feature-text
                  span.font-weight-bold マイプレイリスト化
                  span.caption-desc 複数サーバーのチャンネルをひとつのアプリでまとめて管理
              .feature-row
                .feature-icon-wrap.color-deep-orange
                  v-icon(size="20") mdi-cached
                .feature-text
                  span.font-weight-bold 設定は自動保存
                  span.caption-desc 登録したドメインは端末に安全に保存され、次回もすぐ利用可能

      //- ================= PAGE 4: 快適なリスニング & 設定 =================
      v-window-item(:value="3")
        .tutorial-slide.px-6.py-4
          .tutorial-slide-inner.d-flex.flex-column.align-center.text-center
            .slide-visual.mb-5
              .player-experience-visual
                .player-pill-badge
                  v-icon(color="primary" size="20") mdi-music-circle
                  span.font-weight-bold Spotify-like UI
                .d-flex.justify-center.gap-2.mt-3
                  v-chip(size="small" color="primary" variant="outlined" prepend-icon="mdi-cellphone-link") Media Session
                  v-chip(size="small" color="secondary" variant="outlined" prepend-icon="mdi-white-balance-sunny") 常時点灯
                  v-chip(size="small" color="teal" variant="outlined" prepend-icon="mdi-theme-light-dark") ダークモード

            h2.text-h5.font-weight-black.mb-2 極上のリスニング体験
            p.text-subtitle-2.text-primary.font-weight-bold.mb-3 いつでも・どこでも快適な音楽生活

            p.text-body-2.text-medium-emphasis.mb-6.lead-description
              | 美しいジャケット写真の発光演出、過去の配信履歴（History）や次の配信予定（Next Up）の確認、
              | 通知バー・Bluetoothイヤホン連動など、細部までこだわり抜きました。

            .features-summary-list.w-100
              .feature-row
                .feature-icon-wrap.color-purple
                  v-icon(size="20") mdi-history
                .feature-text
                  span.font-weight-bold 楽曲履歴 & 詳細モーダル
                  span.caption-desc フルプレイヤーで配信履歴や次に流れる曲、DJ情報を確認
              .feature-row
                .feature-icon-wrap.color-teal
                  v-icon(size="20") mdi-cellphone-lock
                .feature-text
                  span.font-weight-bold ロック画面 & イヤホン操作
                  span.caption-desc 画面オフでも通知シェードやワイヤレスイヤホンで再生操作可能
              .feature-row
                .feature-icon-wrap.color-amber
                  v-icon(size="20") mdi-school-outline
                .feature-text
                  span.font-weight-bold 設定からいつでも再確認可能
                  span.caption-desc このチュートリアルは「設定 > チュートリアル」からいつでも見返せます

  //- ボトムナビゲーション・アクションバー
  .tutorial-footer.px-6.pb-6.pt-3
    //- ページネーションドット
    .pagination-dots.d-flex.justify-center.align-center.gap-2.mb-4
      .dot-item(
        v-for="i in 4"
        :key="i"
        :class="{ active: currentPage === i - 1 }"
        @click="currentPage = i - 1"
        :title="`ページ ${i} へ移動`"
      )

    //- アクションボタン
    .action-buttons-wrap.d-flex.align-center.gap-3
      v-btn(
        v-if="currentPage > 0"
        variant="tonal"
        size="large"
        rounded="pill"
        @click="prevPage"
        class="prev-btn"
      ) 前へ

      v-spacer(v-if="currentPage === 0")

      //- 1〜3ページ目: 次へボタン
      v-btn(
        v-if="currentPage < 3"
        color="primary"
        variant="flat"
        size="large"
        rounded="pill"
        append-icon="mdi-arrow-right"
        class="next-btn"
        :class="{ 'flex-grow-1': currentPage > 0, 'w-100': currentPage === 0 }"
        elevation="2"
        @click="nextPage"
      ) 次へ

      //- 最終ページ: はじめるボタン
      v-btn(
        v-else
        color="primary"
        variant="flat"
        size="large"
        rounded="pill"
        append-icon="mdi-radio-tower"
        class="finish-btn flex-grow-1"
        elevation="4"
        @click="finishTutorial"
      ) {{ isFromSettings ? '設定画面に戻る' : 'Castify をはじめる！' }}
</template>

<script lang="ts">
import { useSettingsStore } from '@/stores/settings'
import Setup from '@/js/setup'
import mixins from '@/mixins/mixins'

export default {
  name: 'TutorialPage',
  mixins: [mixins],
  data() {
    return {
      settings: useSettingsStore(),
      currentPage: 0,
    }
  },
  computed: {
    isFromSettings(): boolean {
      const from = this.$route.query.from
      return from === 'settings' || from === 'options'
    },
  },
  setup() {
    Setup.setTitle('チュートリアル')
    Setup.setDescription(
      'Castify の基本的な使い方と便利な機能のご案内です。',
    )
  },
  mounted() {
    this.setTitle('チュートリアル')
  },
  watch: {
    currentPage() {
      this.$nextTick(() => {
        const activeItems = this.$el?.querySelectorAll('.v-window-item')
        activeItems?.forEach((item: HTMLElement) => {
          item.scrollTop = 0
        })
      })
    },
  },
  methods: {
    nextPage() {
      if (this.currentPage < 3) {
        this.currentPage++
      }
    },
    prevPage() {
      if (this.currentPage > 0) {
        this.currentPage--
      }
    },
    handleBackOrPrev() {
      if (this.currentPage > 0) {
        this.currentPage--
      } else if (this.isFromSettings) {
        this.finishTutorial()
      }
    },
    finishTutorial() {
      // チュートリアル閲覧完了フラグをセット
      this.settings.setTutorialCompleted(true)

      // 設定やその他画面から開かれた場合は戻る
      if (this.isFromSettings) {
        if (window.history.length > 1) {
          this.$router.back()
        } else {
          this.$router.replace('/settings')
        }
      } else {
        // 初回起動時はトップへ
        this.$router.replace('/')
      }
    },
  },
}
</script>

<style lang="scss" scoped>
.tutorial-page-root {
  display: flex;
  flex-direction: column;
  height: 100vh;
  height: 100dvh;
  width: 100%;
  max-width: 600px;
  margin: 0 auto;
  background: rgb(var(--v-theme-background));
  color: rgb(var(--v-theme-on-background));
  overflow: hidden;
  user-select: none;
}

.tutorial-header {
  flex-shrink: 0;
  z-index: 10;

  .header-step-indicator {
    font-size: 0.9rem;
  }
}

.tutorial-content-wrap {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;

  .tutorial-window {
    flex: 1;
    min-height: 0;
    height: 100%;
    display: flex;
    flex-direction: column;

    :deep(.v-window__container) {
      height: 100%;
      min-height: 100%;
      width: 100%;
    }

    :deep(.v-window-item) {
      width: 100%;
      height: 100%;
      max-height: 100%;
      overflow-y: auto;
      overflow-x: hidden;
      -webkit-overflow-scrolling: touch;
      overscroll-behavior-y: contain;

      /* スクロールバーのデザイン */
      scrollbar-width: thin;
      scrollbar-color: rgba(var(--v-theme-on-background), 0.25) transparent;

      &::-webkit-scrollbar {
        width: 4px;
      }
      &::-webkit-scrollbar-thumb {
        background-color: rgba(var(--v-theme-on-background), 0.25);
        border-radius: 4px;
      }
      &::-webkit-scrollbar-track {
        background: transparent;
      }
    }
  }
}

.tutorial-slide {
  width: 100%;
  min-height: 100%;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  animation: fadeIn 0.3s ease-in-out;

  .tutorial-slide-inner {
    width: 100%;
    max-width: 480px;
    margin: auto auto;
    padding-bottom: 24px;
  }

  .lead-description {
    line-height: 1.6;
    max-width: 400px;
  }
}

// ビジュアル・アイコン演出
.slide-visual {
  display: flex;
  flex-direction: column;
  align-items: center;

  .icon-halo-bg {
    position: relative;
    padding: 12px;
    border-radius: 50%;
    background: radial-gradient(circle, rgba(var(--v-theme-primary), 0.25) 0%, transparent 70%);
  }
}

// チャンネルカードモックアップ (Page 2)
.mockup-card-container {
  width: 240px;
  perspective: 1000px;

  .mockup-radio-card {
    background: rgb(var(--v-theme-surface));
    border-radius: 20px;
    padding: 12px;
    box-shadow: 0 10px 25px rgba(0, 0, 0, 0.15);
    border: 1px solid rgba(var(--v-theme-primary), 0.2);

    .mockup-art-box {
      width: 100%;
      height: 130px;
      border-radius: 14px;
      background: linear-gradient(135deg, #1db954, #191414);
      display: flex;
      align-items: center;
      justify-content: center;
      position: relative;

      .mockup-live-pill {
        position: absolute;
        top: 8px;
        left: 8px;
        background: rgba(239, 68, 68, 0.9);
        color: white;
        font-size: 0.65rem;
        font-weight: bold;
        padding: 2px 6px;
        border-radius: 8px;
        display: flex;
        align-items: center;
        gap: 4px;

        .dot {
          width: 5px;
          height: 5px;
          border-radius: 50%;
          background: white;
        }
      }

      .mockup-listener-pill {
        position: absolute;
        top: 8px;
        right: 8px;
        background: rgba(0, 0, 0, 0.6);
        color: white;
        font-size: 0.65rem;
        padding: 2px 6px;
        border-radius: 8px;
        display: flex;
        align-items: center;
        gap: 3px;
      }
    }

    .mockup-info-box {
      margin-top: 10px;

      .mockup-title {
        font-size: 0.85rem;
        font-weight: bold;
      }
      .mockup-artist {
        font-size: 0.72rem;
        opacity: 0.7;
      }
      .mockup-bar {
        height: 4px;
        border-radius: 2px;
        background: rgb(var(--v-theme-primary));
        width: 65%;
        margin-top: 6px;
      }
    }
  }
}

// ドメインフロー図 (Page 3)
.domain-flow-visual {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  background: rgba(var(--v-theme-surface), 0.8);
  padding: 16px 20px;
  border-radius: 20px;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.06);

  .flow-node {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 6px;

    .flow-label {
      font-size: 0.7rem;
      font-weight: bold;
      opacity: 0.8;
    }
  }
}

// プレイヤー体験ビジュアル (Page 4)
.player-experience-visual {
  background: rgba(var(--v-theme-surface), 0.9);
  padding: 20px 24px;
  border-radius: 24px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);

  .player-pill-badge {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    background: rgba(var(--v-theme-primary), 0.15);
    color: rgb(var(--v-theme-primary));
    padding: 6px 16px;
    border-radius: 20px;
    font-size: 0.9rem;
  }
}

// 特徴リスト (各ページ共通)
.features-summary-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  text-align: left;

  .feature-row {
    display: flex;
    align-items: center;
    gap: 12px;
    background: rgba(var(--v-theme-surface), 0.7);
    padding: 10px 14px;
    border-radius: 14px;
    border: 1px solid rgba(var(--v-border-color), var(--v-border-opacity));

    .feature-icon-wrap {
      width: 38px;
      height: 38px;
      border-radius: 10px;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;

      &.color-primary {
        background: rgba(var(--v-theme-primary), 0.15);
        color: rgb(var(--v-theme-primary));
      }
      &.color-teal {
        background: rgba(0, 220, 130, 0.15);
        color: #00dc82;
      }
      &.color-indigo {
        background: rgba(99, 102, 241, 0.15);
        color: #6366f1;
      }
      &.color-purple {
        background: rgba(168, 85, 247, 0.15);
        color: #a855f7;
      }
      &.color-amber {
        background: rgba(245, 158, 11, 0.15);
        color: #f59e0b;
      }
      &.color-cyan {
        background: rgba(6, 182, 212, 0.15);
        color: #06b6d4;
      }
      &.color-green {
        background: rgba(16, 185, 129, 0.15);
        color: #10b981;
      }
      &.color-blue {
        background: rgba(59, 130, 246, 0.15);
        color: #3b82f6;
      }
      &.color-pink {
        background: rgba(236, 72, 153, 0.15);
        color: #ec4899;
      }
      &.color-deep-orange {
        background: rgba(249, 115, 22, 0.15);
        color: #f97316;
      }
    }

    .feature-text {
      display: flex;
      flex-direction: column;
      font-size: 0.85rem;

      .caption-desc {
        font-size: 0.72rem;
        opacity: 0.7;
        margin-top: 2px;
      }
    }
  }
}

// フッター・ナビゲーション
.tutorial-footer {
  flex-shrink: 0;
  z-index: 10;
  background: rgb(var(--v-theme-background));
  border-top: 1px solid rgba(var(--v-border-color), var(--v-border-opacity));
  box-shadow: 0 -4px 16px rgba(0, 0, 0, 0.05);

  .pagination-dots {
    .dot-item {
      width: 8px;
      height: 8px;
      border-radius: 4px;
      background: rgba(var(--v-theme-on-background), 0.2);
      cursor: pointer;
      transition: all 0.25s ease;

      &.active {
        width: 24px;
        background: rgb(var(--v-theme-primary));
      }
    }
  }

  .action-buttons-wrap {
    .prev-btn {
      min-width: 80px;
    }
    .next-btn, .finish-btn {
      font-weight: 700;
      letter-spacing: 0.5px;
    }
  }
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
