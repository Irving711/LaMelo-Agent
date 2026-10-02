<script setup lang="ts">
const props = withDefaults(defineProps<{ records?: unknown[]; total: number; current: number; size: number }>(), { records: () => [] })
const emit = defineEmits<{ 'page-change': [number]; 'size-change': [number]; refresh: [] }>()
function changeSize(event: Event) { emit('size-change', Number((event.target as HTMLInputElement).value) || props.size) }
const totalPages = () => Math.max(1, Math.ceil(Number(props.total || 0) / Math.max(1, Number(props.size || 1))))
function previous() { if (props.current > 1) emit('page-change', props.current - 1) }
function next() { if (props.current < totalPages()) emit('page-change', props.current + 1) }
</script>
<template>
  <view class="paged-list"><slot :records="records" /><view v-if="!records.length" class="empty">暂无数据</view><view class="pager"><button size="mini" :disabled="current <= 1" @click="previous">上一页</button><text>第 {{ current }} / {{ totalPages() }} 页，共 {{ total }} 条</text><button size="mini" data-testid="paged-next" :disabled="current >= totalPages()" @click="next">下一页</button><label class="size-picker">每页 <input data-testid="paged-size" type="number" :value="size" @change="changeSize" /></label><button size="mini" data-testid="paged-refresh" @click="emit('refresh')">刷新</button></view></view>
</template>
<style scoped>
.paged-list { min-width: 0; }.empty { padding: 48rpx 0; text-align: center; color: var(--color-text-muted); }.pager { display: flex; flex-wrap: wrap; align-items: center; justify-content: center; gap: 12rpx; padding: 24rpx 0; color: var(--color-text-muted); font-size: 24rpx; }.pager button { margin: 0; }.size-picker { padding: 10rpx 14rpx; border: 1rpx solid var(--color-border); border-radius: 8rpx; }
</style>
