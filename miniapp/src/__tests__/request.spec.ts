import { request } from '../api/request'
import { ApiCodeError, ForbiddenError, NetworkError, UnauthorizedError } from '../utils/errors'

describe('miniapp request wrapper', () => {
  const originalUni = (globalThis as any).uni

  afterEach(() => {
    ;(globalThis as any).uni = originalUni
    vi.restoreAllMocks()
  })

  it('injects the stored token and unwraps a successful envelope', async () => {
    const requestMock = vi.fn((options: any) => {
      expect(options.header.Authorization).toBe('Bearer token-1')
      options.success({ statusCode: 200, data: { code: 0, data: { ok: true } } })
      return { abort: vi.fn() }
    })
    ;(globalThis as any).uni = {
      getStorageSync: vi.fn((key: string) => key === 'lamelo-miniapp-token' ? 'token-1' : undefined),
      request: requestMock
    }

    await expect(request<{ ok: boolean }>({ url: '/demo', request: requestMock })).resolves.toEqual({ ok: true })
  })

  it('maps HTTP auth failures and API code failures to typed errors', async () => {
    const responses = [401, 403]
    for (const statusCode of responses) {
      const requestMock = vi.fn((options: any) => {
        options.success({ statusCode, data: { code: statusCode, message: 'denied' } })
        return { abort: vi.fn() }
      })
      const redirectTo = vi.fn()
      ;(globalThis as any).uni = { getStorageSync: vi.fn(), request: requestMock, redirectTo }
      await expect(request({ url: '/demo', request: requestMock })).rejects.toBeInstanceOf(statusCode === 401 ? UnauthorizedError : ForbiddenError)
      if (statusCode === 401) expect(redirectTo).toHaveBeenCalledWith({ url: '/pages/auth/login' })
    }

    const requestMock = vi.fn((options: any) => {
      options.success({ statusCode: 200, data: { code: 1001, message: 'bad request' } })
      return { abort: vi.fn() }
    })
    ;(globalThis as any).uni = { getStorageSync: vi.fn(), request: requestMock }
    await expect(request({ url: '/demo', request: requestMock })).rejects.toBeInstanceOf(ApiCodeError)
  })

  it('maps successful HTTP responses with 401/403 envelopes to auth errors', async () => {
    for (const code of [401, 403]) {
      const redirectTo = vi.fn()
      const requestMock = vi.fn((options: any) => {
        options.success({ statusCode: 200, data: { code, message: 'envelope denied' } })
        return { abort: vi.fn() }
      })
      ;(globalThis as any).uni = { getStorageSync: vi.fn(), removeStorageSync: vi.fn(), redirectTo, request: requestMock }
      const error = await request({ url: '/demo', request: requestMock }).catch((value) => value)
      expect(error).toBeInstanceOf(code === 401 ? UnauthorizedError : ForbiddenError)
      if (code === 401) expect(redirectTo).toHaveBeenCalledWith({ url: '/pages/auth/login' })
    }
  })

  it('does not redirect when an auth failure already occurs on the login page', async () => {
    const redirectTo = vi.fn()
    const requestMock = vi.fn((options: any) => { options.success({ statusCode: 401, data: { code: 401, message: 'bad credentials' } }); return {} })
    ;(globalThis as any).uni = { getStorageSync: vi.fn(), removeStorageSync: vi.fn(), redirectTo, request: requestMock }
    ;(globalThis as any).getCurrentPages = vi.fn(() => [{ route: 'pages/auth/login' }])
    await expect(request({ url: '/demo', request: requestMock })).rejects.toBeInstanceOf(UnauthorizedError)
    expect(redirectTo).not.toHaveBeenCalled()
  })

  it('maps transport failures and deduplicates concurrent calls by key', async () => {
    let resolveRequest!: (value: unknown) => void
    const pending = new Promise((resolve) => { resolveRequest = resolve })
    const requestMock = vi.fn((options: any) => {
      pending.then(() => options.success({ statusCode: 200, data: { code: 0, data: 'ok' } }))
      return { abort: vi.fn() }
    })
    ;(globalThis as any).uni = { getStorageSync: vi.fn(), request: requestMock }
    const first = request({ url: '/refresh', dedupeKey: 'login-refresh', request: requestMock })
    const second = request({ url: '/refresh', dedupeKey: 'login-refresh', request: requestMock })
    expect(requestMock).toHaveBeenCalledTimes(1)
    resolveRequest(undefined)
    await expect(Promise.all([first, second])).resolves.toEqual(['ok', 'ok'])

    const failed = vi.fn((options: any) => { options.fail({ errMsg: 'timeout' }); return { abort: vi.fn() } })
    ;(globalThis as any).uni = { getStorageSync: vi.fn(), request: failed }
    await expect(request({ url: '/network', request: failed })).rejects.toBeInstanceOf(NetworkError)
  })
})
