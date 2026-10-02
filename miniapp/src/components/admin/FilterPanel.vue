<script setup lang="ts">
import { computed } from 'vue'
const props = withDefaults(defineProps<{ modelValue?: Record<string, unknown>; placeholder?: string }>(), { modelValue: () => ({}), placeholder: '搜索' })
const emit = defineEmits<{ 'update:modelValue': [Record<string, unknown>]; submit: []; reset: [] }>()
const keyword = computed({ get: () => String(props.modelValue.keyword || ''), set: (value: string) => emit('update:modelValue', { ...props.modelValue, keyword: value }) })
</script>
<template>
  <view class="filter-panel"><input v-model="keyword" class="input" data-testid="filter-keyword" :placeholder="placeholder" confirm-type="search" @confirm="emit('submit')" /><view class="actions"><button size="mini" data-testid="filter-submit" @click="emit('submit')">筛选</button><button size="mini" @click="emit('reset')">重置</button></view></view>
</template>
<style scoped>
.filter-panel { display: flex; gap: 16rpx; align-items: center; padding: 20rpx 0; }.input { flex: 1; min-width: 0; height: 72rpx; padding: 0 20rpx; border: 1rpx solid var(--color-border); border-radius: 8rpx; background: var(--color-surface); }.actions { display: flex; gap: 12rpx; }.actions button { margin: 0; }
</style>
