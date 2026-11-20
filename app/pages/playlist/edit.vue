<template lang="pug">
.playlist-edit-root
  //- ヘッダーバー
  .edit-header.px-3.px-sm-4.pt-4.pb-3
    .d-flex.align-center.justify-space-between.gap-2
      .header-title-box.d-flex.align-center.gap-2.flex-grow-1.min-width-0
        v-btn(
          icon="mdi-arrow-left"
          variant="text"
          density="comfortable"
          class="flex-shrink-0"
          @click="$router.push('/')"
          title="プレイリストに戻る"
          aria-label="プレイリストに戻る"
        )
        .header-texts.flex-grow-1.min-width-0
          h1.text-subtitle-1.text-sm-h6.font-weight-bold.text-truncate プレイリストの並び替え・削除
          p.text-caption.text-medium-emphasis.header-desc 順番の変更や不要なチャンネルの削除を行えます

      v-btn.flex-shrink-0(
        color="primary"
        variant="flat"
        rounded="pill"
        size="small"
        prepend-icon="mdi-check"
        @click="$router.push('/')"
      ) 完了

  //- メインコンテンツエリア
  .edit-content.px-3.px-sm-4.pb-16
    //- サブ情報・ヒントバー
    .d-flex.align-center.justify-space-between.my-3.flex-wrap.gap-2
      .d-flex.align-center.gap-2.flex-shrink-0
        v-chip(
          size="small"
          color="primary"
          variant="tonal"
          prepend-icon="mdi-playlist-music"
        ) 全 {{ stationsStore.playlist.length }} チャンネル
      .text-caption.text-disabled.hint-text.min-width-0
        v-icon.mr-1(size="14") mdi-information-outline
        | ドラッグまたは矢印ボタンで順番を移動できます

    //- プレイリストが空の場合
    v-card.empty-card.pa-6.text-center.rounded-xl.mt-4(
      v-if="stationsStore.playlist.length === 0"
      variant="outlined"
    )
      v-avatar(size="64" color="primary" variant="tonal" class="mb-3")
        v-icon(size="36") mdi-playlist-remove
      h3.text-h6.font-weight-bold プレイリストにチャンネルがありません
      p.text-body-2.text-medium-emphasis.mt-2
        | トップページまたは「おすすめ」からチャンネルを追加してください。
      .mt-4
        v-btn(
          color="primary"
          rounded="pill"
          prepend-icon="mdi-arrow-left"
          @click="$router.push('/')"
        ) プレイリストへ戻る

    //- 並び替えリスト
    .edit-list-container(v-else)
      .edit-item-row(
        v-for="(item, index) in stationsStore.playlist"
        :key="item.id"
        :draggable="true"
        :class="{ 'is-dragging': draggedIndex === index, 'drag-over-top': dropTargetIndex === index && dropPosition === 'top', 'drag-over-bottom': dropTargetIndex === index && dropPosition === 'bottom' }"
        @dragstart="onDragStart(index, $event)"
        @dragover.prevent="onDragOver(index, $event)"
        @dragleave="onDragLeave(index, $event)"
        @drop="onDrop(index, $event)"
        @dragend="onDragEnd"
      )
        v-card.edit-item-card.mb-2(
          variant="elevated"
          elevation="1"
          rounded="xl"
        )
          .d-flex.align-center.pa-2.pa-sm-3.gap-1.gap-sm-2
            //- ドラッグハンドル
            .drag-handle-wrap.d-flex.align-center.justify-center.cursor-grab.flex-shrink-0(
              title="ドラッグして並び替え"
            )
              v-icon(color="medium-emphasis" size="20") mdi-drag-vertical

            //- インデックス番号バッジ
            v-avatar(
              size="22"
              color="primary"
              variant="tonal"
              class="font-weight-bold text-caption flex-shrink-0"
            ) {{ index + 1 }}

            //- サムネイル画像
            v-avatar(size="40" rounded="lg" class="flex-shrink-0")
              v-img(
                :src="getItemArtwork(item)"
                cover
                error-icon="mdi-radio-tower"
              )
                template(#placeholder)
                  .d-flex.align-center.justify-center.fill-height
                    v-icon(size="20" color="medium-emphasis") mdi-radio-tower

            //- チャンネル名 & 情報
            .item-meta.flex-grow-1.min-width-0
              .item-title.font-weight-bold.text-truncate(:title="item.station.name")
                | {{ item.station.name }}
              .item-sub-row.d-flex.align-center.gap-1.mt-1
                v-chip.domain-chip.flex-shrink-0(
                  size="x-small"
                  color="secondary"
                  variant="tonal"
                  density="compact"
                )
                  span.domain-chip-text.text-truncate {{ getDomainLabel(item.domain) }}
                span.item-desc-text.text-caption.text-disabled.text-truncate.flex-grow-1.min-width-0(
                  v-if="item.nowPlaying?.now_playing?.song?.title || item.station?.description"
                  :title="item.nowPlaying?.now_playing?.song?.title || item.station?.description"
                )
                  | {{ item.nowPlaying?.now_playing?.song?.title || item.station?.description }}

            //- 右側操作ボタン群 (上へ、下へ、削除)
            .item-actions.d-flex.align-center.gap-1.flex-shrink-0
              //- 上へ移動ボタン
              v-btn(
                icon="mdi-arrow-up"
                variant="tonal"
                density="compact"
                size="small"
                :disabled="index === 0"
                title="1つ上へ移動"
                aria-label="1つ上へ移動"
                @click="moveUp(index)"
              )
              //- 下へ移動ボタン
              v-btn(
                icon="mdi-arrow-down"
                variant="tonal"
                density="compact"
                size="small"
                :disabled="index === stationsStore.playlist.length - 1"
                title="1つ下へ移動"
                aria-label="1つ下へ移動"
                @click="moveDown(index)"
              )
              //- 削除ボタン
              v-btn(
                icon="mdi-delete-outline"
                variant="tonal"
                density="compact"
                size="small"
                color="error"
                title="プレイリストから削除"
                aria-label="プレイリストから削除"
                @click="promptDelete(item)"
              )

  //- 削除確認ダイアログ
  v-dialog(
    v-model="deleteDialog"
    max-width="420"
  )
    v-card.rounded-xl.pa-4(v-if="targetItem")
      v-card-title.d-flex.align-center.gap-2.pa-0.mb-2
        v-icon(color="error" icon="mdi-delete-alert-outline")
        span.font-weight-bold プレイリストから削除
      v-card-text.pa-0.my-2
        p.text-body-2
          | 「
          strong {{ targetItem.station.name }}
          | 」をマイプレイリストから削除しますか？
        p.text-caption.text-medium-emphasis.mt-1
          | ※ 削除しても、「おすすめ」やドメインの追加からいつでも再登録できます。
      v-card-actions.pa-0.mt-4.justify-end
        v-btn(
          variant="text"
          @click="deleteDialog = false"
        ) キャンセル
        v-btn(
          color="error"
          variant="flat"
          rounded="pill"
          prepend-icon="mdi-delete"
          @click="executeDelete"
        ) 削除する
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Toast } from '@capacitor/toast'
import { useStationsStore } from '@/stores/stations'
import type { ChannelItem } from '@/types/azuracast'
import { getChannelArtUrl } from '@/utils/azuracast'

