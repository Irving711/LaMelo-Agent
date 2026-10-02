<script setup lang="ts">
import { computed } from 'vue'
export interface SessionRecord { conversationId: string; latestUserMessage?: string; latestAssistantMessage?: string; createdAt?: string; updatedAt?: string; running?: boolean; chatMode?: string }
const props = withDefaults(defineProps<{ records?: SessionRecord[]; page?: { sessions?: SessionRecord[]; pageNo?: number; totalPages?: number }; pageNo?: number; totalPages?: number; loading?: boolean }>(), { records: () => [], pageNo: 1, totalPages: 1, loading: false })
const emit = defineEmits<{ (event: 'select', id: string): void; (event: 'delete', id: string): void; (event: 'load-more'): void }>()
const items = computed(() => props.records.length ? props.records : (props.page?.sessions || []))
const currentPage = computed(() => props.pageNo || props.page?.pageNo || 1)
const lastPage = computed(() => props.totalPages || props.page?.totalPages || 1)
function title(record: SessionRecord) { return record.latestUserMessage || record.latestAssistantMessage || '新会话' }
function formatDate(value?: string) { if (!value) return ''; const date = new Date(value); return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) }
</script>

<template>
  <view class="session-list">
    <view v-for="record in items" :key="record.conversationId" class="session-row" :data-testid="`session-${record.conversationId}`" @click="emit('select', record.conversationId)">
      <view class="session-main"><text class="session-title">{{ title(record) }}</text><text class="session-date">{{ formatDate(record.createdAt || record.updatedAt) }}</text></view>
      <button class="session-delete" :data-testid="`session-delete-${record.conversationId}`" @click.stop="emit('delete', record.conversationId)">删除</button>
    </view>
    <button v-if="currentPage < lastPage" class="load-more" :disabled="props.loading" data-testid="session-load-more" @click="emit('load-more')">{{ props.loading ? '加载中…' : '加载更多' }}</button>
    <text v-if="!items.length && !props.loading" class="empty">暂无会话</text>
  </view>
</template>

<style scoped>
.session-list { padding: 24rpx; }
.session-row { display: flex; align-items: center; gap: 16rpx; padding: 24rpx 0; border-bottom: 1rpx solid var(--color-border); }
.session-main { flex: 1; min-width: 0; }
.session-title { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.session-date { display: block; margin-top: 8rpx; color: var(--color-text-muted); font-size: 22rpx; }
.session-delete { flex: none; width: 110rpx; margin: 0; padding: 0; color: #dc2626; font-size: 22rpx; background: transparent; }
.load-more { margin: 24rpx auto; color: var(--color-primary); background: transparent; border: 1rpx solid var(--color-primary); }
.empty { display: block; padding: 80rpx 0; text-align: center; color: var(--color-text-muted); }
</style>
