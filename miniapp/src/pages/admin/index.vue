<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { authStore } from '../../stores/auth'
import { manageApi } from '../../api/manage'
import { notifyError } from '../../utils/notify'

const authorized = ref(false)
const checked = ref(false)
const documents = ref<any[]>([])
const sessions = ref<any[]>([])
const navigate = (url: string) => (globalThis as any).uni?.navigateTo?.({ url })

onLoad(async () => { const authenticated = await authStore.guard('/pages/admin/index'); authorized.value = authenticated && authStore.isAdmin; checked.value = true; if (!authorized.value) return; const results = await Promise.allSettled([manageApi.queryDocumentPage({ pageNo: 1, pageSize: 5 }), manageApi.listSessionsPage({ pageNo: 1, pageSize: 5 })]); if (results[0].status === 'fulfilled') documents.value = (results[0].value as any)?.records || []; else notifyError(results[0].reason, '最近文档加载失败'); if (results[1].status === 'fulfilled') sessions.value = (results[1].value as any)?.sessions || []; else notifyError(results[1].reason, '最近会话加载失败') })
</script>

<template>
  <view class="page"><text v-if="!checked" class="muted">正在检查权限…</text><template v-else-if="authorized"><text class="title">管理后台</text><view class="menu"><button @click="navigate('/pages/admin/documents/index')">文档管理</button><button @click="navigate('/pages/admin/knowledge-route/index')">知识路由配置</button><button @click="navigate('/pages/admin/knowledge-route/traces')">路由追踪</button><button @click="navigate('/pages/admin/observability/index')">会话观测</button></view><text class="section">最近文档</text><view v-for="item in documents" :key="item.documentId" class="row" @click="navigate(`/pages/admin/documents/detail?documentId=${item.documentId}`)">{{ item.documentName || item.documentId }}</view><text class="section">最近会话</text><view v-for="item in sessions" :key="item.conversationId" class="row" @click="navigate(`/pages/admin/observability/session?conversationId=${encodeURIComponent(item.conversationId)}`)">{{ item.title || item.conversationId }}</view></template><text v-else class="muted">没有权限访问管理后台</text></view>
</template>

<style scoped>
.page { min-height: 100vh; padding: 48rpx 32rpx; box-sizing: border-box; }
.title { display: block; font-size: 40rpx; font-weight: 600; margin-bottom: 16rpx; }
.menu { display: flex; flex-direction: column; gap: 20rpx; }
.section { display: block; margin-top: 32rpx; font-weight: 600; }.row { padding: 20rpx 0; border-bottom: 1rpx solid var(--color-border); }
.muted { color: var(--color-text-muted); }
</style>
