import { request } from './request'

export interface MiniProgramProfile {
  token: string | null
  username: string | null
  roles: string[]
  needsBinding: boolean
  expireMinutes?: number
}

export interface AuthApi {
  wechatLogin(code: string): Promise<MiniProgramProfile>
  passwordLogin(username: string, password: string): Promise<MiniProgramProfile>
  bindWechat(payload: { username: string; password: string; code: string }): Promise<void>
  unbindWechat(password: string, username?: string): Promise<void>
}

export const authApi: AuthApi = {
  wechatLogin(code) {
    return request<MiniProgramProfile>({ url: '/miniapp/auth/wechat-login', method: 'POST', data: { code }, dedupeKey: 'login-refresh' })
  },
  passwordLogin(username, password) {
    return request<MiniProgramProfile>({ url: '/miniapp/auth/password-login', method: 'POST', data: { username, password }, dedupeKey: 'login-refresh' })
  },
  bindWechat(payload) {
    return request<void>({ url: '/miniapp/auth/bind', method: 'POST', data: payload })
  },
  unbindWechat(password, username) {
    return request<void>({ url: '/miniapp/auth/unbind', method: 'POST', data: { username: username || '', password } })
  }
}
