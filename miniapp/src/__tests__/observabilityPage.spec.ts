import { mount } from '@vue/test-utils'
import ObservabilityPage from '../pages/admin/observability/index.vue'
import ExchangePage from '../pages/admin/observability/exchange.vue'
import { authStore } from '../stores/auth'
import { manageApi } from '../api/manage'

vi.mock('@dcloudio/uni-app', () => ({ onLoad: (callback: (query: Record<string, string>) => void) => callback({ conversationId: 'c1', exchangeId: '7' }) }))

it('loads and displays stage benchmarks for administrators', async () => {
  vi.spyOn(authStore, 'guard').mockResolvedValue(true)
  authStore.profile = { token: 'token', username: 'admin', roles: ['ADMIN'], needsBinding: false }
  vi.spyOn(manageApi, 'listSessionsPage').mockResolvedValue({ sessions: [], totalSize: 0 })
  const benchmarks = vi.spyOn(manageApi, 'getStageBenchmarks').mockResolvedValue([{ stageCode: 'retrieve', executionMode: 'serial', p90DurationMs: 120, sampleCount: 3 }])
  const wrapper = mount(ObservabilityPage)
  await Promise.resolve(); await Promise.resolve(); await wrapper.vm.$nextTick()
  expect(benchmarks).toHaveBeenCalled()
  expect(wrapper.text()).toContain('retrieve')
  expect(wrapper.text()).toContain('120')
  vi.restoreAllMocks()
})

it('shows exchange stage traces returned by the backend', async () => {
  vi.spyOn(authStore, 'guard').mockResolvedValue(true)
  authStore.profile = { token: 'token', username: 'admin', roles: ['ADMIN'], needsBinding: false }
  vi.spyOn(manageApi, 'getExchangeDetail').mockResolvedValue({ exchange: { question: '问题', answer: '答案' }, stageTraces: [{ stageId: 1, stageName: '检索阶段', durationMs: 50, stageState: 'SUCCESS' }] })
  vi.spyOn(manageApi, 'getRetrievalResults').mockResolvedValue([])
  vi.spyOn(manageApi, 'getChannelExecutions').mockResolvedValue([])
  const wrapper = mount(ExchangePage)
  await Promise.resolve(); await Promise.resolve(); await Promise.resolve(); await wrapper.vm.$nextTick()
  expect(wrapper.text()).toContain('检索阶段')
  expect(wrapper.text()).toContain('50')
  vi.restoreAllMocks()
})
