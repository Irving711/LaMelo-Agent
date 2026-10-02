import { validateProductionConfig } from '../../scripts/check-config.mjs'
import packageJson from '../../package.json'

describe('production mini program configuration', () => {
  it('runs production validation before the release build', () => {
    expect(packageJson.scripts['build:mp-weixin']).toMatch(/^node scripts\/check-config\.mjs --production && /)
  })
  it('rejects placeholder secrets and local API addresses', () => {
    expect(validateProductionConfig({ appId: 'wx0000000000000000', apiBaseUrl: 'https://api.example.com' })).toContain('AppID')
    expect(validateProductionConfig({ appId: 'wx1234567890abcdef', apiBaseUrl: 'http://localhost:8200' })).toContain('API')
    expect(validateProductionConfig({ appId: 'wx1234567890abcdef', apiBaseUrl: 'https://api.test.cn', appSecret: 'YOUR_APP_SECRET' })).toContain('AppSecret')
  })

  it('accepts a configured HTTPS API and AppID without embedding the secret', () => {
    expect(validateProductionConfig({ appId: 'wx1234567890abcdef', apiBaseUrl: 'https://api.test.cn' })).toBeNull()
  })
})
