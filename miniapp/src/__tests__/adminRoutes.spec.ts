import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { flushPromises, mount } from '@vue/test-utils'
import PagedList from '../components/admin/PagedList.vue'
import FilterPanel from '../components/admin/FilterPanel.vue'
import StatusBadge from '../components/admin/StatusBadge.vue'
import KnowledgeRoutePage from '../pages/admin/knowledge-route/index.vue'
import RouteTracesPage from '../pages/admin/knowledge-route/traces.vue'
import { authStore } from '../stores/auth'
import { manageApi } from '../api/manage'

describe('miniapp admin routes and shared controls', () => {
  it('declares every mobile administrator route', () => {
    const pages = JSON.parse(readFileSync(resolve(process.cwd(), 'src/pages.json'), 'utf8'))
    const paths = pages.pages.map((page: { path: string }) => page.path)
    expect(paths).toEqual(expect.arrayContaining([
      'pages/admin/index',
      'pages/admin/documents/index',
      'pages/admin/documents/detail',
      'pages/admin/knowledge-route/index',
      'pages/admin/knowledge-route/traces',
      'pages/admin/observability/index',
      'pages/admin/observability/session',
      'pages/admin/observability/exchange'
    ]))
  })

  it('renders page controls and emits pagination/filter events', async () => {
    const wrapper = mount(PagedList, {
      props: { records: [{ id: 'd1' }], total: 21, current: 2, size: 10 }
    })
    await wrapper.get('[data-testid="paged-next"]').trigger('click')
    expect(wrapper.emitted('page-change')).toEqual([[3]])
    await wrapper.get('[data-testid="paged-size"]').setValue('20')
    expect(wrapper.emitted('size-change')).toEqual([[20]])
    await wrapper.get('[data-testid="paged-refresh"]').trigger('click')
    expect(wrapper.emitted('refresh')).toHaveLength(1)

    const filters = mount(FilterPanel, { props: { modelValue: { keyword: '' } } })
    await filters.get('[data-testid="filter-keyword"]').setValue('manual')
    expect(filters.emitted('update:modelValue')?.at(-1)?.[0]).toEqual({ keyword: 'manual' })
    await filters.get('[data-testid="filter-submit"]').trigger('click')
    expect(filters.emitted('submit')).toHaveLength(1)
    expect(mount(StatusBadge, { props: { status: 'SUCCESS' } }).text()).toContain('SUCCESS')
  })

  it('saves a knowledge scope through the backend API', async () => {
    vi.spyOn(authStore, 'guard').mockResolvedValue(true)
    authStore.profile = { token: 'token', username: 'admin', roles: ['ADMIN'], needsBinding: false }
    vi.spyOn(manageApi, 'listKnowledgeScopes').mockResolvedValue([])
    vi.spyOn(manageApi, 'listKnowledgeTopics').mockResolvedValue([])
    const save = vi.spyOn(manageApi, 'saveKnowledgeScope').mockResolvedValue({})
    const wrapper = mount(KnowledgeRoutePage)
    await Promise.resolve(); await wrapper.vm.$nextTick()
    await wrapper.get('[data-testid="scope-code"]').setValue('finance')
    await wrapper.get('[data-testid="scope-name"]').setValue('财务')
    await wrapper.get('[data-testid="scope-save"]').trigger('click')
    await Promise.resolve()
    expect(save).toHaveBeenCalledWith(expect.objectContaining({ scopeCode: 'finance', scopeName: '财务' }))
    vi.restoreAllMocks()
  })

  it('passes route trace filters and pagination to the backend', async () => {
    vi.spyOn(authStore, 'guard').mockResolvedValue(true)
    authStore.profile = { token: 'token', username: 'admin', roles: ['ADMIN'], needsBinding: false }
    const query = vi.spyOn(manageApi, 'queryKnowledgeRouteTracePage').mockResolvedValue({ totalSize: '21', records: [{ id: '1', conversationId: 'c1', question: '问题' }] })
    const wrapper = mount(RouteTracesPage)
    await Promise.resolve(); await wrapper.vm.$nextTick()
    await wrapper.get('[data-testid="trace-conversation"]').setValue('c1')
    await wrapper.get('[data-testid="trace-search"]').trigger('click')
    await Promise.resolve(); await wrapper.vm.$nextTick()
    expect(query).toHaveBeenLastCalledWith(expect.objectContaining({ conversationId: 'c1', pageNo: 1, pageSize: 10 }))
    await wrapper.get('[data-testid="paged-next"]').trigger('click')
    await Promise.resolve()
    expect(query).toHaveBeenLastCalledWith(expect.objectContaining({ conversationId: 'c1', pageNo: 2 }))
    vi.restoreAllMocks()
  })

  it('queries and regenerates a document profile', async () => {
    vi.spyOn(authStore, 'guard').mockResolvedValue(true)
    authStore.profile = { token: 'token', username: 'admin', roles: ['ADMIN'], needsBinding: false }
    vi.spyOn(manageApi, 'listKnowledgeScopes').mockResolvedValue([])
    vi.spyOn(manageApi, 'listKnowledgeTopics').mockResolvedValue([])
    const query = vi.spyOn(manageApi, 'queryDocumentProfile').mockResolvedValue({ documentId: '8', documentSummary: '画像摘要' })
    const regenerate = vi.spyOn(manageApi, 'regenerateDocumentProfile').mockResolvedValue({ documentId: '8', documentSummary: '更新摘要' })
    const wrapper = mount(KnowledgeRoutePage)
    await flushPromises()
    await wrapper.findAll('button').find((button) => button.text() === '文档画像')!.trigger('click')
    await wrapper.get('[data-testid="profile-document-id"]').setValue('8')
    await wrapper.get('[data-testid="profile-query"]').trigger('click')
    await Promise.resolve(); await wrapper.vm.$nextTick()
    expect(query).toHaveBeenCalledWith({ documentId: '8' })
    expect(wrapper.text()).toContain('画像摘要')
    await wrapper.get('[data-testid="profile-regenerate"]').trigger('click')
    await Promise.resolve()
    expect(regenerate).toHaveBeenCalledWith({ documentId: '8' })
    vi.restoreAllMocks()
  })
})
