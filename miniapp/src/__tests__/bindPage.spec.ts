import { mount } from '@vue/test-utils'
import BindPage from '../pages/auth/bind.vue'
import { authStore } from '../stores/auth'

vi.mock('@dcloudio/uni-app', () => ({ onLoad: (callback: (query: Record<string, string>) => void) => callback({}) }))

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

  it('binds the current WeChat identity with account credentials', async () => {
    vi.spyOn(authStore, 'guard').mockResolvedValue(true)
    const bindExistingAccount = vi.spyOn(authStore, 'bindExistingAccount').mockResolvedValue({ token: 't', username: 'u', roles: [], needsBinding: false })
    ;(globalThis as any).uni = { showToast: vi.fn() }
    const wrapper = mount(BindPage)
    await wrapper.findAll('input')[0].setValue('u')
    await wrapper.findAll('input')[1].setValue('p')
    await wrapper.find('[data-testid="bind-submit"]').trigger('click')
    await new Promise((resolve) => setTimeout(resolve, 0))
    expect(bindExistingAccount).toHaveBeenCalledWith({ username: 'u', password: 'p' })
  })
})