const stationsStore = useStationsStore()

onMounted(() => {
  stationsStore.initFromStorage()
})

function getItemArtwork(item: ChannelItem): string {
  return getChannelArtUrl(item)
}

function getDomainLabel(domain: string): string {
  try {
    return new URL(domain).hostname
  } catch {
    return domain
  }
}

// ================= 並び替えロジック =================
function moveUp(index: number) {
  if (index > 0) {
    stationsStore.moveItemUp(index)
  }
}

function moveDown(index: number) {
  if (index < stationsStore.playlist.length - 1) {
    stationsStore.moveItemDown(index)
  }
}

// ドラッグ＆ドロップ状態管理
const draggedIndex = ref<number | null>(null)
const dropTargetIndex = ref<number | null>(null)
const dropPosition = ref<'top' | 'bottom' | null>(null)

function onDragStart(index: number, e: DragEvent) {
  draggedIndex.value = index
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move'
    e.dataTransfer.setData('text/plain', String(index))
  }
}

function onDragOver(index: number, e: DragEvent) {
  if (draggedIndex.value === null || draggedIndex.value === index) {
    dropTargetIndex.value = null
    dropPosition.value = null
    return
  }
  const targetElement = e.currentTarget as HTMLElement
  const rect = targetElement.getBoundingClientRect()
  const offset = e.clientY - rect.top
  dropTargetIndex.value = index
  dropPosition.value = offset < rect.height / 2 ? 'top' : 'bottom'
}

