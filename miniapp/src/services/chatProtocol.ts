export type StreamEventType =
  | 'thinking'
  | 'status'
  | 'text'
  | 'delta'
  | 'reference'
  | 'recommend'
  | 'complete'
  | 'error'
  | 'stopped'

export interface ChatStreamPayload {
  question: string
  conversationId?: string
  chatMode: string
  selectedDocumentId?: string
}

export interface ChatStreamEvent {
  type: StreamEventType | string
  content?: unknown
  timestamp?: string
  conversationId?: string
  exchangeId?: number | string
  count?: number
  [key: string]: unknown
}

/** A representative backend event sequence used by client tests and adapter verification. */
export const CHAT_STREAM_PROTOCOL_FIXTURE: ChatStreamEvent[] = [
  { type: 'thinking', content: '正在分析问题上下文。' },
  { type: 'text', content: '你好' },
  { type: 'text', content: '，世界。' },
  { type: 'reference', content: [{ referenceId: 'ref-1', documentName: '示例文档' }], count: 1 },
  { type: 'complete', content: null }
]

export type NormalizedChatEvent =
  | { type: 'thinking'; content: string; raw: ChatStreamEvent }
  | { type: 'delta'; content: string; raw: ChatStreamEvent }
  | { type: 'reference'; content: unknown[]; raw: ChatStreamEvent }
  | { type: 'complete'; raw: ChatStreamEvent }
  | { type: 'error'; error: Error; raw?: ChatStreamEvent }
  | { type: 'stopped'; content?: string; raw: ChatStreamEvent }

function asText(value: unknown): string {
  if (value == null) return ''
  return typeof value === 'string' ? value : JSON.stringify(value)
}

export function normalizeChatEvent(event: ChatStreamEvent): NormalizedChatEvent | null {
  const type = String(event?.type || '').trim().toLowerCase()
  if (!type) return null

  if (type === 'thinking' || type === 'status') {
    const content = asText(event.content)
    if (type === 'status' && (/^\s*(?:⏹|stopped?\b|stop\b)/i.test(content) || /已停止|停止/.test(content))) {
      return { type: 'stopped', content, raw: event }
    }
    return { type: 'thinking', content, raw: event }
  }
  if (type === 'text' || type === 'delta') return { type: 'delta', content: asText(event.content), raw: event }
  if (type === 'reference') {
    const content = Array.isArray(event.content) ? event.content : event.content == null ? [] : [event.content]
    return { type: 'reference', content, raw: event }
  }
  if (type === 'complete') return { type: 'complete', raw: event }
  if (type === 'stopped') return { type: 'stopped', content: asText(event.content), raw: event }
  if (type === 'error') return { type: 'error', error: new Error(asText(event.content) || '流式请求失败'), raw: event }
  return null
}

export function parseSseBlock(block: string): string[] {
  const lines = block.trim().split(/\r?\n/).filter(Boolean)
  if (!lines.length) return []
  const dataLines = lines.filter((line) => line.startsWith('data:'))
  if (dataLines.length) return [dataLines.map((line) => line.slice(5).replace(/^ /, '')).join('\n')]
  return lines.filter((line) => !line.startsWith(':'))
}

