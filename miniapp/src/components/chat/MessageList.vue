<script setup lang="ts">
import YmBubble from '../ym-chat-ai/components/YmBubble/index.vue'

export interface ChatMessageRecord {
  id: string | number
  role: 'user' | 'assistant' | string
  content: string
  status?: string
  references?: unknown[]
}

const props = withDefaults(defineProps<{
  messages: ChatMessageRecord[]
  streaming?: boolean
}>(), { streaming: false })

const emit = defineEmits<{
  (event: 'retry', id: string | number): void
  (event: 'stop'): void
  (event: 'open-reference', reference: unknown): void
}>()

function isFailed(message: ChatMessageRecord) {
  return message.status === 'error' || message.status === 'failed'
}

function shouldRender(message: ChatMessageRecord) {
  return message.role !== 'assistant' || Boolean(message.content) || message.status === 'streaming'
}

function normalizedReferences(references: unknown[] = []) {
  return references.map((reference: any) => ({ ...reference, document_name: reference?.document_name || reference?.documentName || reference?.title || reference?.name }))
}

function copyMessage(content: string) {
  ;(globalThis as { uni?: { setClipboardData?: (options: { data: string; success?: () => void }) => void } }).uni?.setClipboardData?.({ data: content, success: () => undefined })
}
</script>

<template>
  <view class="message-list">
    <template v-for="message in props.messages" :key="message.id">
    <view v-if="shouldRender(message)" class="message-row" :class="`message-${message.role}`">
      <YmBubble
        :message="message.content || (message.role === 'assistant' && message.status === 'streaming' ? 'loading' : '')"
        :position="message.role === 'user' ? 'right' : 'left'"
        :is-markdown="message.role !== 'user'"
        :resources="message.role !== 'user' ? normalizedReferences(message.references) : []"
        :is-error="isFailed(message)"
        @open-reference="emit('open-reference', $event)"
      >
        <template #footer>
          <view v-if="message.role === 'assistant' && !isFailed(message)" class="bubble-actions">
            <button class="text-button" @click="copyMessage(message.content)">复制</button>
          </view>
          <view v-if="isFailed(message)" class="bubble-actions">
            <button class="text-button" data-testid="message-retry" @click="emit('retry', message.id)">重试</button>
          </view>
        </template>
      </YmBubble>
    </view>
    </template>
  </view>
</template>

<style scoped>
.message-list { display: flex; flex-direction: column; gap: 24rpx; padding: 24rpx 0 160rpx; }
.message-row { display: flex; width: 100%; }
.message-user { justify-content: flex-end; }
.message-row :deep(.ym-bubble) { max-width: 100%; }
.message-row :deep(.ym-bubble-content) { max-width: 86%; }
.bubble-actions { display: flex; justify-content: flex-start; gap: 12rpx; margin: -8rpx 0 12rpx 0; }
.text-button { display: inline-flex; align-items: center; justify-content: center; width: auto; min-width: 0; min-height: 56rpx; margin: 16rpx 0 0 0; padding: 0 22rpx; font-size: 24rpx; color: var(--color-primary); background: rgba(255, 255, 255, 0.86); border: 1rpx solid rgba(37, 99, 235, 0.18); border-radius: 8rpx; }
</style>