function onDragLeave(index: number, e: DragEvent) {
  if (dropTargetIndex.value === index) {
    const related = e.relatedTarget as Node | null
    const current = e.currentTarget as Node | null
    if (!current || !related || !current.contains(related)) {
      dropTargetIndex.value = null
      dropPosition.value = null
    }
  }
}

function onDrop(index: number, e: DragEvent) {
  e.preventDefault()
  if (draggedIndex.value === null) return
  const fromIndex = draggedIndex.value
  let targetIndex = index

  if (fromIndex < index) {
    // 上から下へドラッグ
    targetIndex = dropPosition.value === 'top' ? index - 1 : index
  } else if (fromIndex > index) {
    // 下から上へドラッグ
    targetIndex = dropPosition.value === 'bottom' ? index + 1 : index
  }

  if (fromIndex !== targetIndex && targetIndex >= 0 && targetIndex < stationsStore.playlist.length) {
    stationsStore.movePlaylistItem(fromIndex, targetIndex)
  }

  onDragEnd()
}

function onDragEnd() {
  draggedIndex.value = null
  dropTargetIndex.value = null
  dropPosition.value = null
}

// ================= 削除ロジック =================
const deleteDialog = ref(false)
const targetItem = ref<ChannelItem | null>(null)

function promptDelete(item: ChannelItem) {
  targetItem.value = item
  deleteDialog.value = true
}

async function executeDelete() {
  if (!targetItem.value) return
  const name = targetItem.value.station.name
  stationsStore.removeFromPlaylist(targetItem.value.id)
  deleteDialog.value = false
  targetItem.value = null
  await Toast.show({
    text: `「${name}」をプレイリストから削除しました`,
  })
}
</script>

<style scoped lang="scss">
.playlist-edit-root {
  height: 100%;
  height: 100dvh;
  max-height: 100%;
  overflow-y: auto;
  overflow-x: hidden;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior-y: contain;
  box-sizing: border-box;

  scrollbar-width: thin;
  scrollbar-color: rgba(var(--v-theme-on-surface), 0.2) transparent;

  &::-webkit-scrollbar {
    width: 6px;
  }
  &::-webkit-scrollbar-track {
    background: transparent;
  }
  &::-webkit-scrollbar-thumb {
    background: rgba(var(--v-theme-on-surface), 0.2);
    border-radius: 3px;
  }
  &::-webkit-scrollbar-thumb:hover {
    background: rgba(var(--v-theme-on-surface), 0.4);
  }
}

.edit-content {
  padding-bottom: 160px !important;
}

.gap-1 {
  gap: 4px;
}
.gap-2 {
  gap: 8px;
}

.cursor-grab {
  cursor: grab;
  touch-action: none;
  user-select: none;

  &:active {
    cursor: grabbing;
  }
}

.edit-item-row {
  position: relative;
  transition: transform 0.15s ease;

  &.is-dragging {
    opacity: 0.4;
  }

  &.drag-over-top::before {
    content: '';
    position: absolute;
    top: -4px;
    left: 0;
    right: 0;
    height: 3px;
    background-color: rgb(var(--v-theme-primary));
    border-radius: 2px;
    z-index: 10;
  }

  &.drag-over-bottom::after {
    content: '';
    position: absolute;
    bottom: -4px;
    left: 0;
    right: 0;
    height: 3px;
    background-color: rgb(var(--v-theme-primary));
    border-radius: 2px;
    z-index: 10;
  }
}

.edit-item-card {
  background-color: rgb(var(--v-theme-surface));
  border: 1px solid rgba(var(--v-theme-on-surface), 0.08);
  overflow: hidden;
}

.min-width-0 {
  min-width: 0 !important;
}

.header-desc {
  line-height: 1.35;
  overflow-wrap: break-word;
  word-break: break-word;
}

.hint-text {
  line-height: 1.35;
  overflow-wrap: break-word;
  word-break: break-word;
}

.domain-chip {
  max-width: 100px;
}

.domain-chip-text {
  max-width: 80px;
}

.item-desc-text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
