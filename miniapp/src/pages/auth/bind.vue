<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { authStore } from '../../stores/auth'
import { notifyError, notifySuccess } from '../../utils/notify'

const username = ref('')
const password = ref('')
const submitting = ref(false)

function resolveCode(): Promise<string> {
  return new Promise((resolve, reject) => {
    const api = (globalThis as { uni?: { login?: (options: { provider: string; success?: (result: { code?: string }) => void; fail?: (error: unknown) => void }) => void } }).uni
    if (!api?.login) { reject(new Error('未获取到微信登录凭证')); return }
    api.login({ provider: 'weixin', success: (result) => result.code ? resolve(result.code) : reject(new Error('未获取到微信登录凭证')), fail: reject })
  })
}

async function bind() {
  if (!username.value.trim() || !password.value) { notifyError(new Error('请输入用户名和密码')); return }
  submitting.value = true
  try { await authStore.bindWechat({ username: username.value.trim(), password: password.value, code: await resolveCode() }); notifySuccess('绑定成功') }
  catch (error) { notifyError(error, '绑定失败') } finally { submitting.value = false }
}

onLoad(() => { void authStore.guard('/pages/auth/bind') })
</script>

<template>
  <view class="page"><text class="title">绑定微信账号</text><text class="muted">绑定后可继续使用 LaMelo Agent</text><input v-model="username" class="input" placeholder="用户名" /><input v-model="password" class="input" password placeholder="密码" /><button type="primary" :disabled="submitting" data-testid="bind-submit" @click="bind">确认绑定</button></view>
</template>

<style scoped>
.page { min-height: 100vh; display: flex; flex-direction: column; gap: 20rpx; padding: 48rpx 32rpx; box-sizing: border-box; }
.title { font-size: 40rpx; font-weight: 600; margin-bottom: 4rpx; }
.muted { color: var(--color-text-muted); margin-bottom: 20rpx; }
.input { height: 84rpx; padding: 0 20rpx; border: 1rpx solid var(--color-border); border-radius: 8rpx; background: var(--color-surface); }
</style>
