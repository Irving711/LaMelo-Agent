import { mount } from '@vue/test-utils'
import MessageList from '../components/chat/MessageList.vue'
import CitationList from '../components/chat/CitationList.vue'
import SessionList from '../components/session/SessionList.vue'

describe('ordinary user chat components', () => {
  it('renders normalized messages and emits retry and stop actions', async () => {
    const wrapper = mount(MessageList, {
      props: {
        messages: [
          { id: 'q1', role: 'user', content: '问题' },
          { id: 'a1', role: 'assistant', content: '答案', status: 'error' }
        ],
        streaming: true
      }
    })
    expect(wrapper.text()).toContain('问题')
    expect(wrapper.text()).toContain('答案')
    await wrapper.get('[data-testid="message-retry"]').trigger('click')
    await wrapper.get('[data-testid="message-stop"]').trigger('click')
    expect(wrapper.emitted('retry')).toEqual([['a1']])
    expect(wrapper.emitted('stop')).toEqual([[]])
  })

  it('renders citations and emits the selected reference', async () => {
    const wrapper = mount(CitationList, { props: { citations: [{ referenceId: 'r1', documentName: '指南', content: '片段' }] } })
    expect(wrapper.text()).toContain('指南')
    await wrapper.get('[data-testid="citation-r1"]').trigger('click')
    expect(wrapper.emitted('open-reference')).toEqual([[{ referenceId: 'r1', documentName: '指南', content: '片段' }]])
  })

  it('supports paginated session selection, deletion and loading more', async () => {
    const wrapper = mount(SessionList, {
      props: { records: [{ conversationId: 'c1', latestUserMessage: '你好', updatedAt: '2026-01-02T00:00:00Z' }], pageNo: 1, totalPages: 2, loading: false }
    })
    await wrapper.get('[data-testid="session-c1"]').trigger('click')
    await wrapper.get('[data-testid="session-delete-c1"]').trigger('click')
    await wrapper.get('[data-testid="session-load-more"]').trigger('click')
    expect(wrapper.emitted('select')).toEqual([['c1']])
    expect(wrapper.emitted('delete')).toEqual([['c1']])
    expect(wrapper.emitted('load-more')).toEqual([[]])
  })
})
