<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { manageApi } from '../../../api/manage'
import AdminGuard from '../../../components/admin/AdminGuard.vue'
import PagedList from '../../../components/admin/PagedList.vue'
import StatusBadge from '../../../components/admin/StatusBadge.vue'
import { authStore } from '../../../stores/auth'
import { notifyError } from '../../../utils/notify'
const page=reactive({records:[] as any[],total:0,current:1,size:12})
const benchmarks=ref<any[]>([])
async function load(){if(!authStore.isAdmin)return;try{const data:any=await manageApi.listSessionsPage({pageNo:page.current,pageSize:page.size});Object.assign(page,{records:data?.sessions||data?.records||[],total:Number(data?.totalSize||data?.total||0)})}catch(error){notifyError(error,'会话加载失败')}}
async function loadBenchmarks(){try{benchmarks.value=await manageApi.getStageBenchmarks() as any[]||[]}catch(error){notifyError(error,'阶段基准加载失败')}}
function open(item:any){(globalThis as any).uni?.navigateTo?.({url:`/pages/admin/observability/session?conversationId=${encodeURIComponent(item.conversationId)}`})}
onMounted(async()=>{if((await authStore.guard('/pages/admin/observability/index'))&&authStore.isAdmin)await Promise.all([load(),loadBenchmarks()])})
</script>
<template><AdminGuard route="/pages/admin/observability/index"><view class="page"><text class="title">会话观测</text><text class="section">阶段耗时基准</text><view v-for="item in benchmarks" :key="`${item.stageCode}-${item.executionMode}`" class="row"><text>{{ item.stageCode }} · {{ item.executionMode }}</text><text>P90 {{ item.p90DurationMs }} ms（{{ item.sampleCount }} 次）</text></view><text class="section">会话列表</text><PagedList v-bind="page" @page-change="page.current=$event;load()" @size-change="page.size=$event;page.current=1;load()" @refresh="load"><template #default="{records}"><view v-for="item in records" :key="item.conversationId" class="row" @click="open(item)"><text>{{ item.title || item.lastUserMessage || item.conversationId }}</text><StatusBadge :status="item.latestTurnStatus || item.status" /></view></template></PagedList></view></AdminGuard></template><style scoped>.page{padding:32rpx}.title{display:block;font-size:38rpx;font-weight:600}.section{display:block;font-weight:600;margin-top:26rpx}.row{display:flex;justify-content:space-between;gap:12rpx;padding:22rpx 0;border-bottom:1rpx solid var(--color-border)}</style>
