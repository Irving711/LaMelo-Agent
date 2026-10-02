import { createAuthStore } from '../stores/auth'

describe('miniapp auth store', () => {
  const originalUni = (globalThis as any).uni

  afterEach(() => {
    ;(globalThis as any).uni = originalUni
    vi.restoreAllMocks()
  })

  it('persists login profile and derives admin from server roles', async () => {
    const setStorageSync = vi.fn()
    ;(globalThis as any).uni = { setStorageSync, removeStorageSync: vi.fn(), getStorageSync: vi.fn() }
    const api = { wechatLogin: vi.fn().mockResolvedValue({ token: 't', username: 'u', roles: ['USER'], needsBinding: false }), passwordLogin: vi.fn(), bindWechat: vi.fn(), unbindWechat: vi.fn() }
    const store = createAuthStore({ api })
    await store.loginWithWechat('code')
    expect(store.token).toBe('t')
    expect(store.isAdmin).toBe(false)
    expect(setStorageSync).toHaveBeenCalledWith('lamelo-miniapp-token', 't')
    expect(setStorageSync).toHaveBeenCalledWith('lamelo-miniapp-profile', expect.not.objectContaining({ token: expect.anything() }))
    expect(setStorageSync).toHaveBeenCalledWith('lamelo-miniapp-profile', expect.objectContaining({ username: 'u' }))
  })

  it('restores a stored session and logout removes all auth state', async () => {
    const removeStorageSync = vi.fn()
    ;(globalThis as any).uni = {
      getStorageSync: vi.fn((key: string) => key === 'lamelo-miniapp-token' ? 'stored' : { username: 'admin', roles: ['ADMIN'] }),
      removeStorageSync
    }
    const store = createAuthStore({ api: { wechatLogin: vi.fn(), passwordLogin: vi.fn(), bindWechat: vi.fn(), unbindWechat: vi.fn() } })
    expect(await store.restoreSession()).toBe(true)
    expect(store.isAdmin).toBe(true)
    store.logout()
    expect(store.token).toBeNull()
    expect(removeStorageSync).toHaveBeenCalledWith('lamelo-miniapp-token')
    expect(removeStorageSync).toHaveBeenCalledWith('lamelo-miniapp-profile')
  })

  it('preserves a requested route when redirecting to login', () => {
    const setStorageSync = vi.fn()
    const navigateTo = vi.fn()
    ;(globalThis as any).uni = { setStorageSync, navigateTo, getStorageSync: vi.fn() }
    const store = createAuthStore({ api: { wechatLogin: vi.fn(), passwordLogin: vi.fn(), bindWechat: vi.fn(), unbindWechat: vi.fn() } })
    store.redirectToLogin('/pages/chat/index?conversationId=c1')
    expect(setStorageSync).toHaveBeenCalledWith('lamelo-miniapp-login-route', '/pages/chat/index?conversationId=c1')
    expect(navigateTo).toHaveBeenCalledWith({ url: '/pages/auth/login' })
  })

  it('restores the requested route after login', async () => {
    const redirectTo = vi.fn()
    ;(globalThis as any).uni = {
      setStorageSync: vi.fn(), removeStorageSync: vi.fn(), getStorageSync: vi.fn((key: string) => key === 'lamelo-miniapp-login-route' ? '/pages/chat/index?conversationId=c1' : null), redirectTo
    }
    const store = createAuthStore({ api: { wechatLogin: vi.fn().mockResolvedValue({ token: 't', username: 'u', roles: [], needsBinding: false }), passwordLogin: vi.fn(), bindWechat: vi.fn(), unbindWechat: vi.fn() } })
    await store.loginWithWechat('code')
    expect(redirectTo).toHaveBeenCalledWith({ url: '/pages/chat/index?conversationId=c1' })
  })

  it('keeps the pending route while an unbound WeChat login has no token', async () => {
    const redirectTo = vi.fn()
    const getStorageSync = vi.fn((key: string) => key === 'lamelo-miniapp-login-route' ? '/pages/chat/index' : null)
    ;(globalThis as any).uni = { setStorageSync: vi.fn(), removeStorageSync: vi.fn(), getStorageSync, redirectTo }
    const store = createAuthStore({ api: { wechatLogin: vi.fn().mockResolvedValue({ token: null, username: 'u', roles: [], needsBinding: true }), passwordLogin: vi.fn().mockResolvedValue({ token: 't', username: 'u', roles: [], needsBinding: true }), bindWechat: vi.fn().mockResolvedValue(undefined), unbindWechat: vi.fn() } })
    await store.loginWithWechat('code')
    expect(store.token).toBeNull()
    expect(redirectTo).not.toHaveBeenCalled()
    await store.loginWithPassword('u', 'p')
    await store.bindWechat({ username: 'u', password: 'p', code: 'code' })
    expect(redirectTo).toHaveBeenCalledWith({ url: '/pages/chat/index' })
  })

  it('marks the profile unbound and persists it after a successful unbind', async () => {
    const setStorageSync = vi.fn()
    ;(globalThis as any).uni = {
      getStorageSync: vi.fn((key: string) => key === 'lamelo-miniapp-token' ? 'token' : undefined),
      setStorageSync,
      removeStorageSync: vi.fn()
    }
    const api = { wechatLogin: vi.fn(), passwordLogin: vi.fn(), bindWechat: vi.fn(), unbindWechat: vi.fn().mockResolvedValue(undefined) }
    const store = createAuthStore({ api })
    store.profile = { token: 'token', username: 'u', roles: ['USER'], needsBinding: false }
    await store.unbindWechat('password')
    expect(api.unbindWechat).toHaveBeenCalledWith('password', 'u')
    expect(store.profile?.needsBinding).toBe(true)
    expect(setStorageSync).toHaveBeenCalledWith('lamelo-miniapp-profile', expect.objectContaining({ needsBinding: true }))
  })
})
