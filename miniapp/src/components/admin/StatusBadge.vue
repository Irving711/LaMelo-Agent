<script setup lang="ts">
const props = withDefaults(defineProps<{ status?: string | number; label?: string }>(), { status: '', label: '' })
const text = () => props.label || String(props.status || '未知')
const tone = () => { const value = String(props.status || '').toLowerCase(); if (['success', 'completed', 'done', '3'].includes(value)) return 'success'; if (['failed', 'error', '4'].includes(value)) return 'danger'; if (['running', 'processing', '2'].includes(value)) return 'running'; return 'waiting' }
</script>
<template><text class="badge" :class="'badge-' + tone()">{{ text() }}</text></template>
<style scoped>
.badge { display: inline-block; padding: 6rpx 12rpx; border-radius: 999rpx; font-size: 22rpx; }.badge-success { color: #166534; background: #dcfce7; }.badge-danger { color: #991b1b; background: #fee2e2; }.badge-running { color: #1d4ed8; background: #dbeafe; }.badge-waiting { color: #92400e; background: #fef3c7; }
</style>
