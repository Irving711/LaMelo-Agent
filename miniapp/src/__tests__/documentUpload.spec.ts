import { flushPromises, mount } from '@vue/test-utils'
import DocumentUpload from '../components/admin/DocumentUpload.vue'
import { manageApi } from '../api/manage'

it('polls the uploaded task and stops at a canceled terminal state', async () => {
  const originalUni = (globalThis as any).uni
  ;(globalThis as any).uni = { chooseMessageFile: vi.fn((options: any) => options.success({ tempFiles: [{ path: '/tmp/doc.pdf', name: 'doc.pdf' }] })), showToast: vi.fn() }
  vi.spyOn(manageApi, 'uploadDocument').mockImplementation(async (_payload, onProgress) => { onProgress?.(37); return { documentId: 8, taskId: 9 } })
  const poll = vi.spyOn(manageApi, 'queryTaskLogs').mockResolvedValue({ taskId: 9, taskStatus: 5, taskStatusName: '已取消', logs: [] })
  const wrapper = mount(DocumentUpload)
  await wrapper.findAll('button')[0].trigger('click')
  await wrapper.findAll('button')[1].trigger('click')
  await flushPromises()
  expect(poll).toHaveBeenCalledWith({ taskId: 9, pageNo: 1, pageSize: 1 })
  expect(wrapper.emitted('progress')).toEqual([[37], [100]])
  expect(wrapper.emitted('terminal')).toHaveLength(1)
  expect(wrapper.text()).toContain('已取消')
  wrapper.unmount()
  ;(globalThis as any).uni = originalUni
  vi.restoreAllMocks()
})
