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
    const api = { wechatLogin: vi.fn().mockResolvedValue({ token: 't', username: 'u', roles: ['USER'], needsBinding: false }), passwordLogin: vi.fn(), bindExistingAccount: vi.fn(), setCredentials: vi.fn(), unbindWechat: vi.fn() }
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
    const store = createAuthStore({ api: { wechatLogin: vi.fn(), passwordLogin: vi.fn(), bindExistingAccount: vi.fn(), setCredentials: vi.fn(), unbindWechat: vi.fn() } })
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
    const store = createAuthStore({ api: { wechatLogin: vi.fn(), passwordLogin: vi.fn(), bindExistingAccount: vi.fn(), setCredentials: vi.fn(), unbindWechat: vi.fn() } })
    store.redirectToLogin('/pages/chat/index?conversationId=c1')
    expect(setStorageSync).toHaveBeenCalledWith('lamelo-miniapp-login-route', '/pages/chat/index?conversationId=c1')
    expect(navigateTo).toHaveBeenCalledWith({ url: '/pages/auth/login' })
  })

  it('restores a requested chat home route after login with reLaunch', async () => {
    const redirectTo = vi.fn()
    const reLaunch = vi.fn()
    ;(globalThis as any).uni = {
      setStorageSync: vi.fn(), removeStorageSync: vi.fn(), getStorageSync: vi.fn((key: string) => key === 'lamelo-miniapp-login-route' ? '/pages/chat/index?conversationId=c1' : null), redirectTo, reLaunch
    }
    const store = createAuthStore({ api: { wechatLogin: vi.fn().mockResolvedValue({ token: 't', username: 'u', roles: [], needsBinding: false }), passwordLogin: vi.fn(), bindExistingAccount: vi.fn(), setCredentials: vi.fn(), unbindWechat: vi.fn() } })
    await store.loginWithWechat('code')
    expect(reLaunch).toHaveBeenCalledWith({ url: '/pages/chat/index?conversationId=c1' })
    expect(redirectTo).not.toHaveBeenCalled()
  })

  it('restores a requested secondary route after login with redirectTo', async () => {
    const redirectTo = vi.fn()
    const reLaunch = vi.fn()
    ;(globalThis as any).uni = {
      setStorageSync: vi.fn(), removeStorageSync: vi.fn(), getStorageSync: vi.fn((key: string) => key === 'lamelo-miniapp-login-route' ? '/pages/admin/index' : null), redirectTo, reLaunch
    }
    const store = createAuthStore({ api: { wechatLogin: vi.fn().mockResolvedValue({ token: 't', username: 'u', roles: [], needsBinding: false }), passwordLogin: vi.fn(), bindExistingAccount: vi.fn(), setCredentials: vi.fn(), unbindWechat: vi.fn() } })
    await store.loginWithWechat('code')
    expect(redirectTo).toHaveBeenCalledWith({ url: '/pages/admin/index' })
    expect(reLaunch).not.toHaveBeenCalled()
  })

  it('enters the app after a wechat login when no route was requested', async () => {
    const redirectTo = vi.fn()
    const reLaunch = vi.fn()
    ;(globalThis as any).uni = {
      setStorageSync: vi.fn(), removeStorageSync: vi.fn(), getStorageSync: vi.fn(() => null), redirectTo, reLaunch
    }
    const store = createAuthStore({ api: { wechatLogin: vi.fn().mockResolvedValue({ token: 't', username: 'u', roles: [], needsBinding: false }), passwordLogin: vi.fn(), bindExistingAccount: vi.fn(), setCredentials: vi.fn(), unbindWechat: vi.fn() } })
    await store.loginWithWechat('code')
    expect(reLaunch).toHaveBeenCalledWith({ url: '/pages/chat/index' })
    expect(redirectTo).not.toHaveBeenCalled()
  })

  it('enters the app after a password login when no route was requested', async () => {
    const reLaunch = vi.fn()
    ;(globalThis as any).uni = {
      setStorageSync: vi.fn(), removeStorageSync: vi.fn(), getStorageSync: vi.fn(() => null), redirectTo: vi.fn(), reLaunch
    }
    const store = createAuthStore({ api: { wechatLogin: vi.fn(), passwordLogin: vi.fn().mockResolvedValue({ token: 't', username: 'admin', roles: ['ADMIN'], needsBinding: false }), bindExistingAccount: vi.fn(), setCredentials: vi.fn(), unbindWechat: vi.fn() } })
    await store.loginWithPassword('admin', 'admin123')
    expect(reLaunch).toHaveBeenCalledWith({ url: '/pages/chat/index' })
  })

  it('assigns the target account profile after binding an existing account', async () => {
    ;(globalThis as any).uni = { setStorageSync: vi.fn(), removeStorageSync: vi.fn(), getStorageSync: vi.fn(), redirectTo: vi.fn(), reLaunch: vi.fn() }
    const api = { wechatLogin: vi.fn(), passwordLogin: vi.fn(), bindExistingAccount: vi.fn().mockResolvedValue({ token: 'boss', username: 'boss', roles: ['ADMIN'], needsBinding: false }), setCredentials: vi.fn(), unbindWechat: vi.fn() }
    const store = createAuthStore({ api })
    const profile = await store.bindExistingAccount({ username: 'boss', password: 'p' })
    expect(profile.token).toBe('boss')
    expect(store.token).toBe('boss')
    expect(store.isAdmin).toBe(true)
  })

  it('assigns the updated profile after setting credentials', async () => {
    ;(globalThis as any).uni = { setStorageSync: vi.fn(), removeStorageSync: vi.fn(), getStorageSync: vi.fn() }
    const api = { wechatLogin: vi.fn(), passwordLogin: vi.fn(), bindExistingAccount: vi.fn(), setCredentials: vi.fn().mockResolvedValue({ token: 't2', username: 'newuser', roles: [], needsBinding: false }), unbindWechat: vi.fn() }
    const store = createAuthStore({ api })
    await store.setCredentials({ username: 'newuser', password: 'p' })
    expect(store.profile?.needsBinding).toBe(false)
    expect(store.profile?.username).toBe('newuser')
  })

  it('marks the profile unbound and persists it after a successful unbind', async () => {
    const setStorageSync = vi.fn()
    ;(globalThis as any).uni = {
      getStorageSync: vi.fn((key: string) => key === 'lamelo-miniapp-token' ? 'token' : undefined),
      setStorageSync,
      removeStorageSync: vi.fn()
    }
    const api = { wechatLogin: vi.fn(), passwordLogin: vi.fn(), bindExistingAccount: vi.fn(), setCredentials: vi.fn(), unbindWechat: vi.fn().mockResolvedValue(undefined) }
    const store = createAuthStore({ api })
    store.profile = { token: 'token', username: 'u', roles: ['USER'], needsBinding: false }
    await store.unbindWechat('password')
    expect(api.unbindWechat).toHaveBeenCalledWith('password')
    expect(store.profile?.needsBinding).toBe(true)
    expect(setStorageSync).toHaveBeenCalledWith('lamelo-miniapp-profile', expect.objectContaining({ needsBinding: true }))
  })
})
