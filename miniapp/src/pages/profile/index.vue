<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { authStore } from '../../stores/auth'
import { notifyError, notifySuccess } from '../../utils/notify'

const password = ref('')
const newUsername = ref('')
const newPassword = ref('')
const settingUp = ref(false)
const profile = ref(authStore.profile)
const isAdmin = ref(authStore.isAdmin)
const bindingLabel = computed(() => profile.value?.needsBinding ? '微信已绑定（未设置密码）' : '微信已绑定')
function goBind() { ;(globalThis as { uni?: { navigateTo?: (options: { url: string }) => void } }).uni?.navigateTo?.({ url: '/pages/auth/bind' }) }
function adminEntry() { ;(globalThis as { uni?: { navigateTo?: (options: { url: string }) => void } }).uni?.navigateTo?.({ url: '/pages/admin/index' }) }
async function setCredentials() {
  if (!newUsername.value.trim() || !newPassword.value) { notifyError(new Error('请输入账号和密码')); return }
  settingUp.value = true
  try {
    await authStore.setCredentials({ username: newUsername.value.trim(), password: newPassword.value })
    profile.value = authStore.profile
    isAdmin.value = authStore.isAdmin
    newUsername.value = ''
    newPassword.value = ''
    notifySuccess('设置成功')
  } catch (error) { notifyError(error, '设置失败') } finally { settingUp.value = false }
}
async function unbind() { if (!password.value) { notifyError(new Error('请输入密码')); return }; try { await authStore.unbindWechat(password.value); profile.value = authStore.profile; password.value = ''; notifySuccess('已解除微信绑定') } catch (error) { notifyError(error, '解除绑定失败') } }
function logout() { authStore.logout(); ;(globalThis as { uni?: { reLaunch?: (options: { url: string }) => void } }).uni?.reLaunch?.({ url: '/pages/auth/login' }) }
onLoad(() => { void authStore.guard('/pages/profile/index').then(() => { profile.value = authStore.profile; isAdmin.value = authStore.isAdmin }) })
</script>

<template>
  <view class="page"><text class="title">我的</text><view class="profile"><text class="username">{{ profile?.username || '用户' }}</text><text class="muted">{{ profile?.roles?.join('、') || '普通用户' }}</text><text class="binding">{{ bindingLabel }}</text></view><button @click="goBind">绑定已有账号</button><view v-if="profile?.needsBinding" class="section"><text class="section-title">设置用户名密码</text><input v-model="newUsername" class="input" placeholder="账号" /><input v-model="newPassword" class="input" password placeholder="密码" /><button :disabled="settingUp" data-testid="set-credentials" @click="setCredentials">保存</button></view><view v-else class="section"><text class="section-title">解除微信绑定</text><input v-model="password" class="input" password placeholder="输入密码以解除绑定" /><button data-testid="unbind" @click="unbind">解除微信绑定</button></view><button v-if="isAdmin" data-testid="admin-entry" @click="adminEntry">管理后台</button><button class="logout" data-testid="logout" @click="logout">退出登录</button></view>
</template>
<style scoped>.page { min-height: 100vh; padding: 48rpx 32rpx; box-sizing: border-box; }.title { display: block; font-size: 40rpx; font-weight: 600; margin-bottom: 28rpx; }.profile { display: flex; flex-direction: column; gap: 10rpx; margin-bottom: 32rpx; padding: 28rpx; background: var(--color-surface); border: 1rpx solid var(--color-border); border-radius: 8rpx; }.username { font-size: 34rpx; font-weight: 600; }.muted, .binding { color: var(--color-text-muted); }.section { display: flex; flex-direction: column; gap: 16rpx; margin: 24rpx 0; }.section-title { color: var(--color-text-muted); font-size: 26rpx; }.input { height: 84rpx; padding: 0 20rpx; border: 1rpx solid var(--color-border); border-radius: 8rpx; background: var(--color-surface); }.logout { margin-top: 24rpx; color: #dc2626; background: transparent; border: 1rpx solid #dc2626; }</style>
