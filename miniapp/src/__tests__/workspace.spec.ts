import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { mount } from '@vue/test-utils'
import AppEmptyState from '../components/AppEmptyState.vue'

const root = resolve(process.cwd())

describe('miniapp workspace', () => {
  it('declares login and all three tab pages', () => {
    const pages = JSON.parse(readFileSync(resolve(root, 'src/pages.json'), 'utf8'))
    const paths = pages.pages.map((page: { path: string }) => page.path)
    expect(paths).toEqual(expect.arrayContaining(['pages/auth/login', 'pages/chat/index', 'pages/sessions/index', 'pages/profile/index', 'pages/admin/index']))
    expect(pages.tabBar.list.map((tab: { pagePath: string }) => tab.pagePath)).toEqual([
      'pages/chat/index', 'pages/sessions/index', 'pages/profile/index'
    ])
  })

  it('renders default and custom empty state text', () => {
    expect(mount(AppEmptyState).text()).toContain('暂无内容')
    expect(mount(AppEmptyState, { props: { title: '没有结果', description: '请调整筛选条件。' } }).text()).toContain('没有结果')
  })
})
