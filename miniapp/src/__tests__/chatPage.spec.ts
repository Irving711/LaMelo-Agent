import { mount } from '@vue/test-utils'
import MessageList from '../components/chat/MessageList.vue'
import ChatComposer from '../components/chat/ChatComposer.vue'
import StreamStatus from '../components/chat/StreamStatus.vue'
import CitationList from '../components/chat/CitationList.vue'
import SessionList from '../components/session/SessionList.vue'
import ChatSidebar from '../components/chat/ChatSidebar.vue'

describe('ordinary user chat components', () => {
  it('opens recent sessions from a drawer and exposes new chat and profile actions', async () => {
    const wrapper = mount(ChatSidebar, { props: { open: true, records: [{ conversationId: 'c1', latestUserMessage: '旧问题' }], pageNo: 1, totalPages: 1 } })
    expect(wrapper.text()).toContain('旧问题')
    await wrapper.get('[data-testid="sidebar-session-c1"]').trigger('click')
    await wrapper.get('[data-testid="sidebar-new-chat"]').trigger('click')
    await wrapper.get('[data-testid="sidebar-profile"]').trigger('click')
    expect(wrapper.emitted('select')).toEqual([['c1']])
    expect(wrapper.emitted('new-chat')).toEqual([[]])
    expect(wrapper.emitted('profile')).toEqual([[]])
  })
  it('does not show generation status copy', () => {
    expect(mount(StreamStatus, { props: { status: 'streaming' } }).text()).toBe('')
    expect(mount(StreamStatus, { props: { status: 'completed' } }).text()).toBe('')
  })
  it('uses the imported YM bubble for streamed Markdown answers', () => {
    const wrapper = mount(MessageList, { props: { messages: [{ id: 'a1', role: 'assistant', content: '**加粗**', status: 'streaming' }], streaming: true } })
    expect(wrapper.find('.ym-bubble--left').exists()).toBe(true)
    expect(wrapper.find('.markdown-wrapper').exists()).toBe(true)
  })

  it('uses the YM sender for submit and cancel while retaining the document selector', async () => {
    const wrapper = mount(ChatComposer, {
      props: { mode: 'DOCUMENT', selectedDocumentId: 'd1', modes: [{ value: 'DOCUMENT', label: '指定文档' }], knowledgeOptions: [{ documentId: 'd1', documentName: '指南' }], loading: true }
    })
    expect(wrapper.find('.ym-sender').exists()).toBe(true)
    expect(wrapper.text()).toContain('指南')
  })

  it('defaults the composer mode list to open chat first', () => {
    const wrapper = mount(ChatComposer, { props: { mode: 'OPEN_CHAT' } })
    expect(wrapper.text()).toContain('开放式对话')
  })
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
    expect(wrapper.emitted('retry')).toEqual([['a1']])
    expect(wrapper.find('[data-testid="message-stop"]').exists()).toBe(false)
  })

  it('does not render a loading bubble after an empty assistant message has finished', () => {
    const wrapper = mount(MessageList, { props: { messages: [{ id: 'a1', role: 'assistant', content: '', status: 'completed' }] } })
    expect(wrapper.find('.ym-bubble').exists()).toBe(false)
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
