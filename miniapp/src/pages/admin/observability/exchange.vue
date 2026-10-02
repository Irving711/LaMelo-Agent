<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { manageApi } from '../../../api/manage'
import AdminGuard from '../../../components/admin/AdminGuard.vue'
import { authStore } from '../../../stores/auth'
import { notifyError } from '../../../utils/notify'
const detail=ref<any>({});const retrieval=ref<any[]>([]);const channels=ref<any[]>([])
onLoad(async(q)=>{const c=String(q?.conversationId||'');const e=String(q?.exchangeId||'');if(c&&e&&(await authStore.guard('/pages/admin/observability/exchange'))&&authStore.isAdmin){try{const [exchange,results,executions]=await Promise.all([manageApi.getExchangeDetail(c,e),manageApi.getRetrievalResults(c,e),manageApi.getChannelExecutions(c,e)]);detail.value=exchange||{};retrieval.value=results as any[]||[];channels.value=executions as any[]||[]}catch(error){notifyError(error,'轮次详情加载失败')}}})
</script>
<template><AdminGuard route="/pages/admin/observability/exchange"><view class="page"><text class="title">轮次详情</text><text class="section">问题</text><text class="block">{{ detail.exchange?.question || '-' }}</text><text class="section">回答</text><text class="block">{{ detail.exchange?.answer || '-' }}</text><text class="section">阶段追踪</text><view v-for="item in (detail.stageTraces||[])" :key="item.stageId" class="row"><text>{{ item.stageName || item.stageCode }} · {{ item.stageState }}</text><text>{{ item.durationMs }} ms</text><text v-if="item.errorMessage">{{ item.errorMessage }}</text></view><text class="section">检索结果</text><text v-for="item in retrieval" :key="item.id || item.documentId" class="row">{{ item.content || item.documentName || JSON.stringify(item) }}</text><text class="section">通道执行</text><text v-for="item in channels" :key="item.id || item.channel" class="row">{{ item.channel || item.name || JSON.stringify(item) }}</text></view></AdminGuard></template><style scoped>.page{padding:32rpx}.title{display:block;font-size:38rpx;font-weight:600}.block{display:block;padding:20rpx 0}.section{display:block;font-weight:600;margin-top:22rpx}.row{display:flex;justify-content:space-between;gap:12rpx;padding:18rpx 0;border-bottom:1rpx solid var(--color-border)}</style>
