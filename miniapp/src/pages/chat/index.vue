<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { authStore } from '../../stores/auth'
import { chatApi } from '../../api/chat'
import { chatStore } from '../../stores/chat'
import type { ChatMessageRecord } from '../../components/chat/MessageList.vue'
import MessageList from '../../components/chat/MessageList.vue'
import ChatComposer, { type KnowledgeOption } from '../../components/chat/ChatComposer.vue'
import ChatSidebar from '../../components/chat/ChatSidebar.vue'
import type { SessionRecord } from '../../components/session/SessionList.vue'
import { notifyError, notifySuccess } from '../../utils/notify'

const conversationId = ref('')
const mode = ref('OPEN_CHAT')
const selectedDocumentId = ref('')
const knowledgeOptions = ref<KnowledgeOption[]>([])
const messages = ref<ChatMessageRecord[]>([])
const loading = ref(false)
const sidebarOpen = ref(false)
const sessions = ref<SessionRecord[]>([])
const sessionPage = ref(1)
const sessionTotalPages = ref(1)
const sessionsLoading = ref(false)
const snapshot = ref(chatStore.state)
let pollTimer: ReturnType<typeof setInterval> | undefined
let conversationVersion = 0

const modes = [
  { value: 'OPEN_CHAT', label: '开放式对话' },
  { value: 'AUTO_DOCUMENT', label: '自动知识问答' },
  { value: 'DOCUMENT', label: '当前文档问答' }
]
const streaming = computed(() => snapshot.value.status === 'streaming')

