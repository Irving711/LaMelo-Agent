<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { authApi } from '../../api/auth'
import { authStore } from '../../stores/auth'
import { notifyError } from '../../utils/notify'

type Mode = 'password' | 'sms' | 'register'
const mode = ref<Mode>('password')
const username = ref('')
const password = ref('')
const mobile = ref('')
const code = ref('')
const agreed = ref(false)
const submitting = ref(false)
const countdown = ref(0)
const requestedRoute = ref('')
let countdownTimer: ReturnType<typeof setInterval> | null = null

const title = computed(() => mode.value === 'register' ? '创建账号' : '欢迎回来')
const submitText = computed(() => mode.value === 'register' ? '注册并登录' : mode.value === 'sms' ? '验证码登录' : '账号密码登录')

function ensureAgreed() {
  if (agreed.value) return true
  uni.showToast({ title: '请先阅读并同意用户协议', icon: 'none' })
  return false
}

async function submit() {
  if (!ensureAgreed()) return
  submitting.value = true
  try {
    if (mode.value === 'sms') {
      if (!/^1\d{10}$/.test(mobile.value) || !/^\d{4,6}$/.test(code.value)) throw new Error('请输入正确的手机号和验证码')
      await authStore.loginWithSms(mobile.value, code.value)
    } else if (mode.value === 'register') {
      if (username.value.trim().length < 3 || password.value.length < 6) throw new Error('账号至少 3 位，密码至少 6 位')
      await authStore.register(username.value.trim(), password.value)
    } else {
      if (!username.value.trim() || !password.value) throw new Error('请输入账号和密码')
      await authStore.loginWithPassword(username.value.trim(), password.value)
    }
  } catch (error) {
    notifyError(error, mode.value === 'register' ? '注册失败' : '登录失败')
  } finally { submitting.value = false }
}

async function sendCode() {
  if (!/^1\d{10}$/.test(mobile.value)) { notifyError(new Error('请输入正确的手机号')); return }
  if (!authApi.sendSmsCode) { notifyError(new Error('短信服务尚未配置')); return }
  try {
    await authApi.sendSmsCode(mobile.value, 'login')
    countdown.value = 60
    countdownTimer = setInterval(() => {
      countdown.value -= 1
      if (countdown.value <= 0 && countdownTimer) { clearInterval(countdownTimer); countdownTimer = null }
    }, 1000)
  } catch (error) { notifyError(error, '验证码发送失败') }
}

function switchMode(next: Mode) { mode.value = next; code.value = '' }

type UniLoginResult = { code?: string }
function uniLogin(): Promise<string> {
  return new Promise((resolve, reject) => {
    const api = (globalThis as { uni?: { login?: (options: { provider: string; success?: (result: UniLoginResult) => void; fail?: (error: unknown) => void }) => void } }).uni
    if (!api?.login) { reject(new Error('当前环境不支持微信登录')); return }
    api.login({ provider: 'weixin', success: (result) => result.code ? resolve(result.code) : reject(new Error('未获取到微信登录凭证')), fail: reject })
  })
}

async function loginWithWechat() {
  if (!ensureAgreed()) return
  submitting.value = true
  try { await authStore.loginWithWechat(await uniLogin()) } catch (error) { notifyError(error, '微信登录失败') } finally { submitting.value = false }
}

onLoad((query) => { requestedRoute.value = String(query?.redirect || '') })
</script>

<template>
  <view class="page">
    <view class="hero"><view class="logo">L</view><text class="title">{{ title }}</text><text class="subtitle">LaMelo Agent 智能工作空间</text></view>
    <view class="panel">
      <view v-if="mode !== 'sms'" class="field-group"><input v-model="username" class="input" placeholder="账号" maxlength="64" /><input v-model="password" class="input" password placeholder="密码" maxlength="128" /></view>
      <view v-else class="field-group"><input v-model="mobile" class="input" type="number" placeholder="手机号" maxlength="11" /><view class="code-row"><input v-model="code" class="input code-input" type="number" placeholder="验证码" maxlength="6" /><button class="code-button" :disabled="countdown > 0" @click="sendCode">{{ countdown ? `${countdown}s` : '获取验证码' }}</button></view></view>
      <button class="primary-button" :disabled="submitting" @click="submit">{{ submitText }}</button>
      <button class="wechat-button" :disabled="submitting" @click="loginWithWechat">微信一键登录</button>
      <view class="switch-row"><text @click="switchMode(mode === 'password' ? 'register' : 'password')">{{ mode === 'register' ? '已有账号？去登录' : '没有账号？去注册' }}</text><text @click="switchMode(mode === 'sms' ? 'password' : 'sms')">{{ mode === 'sms' ? '账号密码登录' : '短信验证码登录' }}</text></view>
      <label class="agreement"><checkbox :checked="agreed" color="#3ba662" @click="agreed = !agreed" /><text>我已阅读并同意</text><text class="link">《用户协议》</text><text>和</text><text class="link">《隐私协议》</text></label>
      <text v-if="requestedRoute" class="redirect-hint">登录后返回原页面</text>
    </view>
  </view>
</template>

<style scoped lang="scss">
.page { min-height: 100vh; box-sizing: border-box; padding: 80rpx 48rpx 40rpx; display: flex; flex-direction: column; justify-content: space-between; background: radial-gradient(circle at 20% 10%, rgba(255,255,255,.9), transparent 40%), linear-gradient(145deg, #eefcf9 0%, #f7fbf8 48%, #dff3ea 100%); color: #383838; }
.hero { display: flex; flex-direction: column; align-items: center; padding-top: 44rpx; }
.logo { width: 150rpx; height: 150rpx; line-height: 150rpx; border-radius: 24rpx; text-align: center; background: #3ba662; color: #fff; font-size: 92rpx; font-weight: 800; box-shadow: 0 18rpx 40rpx rgba(59,166,98,.22); }
.title { margin-top: 48rpx; font-size: 48rpx; font-weight: 700; }
.subtitle { margin-top: 14rpx; color: #7b8580; font-size: 26rpx; }
.panel { padding: 44rpx 0 12rpx; }
.field-group { display: flex; flex-direction: column; gap: 22rpx; }
.input { height: 92rpx; box-sizing: border-box; padding: 0 28rpx; border: 2rpx solid rgba(59,166,98,.16); border-radius: 16rpx; background: rgba(255,255,255,.86); font-size: 30rpx; }
.code-row { display: flex; gap: 18rpx; }
.code-input { flex: 1; }
.code-button { width: 190rpx; padding: 0; border-radius: 16rpx; color: #3ba662; background: #fff; border: 2rpx solid #3ba662; font-size: 25rpx; }
.primary-button, .wechat-button { margin-top: 24rpx; height: 94rpx; line-height: 94rpx; border-radius: 18rpx; font-size: 30rpx; }
.primary-button { color: #fff; background: #3ba662; }
.wechat-button { color: #3ba662; background: rgba(255,255,255,.82); border: 2rpx solid rgba(59,166,98,.55); }
.switch-row { display: flex; justify-content: space-between; margin-top: 28rpx; color: #3ba662; font-size: 26rpx; }
.agreement { display: flex; align-items: center; justify-content: center; margin-top: 44rpx; color: #555; font-size: 23rpx; }
.agreement checkbox { transform: scale(.7); margin-right: -4rpx; }
.link { color: #3ba662; }
.redirect-hint { display: block; margin-top: 22rpx; text-align: center; color: #87918c; font-size: 22rpx; }
button::after { border: 0; }
</style>
