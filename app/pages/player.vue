<template lang="pug">
.player-redirect-page
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { usePlayerStore } from '@/stores/player'

const router = useRouter()
const playerStore = usePlayerStore()

onMounted(() => {
  playerStore.openFullPlayer()
  router.replace('/')
})
</script>
