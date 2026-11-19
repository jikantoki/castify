export interface FeatureItem {
  id: string
  title: string
  summary: string
  icon: string
  color: string
  details: string[]
}

export interface TechStackItem {
  category: string
  name: string
  version: string
  description: string
  icon: string
}

export interface CreatorItem {
  id: string
  name: string
  role: string
  url: string
  icon: string
  color: string
  description: string
}

export const TEMPLATE_INFO = {
  name: 'Castify',
  englishName: 'Tune into the Soundscape.',
  title: 'つなぐ、ひろがる、あなただけの電波。',
  version: '1.0.0',
  description: `Castify は、世界中の AzuraCast インターネットラジオチャンネルを、どこでも最高の体験で楽しむためのクロスプラットフォーム音楽プレイヤーです。好きなAzuraCastサーバーのドメインを追加して自分だけのプレイリストを作り、リアルタイムのオンエア楽曲や過去の配信履歴、次の配信予定をSpotifyライクな洗練されたUIで快適に楽しめます。`,
  githubUrl: 'https://github.com/',

  features: [
    {
      id: 'azuracast',
      title: 'AzuraCast 連携 & 途切れない再生',
      summary: 'ページ遷移しても止まらないストリーミング',
      icon: 'mdi-radio-tower',
      color: '#1DB954',
      details: [
        'Nuxt のグローバルストアによるシングルトン再生エンジン',
        'どのページへ遷移しても音声が絶対に止まらないアーキテクチャ',
        'キャッシュバスターによる最新ライブストリームへの即時同期',
        '常に画面下部に常駐するスマートなミニプレイヤー',
      ],
    },
    {
      id: 'custom-domains',
      title: 'カスタムドメイン追加',
      summary: '好きなAzuraCastサーバーの全チャンネルを即登録',
      icon: 'mdi-server-plus',
      color: '#119EFF',
      details: [
        'ドメインやURL（https://...）を入力するだけでAPIからチャンネル一覧を自動取得',
        'ワンクリックでマイプレイリストへ一括インポート',
        '複数サーバーのチャンネルをシームレスに横断再生',
        'Pinia によるローカルストレージ永続化',
      ],
    },
    {
      id: 'spotify-ui',
      title: 'Spotify風 UI & 詳細情報',
      summary: '洗練されたプレイヤーと配信履歴の確認',
      icon: 'mdi-music-circle',
      color: '#E91E63',
      details: [
        'アルバムアートワークのアンビエント発光グラデーション背景',
        'リアルタイムな曲名・アーティスト名・LIVEインジケーター',
        '詳細情報モーダルで「過去の配信履歴 (History)」を確認可能',
        '「次に流れる予定の楽曲 (Next Up)」や「生配信DJ情報」を即座に表示',
      ],
    },
    {
      id: 'media-session',
      title: 'メディアコントロール API',
      summary: 'ブラウザ & Android 通知バー・ロック画面に完全対応',
      icon: 'mdi-cellphone-link',
      color: '#9C27B0',
      details: [
        'Web標準 Media Session API のネイティブ連携',
        'Android の通知シェードやロック画面にアルバムアートと曲情報を表示',
        'Bluetoothイヤホンやヘッドセットのボタンから再生・一時停止・曲送り操作',
        'PCブラウザのメディアハブやキーボードメディアキーに対応',
      ],
    },
  ],

  techStack: [
    {
      category: 'Frontend Framework',
      name: 'Nuxt 4',
      version: '4.6.x',
      description: 'Vue 3 + Vite による高速SSR/SSG & モバイル向けPWA構築基盤',
      icon: 'mdi-nuxt',
    },
    {
      category: 'Native Hybrid',
      name: 'Capacitor',
      version: '8.x',
      description: 'Web技術をiOS / Androidネイティブアプリへとブリッジ',
      icon: 'mdi-cellphone-link',
    },
    {
      category: 'UI Component',
      name: 'Vuetify 3',
      version: '3.7.x',
      description: 'Material Design 3 に基づくモダンで美しいUIコンポーネント群',
      icon: 'mdi-vuetify',
    },
    {
      category: 'State Management',
      name: 'Pinia & Persistedstate',
      version: '3.x',
      description: 'オーディオ再生状態の一元管理と設定・プレイリストのローカル保存',
      icon: 'mdi-database',
    },
  ],

  creators: [
    {
      id: 'enoki',
      name: 'エノキ電気',
      role: 'アプリ開発 / サーバー・インフラ構築',
      url: 'https://enoki.xyz/profile',
      icon: 'mdi-lightning-bolt',
      color: '#00DC82',
      description:
        '「radio.enoki.xyz」を運営。Web・インフラ・アプリ開発、AI音楽制作などを手がける。',
    },
    {
      id: 'neruru',
      name: 'ねるる',
      role: 'ラジオ配信 / コンテンツ・音楽制作',
      url: 'https://architectural-n.net',
      icon: 'mdi-headphones',
      color: '#E91E63',
      description:
        '「cast.architectural-n.net」を運営。インターネットラジオ「ねるるラジオ」「ねるるの日常」等の配信や楽曲を制作。',
    },
  ],
}
