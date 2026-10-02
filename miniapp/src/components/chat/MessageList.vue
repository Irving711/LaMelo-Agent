<script setup lang="ts">
import CitationList from './CitationList.vue'

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
</script>

<template>
  <view class="message-list">
    <view v-for="message in props.messages" :key="message.id" class="message-row" :class="`message-${message.role}`">
      <view class="message-bubble">
        <text class="message-role">{{ message.role === 'user' ? '我' : 'LaMelo' }}</text>
        <text class="message-content">{{ message.content || (message.role === 'assistant' ? '…' : '') }}</text>
        <CitationList v-if="message.references?.length" :citations="message.references" @open-reference="emit('open-reference', $event)" />
        <button v-if="isFailed(message)" class="text-button" data-testid="message-retry" @click="emit('retry', message.id)">重试</button>
      </view>
    </view>
    <button v-if="props.streaming" class="stop-button" data-testid="message-stop" @click="emit('stop')">停止生成</button>
  </view>
</template>

<style scoped>
.message-list { display: flex; flex-direction: column; gap: 24rpx; padding: 24rpx 0 160rpx; }
.message-row { display: flex; width: 100%; }
.message-user { justify-content: flex-end; }
.message-bubble { max-width: 86%; padding: 22rpx 24rpx; border-radius: 12rpx; background: var(--color-surface); border: 1rpx solid var(--color-border); box-sizing: border-box; }
.message-user .message-bubble { background: #dbeafe; border-color: #bfdbfe; }
.message-role { display: block; margin-bottom: 8rpx; color: var(--color-text-muted); font-size: 22rpx; }
.message-content { display: block; white-space: pre-wrap; line-height: 1.55; word-break: break-word; }
.text-button, .stop-button { display: inline-flex; align-items: center; justify-content: center; min-height: 56rpx; margin-top: 16rpx; padding: 0 22rpx; font-size: 24rpx; color: var(--color-primary); background: transparent; border: 1rpx solid var(--color-primary); border-radius: 8rpx; }
.stop-button { align-self: center; }
</style>
