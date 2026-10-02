import { mount } from '@vue/test-utils'
import BindPage from '../pages/auth/bind.vue'
import { authStore } from '../stores/auth'

vi.mock('@dcloudio/uni-app', () => ({ onLoad: (callback: (query: Record<string, string>) => void) => callback({ code: 'already-used' }) }))

describe('bind page access guard', () => {
  const originalUni = (globalThis as any).uni

  afterEach(() => {
    ;(globalThis as any).uni = originalUni
    vi.restoreAllMocks()
  })

  it('redirects directly opened bind pages to login when there is no session', async () => {
    const navigateTo = vi.fn()
    ;(globalThis as any).uni = { getStorageSync: vi.fn(), setStorageSync: vi.fn(), navigateTo }
    mount(BindPage)
    await Promise.resolve()
    expect(navigateTo).toHaveBeenCalledWith({ url: '/pages/auth/login' })
  })

  it('gets a fresh WeChat code when binding after login', async () => {
    vi.spyOn(authStore, 'guard').mockResolvedValue(true)
    const bindWechat = vi.spyOn(authStore, 'bindWechat').mockResolvedValue()
    const login = vi.fn(({ success }) => success({ code: 'fresh-code' }))
    ;(globalThis as any).uni = { login, showToast: vi.fn() }
    const wrapper = mount(BindPage)
    await wrapper.findAll('input')[0].setValue('u')
    await wrapper.findAll('input')[1].setValue('p')
    await wrapper.find('[data-testid="bind-submit"]').trigger('click')
    await new Promise((resolve) => setTimeout(resolve, 0))
    expect(login).toHaveBeenCalledTimes(1)
    expect(bindWechat).toHaveBeenCalledWith({ username: 'u', password: 'p', code: 'fresh-code' })
  })
})
