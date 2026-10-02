import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

export function validateProductionConfig({ appId, apiBaseUrl, appSecret }) {
  if (!/^wx[0-9a-f]{16}$/i.test(appId || '') || /^wx0{16}$/i.test(appId)) return 'AppID 无效或仍是占位值'
  let apiUrl
  try { apiUrl = new URL(apiBaseUrl || '') } catch { return 'API 地址无效' }
  if (apiUrl.protocol !== 'https:' || /^(localhost|127\.0\.0\.1|0\.0\.0\.0)$/i.test(apiUrl.hostname) || /example\.(com|test)$/i.test(apiUrl.hostname)) return 'API 地址必须是正式 HTTPS 域名'
  if (appSecret && /(your[_-]?app|example|placeholder|change[_-]?me)/i.test(appSecret)) return 'AppSecret 仍是占位值'
  return null
}

function readEnv(file) {
  if (!fs.existsSync(file)) return {}
  return Object.fromEntries(fs.readFileSync(file, 'utf8').split(/\r?\n/).map((line) => line.match(/^\s*([A-Z][A-Z0-9_]*)\s*=\s*(.*?)\s*$/)).filter(Boolean).map((match) => [match[1], match[2].replace(/^['"]|['"]$/g, '')]))
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const root = process.cwd()
  const production = process.argv.includes('--production')
  const manifest = JSON.parse(fs.readFileSync(path.join(root, 'src', 'manifest.json'), 'utf8'))
  const envValues = readEnv(path.join(root, '.env.production'))
  const all = { ...envValues, ...process.env }
  const frontendSecret = Object.keys(envValues).find((key) => /^VITE_.*(SECRET|TOKEN|PASSWORD)/i.test(key))
  if (production && frontendSecret) { console.error('小程序前端环境文件不得包含 AppSecret 或其他密钥'); process.exit(1) }
  const error = production ? validateProductionConfig({
    appId: manifest['mp-weixin']?.appid,
    apiBaseUrl: all.VITE_LAMELO_AGENT_API_BASE_URL,
    appSecret: all.LAMELO_AGENT_WECHAT_APP_SECRET
  }) : null
  if (error) { console.error(error); process.exit(1) }
  console.log('配置检查通过')
}
