import { flushPromises, mount } from '@vue/test-utils'
import AdminHome from '../pages/admin/index.vue'
import { authStore } from '../stores/auth'
import { manageApi } from '../api/manage'

vi.mock('@dcloudio/uni-app', () => ({ onLoad: (callback: () => void) => callback() }))

it('loads recent administrator data only after the role check', async () => {
  let finishGuard!: (value: boolean) => void
  vi.spyOn(authStore, 'guard').mockReturnValue(new Promise((resolve) => { finishGuard = resolve }))
  authStore.profile = { token: 'token', username: 'admin', roles: ['ADMIN'], needsBinding: false }
  const documents = vi.spyOn(manageApi, 'queryDocumentPage').mockResolvedValue({ records: [{ documentId: 1, documentName: '文档 A' }] })
  const sessions = vi.spyOn(manageApi, 'listSessionsPage').mockResolvedValue({ sessions: [{ conversationId: 'c1', title: '会话 A' }] })
  const wrapper = mount(AdminHome)
  expect(documents).not.toHaveBeenCalled()
  expect(sessions).not.toHaveBeenCalled()
  finishGuard(true)
  await flushPromises(); await wrapper.vm.$nextTick()
  expect(documents).toHaveBeenCalledWith({ pageNo: 1, pageSize: 5 })
  expect(wrapper.text()).toContain('文档 A')
  expect(wrapper.text()).toContain('会话 A')
  vi.restoreAllMocks()
})
