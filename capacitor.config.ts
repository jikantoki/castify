import type { CapacitorConfig } from '@capacitor/cli'

const config: CapacitorConfig = {
  appId: 'xyz.enoki.castify',
  appName: 'Castify',
  webDir: 'dist',
  plugins: {
    StatusBar: {
      overlaysWebView: false,
    },
    PushNotifications: {
      presentationOptions: ['badge', 'sound', 'alert'],
    },
    CapacitorHttp: {
      enabled: true,
    },
  },
  server: {
    androidScheme: 'https',
  },
  android: {
    // useLegacyBridge: true,
  },
}

export default config
