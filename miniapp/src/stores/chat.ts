import { chatApi } from '../api/chat'
import { createChatState, type ChatStateSnapshot } from '../services/chatState'
import type { ChatStreamHandlers } from '../services/chatStream'
import type { ChatStreamPayload, NormalizedChatEvent } from '../services/chatProtocol'

export interface ChatStore {
  state: ChatStateSnapshot
  send(payload: ChatStreamPayload): Promise<void>
  stop(): void
}

export function createChatStore(options: { openStream?: typeof chatApi.openStream } = {}): ChatStore {
  const reducer = createChatState()
  let active: ReturnType<typeof chatApi.openStream> | null = null
  const openStream = options.openStream || chatApi.openStream
  const store = {
    state: reducer.snapshot(),
    async send(payload: ChatStreamPayload) {
      reducer.start()
      store.state = reducer.snapshot()
      const apply = (event: NormalizedChatEvent) => { store.state = reducer.apply(event) }
      const handlers: ChatStreamHandlers = {
        onThinking: apply,
        onDelta: apply,
        onReference: apply,
        onComplete: apply,
        onStopped: apply,
        onError: (error) => apply({ type: 'error', error })
      }
      active = openStream(payload, handlers)
      await active.done
      active = null
    },
    stop() { active?.close() }
  }
  return store
}

export const chatStore = createChatStore()
export const useChatStore = createChatStore
