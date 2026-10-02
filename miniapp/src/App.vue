<script setup lang="ts">
import { onLaunch, onShow, onHide } from '@dcloudio/uni-app'
import { authStore } from './stores/auth'

onLaunch(() => {
  void authStore.restoreSession().then((authenticated) => {
    const pages = (globalThis as { getCurrentPages?: () => Array<{ route?: string }> }).getCurrentPages?.() || []
    const current = pages[pages.length - 1]
    if (!authenticated && current?.route && current.route !== 'pages/auth/login') authStore.redirectToLogin(`/${current.route}`)
  })
})
onShow(() => {})
onHide(() => {})
</script>

<template>
  <slot />
  <slot name="loading" />
  <slot name="empty" />
  <slot name="error" />
</template>

<style lang="less">
@import './components/ym-chat-ai/styles/variables.less';
@import './components/ym-chat-ai/styles/iconfont.css';
</style>
