<script setup lang="ts">
import type { SessionRecord } from '../session/SessionList.vue'

withDefaults(defineProps<{ open: boolean; records?: SessionRecord[]; pageNo?: number; totalPages?: number; loading?: boolean; activeId?: string }>(), {
  records: () => [], pageNo: 1, totalPages: 1, loading: false, activeId: ''
})
const emit = defineEmits<{
  (event: 'close'): void
  (event: 'new-chat'): void
  (event: 'select', id: string): void
  (event: 'delete', id: string): void
  (event: 'load-more'): void
  (event: 'profile'): void
}>()

function title(record: SessionRecord) {
  return record.latestUserMessage || record.latestAssistantMessage || '新会话'
}
</script>

<template>
  <view v-if="open" class="sidebar-layer">
    <view class="sidebar-backdrop" data-testid="sidebar-backdrop" @click="emit('close')" />
    <view class="sidebar-panel">
      <view class="sidebar-top">
        <text class="sidebar-heading">会话</text>
        <button class="icon-button close-button" aria-label="关闭菜单" data-testid="sidebar-close" @click="emit('close')">×</button>
      </view>
      <button class="new-chat" data-testid="sidebar-new-chat" @click="emit('new-chat')"><text class="new-icon">＋</text> 新聊天</button>
      <text class="section-heading">最近的会话</text>
      <scroll-view scroll-y class="session-scroll">
        <view v-for="record in records" :key="record.conversationId" class="session-row" :class="{ active: record.conversationId === activeId }" :data-testid="`sidebar-session-${record.conversationId}`" @click="emit('select', record.conversationId)">
          <text class="session-title">{{ title(record) }}</text>
          <button class="delete-button" :data-testid="`sidebar-delete-${record.conversationId}`" aria-label="删除会话" @click.stop="emit('delete', record.conversationId)">×</button>
        </view>
        <text v-if="loading && !records.length" class="list-state">正在加载会话…</text>
        <text v-else-if="!records.length" class="list-state">暂无会话</text>
        <button v-if="pageNo < totalPages" class="load-more" :disabled="loading" data-testid="sidebar-load-more" @click="emit('load-more')">{{ loading ? '加载中…' : '加载更多' }}</button>
      </scroll-view>
      <button class="profile-entry" data-testid="sidebar-profile" @click="emit('profile')"><text class="profile-icon">我</text><text>我的</text><text class="entry-arrow">›</text></button>
    </view>
  </view>
</template>

<style scoped>
.sidebar-layer { position: fixed; inset: 0; z-index: 100; }
.sidebar-backdrop { position: absolute; inset: 0; background: rgba(17, 39, 69, .38); }
.sidebar-panel { position: absolute; top: 0; bottom: 0; left: 0; width: 620rpx; max-width: 84vw; box-sizing: border-box; display: flex; flex-direction: column; padding: calc(30rpx + env(safe-area-inset-top)) 24rpx calc(24rpx + env(safe-area-inset-bottom)); background: #f0f7fb; box-shadow: 12rpx 0 40rpx rgba(30, 68, 112, .18); }
.sidebar-top { display: flex; align-items: center; justify-content: space-between; min-height: 68rpx; padding: 0 12rpx 24rpx; }
.sidebar-heading { color: #24384c; font-size: 32rpx; font-weight: 700; }
button { margin: 0; }
button::after { border: 0; }
.icon-button { display: flex; align-items: center; justify-content: center; padding: 0; background: transparent; }
.close-button { width: 58rpx; height: 58rpx; color: #617389; font-size: 48rpx; line-height: 1; }
.new-chat { display: flex; align-items: center; width: 100%; height: 82rpx; padding: 0 22rpx; color: #174876; background: #dcebf9; border: 1rpx solid #c5dbef; border-radius: 20rpx; font-size: 28rpx; font-weight: 600; text-align: left; }
.new-icon { margin-right: 12rpx; font-size: 37rpx; line-height: 1; }
.section-heading { padding: 36rpx 16rpx 14rpx; color: #677e94; font-size: 23rpx; font-weight: 600; }
.session-scroll { flex: 1; min-height: 0; }
.session-row { display: flex; align-items: center; min-height: 78rpx; margin-bottom: 4rpx; padding: 0 12rpx 0 18rpx; border-radius: 16rpx; }
.session-row.active { background: #dbeaf8; }
.session-title { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #283b4f; font-size: 26rpx; }
.delete-button { flex: none; display: flex; align-items: center; justify-content: center; width: 52rpx; height: 52rpx; padding: 0; color: #8a9bad; background: transparent; font-size: 37rpx; line-height: 1; }
.load-more { margin: 20rpx auto; padding: 0 24rpx; color: #276ba7; background: transparent; font-size: 24rpx; }
.list-state { display: block; padding: 40rpx 18rpx; color: #8798a9; font-size: 25rpx; }
.profile-entry { display: flex; align-items: center; gap: 18rpx; width: 100%; min-height: 90rpx; padding: 12rpx; border-top: 1rpx solid #d5e3ee; border-radius: 0; color: #2c4155; background: transparent; font-size: 27rpx; text-align: left; }
.profile-icon { display: flex; align-items: center; justify-content: center; width: 52rpx; height: 52rpx; border-radius: 50%; color: #fff; background: #5599d0; font-size: 24rpx; }
.entry-arrow { margin-left: auto; color: #879bad; font-size: 40rpx; }
</style>
