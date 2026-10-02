import { flushPromises, mount } from '@vue/test-utils'
import DocumentsPage from '../pages/admin/documents/index.vue'
import { authStore } from '../stores/auth'
import { manageApi } from '../api/manage'

afterEach(() => vi.restoreAllMocks())

it('blocks document loading for a non-admin account', async () => {
  vi.spyOn(authStore, 'guard').mockResolvedValue(true)
  authStore.profile = { token: 'token', username: 'user', roles: ['USER'], needsBinding: false }
  const query = vi.spyOn(manageApi, 'queryDocumentPage').mockResolvedValue({ records: [], total: 0 })
  const wrapper = mount(DocumentsPage)
  await flushPromises()
  expect(query).not.toHaveBeenCalled()
  expect(wrapper.text()).toContain('没有管理员权限')
})

it('deletes a document only after confirmation', async () => {
  vi.spyOn(authStore, 'guard').mockResolvedValue(true)
  authStore.profile = { token: 'token', username: 'admin', roles: ['ADMIN'], needsBinding: false }
  vi.spyOn(manageApi, 'queryDocumentPage').mockResolvedValue({ records: [{ documentId: 5, documentName: '测试文档' }], total: 1 })
  const remove = vi.spyOn(manageApi, 'deleteDocument').mockResolvedValue({})
  let completeModal!: (result: { confirm: boolean }) => void
  const originalUni = (globalThis as any).uni
  ;(globalThis as any).uni = { showModal: (options: any) => { completeModal = options.success } }
  const wrapper = mount(DocumentsPage)
  await flushPromises()
  await wrapper.findAll('button').find((button) => button.text() === '删除')!.trigger('click')
  expect(remove).not.toHaveBeenCalled()
  completeModal({ confirm: true })
  await flushPromises()
  expect(remove).toHaveBeenCalledWith({ documentId: 5 })
  wrapper.unmount()
  ;(globalThis as any).uni = originalUni
})

it('resets the server filter and page number together', async () => {
  vi.spyOn(authStore, 'guard').mockResolvedValue(true)
  authStore.profile = { token: 'token', username: 'admin', roles: ['ADMIN'], needsBinding: false }
  const query = vi.spyOn(manageApi, 'queryDocumentPage').mockResolvedValue({ records: [], total: 11 })
  const wrapper = mount(DocumentsPage)
  await flushPromises()
  await wrapper.get('[data-testid="filter-keyword"]').setValue('guide')
  await wrapper.get('[data-testid="filter-submit"]').trigger('click')
  await flushPromises()
  expect(query).toHaveBeenLastCalledWith({ pageNo: 1, pageSize: 10, keyword: 'guide' })
  await wrapper.findAll('button').find((button) => button.text() === '重置')!.trigger('click')
  await flushPromises()
  expect(query).toHaveBeenLastCalledWith({ pageNo: 1, pageSize: 10, keyword: '' })
})
