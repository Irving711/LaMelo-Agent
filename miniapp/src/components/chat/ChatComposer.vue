<script setup lang="ts">
export interface KnowledgeOption { documentId: string; documentName?: string; knowledgeScopeName?: string }
const props = withDefaults(defineProps<{
  disabled?: boolean
  mode: string
  selectedDocumentId?: string
  modes?: Array<{ value: string; label: string }>
  knowledgeOptions?: KnowledgeOption[]
}>(), { disabled: false, selectedDocumentId: '', modes: () => [{ value: 'AUTO_DOCUMENT', label: '自动知识问答' }, { value: 'OPEN_CHAT', label: '开放式对话' }, { value: 'DOCUMENT', label: '指定文档问答' }], knowledgeOptions: () => [] })
const question = defineModel<string>({ default: '' })
const emit = defineEmits<{ (event: 'submit', value: string): void; (event: 'update:mode', value: string): void; (event: 'update:selectedDocumentId', value: string): void }>()
</script>

<template>
  <view class="composer">
    <view class="selectors">
      <picker :value="Math.max(0, props.modes.findIndex((item) => item.value === props.mode))" :range="props.modes.map((item) => item.label)" @change="emit('update:mode', props.modes[Number($event.detail.value)]?.value || props.mode)"><view class="selector">{{ props.modes.find((item) => item.value === props.mode)?.label || props.mode }}</view></picker>
      <picker v-if="props.mode === 'DOCUMENT'" :value="Math.max(0, props.knowledgeOptions.findIndex((item) => item.documentId === props.selectedDocumentId))" :range="props.knowledgeOptions.map((item) => item.documentName || item.documentId)" @change="emit('update:selectedDocumentId', props.knowledgeOptions[Number($event.detail.value)]?.documentId || '')"><view class="selector">{{ props.knowledgeOptions.find((item) => item.documentId === props.selectedDocumentId)?.documentName || '选择文档' }}</view></picker>
    </view>
    <textarea v-model="question" :disabled="props.disabled" class="question-input" maxlength="4000" placeholder="输入你的问题" @confirm="question.trim() && emit('submit', question.trim())" />
    <button type="primary" :disabled="props.disabled || !question.trim()" class="send-button" @click="emit('submit', question.trim())">发送</button>
  </view>
</template>

<style scoped>
.composer { position: fixed; left: 0; right: 0; bottom: 0; padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom)); background: var(--color-surface); border-top: 1rpx solid var(--color-border); box-sizing: border-box; }
.selectors { display: flex; gap: 12rpx; margin-bottom: 12rpx; }
.selector { max-width: 320rpx; padding: 10rpx 16rpx; border: 1rpx solid var(--color-border); border-radius: 8rpx; color: var(--color-text-muted); font-size: 22rpx; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.question-input { width: 100%; min-height: 78rpx; padding: 14rpx; box-sizing: border-box; border: 1rpx solid var(--color-border); border-radius: 8rpx; background: var(--color-background); }
.send-button { margin-top: 12rpx; }
</style>
