import { getToken, clearAuthStorage, setLoginRoute } from '../utils/storage'
import { ApiCodeError, ForbiddenError, NetworkError, UnauthorizedError } from '../utils/errors'

export type UniRequestResponse<T = unknown> = { statusCode: number; data?: T; header?: Record<string, string> }
export type UniRequestOptions = {
  url: string
  method?: string
  data?: unknown
  header?: Record<string, string>
  timeout?: number
  success?: (response: UniRequestResponse) => void
  fail?: (error: unknown) => void
  complete?: () => void
  [key: string]: unknown
}
export type RequestExecutor = (options: UniRequestOptions) => { abort?: () => void } | void

const pending = new Map<string, Promise<unknown>>()

function defaultExecutor(options: UniRequestOptions) {
  const uniApi = (globalThis as { uni?: { request?: RequestExecutor } }).uni
  if (!uniApi?.request) throw new NetworkError('uni.request 不可用')
  return uniApi.request(options)
}

export function getApiBaseUrl(): string {
  return (import.meta as ImportMeta & { env?: Record<string, string> }).env?.VITE_LAMELO_AGENT_API_BASE_URL || ''
}

export function buildApiUrl(url: string): string {
  const base = getApiBaseUrl()
  if (!base || /^https?:\/\//i.test(url)) return url
  return `${base.replace(/\/$/, '')}/${url.replace(/^\//, '')}`
}

export function authFailure(status: number, message?: string, payload?: unknown): UnauthorizedError | ForbiddenError {
  if (status === 401) {
    clearAuthStorage()
    const pages = (globalThis as { getCurrentPages?: () => Array<{ route?: string; options?: Record<string, string> }> }).getCurrentPages?.() || []
    const current = pages[pages.length - 1]
    if (current?.route && current.route !== 'pages/auth/login') {
      const query = Object.entries(current.options || {}).map(([key, value]) => `${key}=${encodeURIComponent(value)}`).join('&')
      setLoginRoute(`/${current.route}${query ? `?${query}` : ''}`)
    }
    if (current?.route !== 'pages/auth/login') {
      ;(globalThis as { uni?: { redirectTo?: (options: { url: string }) => void } }).uni?.redirectTo?.({ url: '/pages/auth/login' })
    }
    return new UnauthorizedError(message, payload)
  }
  return new ForbiddenError(message, payload)
}

function unwrap<T>(response: UniRequestResponse): T {
  const payload = response.data as { code?: number | string; message?: string; data?: T } | T | undefined
  if (response.statusCode === 401) {
    throw authFailure(401, typeof payload === 'object' && payload ? String((payload as any).message || '') : undefined, payload)
  }
  if (response.statusCode === 403) {
    throw authFailure(403, typeof payload === 'object' && payload ? String((payload as any).message || '') : undefined, payload)
  }
  if (response.statusCode < 200 || response.statusCode >= 300) {
    throw new ApiCodeError(`请求失败，状态码 ${response.statusCode}`, response.statusCode, payload)
  }
  if (!payload || typeof payload !== 'object' || !('code' in payload)) return payload as T
  const code = (payload as any).code
  if (String(code) === '401' || String(code) === '403') {
    throw authFailure(Number(code), (payload as any).message, payload)
  }
  if (String(code) !== '0') throw new ApiCodeError((payload as any).message || '请求失败', code, payload)
  return (payload as any).data as T
}

export function request<T = unknown>(options: UniRequestOptions & { request?: RequestExecutor; dedupeKey?: string }): Promise<T> {
  const key = options.dedupeKey
  if (key && pending.has(key)) return pending.get(key) as Promise<T>
  const executor = options.request || defaultExecutor
  const promise = new Promise<T>((resolve, reject) => {
    const token = getToken()
    const header: Record<string, string> = { 'Content-Type': 'application/json', ...(options.header || {}) }
    if (token && !header.Authorization) header.Authorization = `Bearer ${token}`
    try {
      executor({ ...options, url: buildApiUrl(options.url), header, success: (response) => {
        try { resolve(unwrap<T>(response)) } catch (error) { reject(error) }
      }, fail: (error) => {
        const message = typeof error === 'string' ? error : String((error as any)?.errMsg || '网络请求失败')
        reject(new NetworkError(message, error))
      } })
    } catch (error) {
      reject(error instanceof NetworkError ? error : new NetworkError(String((error as Error)?.message || error), error))
    }
  })
  if (key) {
    pending.set(key, promise)
    promise.finally(() => pending.delete(key)).catch(() => undefined)
  }
  return promise
}
