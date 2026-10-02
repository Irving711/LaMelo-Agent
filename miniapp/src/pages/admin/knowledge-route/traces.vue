<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { manageApi } from '../../../api/manage'
import AdminGuard from '../../../components/admin/AdminGuard.vue'
import PagedList from '../../../components/admin/PagedList.vue'
import { authStore } from '../../../stores/auth'
import { notifyError } from '../../../utils/notify'
const page = reactive({ records: [] as any[], total: 0, current: 1, size: 10 })
const filters = reactive({ conversationId: '', mode: '', routeStatus: '' })
const selected = ref<any>(null)
async function load() { if (!authStore.isAdmin) return; try { const data: any = await manageApi.queryKnowledgeRouteTracePage({ pageNo: page.current, pageSize: page.size, ...filters }); Object.assign(page, { records: data?.records || [], total: Number(data?.totalSize || 0) }) } catch (error) { notifyError(error, '路由追踪加载失败') } }
function search() { page.current = 1; void load() }
function reset() { Object.assign(filters, { conversationId: '', mode: '', routeStatus: '' }); search() }
function openExchange(item: any) { if (!item.conversationId || !item.exchangeId) return; (globalThis as any).uni?.navigateTo?.({ url: `/pages/admin/observability/exchange?conversationId=${encodeURIComponent(item.conversationId)}&exchangeId=${encodeURIComponent(item.exchangeId)}` }) }
onMounted(async () => { if (await authStore.guard('/pages/admin/knowledge-route/traces')) await load() })
</script>
<template><AdminGuard route="/pages/admin/knowledge-route/traces"><view class="page"><text class="title">路由追踪</text><view class="filters"><input v-model="filters.conversationId" data-testid="trace-conversation" placeholder="会话 ID" /><input v-model="filters.mode" placeholder="模式" /><input v-model="filters.routeStatus" placeholder="路由状态" /><button size="mini" data-testid="trace-search" @click="search">查询</button><button size="mini" @click="reset">重置</button></view><PagedList v-bind="page" @page-change="page.current=$event;load()" @size-change="page.size=$event;page.current=1;load()" @refresh="load"><template #default="{records}"><view v-for="item in records" :key="item.id" class="row" @click="selected=item"><text>{{ item.question || item.id }}</text><text>{{ item.routeStatus || '-' }}</text></view></template></PagedList><view v-if="selected" class="detail"><text class="section">追踪详情</text><text>会话：{{ selected.conversationId }}</text><text>改写问题：{{ selected.rewriteQuestion || '-' }}</text><text>知识域候选：{{ selected.topScopesJson || '-' }}</text><text>主题候选：{{ selected.topTopicsJson || '-' }}</text><text>文档候选：{{ selected.topDocumentsJson || '-' }}</text><text>置信度：{{ selected.confidence || '-' }}</text><text v-if="selected.errorMsg">错误：{{ selected.errorMsg }}</text><button size="mini" @click="openExchange(selected)">查看轮次详情</button></view></view></AdminGuard></template>
<style scoped>.page{padding:32rpx}.title{display:block;font-size:38rpx;font-weight:600}.filters{display:flex;flex-wrap:wrap;gap:12rpx;margin:20rpx 0}.filters input{min-width:180rpx;flex:1;height:72rpx;padding:0 16rpx;border:1rpx solid var(--color-border)}.row{display:flex;justify-content:space-between;padding:22rpx 0;border-bottom:1rpx solid var(--color-border)}.detail{display:flex;flex-direction:column;gap:12rpx;margin-top:28rpx;word-break:break-all}.section{font-weight:600}</style>
