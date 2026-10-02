<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { manageApi } from '../../../api/manage'
import AdminGuard from '../../../components/admin/AdminGuard.vue'
import { authStore } from '../../../stores/auth'
import { notifyError, notifySuccess } from '../../../utils/notify'

const scopes = ref<any[]>([])
const topics = ref<any[]>([])
const relations = ref<any[]>([])
const active = ref<'scope' | 'topic' | 'relation' | 'profile'>('scope')
const scope = reactive({ id: '', scopeCode: '', scopeName: '', parentScopeCode: '', description: '', aliases: '', examples: '', sortOrder: '0' })
const topic = reactive({ id: '', topicCode: '', topicName: '', scopeCode: '', description: '', aliases: '', examples: '', answerShape: '', executionPreference: '', sortOrder: '0' })
const relation = reactive({ topicCode: '', documentId: '', relationScore: '0.9', relationSource: 'manual', reason: '' })
const profileDocumentId = ref('')
const profile = ref<any>(null)

async function load() {
  try {
    scopes.value = await manageApi.listKnowledgeScopes() as any[] || []
    const result: any = await manageApi.listKnowledgeTopics()
    topics.value = Array.isArray(result) ? result : []
    if (relation.topicCode) relations.value = await manageApi.listTopicDocuments({ topicCode: relation.topicCode }) as any[] || []
  } catch (error) { notifyError(error, '知识路由加载失败') }
}
onMounted(async () => { if ((await authStore.guard('/pages/admin/knowledge-route/index')) && authStore.isAdmin) await load() })
async function saveScope() {
  if (!scope.scopeCode.trim() || !scope.scopeName.trim()) { notifyError(new Error('请输入知识域编码和名称')); return }
  try { await manageApi.saveKnowledgeScope({ ...scope }); notifySuccess('知识域已保存'); await load() } catch (error) { notifyError(error, '保存失败') }
}
async function saveTopic() {
  if (!topic.topicCode.trim() || !topic.topicName.trim() || !topic.scopeCode.trim()) { notifyError(new Error('请输入主题编码、名称和知识域编码')); return }
  try { await manageApi.saveKnowledgeTopic({ ...topic }); notifySuccess('主题已保存'); await load() } catch (error) { notifyError(error, '保存失败') }
}
function confirmRemove(action: () => Promise<unknown>) {
  (globalThis as any).uni?.showModal?.({ title: '确认删除', content: '删除后关联数据可能受到影响，是否继续？', success: (result: any) => { if (result.confirm) void action() } })
}
function deleteScope(code: string) { confirmRemove(async () => { try { await manageApi.deleteKnowledgeScope({ scopeCode: code }); await load() } catch (error) { notifyError(error, '删除失败') } }) }
function deleteTopic(code: string) { confirmRemove(async () => { try { await manageApi.deleteKnowledgeTopic({ topicCode: code }); await load() } catch (error) { notifyError(error, '删除失败') } }) }
async function loadRelations() { if (!relation.topicCode) return; try { relations.value = await manageApi.listTopicDocuments({ topicCode: relation.topicCode }) as any[] || [] } catch (error) { notifyError(error, '关联加载失败') } }
async function saveRelation() { if (!relation.topicCode || !relation.documentId) { notifyError(new Error('请输入主题编码和文档 ID')); return }; try { await manageApi.saveTopicDocumentRelation({ ...relation }); notifySuccess('关联已保存'); await loadRelations() } catch (error) { notifyError(error, '保存失败') } }
function removeRelation(item: any) { confirmRemove(async () => { try { await manageApi.removeTopicDocumentRelation({ topicCode: item.topicCode, documentId: item.documentId }); await loadRelations() } catch (error) { notifyError(error, '移除失败') } }) }
async function queryProfile() { if (!profileDocumentId.value) return; try { profile.value = await manageApi.queryDocumentProfile({ documentId: profileDocumentId.value }) } catch (error) { notifyError(error, '画像查询失败') } }
async function regenerateProfile() { if (!profileDocumentId.value) return; try { profile.value = await manageApi.regenerateDocumentProfile({ documentId: profileDocumentId.value }); notifySuccess('画像已重新生成') } catch (error) { notifyError(error, '画像生成失败') } }
</script>
<template>
  <AdminGuard route="/pages/admin/knowledge-route/index"><view class="page"><text class="title">知识路由配置</text>
    <view class="tabs"><button size="mini" @click="active='scope'">知识域</button><button size="mini" @click="active='topic'">主题</button><button size="mini" @click="active='relation'">文档关联</button><button size="mini" @click="active='profile'">文档画像</button></view>
    <template v-if="active==='scope'"><text class="section">知识域</text><view v-for="item in scopes" :key="item.scopeCode" class="row"><text @click="Object.assign(scope,item)">{{ item.scopeName }} · {{ item.scopeCode }}</text><button size="mini" @click="deleteScope(item.scopeCode)">删除</button></view><input v-model="scope.scopeCode" data-testid="scope-code" placeholder="知识域编码" /><input v-model="scope.scopeName" data-testid="scope-name" placeholder="名称" /><input v-model="scope.parentScopeCode" placeholder="上级知识域编码，可选" /><input v-model="scope.description" placeholder="描述" /><button type="primary" data-testid="scope-save" @click="saveScope">保存知识域</button></template>
    <template v-else-if="active==='topic'"><text class="section">主题</text><view v-for="item in topics" :key="item.topicCode" class="row"><text @click="Object.assign(topic,item)">{{ item.topicName }} · {{ item.topicCode }}</text><button size="mini" @click="deleteTopic(item.topicCode)">删除</button></view><input v-model="topic.topicCode" placeholder="主题编码" /><input v-model="topic.topicName" placeholder="主题名称" /><input v-model="topic.scopeCode" placeholder="所属知识域编码" /><input v-model="topic.aliases" placeholder="别名" /><input v-model="topic.description" placeholder="描述" /><button type="primary" @click="saveTopic">保存主题</button></template>
    <template v-else-if="active==='relation'"><text class="section">主题文档关联</text><input v-model="relation.topicCode" placeholder="主题编码" /><button size="mini" @click="loadRelations">查询关联</button><view v-for="item in relations" :key="`${item.topicCode}-${item.documentId}`" class="row"><text>{{ item.documentName || item.documentId }}</text><button size="mini" @click="removeRelation(item)">移除</button></view><input v-model="relation.documentId" placeholder="文档 ID" /><input v-model="relation.relationScore" type="digit" placeholder="关联分数" /><input v-model="relation.reason" placeholder="关联说明" /><button type="primary" @click="saveRelation">保存关联</button></template>
    <template v-else><text class="section">文档画像</text><input v-model="profileDocumentId" data-testid="profile-document-id" placeholder="文档 ID" /><view class="tabs"><button size="mini" data-testid="profile-query" @click="queryProfile">查询画像</button><button size="mini" data-testid="profile-regenerate" @click="regenerateProfile">重新生成</button></view><view v-if="profile" class="profile"><text>状态：{{ profile.profileStatus || '-' }}</text><text>摘要：{{ profile.documentSummary || '-' }}</text><text>核心主题：{{ profile.coreTopics || '-' }}</text><text>示例问题：{{ profile.exampleQuestions || '-' }}</text><text v-if="profile.errorMsg">错误：{{ profile.errorMsg }}</text></view></template>
  </view></AdminGuard>
</template>
<style scoped>.page{padding:32rpx}.title{display:block;font-size:38rpx;font-weight:600}.section{display:block;font-weight:600;margin:28rpx 0 12rpx}.tabs{display:flex;flex-wrap:wrap;gap:12rpx}.row{display:flex;align-items:center;justify-content:space-between;padding:18rpx 0;border-bottom:1rpx solid var(--color-border)}input{height:76rpx;margin:14rpx 0;padding:0 20rpx;border:1rpx solid var(--color-border);border-radius:8rpx}.profile{display:flex;flex-direction:column;gap:12rpx;word-break:break-all}</style>
