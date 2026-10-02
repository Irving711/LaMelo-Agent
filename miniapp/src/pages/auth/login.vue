<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { authStore } from '../../stores/auth'
import { notifyError } from '../../utils/notify'

const passwordMode = ref(false)
const username = ref('')
const password = ref('')
const submitting = ref(false)
const requestedRoute = ref('')
const pendingWechatBinding = ref(false)

type UniLoginResult = { code?: string }
function uniLogin(): Promise<string> {
  return new Promise((resolve, reject) => {
    const api = (globalThis as { uni?: { login?: (options: { provider: string; success?: (result: UniLoginResult) => void; fail?: (error: unknown) => void }) => void } }).uni
    if (!api?.login) { reject(new Error('当前环境不支持微信登录')); return }
    api.login({ provider: 'weixin', success: (result) => result.code ? resolve(result.code) : reject(new Error('未获取到微信登录凭证')), fail: reject })
  })
}

function goBind() {
  ;(globalThis as { uni?: { navigateTo?: (options: { url: string }) => void } }).uni?.navigateTo?.({ url: '/pages/auth/bind' })
}

async function loginWithWechat() {
  submitting.value = true
  try {
    const code = await uniLogin()
    const profile = await authStore.loginWithWechat(code)
    if (profile.needsBinding || !profile.token) {
      pendingWechatBinding.value = true
      passwordMode.value = true
    }
  } catch (error) { notifyError(error, '微信登录失败') } finally { submitting.value = false }
}

async function loginWithPassword() {
  if (!username.value.trim() || !password.value) { notifyError(new Error('请输入用户名和密码')); return }
  submitting.value = true
  try {
    const profile = await authStore.loginWithPassword(username.value.trim(), password.value)
    if (pendingWechatBinding.value || profile.needsBinding) goBind()
  } catch (error) { notifyError(error, '账号登录失败') } finally { submitting.value = false }
}

onLoad((query) => { requestedRoute.value = String(query?.redirect || '') })
</script>

<template>
  <view class="page">
    <view class="brand"><uni-icons type="person" size="48" color="var(--color-primary)" /><text class="title">登录 LaMelo Agent</text></view>
    <button type="primary" :disabled="submitting" data-testid="wechat-login" @click="loginWithWechat">微信登录</button>
    <view class="divider"><text>或使用账号登录</text></view>
    <template v-if="passwordMode">
      <input v-model="username" class="input" type="text" placeholder="用户名" />
      <input v-model="password" class="input" password placeholder="密码" @confirm="loginWithPassword" />
      <button :disabled="submitting" data-testid="password-login" @click="loginWithPassword">账号登录</button>
    </template>
    <button class="link-button" @click="passwordMode = !passwordMode">{{ passwordMode ? '收起账号登录' : '使用账号登录' }}</button>
    <text v-if="requestedRoute" class="redirect-hint">登录后返回原页面</text>
  </view>
</template>

<style scoped>
.page { min-height: 100vh; display: flex; flex-direction: column; align-items: stretch; justify-content: center; gap: 20rpx; padding: 48rpx; box-sizing: border-box; }
.brand { display: flex; flex-direction: column; align-items: center; gap: 20rpx; margin-bottom: 24rpx; }
.title { font-size: 40rpx; font-weight: 600; }
button { width: 100%; }
.divider { margin: 8rpx 0; text-align: center; color: var(--color-text-muted); font-size: 24rpx; }
.input { width: 100%; height: 84rpx; padding: 0 20rpx; box-sizing: border-box; border: 1rpx solid var(--color-border); border-radius: 8rpx; background: var(--color-surface); }
.link-button { border: 0; color: var(--color-primary); background: transparent; }
.redirect-hint { display: block; text-align: center; color: var(--color-text-muted); font-size: 22rpx; }
</style>
