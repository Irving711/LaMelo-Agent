<script setup lang="ts">
withDefaults(
  defineProps<{
    title?: string
    description?: string
  }>(),
  {
    title: '暂无内容',
    description: '这里还没有可展示的内容。'
  }
)
</script>

<template>
  <view class="empty-state">
    <uni-icons type="info" size="28" color="var(--color-text-muted)" />
    <text class="empty-state__title">{{ title }}</text>
    <text class="empty-state__description">{{ description }}</text>
  </view>
</template>

<style scoped>
.empty-state { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 64rpx 32rpx; gap: 16rpx; }
.empty-state__title { color: var(--color-text); font-size: 32rpx; font-weight: 600; }
.empty-state__description { color: var(--color-text-muted); font-size: 26rpx; text-align: center; }
</style>
