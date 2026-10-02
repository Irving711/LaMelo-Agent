<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { authStore } from '../../stores/auth'
import { chatApi } from '../../api/chat'
import SessionList, { type SessionRecord } from '../../components/session/SessionList.vue'
import { notifyError, notifySuccess } from '../../utils/notify'

const records = ref<SessionRecord[]>([])
const pageNo = ref(1)
const totalPages = ref(1)
const loading = ref(false)

async function loadPage(nextPage = 1) {
  if (loading.value || (nextPage > 1 && nextPage > totalPages.value)) return
  loading.value = true
  try {
    const response: any = await chatApi.listSessionsPage({ pageNo: nextPage, pageSize: 20 })
    const page = response || {}
    const next = Array.isArray(page.sessions) ? page.sessions : []
    records.value = nextPage === 1 ? next : [...records.value, ...next]
    records.value.sort((a, b) => new Date(String(b.createdAt || b.updatedAt || 0)).getTime() - new Date(String(a.createdAt || a.updatedAt || 0)).getTime())
    pageNo.value = Number(page.pageNo || nextPage)
    totalPages.value = Number(page.totalPages || page.pages || pageNo.value || 1)
  } catch (error) { notifyError(error, '会话加载失败') } finally { loading.value = false }
}
function select(id: string) { ;(globalThis as { uni?: { navigateTo?: (options: { url: string }) => void } }).uni?.navigateTo?.({ url: `/pages/chat/index?conversationId=${encodeURIComponent(id)}` }) }
function confirmDelete(id: string) {
  const api = (globalThis as { uni?: { showModal?: (options: { title: string; content: string; success?: (result: { confirm: boolean }) => void }) => void } }).uni
  const remove = () => { void chatApi.deleteSession(id).then(() => { records.value = records.value.filter((record) => record.conversationId !== id); notifySuccess('会话已删除') }).catch((error) => notifyError(error, '删除失败')) }
  if (!api?.showModal) { remove(); return }
  api.showModal({ title: '删除会话', content: '删除后无法恢复，确认继续吗？', success: (result) => { if (result.confirm) remove() } })
}
onLoad(() => { void authStore.guard('/pages/sessions/index').then((ok) => { if (ok) void loadPage() }) })
</script>
<template><view class="page"><view class="header"><text class="title">会话</text></view><SessionList :records="records" :page-no="pageNo" :total-pages="totalPages" :loading="loading" @select="select" @delete="confirmDelete" @load-more="loadPage(pageNo + 1)" /></view></template>
<style scoped>.page { min-height: 100vh; }.header { padding: 28rpx 32rpx 0; }.title { font-size: 40rpx; font-weight: 600; }</style>
