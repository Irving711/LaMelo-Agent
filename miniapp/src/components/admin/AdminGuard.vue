<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { authStore } from '../../stores/auth'
const props = withDefaults(defineProps<{ route?: string }>(), { route: '/pages/admin/index' })
const ready = ref(false)
const allowed = ref(false)
const message = ref('')
onMounted(async () => {
  const authenticated = await authStore.guard(props.route)
  allowed.value = authenticated && authStore.isAdmin
  message.value = authenticated ? (allowed.value ? '' : '当前账号没有管理员权限') : '登录已过期，请重新登录'
  ready.value = true
})
</script>
<template>
  <view class="admin-guard">
    <view v-if="!ready" class="muted">正在检查管理员权限…</view>
    <view v-else-if="!allowed" class="forbidden" data-testid="admin-forbidden"><text class="title">无法访问管理后台</text><text class="muted">{{ message }}</text></view>
    <slot v-else />
  </view>
</template>
<style scoped>
.admin-guard { min-height: 100vh; }.forbidden { display: flex; flex-direction: column; gap: 16rpx; padding: 64rpx 32rpx; }.title { font-size: 36rpx; font-weight: 600; }.muted { color: var(--color-text-muted); }
</style>
