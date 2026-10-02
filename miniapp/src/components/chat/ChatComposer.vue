<script setup lang="ts">
import { ref } from 'vue'
import YmSender from '../ym-chat-ai/components/YmSender/index.vue'
import YmAttachments from '../ym-chat-ai/components/YmAttachments/index.vue'

export interface KnowledgeOption { documentId: string; documentName?: string; knowledgeScopeName?: string }

const props = withDefaults(defineProps<{
  disabled?: boolean
  loading?: boolean
  mode: string
  selectedDocumentId?: string
  modes?: Array<{ value: string; label: string }>
  knowledgeOptions?: KnowledgeOption[]
}>(), {
  disabled: false,
  loading: false,
  selectedDocumentId: '',
  modes: () => [{ value: 'AUTO_DOCUMENT', label: '自动知识问答' }, { value: 'OPEN_CHAT', label: '开放式对话' }, { value: 'DOCUMENT', label: '指定文档问答' }],
  knowledgeOptions: () => []
})

const emit = defineEmits<{
  (event: 'submit', value: string): void
  (event: 'stop'): void
  (event: 'update:mode', value: string): void
  (event: 'update:selectedDocumentId', value: string): void
  (event: 'update:attachments', value: any[]): void
}>()

const attachments = ref<any[]>([])

function onSend(event: { message?: string }) {
  const value = String(event?.message || '').trim()
  if (value) emit('submit', value)
}
function onCancel() { emit('stop') }
function onItemsUpdate(items: any[]) {
  attachments.value = items
  emit('update:attachments', items)
}
</script>

<template>
  <view class="composer">
    <view class="selectors">
      <picker :value="Math.max(0, props.modes.findIndex((item) => item.value === props.mode))" :range="props.modes.map((item) => item.label)" @change="emit('update:mode', props.modes[Number($event.detail.value)]?.value || props.mode)">
        <view class="selector">{{ props.modes.find((item) => item.value === props.mode)?.label || props.mode }}</view>
      </picker>
      <picker v-if="props.mode === 'DOCUMENT'" :value="Math.max(0, props.knowledgeOptions.findIndex((item) => item.documentId === props.selectedDocumentId))" :range="props.knowledgeOptions.map((item) => item.documentName || item.documentId)" @change="emit('update:selectedDocumentId', props.knowledgeOptions[Number($event.detail.value)]?.documentId || '')">
        <view class="selector">{{ props.knowledgeOptions.find((item) => item.documentId === props.selectedDocumentId)?.documentName || '选择文档' }}</view>
      </picker>
    </view>
    <YmSender :disabled="props.disabled" :loading="props.loading" :show-online="true" :show-upload="true" placeholder="输入你的问题" :max-length="4000" @send="onSend" @cancel="onCancel">
      <template #header>
        <view class="attachment-header"><YmAttachments :items="attachments" @update:items="onItemsUpdate" /></view>
      </template>
    </YmSender>
  </view>
</template>

<style scoped>
.composer { position: fixed; left: 0; right: 0; bottom: 0; z-index: 10; padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom)); background: transparent; box-sizing: border-box; }
.selectors { display: flex; gap: 12rpx; margin-bottom: 12rpx; }
.selector { max-width: 320rpx; padding: 10rpx 16rpx; border: 1rpx solid var(--color-border); border-radius: 8rpx; color: var(--color-text-muted); font-size: 22rpx; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.attachment-header { padding: 20rpx 20rpx 0; }
</style>
