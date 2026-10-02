<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { authStore } from '../../stores/auth'
import { notifyError, notifySuccess } from '../../utils/notify'

const password = ref('')
const profile = ref(authStore.profile)
const isAdmin = ref(authStore.isAdmin)
const bindingLabel = computed(() => profile.value?.needsBinding ? '未绑定微信' : '微信已绑定')
function goBind() { ;(globalThis as { uni?: { navigateTo?: (options: { url: string }) => void } }).uni?.navigateTo?.({ url: '/pages/auth/bind' }) }
function adminEntry() { ;(globalThis as { uni?: { navigateTo?: (options: { url: string }) => void } }).uni?.navigateTo?.({ url: '/pages/admin/index' }) }
async function unbind() { if (!password.value) { notifyError(new Error('请输入密码')); return }; try { await authStore.unbindWechat(password.value); profile.value = authStore.profile; password.value = ''; notifySuccess('已解除微信绑定') } catch (error) { notifyError(error, '解除绑定失败') } }
function logout() { authStore.logout(); ;(globalThis as { uni?: { reLaunch?: (options: { url: string }) => void } }).uni?.reLaunch?.({ url: '/pages/auth/login' }) }
onLoad(() => { void authStore.guard('/pages/profile/index').then(() => { profile.value = authStore.profile; isAdmin.value = authStore.isAdmin }) })
</script>
<template>
  <view class="page"><text class="title">我的</text><view class="profile"><text class="username">{{ profile?.username || '用户' }}</text><text class="muted">{{ profile?.roles?.join('、') || '普通用户' }}</text><text class="binding">{{ bindingLabel }}</text></view><button v-if="profile?.needsBinding" @click="goBind">绑定微信</button><template v-else><input v-model="password" class="input" password placeholder="输入密码以解除绑定" /><button @click="unbind">解除微信绑定</button></template><button v-if="isAdmin" data-testid="admin-entry" @click="adminEntry">管理后台</button><button class="logout" data-testid="logout" @click="logout">退出登录</button></view>
</template>
<style scoped>.page { min-height: 100vh; padding: 48rpx 32rpx; box-sizing: border-box; }.title { display: block; font-size: 40rpx; font-weight: 600; margin-bottom: 28rpx; }.profile { display: flex; flex-direction: column; gap: 10rpx; margin-bottom: 32rpx; padding: 28rpx; background: var(--color-surface); border: 1rpx solid var(--color-border); border-radius: 8rpx; }.username { font-size: 34rpx; font-weight: 600; }.muted, .binding { color: var(--color-text-muted); }.input { height: 84rpx; margin-bottom: 16rpx; padding: 0 20rpx; border: 1rpx solid var(--color-border); border-radius: 8rpx; }.logout { margin-top: 24rpx; color: #dc2626; background: transparent; border: 1rpx solid #dc2626; }</style>
