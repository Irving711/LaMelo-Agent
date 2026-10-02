import { defineConfig, type PluginOption } from 'vite'
import uniModule from '@dcloudio/vite-plugin-uni'
import { fileURLToPath, URL } from 'node:url'

const uni = (uniModule as typeof uniModule & { default?: () => unknown }).default ?? uniModule

export default defineConfig({
  plugins: [(uni as () => PluginOption)()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['src/**/__tests__/*.spec.ts']
  }
})
