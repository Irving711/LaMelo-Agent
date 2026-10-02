import { ChatStreamClient } from '../services/chatStream'
import { CHAT_STREAM_PROTOCOL_FIXTURE } from '../services/chatProtocol'
import { createChatState } from '../services/chatState'
import { UnauthorizedError, ForbiddenError } from '../utils/errors'

function requestHarness() {
  let options: any
  let chunkHandler: ((result: { data: string | ArrayBuffer }) => void) | undefined
  let aborted = false
  const task = {
    onChunkReceived(handler: (result: { data: string | ArrayBuffer }) => void) { chunkHandler = handler },
    abort() { aborted = true }
  }
  return {
    request(next: any) { options = next; return task },
    emit(data: string | ArrayBuffer) { chunkHandler?.({ data }) },
    succeed() { options.success({ statusCode: 200 }) },
    fail(error: unknown) { options.fail(error) },
    get options() { return options },
    get aborted() { return aborted }
  }
}

describe('ChatStreamClient', () => {
  const originalUni = (globalThis as any).uni
  afterEach(() => { ;(globalThis as any).uni = originalUni; vi.restoreAllMocks() })
  it('normalizes incremental text, thinking, references, and completion', async () => {
    const harness = requestHarness(); const events: string[] = []; const state = createChatState(); state.start()
    const stream = new ChatStreamClient({ baseUrl: 'https://example.test', request: harness.request })
    const result = stream.open({ question: 'hi', chatMode: 'GENERAL' }, { onThinking: (e) => { events.push('thinking'); state.apply(e) }, onDelta: (e) => { events.push(e.content); state.apply(e) }, onReference: (e) => state.apply(e), onComplete: (e) => state.apply(e) })
    harness.emit(`data: ${JSON.stringify(CHAT_STREAM_PROTOCOL_FIXTURE[0])}\n\n`)
    harness.emit(`data: ${JSON.stringify(CHAT_STREAM_PROTOCOL_FIXTURE[1])}\n\n`)
    harness.emit(`data: ${JSON.stringify(CHAT_STREAM_PROTOCOL_FIXTURE[2])}\n\n`)
    harness.emit(`data: ${JSON.stringify(CHAT_STREAM_PROTOCOL_FIXTURE[3])}\n\n`)
    harness.succeed(); await result.done
    expect(events).toEqual(['thinking', '你好', '，世界。']); expect(state.snapshot()).toMatchObject({ status: 'completed', answer: '你好，世界。', references: [{ referenceId: 'ref-1' }] })
  })

  it('preserves the answer on malformed JSON and reports one error', async () => {
    const harness = requestHarness(); const errors: Error[] = []; const state = createChatState(); state.start()
    const result = new ChatStreamClient({ baseUrl: 'https://example.test', request: harness.request }).open({ question: 'hi', chatMode: 'GENERAL' }, { onDelta: (e) => state.apply(e), onError: (error) => { errors.push(error); state.apply({ type: 'error', error }) } })
    harness.emit(`data: {"type":"text","content":"ok"}\n\n`); harness.emit('data: {broken}\n\n'); harness.succeed(); await result.done
    expect(state.snapshot().answer).toBe('ok'); expect(errors).toHaveLength(1); expect(state.snapshot().status).toBe('error')
  })

  it('stops without losing accumulated text and close is idempotent', async () => {
    const harness = requestHarness(); const stopped: string[] = []; const state = createChatState(); state.start()
    const result = new ChatStreamClient({ baseUrl: 'https://example.test', request: harness.request }).open({ question: 'hi', chatMode: 'GENERAL' }, { onDelta: (e) => state.apply(e), onStopped: (e) => { stopped.push(e.content || ''); state.apply(e) } })
    harness.emit(`data: ${JSON.stringify({ type: 'text', content: 'partial' })}\n\n`); result.close(); result.close(); await result.done
    expect(harness.aborted).toBe(true); expect(stopped).toEqual(['用户停止']); expect(state.snapshot()).toMatchObject({ status: 'stopped', answer: 'partial' })
  })

  it('reports timeout while retaining received text', async () => {
    vi.useFakeTimers(); const harness = requestHarness(); const errors: Error[] = []; const state = createChatState(); state.start()
    const result = new ChatStreamClient({ baseUrl: 'https://example.test', timeoutMs: 50, request: harness.request }).open({ question: 'hi', chatMode: 'GENERAL' }, { onDelta: (e) => state.apply(e), onError: (error) => { errors.push(error); state.apply({ type: 'error', error }) } })
    harness.emit(`data: ${JSON.stringify({ type: 'text', content: 'partial' })}\n\n`); vi.advanceTimersByTime(50); await result.done
    expect(harness.aborted).toBe(true); expect(errors[0].message).toContain('超时'); expect(state.snapshot().answer).toBe('partial'); vi.useRealTimers()
  })

  it('decodes a UTF-8 character split across native chunks', async () => {
    const harness = requestHarness(); const answer: string[] = []
    const result = new ChatStreamClient({ baseUrl: 'https://example.test', request: harness.request }).open({ question: 'hi', chatMode: 'GENERAL' }, { onDelta: (event) => answer.push(event.content) })
    const bytes = new TextEncoder().encode(`data: ${JSON.stringify({ type: 'text', content: '中' })}\n\n`)
    const split = bytes.findIndex((value, index) => value === 0xe4 && bytes[index + 1] === 0xb8 && bytes[index + 2] === 0xad) + 1
    harness.emit(bytes.slice(0, split).buffer); harness.emit(bytes.slice(split).buffer); harness.succeed(); await result.done
    expect(answer.join('')).toBe('中')
  })

  it('ignores late content after completion, stop, or error', () => {
    const state = createChatState(); state.start()
    state.apply({ type: 'delta', content: 'answer', raw: { type: 'text', content: 'answer' } })
    state.apply({ type: 'complete', raw: { type: 'complete', content: null } })
    const completed = state.snapshot()
    state.apply({ type: 'delta', content: 'late', raw: { type: 'text', content: 'late' } })
    state.apply({ type: 'thinking', content: 'late thinking', raw: { type: 'thinking', content: 'late thinking' } })
    state.apply({ type: 'reference', content: [{ id: 'late' }], raw: { type: 'reference', content: [{ id: 'late' }] } })
    expect(state.snapshot()).toEqual(completed)

    state.start(); state.apply({ type: 'stopped', content: 'stop', raw: { type: 'stopped', content: 'stop' } }); const stopped = state.snapshot()
    state.apply({ type: 'delta', content: 'late', raw: { type: 'text', content: 'late' } }); expect(state.snapshot()).toEqual(stopped)

    state.start(); const error = new Error('failed'); state.apply({ type: 'error', error }); const failed = state.snapshot()
    state.apply({ type: 'reference', content: [{ id: 'late' }], raw: { type: 'reference', content: [{ id: 'late' }] } }); expect(state.snapshot()).toEqual(failed)
  })

  it('maps stream auth responses to typed errors and preserves the configured base URL', async () => {
    for (const statusCode of [401, 403]) {
      const harness = requestHarness(); const errors: Error[] = []; const redirectTo = vi.fn()
      ;(globalThis as any).uni = { redirectTo, removeStorageSync: vi.fn() }
      const result = new ChatStreamClient({ baseUrl: 'https://api.example.test/', token: 't', request: harness.request }).open({ question: 'hi', chatMode: 'GENERAL' }, { onError: (error) => errors.push(error) })
      expect(harness.options.url).toBe('https://api.example.test/api/chat/stream')
      harness.options.success({ statusCode, data: { code: statusCode, message: 'denied' } }); await result.done
      expect(errors[0]).toBeInstanceOf(statusCode === 401 ? UnauthorizedError : ForbiddenError)
      if (statusCode === 401) expect(redirectTo).toHaveBeenCalledWith({ url: '/pages/auth/login' })
    }
  })
})
