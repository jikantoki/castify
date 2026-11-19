<template lang="pug">
.wait
  .wait-wrap(style="background:rgb(var(--v-theme-background));")
    img.splash-img(src="/icon.png" alt="App Icon" onerror="this.src='/favicon.ico'")
    p.mt-4.text-h5.font-weight-bold.splash-title Castify
    p.text-caption.text-medium-emphasis.splash-subtitle つなぐ、ひろがる、あなただけの電波。
    .ma-8
    .loading
      v-progress-linear(indeterminate color="primary" rounded height="4")
</template>

<script lang="ts">
export default {
  name: 'CommonSplash',
}
</script>

<style lang="scss" scoped>
.wait-wrap {
  position: fixed;
  z-index: 9999;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: rgb(var(--v-theme-background));
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;

  .splash-img {
    width: 96px;
    height: 96px;
    border-radius: 24px;
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
  }

  .splash-title {
    letter-spacing: 0.5px;
    margin-bottom: 2px;
  }

  .splash-subtitle {
    letter-spacing: 0.8px;
  }

  .loading {
    width: 140px;
  }
}
</style>
