import {
  normalizeChatEvent,
  parseSseBlock,
  type ChatStreamEvent,
  type ChatStreamPayload,
  type NormalizedChatEvent
} from './chatProtocol'
import { authFailure, buildApiUrl } from '../api/request'
import { NetworkError } from '../utils/errors'

export interface ChatStreamHandlers {
  onThinking?: (event: Extract<NormalizedChatEvent, { type: 'thinking' }>) => void
  onDelta?: (event: Extract<NormalizedChatEvent, { type: 'delta' }>) => void
  onReference?: (event: Extract<NormalizedChatEvent, { type: 'reference' }>) => void
  onComplete?: (event: Extract<NormalizedChatEvent, { type: 'complete' }>) => void
  onError?: (error: Error, event?: Extract<NormalizedChatEvent, { type: 'error' }>) => void
  onStopped?: (event: Extract<NormalizedChatEvent, { type: 'stopped' }>) => void
}

export interface ChatStreamOpenResult {
  close(): void
  done: Promise<void>
}

interface RequestTask {
  abort?: () => void
  onChunkReceived?: (handler: (result: { data: string | ArrayBuffer }) => void) => void
}

interface UniRequestOptions {
  url: string
  method: 'POST'
  data: ChatStreamPayload
  header: Record<string, string>
  responseType: 'arraybuffer'
  enableChunked: boolean
  timeout: number
  success?: (response: { statusCode: number; data?: unknown }) => void
  fail?: (error: unknown) => void
}

type RequestFactory = (options: UniRequestOptions) => RequestTask

const defaultRequest: RequestFactory = (options) => {
  const uniApi = (globalThis as { uni?: { request: RequestFactory } }).uni
  if (!uniApi?.request) throw new Error('uni.request 不可用')
  return uniApi.request(options)
}

function decodeChunk(data: string | ArrayBuffer, decoder: TextDecoder | null, stream = true): string {
  if (typeof data === 'string') return data
  if (decoder) return decoder.decode(data, { stream })
  return Array.from(new Uint8Array(data), (value) => String.fromCharCode(value)).join('')
}

function eventError(value: unknown): Error {
  if (value instanceof Error) return value
  if (typeof value === 'string') return new NetworkError(value)
  const message = (value as { errMsg?: string })?.errMsg
  return new NetworkError(message || '流式请求失败', value)
}

function responseError(statusCode: number, data: unknown): Error | undefined {
  const envelope = data && typeof data === 'object' ? data as { code?: number | string; message?: string } : undefined
  const code = envelope?.code == null ? statusCode : Number(envelope.code)
  if (code === 401 || code === 403) return authFailure(code, envelope?.message, data)
  if (statusCode < 200 || statusCode >= 300) return new NetworkError(`流式请求失败，状态码 ${statusCode}`, data)
  return undefined
}

export class ChatStreamClient {
  private readonly baseUrl: string
  private readonly token?: string
  private readonly timeoutMs: number
  private readonly request: RequestFactory

  constructor(options: { baseUrl: string; token?: string; timeoutMs?: number; request?: RequestFactory }) {
    this.baseUrl = options.baseUrl.replace(/\/$/, '')
    this.token = options.token
    this.timeoutMs = options.timeoutMs ?? 60_000
    this.request = options.request ?? defaultRequest
  }

  open(payload: ChatStreamPayload, handlers: ChatStreamHandlers = {}): ChatStreamOpenResult {
    let task: RequestTask | undefined
    let buffer = ''
    let terminal = false
    let settled = false
    let sawStopped = false
    const decoder = typeof TextDecoder !== 'undefined' ? new TextDecoder('utf-8') : null
    let resolveDone!: () => void
    const done = new Promise<void>((resolve) => { resolveDone = resolve })

    const emit = (event: NormalizedChatEvent) => {
      if (terminal && event.type !== 'error') return
      if (event.type === 'thinking') handlers.onThinking?.(event)
      if (event.type === 'delta') handlers.onDelta?.(event)
      if (event.type === 'reference') handlers.onReference?.(event)
      if (event.type === 'complete') handlers.onComplete?.(event)
      if (event.type === 'stopped') { sawStopped = true; handlers.onStopped?.(event) }
      if (event.type === 'error') handlers.onError?.(event.error, event)
      if (event.type === 'complete' || event.type === 'error' || event.type === 'stopped') terminal = true
    }

    const consumePayload = (raw: string) => {
      const payload = raw.trim()
      if (!payload || payload === '[DONE]') return
      let parsed: ChatStreamEvent
      try { parsed = JSON.parse(payload) as ChatStreamEvent }
      catch { emit({ type: 'error', error: new Error(`无法解析后端流式事件: ${payload}`) }); return }
      const normalized = normalizeChatEvent(parsed)
      if (normalized) emit(normalized)
    }

    const consumeChunk = (chunk: string, flush = false) => {
      buffer += chunk
      let boundary = buffer.search(/\r?\n\r?\n/)
      while (boundary >= 0) {
        const separator = buffer.slice(boundary).match(/^\r?\n\r?\n/)?.[0] || '\n\n'
        const block = buffer.slice(0, boundary)
        buffer = buffer.slice(boundary + separator.length)
        parseSseBlock(block).forEach(consumePayload)
        boundary = buffer.search(/\r?\n\r?\n/)
      }
      if (flush && buffer.trim()) {
        parseSseBlock(buffer).forEach(consumePayload)
        buffer = ''
      }
    }

    const finish = (failure?: Error) => {
      if (settled) return
      const tail = decoder?.decode(new Uint8Array(), { stream: false }) || ''
      consumeChunk(tail, true)
      if (failure && !terminal) emit({ type: 'error', error: failure })
      else if (!sawStopped) emit({ type: 'complete', raw: { type: 'complete', content: null } })
      settled = true
      resolveDone()
    }

    const timer = setTimeout(() => { task?.abort?.(); finish(new Error('流式请求超时')) }, this.timeoutMs)
    try {
      task = this.request({
        url: this.baseUrl ? `${this.baseUrl}/api/chat/stream` : buildApiUrl('/api/chat/stream'), method: 'POST', data: payload,
        header: { 'Content-Type': 'application/json', Accept: 'text/event-stream', 'X-LaMelo-Client': 'miniapp', ...(this.token ? { Authorization: `Bearer ${this.token}` } : {}) },
        responseType: 'arraybuffer', enableChunked: true, timeout: this.timeoutMs,
        success: (response) => {
          clearTimeout(timer)
          const responseFailure = responseError(response.statusCode, response.data)
          if (responseFailure) finish(responseFailure)
          else if (response.statusCode >= 200 && response.statusCode < 300) {
            if (typeof response.data === 'string' || response.data instanceof ArrayBuffer) consumeChunk(decodeChunk(response.data, decoder), true)
            finish()
          } else finish(new Error(`流式请求失败，状态码 ${response.statusCode}`))
        },
        fail: (error) => { clearTimeout(timer); finish(eventError(error)) }
      })
      task.onChunkReceived?.(({ data }) => consumeChunk(decodeChunk(data, decoder)))
    } catch (error) { clearTimeout(timer); finish(eventError(error)) }

    return {
      close: () => { if (settled) return; clearTimeout(timer); task?.abort?.(); emit({ type: 'stopped', content: '用户停止', raw: { type: 'stopped', content: '用户停止' } }); settled = true; resolveDone() },
      done
    }
  }
}
