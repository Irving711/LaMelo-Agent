import type { NormalizedChatEvent } from './chatProtocol'

export type ChatStateStatus = 'idle' | 'streaming' | 'completed' | 'error' | 'stopped'

export interface ChatStateSnapshot {
  status: ChatStateStatus
  answer: string
  thinking: string[]
  references: unknown[]
  recommendations: string[]
  error: Error | null
}

export class ChatState {
  private current: ChatStateSnapshot = {
    status: 'idle',
    answer: '',
    thinking: [],
    references: [],
    recommendations: [],
    error: null
  }

  start(): ChatStateSnapshot {
    this.current = { status: 'streaming', answer: '', thinking: [], references: [], recommendations: [], error: null }
    return this.snapshot()
  }

  apply(event: NormalizedChatEvent): ChatStateSnapshot {
    if (this.current.status === 'completed' || this.current.status === 'stopped' || this.current.status === 'error') {
      if (event.type !== 'error') return this.snapshot()
    }
    if (event.type === 'delta') this.current.answer += event.content
    if (event.type === 'thinking' && event.content) this.current.thinking.push(event.content)
    if (event.type === 'reference') this.current.references.push(...event.content)
    if (event.type === 'recommend') this.current.recommendations = [...event.content]
    if (event.type === 'error') {
      if (this.current.status !== 'completed' && this.current.status !== 'stopped') {
        this.current.status = 'error'
        this.current.error = event.error
      }
    }
    if (event.type === 'complete' && this.current.status === 'streaming') this.current.status = 'completed'
    if (event.type === 'stopped' && this.current.status === 'streaming') this.current.status = 'stopped'
    return this.snapshot()
  }

  snapshot(): ChatStateSnapshot {
    return { ...this.current, thinking: [...this.current.thinking], references: [...this.current.references], recommendations: [...this.current.recommendations] }
  }
}

export function createChatState(): ChatState {
  return new ChatState()
}
