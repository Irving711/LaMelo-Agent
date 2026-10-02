<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { authStore } from '../../stores/auth'
import { notifyError } from '../../utils/notify'

const submitting = ref(false)
const requestedRoute = ref('')

type UniLoginResult = { code?: string }
function uniLogin(): Promise<string> {
  return new Promise((resolve, reject) => {
    const api = (globalThis as { uni?: { login?: (options: { provider: string; success?: (result: UniLoginResult) => void; fail?: (error: unknown) => void }) => void } }).uni
    if (!api?.login) { reject(new Error('当前环境不支持微信登录')); return }
    api.login({ provider: 'weixin', success: (result) => result.code ? resolve(result.code) : reject(new Error('未获取到微信登录凭证')), fail: reject })
  })
}

async function loginWithWechat() {
  submitting.value = true
  try {
    const code = await uniLogin()
    await authStore.loginWithWechat(code)
  } catch (error) { notifyError(error, '微信登录失败') } finally { submitting.value = false }
}

onLoad((query) => { requestedRoute.value = String(query?.redirect || '') })
</script>

<template>
  <view class="page">
    <view class="brand"><uni-icons type="person" size="48" color="var(--color-primary)" /><text class="title">登录 LaMelo Agent</text></view>
    <button type="primary" :disabled="submitting" data-testid="wechat-login" @click="loginWithWechat">微信登录</button>
    <text v-if="requestedRoute" class="redirect-hint">登录后返回原页面</text>
  </view>
</template>

<style scoped>
.page { min-height: 100vh; display: flex; flex-direction: column; align-items: stretch; justify-content: center; gap: 20rpx; padding: 48rpx; box-sizing: border-box; }
.brand { display: flex; flex-direction: column; align-items: center; gap: 20rpx; margin-bottom: 24rpx; }
.title { font-size: 40rpx; font-weight: 600; }
button { width: 100%; }
.redirect-hint { display: block; text-align: center; color: var(--color-text-muted); font-size: 22rpx; }
</style>
