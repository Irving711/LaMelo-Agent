import { mount } from '@vue/test-utils'
import ProfilePage from '../pages/profile/index.vue'
import { authStore } from '../stores/auth'

vi.mock('@dcloudio/uni-app', () => ({ onLoad: (callback: () => void) => callback() }))

describe('profile binding state', () => {
  const originalUni = (globalThis as any).uni

  afterEach(() => {
    ;(globalThis as any).uni = originalUni
    vi.restoreAllMocks()
  })

  it('updates the visible binding status after unbind succeeds', async () => {
    ;(globalThis as any).uni = {
      getStorageSync: vi.fn((key: string) => key === 'lamelo-miniapp-token' ? 'token' : { username: 'u', roles: ['USER'], needsBinding: false }),
      setStorageSync: vi.fn(),
      removeStorageSync: vi.fn(),
      showToast: vi.fn()
    }
    vi.spyOn(authStore, 'unbindWechat').mockImplementation(async () => {
      authStore.profile = { ...(authStore.profile || { token: 'token', username: 'u', roles: ['USER'] }), needsBinding: true }
    })
    const wrapper = mount(ProfilePage)
    await Promise.resolve()
    await Promise.resolve()
    expect(wrapper.text()).toContain('微信已绑定')
    await wrapper.find('input').setValue('password')
    await wrapper.find('button').trigger('click')
    await new Promise((resolve) => setTimeout(resolve, 0))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('未绑定微信')
  })
})
