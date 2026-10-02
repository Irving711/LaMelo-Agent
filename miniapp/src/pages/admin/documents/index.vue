<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { manageApi } from '../../../api/manage'
import AdminGuard from '../../../components/admin/AdminGuard.vue'
import FilterPanel from '../../../components/admin/FilterPanel.vue'
import PagedList from '../../../components/admin/PagedList.vue'
import StatusBadge from '../../../components/admin/StatusBadge.vue'
import DocumentUpload from '../../../components/admin/DocumentUpload.vue'
import { authStore } from '../../../stores/auth'
const page = reactive({ records: [] as any[], total: 0, current: 1, size: 10 }); const filters = ref<Record<string, unknown>>({ keyword: '' }); const error = ref('')
async function load() { try { const data: any = await manageApi.queryDocumentPage({ pageNo: page.current, pageSize: page.size, keyword: String(filters.value.keyword || '') }); Object.assign(page, { records: data?.records || [], total: Number(data?.total || 0) }) } catch (e) { error.value = e instanceof Error ? e.message : '加载失败' } }
function open(item: any) { (globalThis as any).uni?.navigateTo?.({ url: `/pages/admin/documents/detail?documentId=${item.documentId || item.id}` }) }
function remove(item: any) { const api: any = (globalThis as any).uni; const run = () => manageApi.deleteDocument({ documentId: item.documentId || item.id }).then(() => { if (page.current > 1 && (page.current - 1) * page.size >= page.total - 1) page.current--; return load() }).catch((e) => { error.value = e instanceof Error ? e.message : '删除失败' }); if (api?.showModal) api.showModal({ title: '确认删除', content: '删除后不可恢复，是否继续？', success: (r: any) => { if (r.confirm) void run() } }); else void run() }
function reset() { filters.value = { keyword: '' }; page.current = 1; void load() }
onMounted(async () => { if ((await authStore.guard('/pages/admin/documents/index')) && authStore.isAdmin) await load() })
</script>
<template><AdminGuard route="/pages/admin/documents/index"><view class="page"><text class="title">文档管理</text><DocumentUpload @uploaded="load" /><FilterPanel v-model="filters" @submit="page.current = 1; load()" @reset="reset" /><text v-if="error" class="error">{{ error }}</text><PagedList v-bind="page" @page-change="page.current = $event; load()" @size-change="page.size = $event; page.current = 1; load()" @refresh="load"><template #default="{ records }"><view v-for="item in records" :key="item.documentId || item.id" class="row"><view class="main" @click="open(item)"><text class="name">{{ item.documentName || item.originalFileName || '未命名文档' }}</text><text class="muted">{{ item.originalFileName || '-' }}</text></view><StatusBadge :status="item.parseStatusName || item.parseStatus" /><button size="mini" @click.stop="remove(item)">删除</button></view></template></PagedList></view></AdminGuard></template>
<style scoped>.page{padding:32rpx}.title{display:block;font-size:38rpx;font-weight:600}.row{display:flex;justify-content:space-between;gap:16rpx;padding:24rpx 0;border-bottom:1rpx solid var(--color-border)}.name,.muted{display:block}.muted{color:var(--color-text-muted);font-size:24rpx}.error{color:#b91c1c}</style>