function createConversationId() { return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}` }
function mapExchange(exchange: any): ChatMessageRecord[] {
  const status = String(exchange?.status || '').toLowerCase()
  const failed = status === 'failed' || status === 'error'
  const records: ChatMessageRecord[] = []
  if (exchange?.question) records.push({ id: `${exchange.exchangeId || Math.random()}-q`, role: 'user', content: String(exchange.question) })
  if (exchange?.answer || exchange?.errorMessage || exchange?.status) records.push({ id: `${exchange.exchangeId || Math.random()}-a`, role: 'assistant', content: String(exchange.answer || exchange.errorMessage || ''), status: failed ? 'error' : status, references: exchange.references || [], recommendations: exchange.recommendations || [] })
  return records
}
async function loadDocuments() {
  try { knowledgeOptions.value = (await chatApi.listKnowledgeDocumentOptions() as any[] || []).map((item: any) => ({ documentId: String(item.documentId ?? item.id ?? ''), documentName: item.documentName || item.name, knowledgeScopeName: item.knowledgeScopeName })) } catch (error) { notifyError(error, '知识范围加载失败') }
}
async function loadConversation(id: string) {
  if (!id) return
  const version = ++conversationVersion
  loading.value = true
  try { const session: any = await chatApi.getSession(id); if (version !== conversationVersion) return; conversationId.value = id; messages.value = (session?.exchanges || []).flatMap(mapExchange); mode.value = String(session?.chatMode || 'OPEN_CHAT'); selectedDocumentId.value = String(session?.selectedDocumentId || '') } catch (error) { if (version === conversationVersion) notifyError(error, '会话加载失败') } finally { if (version === conversationVersion) loading.value = false }
}
async function loadSessions(nextPage = 1) {
  if (sessionsLoading.value || (nextPage > 1 && nextPage > sessionTotalPages.value)) return
  sessionsLoading.value = true
  try {
    const page: any = await chatApi.listSessionsPage({ pageNo: nextPage, pageSize: 20 })
    const next: SessionRecord[] = Array.isArray(page?.sessions) ? page.sessions : []
    sessions.value = nextPage === 1 ? next : [...sessions.value, ...next]
    sessions.value.sort((a, b) => new Date(String(b.createdAt || b.updatedAt || 0)).getTime() - new Date(String(a.createdAt || a.updatedAt || 0)).getTime())
    sessionPage.value = Number(page?.pageNo || nextPage)
    sessionTotalPages.value = Number(page?.totalPages || page?.pages || sessionPage.value)
  } catch (error) { notifyError(error, '会话加载失败') } finally { sessionsLoading.value = false }
}
function openSidebar() { sidebarOpen.value = true; void loadSessions() }
function selectSession(id: string) { sidebarOpen.value = false; if (id === conversationId.value) return; newConversation(); void loadConversation(id) }
function confirmDeleteSession(id: string) {
  const remove = () => { void chatApi.deleteSession(id).then(() => { sessions.value = sessions.value.filter((record) => record.conversationId !== id); if (conversationId.value === id) newConversation(); notifySuccess('会话已删除') }).catch((error) => notifyError(error, '删除失败')) }
  const api = (globalThis as { uni?: { showModal?: (options: { title: string; content: string; success?: (result: { confirm: boolean }) => void }) => void } }).uni
  if (!api?.showModal) { remove(); return }
  api.showModal({ title: '删除会话', content: '删除后无法恢复，确认继续吗？', success: (result) => { if (result.confirm) remove() } })
}
function startNewChat() { sidebarOpen.value = false; newConversation() }
function openProfile() { sidebarOpen.value = false; (globalThis as { uni?: { navigateTo?: (options: { url: string }) => void } }).uni?.navigateTo?.({ url: '/pages/profile/index' }) }
function syncStream() {
  snapshot.value = chatStore.state
  const answer = `${snapshot.value.thinking.length ? `<think>${snapshot.value.thinking.join('')}</think>` : ''}${snapshot.value.answer}`
  const index = messages.value.findIndex((message) => message.role === 'assistant' && message.id === 'stream-answer')
  if (index >= 0) messages.value[index] = { ...messages.value[index], content: answer, references: snapshot.value.references, recommendations: snapshot.value.recommendations }
  else if (streaming.value || answer) messages.value.push({ id: 'stream-answer', role: 'assistant', content: answer, references: snapshot.value.references })
  if (!streaming.value) stopPolling()
}
function startPolling() { stopPolling(); pollTimer = setInterval(syncStream, 50); syncStream() }
function stopPolling() { if (pollTimer) clearInterval(pollTimer); pollTimer = undefined }
async function sendQuestion(value: string) {
  const question = String(value || '').trim()
  if (!question || streaming.value || loading.value) return
  const version = conversationVersion
  if (!conversationId.value) conversationId.value = createConversationId()
  messages.value.push({ id: `${Date.now()}-q`, role: 'user', content: question }); messages.value.push({ id: 'stream-answer', role: 'assistant', content: '', status: 'streaming', references: [] })
  const pending = chatStore.send({ question, conversationId: conversationId.value, chatMode: mode.value, ...(mode.value === 'DOCUMENT' && selectedDocumentId.value ? { selectedDocumentId: selectedDocumentId.value } : {}) })
  startPolling()
  try { await pending; if (version !== conversationVersion) return; syncStream(); const index = messages.value.findIndex((message) => message.id === 'stream-answer'); if (index >= 0) messages.value[index] = { ...messages.value[index], content: snapshot.value.answer, status: snapshot.value.status, references: snapshot.value.references, recommendations: snapshot.value.recommendations } } catch (error) { if (version !== conversationVersion) return; notifyError(error, '发送失败'); const index = messages.value.findIndex((message) => message.id === 'stream-answer'); if (index >= 0) messages.value[index] = { ...messages.value[index], status: 'error' } } finally { if (version === conversationVersion) { stopPolling(); syncStream() } }
}
function stop() { if (!streaming.value) return; chatStore.stop(); if (conversationId.value) void chatApi.stopSession(conversationId.value).catch(() => undefined); syncStream() }
function retry(id: string | number) { const index = messages.value.findIndex((message) => String(message.id) === String(id)); const question = index > 0 ? messages.value[index - 1]?.content : ''; if (!question) return; messages.value.splice(Math.max(0, index - 1), 2); void sendQuestion(question) }
function openReference(reference: any) { const text = reference?.content || reference?.snippet || reference?.documentName || reference?.title || JSON.stringify(reference); ;(globalThis as { uni?: { showModal?: (options: { title: string; content: string; showCancel?: boolean }) => void } }).uni?.showModal?.({ title: reference?.documentName || '参考资料', content: String(text), showCancel: false }) }
function newConversation() { stop(); ++conversationVersion; stopPolling(); loading.value = false; conversationId.value = createConversationId(); messages.value = []; mode.value = 'OPEN_CHAT'; selectedDocumentId.value = ''; snapshot.value = { status: 'idle', answer: '', thinking: [], references: [], recommendations: [], error: null } }
onLoad((query) => { void authStore.guard(`/pages/chat/index${query?.conversationId ? `?conversationId=${encodeURIComponent(String(query.conversationId))}` : ''}`).then((ok) => { if (ok) { void loadDocuments(); if (query?.conversationId) void loadConversation(String(query.conversationId)) } }) })
onBeforeUnmount(stopPolling)
</script>
<template>
  <view class="page"><view class="header"><button class="menu-button" aria-label="打开会话菜单" data-testid="open-sidebar" @click="openSidebar"><text class="menu-line" /><text class="menu-line" /><text class="menu-line" /></button><text class="title">AI聊天助手</text></view><view v-if="loading" class="loading">正在加载会话…</view><view v-if="!messages.length && !loading" class="welcome-card">👋 你好，我是 AI 问答助手，我可以帮助你回答问题。请直接发送消息开始对话！</view><MessageList :messages="messages" :streaming="streaming" @retry="retry" @stop="stop" @open-reference="openReference" @recommend="sendQuestion" /><ChatComposer v-model:mode="mode" v-model:selected-document-id="selectedDocumentId" :modes="modes" :knowledge-options="knowledgeOptions" :disabled="loading" :loading="streaming" @submit="sendQuestion" @stop="stop" /><ChatSidebar :open="sidebarOpen" :records="sessions" :page-no="sessionPage" :total-pages="sessionTotalPages" :loading="sessionsLoading" :active-id="conversationId" @close="sidebarOpen = false" @new-chat="startNewChat" @select="selectSession" @delete="confirmDeleteSession" @load-more="loadSessions(sessionPage + 1)" @profile="openProfile" /></view>
</template>
<style scoped>
.page { min-height: 100vh; padding: 28rpx 32rpx 260rpx; box-sizing: border-box; background: linear-gradient(145deg, #e2fcfd 0%, #ced6fc 100%); }
.header { display: flex; align-items: center; min-height: 72rpx; padding: 0 20rpx; border-radius: 999rpx; background: rgba(255, 255, 255, 0.82); box-shadow: 0 4rpx 16rpx rgba(60, 104, 160, 0.08); }
.menu-button { display: flex; flex: none; flex-direction: column; align-items: center; justify-content: center; gap: 6rpx; width: 56rpx; height: 56rpx; margin: 0 12rpx 0 0; padding: 0; background: transparent; }
.menu-button::after { border: 0; }
.menu-line { width: 27rpx; height: 3rpx; border-radius: 2rpx; background: #2b5274; }
.title { font-size: 30rpx; font-weight: 600; color: #212529; }
.welcome-card { margin-top: 24rpx; padding: 24rpx; color: #212529; line-height: 1.65; background: rgba(255, 255, 255, 0.9); border: 1rpx solid rgba(255, 255, 255, 0.72); border-radius: 12rpx; box-shadow: 0 4rpx 12rpx rgba(60, 104, 160, 0.1); }
.loading { padding: 30rpx 0; color: var(--color-text-muted); text-align: center; }
</style>
