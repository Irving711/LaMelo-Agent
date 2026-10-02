import { request, getApiBaseUrl } from './request'
import { ChatStreamClient, type ChatStreamHandlers, type ChatStreamOpenResult } from '../services/chatStream'
import type { ChatStreamPayload } from '../services/chatProtocol'
import { getToken } from '../utils/storage'

export interface SessionListQuery {
  keyword?: string
  chatMode?: string
  turnStatus?: string
  pageNo?: string | number
  pageSize?: string | number
}

const post = <T>(url: string, data: unknown = {}) => request<T>({ url, method: 'POST', data,
  header: { 'X-LaMelo-Client': 'miniapp' } })

export const chatApi = {
  listKnowledgeDocumentOptions: () => post('/api/chat/document/options'),
  listSessionsPage: (query: SessionListQuery = {}) => post('/api/chat/session/list', {
    keyword: String(query.keyword || '').trim(), chatMode: String(query.chatMode || 'ALL'), turnStatus: String(query.turnStatus || 'ALL'),
    pageNo: String(query.pageNo ?? '1'), pageSize: String(query.pageSize ?? '20')
  }),
  listSessions: (query: SessionListQuery = {}) => chatApi.listSessionsPage(query).then((data: any) => data?.sessions || []),
  getSession: (conversationId: string) => post('/api/chat/session/detail', { conversationId }),
  getExchangeDetail: (conversationId: string, exchangeId: string | number) => post('/api/chat/exchange/detail', { conversationId, exchangeId: String(exchangeId) }),
  deleteSession: (conversationId: string) => post('/api/chat/session/reset', { conversationId }),
  stopSession: (conversationId: string) => post('/api/chat/session/stop', { conversationId }),
  rebuildConversationSummary: (conversationId: string) => post('/api/chat/session/summary/rebuild', { conversationId }),
  getRetrievalResults: (conversationId: string, exchangeId: string | number) => post('/api/chat/exchange/retrieval/results', { conversationId, exchangeId: String(exchangeId) }),
  getChannelExecutions: (conversationId: string, exchangeId: string | number) => post('/api/chat/exchange/channel/executions', { conversationId, exchangeId: String(exchangeId) }),
  getStageBenchmarks: () => post('/api/chat/stage/benchmarks'),
  openStream(payload: ChatStreamPayload, handlers: ChatStreamHandlers = {}): ChatStreamOpenResult {
    return new ChatStreamClient({ baseUrl: getApiBaseUrl(), token: getToken() || undefined }).open(payload, handlers)
  }
}
