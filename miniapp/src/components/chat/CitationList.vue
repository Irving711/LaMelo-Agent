<script setup lang="ts">
const props = withDefaults(defineProps<{ citations?: unknown[] }>(), { citations: () => [] })
const emit = defineEmits<{ (event: 'open-reference', reference: unknown): void }>()

function label(reference: any, index: number) {
  return reference?.documentName || reference?.title || reference?.name || `参考资料 ${index + 1}`
}
function referenceKey(reference: any, index: number) {
  return String(reference?.referenceId || reference?.id || index)
}
</script>

<template>
  <view v-if="props.citations.length" class="citation-list">
    <text class="citation-title">参考资料</text>
    <button v-for="(reference, index) in props.citations" :key="referenceKey(reference, index)" class="citation" :data-testid="`citation-${referenceKey(reference, index)}`" @click="emit('open-reference', reference)">
      {{ label(reference, index) }}
    </button>
  </view>
</template>

<style scoped>
.citation-list { margin-top: 18rpx; padding-top: 16rpx; border-top: 1rpx solid var(--color-border); }
.citation-title { display: block; color: var(--color-text-muted); font-size: 22rpx; margin-bottom: 10rpx; }
.citation { display: block; width: 100%; margin: 8rpx 0 0; padding: 12rpx 16rpx; color: var(--color-primary); text-align: left; font-size: 23rpx; background: transparent; border: 1rpx solid var(--color-border); border-radius: 8rpx; }
</style>
