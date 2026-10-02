import { request } from './request'
import { getToken } from '../utils/storage'
import { authFailure } from './request'
import { ApiCodeError, ApiError, NetworkError } from '../utils/errors'

const post = <T>(url: string, data: unknown = {}) => request<T>({ url, method: 'POST', data })

export const manageApi = {
  listSessionsPage: (query: { pageNo?: number; pageSize?: number } = {}) => post('/manage/chat/session/list', query),
  getSession: (conversationId: string) => post('/manage/chat/session/detail', { conversationId }),
  getExchangeDetail: (conversationId: string, exchangeId: string | number) => post('/manage/chat/exchange/detail', { conversationId, exchangeId: String(exchangeId) }),
  getRetrievalResults: (conversationId: string, exchangeId: string | number) => post('/manage/chat/exchange/retrieval/results', { conversationId, exchangeId: String(exchangeId) }),
  getChannelExecutions: (conversationId: string, exchangeId: string | number) => post('/manage/chat/exchange/channel/executions', { conversationId, exchangeId: String(exchangeId) }),
  getStageBenchmarks: () => post('/manage/chat/stage/benchmarks'),
  uploadDocument: (payload: { filePath: string; documentName?: string; operatorId?: string | number; knowledgeScopeCode?: string; knowledgeScopeName?: string; businessCategory?: string; documentTags?: string }, onProgress?: (percent: number) => void) => new Promise((resolve, reject) => {
    const uniApi = (globalThis as { uni?: { uploadFile?: (options: any) => { abort?: () => void; onProgressUpdate?: (handler: (event: { progress: number }) => void) => void } } }).uni
    if (!uniApi?.uploadFile) { reject(new Error('uni.uploadFile 不可用')); return }
    const base = (import.meta as ImportMeta & { env?: Record<string, string> }).env?.VITE_LAMELO_AGENT_API_BASE_URL || ''
    const meta = JSON.stringify({ documentName: payload.documentName || '', operatorId: payload.operatorId ?? '', knowledgeScopeCode: payload.knowledgeScopeCode || '', knowledgeScopeName: payload.knowledgeScopeName || '', businessCategory: payload.businessCategory || '', documentTags: payload.documentTags || '' })
    try {
      const task = uniApi.uploadFile!({
        url: `${base.replace(/\/$/, '')}/manage/document/upload`,
        filePath: payload.filePath,
        name: 'file',
        formData: { meta },
        header: getToken() ? { Authorization: `Bearer ${getToken()}` } : {},
        success: (response: { statusCode: number; data: string }) => {
          try {
            const body = JSON.parse(response.data || '{}') as { code?: number | string; message?: string; data?: unknown }
            if (response.statusCode === 401 || response.statusCode === 403 || Number(body.code) === 401 || Number(body.code) === 403) throw authFailure(Number(body.code || response.statusCode), body.message, body)
            if (response.statusCode < 200 || response.statusCode >= 300) throw new ApiCodeError(`请求失败，状态码 ${response.statusCode}`, response.statusCode, body)
            if (String(body.code) !== '0') throw new ApiCodeError(body.message || '上传失败', body.code ?? 'unknown', body)
            resolve(body.data)
          } catch (error) {
            if (response.statusCode === 401 || response.statusCode === 403) reject(authFailure(response.statusCode, undefined, error))
            else reject(error instanceof ApiError ? error : new NetworkError('无法解析上传响应', error))
          }
        },
        fail: (error: unknown) => reject(new NetworkError(String((error as any)?.errMsg || '上传失败'), error))
      })
      task?.onProgressUpdate?.((event) => onProgress?.(Number(event.progress) || 0))
    } catch (error) {
      reject(new NetworkError('上传请求创建失败', error))
    }
  }),
  queryDocumentPage: (payload: { pageNo?: number; pageSize?: number; keyword?: string } = {}) => post('/manage/document/page/query', payload),
  queryDocumentDetail: (documentId: string | number) => post('/manage/document/detail/query', { documentId }),
  deleteDocument: (payload: unknown) => post('/manage/document/delete', payload),
  queryStrategyPlan: (documentId: string | number) => post('/manage/document/strategy/plan/query', { documentId }),
  confirmStrategy: (payload: unknown) => post('/manage/document/strategy/confirm', payload),
  buildIndex: (payload: unknown) => post('/manage/document/index/build', payload),
  queryDocumentChunks: (payload: { documentId: string | number; taskId?: string | number; pageNo?: number; pageSize?: number }) => post('/manage/document/chunk/query', payload),
  queryDocumentChunkDetail: (payload: unknown) => post('/manage/document/chunk/detail/query', payload),
  queryTaskLogs: (payload: { taskId: string | number; pageNo?: number; pageSize?: number }) => post('/manage/document/task/log/query', payload),
  saveKnowledgeScope: (payload: unknown) => post('/manage/knowledge/scope/save', payload),
  deleteKnowledgeScope: (payload: unknown) => post('/manage/knowledge/scope/delete', payload),
  listKnowledgeScopes: () => post('/manage/knowledge/scope/list'),
  saveKnowledgeTopic: (payload: unknown) => post('/manage/knowledge/topic/save', payload),
  deleteKnowledgeTopic: (payload: unknown) => post('/manage/knowledge/topic/delete', payload),
  listKnowledgeTopics: (payload: { pageNo?: string | number; pageSize?: string | number; [key: string]: unknown } = {}) => post('/manage/knowledge/topic/list', payload),
  queryDocumentProfile: (payload: unknown) => post('/manage/knowledge/document/profile/detail', payload),
  regenerateDocumentProfile: (payload: unknown) => post('/manage/knowledge/document/profile/regenerate', payload),
  batchRegenerateDocumentProfiles: (payload: unknown) => post('/manage/knowledge/document/profile/batch/regenerate', payload),
  listTopicDocuments: (payload: { pageNo?: string | number; pageSize?: string | number; [key: string]: unknown } = {}) => post('/manage/knowledge/topic/document/list', payload),
  saveTopicDocumentRelation: (payload: unknown) => post('/manage/knowledge/topic/document/save', payload),
  removeTopicDocumentRelation: (payload: unknown) => post('/manage/knowledge/topic/document/remove', payload),
  queryKnowledgeRouteTracePage: (payload: { pageNo?: string | number; pageSize?: string | number; [key: string]: unknown } = {}) => post('/manage/knowledge/route/trace/page/query', payload)
}
