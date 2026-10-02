<script setup lang="ts">
const props = defineProps<{ status: string; error?: Error | null }>()
const labels: Record<string, string> = { idle: '', streaming: '正在生成…', completed: '已完成', stopped: '已停止', error: '生成失败' }
</script>

<template><text v-if="labels[props.status]" class="stream-status" :class="`status-${props.status}`">{{ props.status === 'error' && props.error?.message ? `${labels[props.status]}：${props.error.message}` : labels[props.status] }}</text></template>

<style scoped>
.stream-status { display: block; margin: 8rpx 0; color: var(--color-text-muted); font-size: 23rpx; }
.status-error { color: #dc2626; }
</style>
