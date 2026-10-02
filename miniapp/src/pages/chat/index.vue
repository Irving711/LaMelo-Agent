<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { authStore } from '../../stores/auth'
import { chatApi } from '../../api/chat'
import { chatStore } from '../../stores/chat'
import type { ChatMessageRecord } from '../../components/chat/MessageList.vue'
import MessageList from '../../components/chat/MessageList.vue'
import ChatComposer, { type KnowledgeOption } from '../../components/chat/ChatComposer.vue'
import StreamStatus from '../../components/chat/StreamStatus.vue'
import { notifyError } from '../../utils/notify'

const conversationId = ref('')
const draft = ref('')
const mode = ref('AUTO_DOCUMENT')
const selectedDocumentId = ref('')
const knowledgeOptions = ref<KnowledgeOption[]>([])
const messages = ref<ChatMessageRecord[]>([])
const loading = ref(false)
const snapshot = ref(chatStore.state)
let pollTimer: ReturnType<typeof setInterval> | undefined

const modes = [
  { value: 'AUTO_DOCUMENT', label: '自动知识问答' },
  { value: 'OPEN_CHAT', label: '开放式对话' },
  { value: 'DOCUMENT', label: '当前文档问答' }
]
const streaming = computed(() => snapshot.value.status === 'streaming')

function createConversationId() { return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}` }
function mapExchange(exchange: any): ChatMessageRecord[] {
  const status = String(exchange?.status || '').toLowerCase()
  const failed = status === 'failed' || status === 'error'
  const records: ChatMessageRecord[] = []
  if (exchange?.question) records.push({ id: `${exchange.exchangeId || Math.random()}-q`, role: 'user', content: String(exchange.question) })
  if (exchange?.answer || exchange?.errorMessage || exchange?.status) records.push({ id: `${exchange.exchangeId || Math.random()}-a`, role: 'assistant', content: String(exchange.answer || exchange.errorMessage || ''), status: failed ? 'error' : status, references: exchange.references || [] })
  return records
}
async function loadDocuments() {
  try { knowledgeOptions.value = (await chatApi.listKnowledgeDocumentOptions() as any[] || []).map((item: any) => ({ documentId: String(item.documentId ?? item.id ?? ''), documentName: item.documentName || item.name, knowledgeScopeName: item.knowledgeScopeName })) } catch (error) { notifyError(error, '知识范围加载失败') }
}
async function loadConversation(id: string) {
  if (!id) return
  loading.value = true
  try { const session: any = await chatApi.getSession(id); conversationId.value = id; messages.value = (session?.exchanges || []).flatMap(mapExchange); if (session?.chatMode) mode.value = String(session.chatMode); if (session?.selectedDocumentId) selectedDocumentId.value = String(session.selectedDocumentId) } catch (error) { notifyError(error, '会话加载失败') } finally { loading.value = false }
}
function syncStream() {
  snapshot.value = chatStore.state
  const answer = snapshot.value.answer
  const index = messages.value.findIndex((message) => message.role === 'assistant' && message.id === 'stream-answer')
  if (index >= 0) messages.value[index] = { ...messages.value[index], content: answer, references: snapshot.value.references }
  else if (streaming.value || answer) messages.value.push({ id: 'stream-answer', role: 'assistant', content: answer, references: snapshot.value.references })
  if (!streaming.value) stopPolling()
}
function startPolling() { stopPolling(); pollTimer = setInterval(syncStream, 50); syncStream() }
function stopPolling() { if (pollTimer) clearInterval(pollTimer); pollTimer = undefined }
async function sendQuestion(value: string) {
  const question = String(value || '').trim()
  if (!question || streaming.value) return
  if (!conversationId.value) conversationId.value = createConversationId()
  messages.value.push({ id: `${Date.now()}-q`, role: 'user', content: question }); messages.value.push({ id: 'stream-answer', role: 'assistant', content: '', status: 'streaming', references: [] }); draft.value = ''
  const pending = chatStore.send({ question, conversationId: conversationId.value, chatMode: mode.value, ...(mode.value === 'DOCUMENT' && selectedDocumentId.value ? { selectedDocumentId: selectedDocumentId.value } : {}) })
  startPolling()
  try { await pending; syncStream(); const index = messages.value.findIndex((message) => message.id === 'stream-answer'); if (index >= 0) messages.value[index] = { ...messages.value[index], content: snapshot.value.answer, status: snapshot.value.status, references: snapshot.value.references } } catch (error) { notifyError(error, '发送失败'); const index = messages.value.findIndex((message) => message.id === 'stream-answer'); if (index >= 0) messages.value[index] = { ...messages.value[index], status: 'error' } } finally { stopPolling(); syncStream() }
}
function stop() { chatStore.stop(); if (conversationId.value) void chatApi.stopSession(conversationId.value).catch(() => undefined); syncStream() }
function retry(id: string | number) { const index = messages.value.findIndex((message) => String(message.id) === String(id)); const question = index > 0 ? messages.value[index - 1]?.content : ''; if (!question) return; messages.value.splice(Math.max(0, index - 1), 2); void sendQuestion(question) }
function openReference(reference: any) { const text = reference?.content || reference?.snippet || reference?.documentName || reference?.title || JSON.stringify(reference); ;(globalThis as { uni?: { showModal?: (options: { title: string; content: string; showCancel?: boolean }) => void } }).uni?.showModal?.({ title: reference?.documentName || '参考资料', content: String(text), showCancel: false }) }
function newConversation() { stop(); conversationId.value = createConversationId(); messages.value = []; snapshot.value = { status: 'idle', answer: '', thinking: [], references: [], error: null } }
onLoad((query) => { void authStore.guard(`/pages/chat/index${query?.conversationId ? `?conversationId=${encodeURIComponent(String(query.conversationId))}` : ''}`).then((ok) => { if (ok) { void loadDocuments(); if (query?.conversationId) void loadConversation(String(query.conversationId)) } }) })
onBeforeUnmount(stopPolling)
</script>
<template>
  <view class="page"><view class="header"><text class="title">对话</text><button class="new-button" @click="newConversation">新会话</button></view><view v-if="loading" class="loading">正在加载会话…</view><MessageList :messages="messages" :streaming="streaming" @retry="retry" @stop="stop" @open-reference="openReference" /><StreamStatus :status="snapshot.status" :error="snapshot.error" /><ChatComposer v-model="draft" v-model:mode="mode" v-model:selected-document-id="selectedDocumentId" :modes="modes" :knowledge-options="knowledgeOptions" :disabled="streaming" @submit="sendQuestion" /></view>
</template>
<style scoped>
.page { min-height: 100vh; padding: 28rpx 32rpx 260rpx; box-sizing: border-box; }
.header { display: flex; align-items: center; justify-content: space-between; }
.title { font-size: 40rpx; font-weight: 600; }
.new-button { width: auto; margin: 0; padding: 0 22rpx; color: var(--color-primary); background: transparent; border: 1rpx solid var(--color-primary); border-radius: 8rpx; font-size: 23rpx; }
.loading { padding: 30rpx 0; color: var(--color-text-muted); text-align: center; }
</style>
