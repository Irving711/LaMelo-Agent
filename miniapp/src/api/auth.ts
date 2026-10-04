import { request } from './request'

export interface MiniProgramProfile {
  token: string | null
  username: string | null
  roles: string[]
  /** 账号尚未设置可用密码时为 true */
  needsBinding: boolean
  expireMinutes?: number
}

export interface AuthApi {
  wechatLogin(code: string): Promise<MiniProgramProfile>
  passwordLogin(username: string, password: string): Promise<MiniProgramProfile>
  register?(payload: { username: string; password: string }): Promise<MiniProgramProfile>
  sendSmsCode?(mobile: string, purpose: 'register' | 'login' | 'reset'): Promise<void>
  smsLogin?(mobile: string, code: string): Promise<MiniProgramProfile>
  resetPassword?(payload: { mobile: string; code: string; password: string }): Promise<void>
  bindExistingAccount(payload: { username: string; password: string }): Promise<MiniProgramProfile>
  setCredentials(payload: { username: string; password: string }): Promise<MiniProgramProfile>
  unbindWechat(password: string): Promise<void>
}

export const authApi: AuthApi = {
  wechatLogin(code) {
    return request<MiniProgramProfile>({ url: '/miniapp/auth/wechat-login', method: 'POST', data: { code }, dedupeKey: 'login-refresh' })
  },
  passwordLogin(username, password) {
    return request<MiniProgramProfile>({ url: '/miniapp/auth/password-login', method: 'POST', data: { username, password }, dedupeKey: 'login-refresh' })
  },
  register(payload) {
    return request<MiniProgramProfile>({ url: '/miniapp/auth/register', method: 'POST', data: payload, dedupeKey: 'login-refresh' })
  },
  sendSmsCode(mobile, purpose) {
    return request<void>({ url: '/miniapp/auth/sms/send', method: 'POST', data: { mobile, purpose } })
  },
  smsLogin(mobile, code) {
    return request<MiniProgramProfile>({ url: '/miniapp/auth/sms-login', method: 'POST', data: { mobile, code }, dedupeKey: 'login-refresh' })
  },
  resetPassword(payload) {
    return request<void>({ url: '/miniapp/auth/password-reset', method: 'POST', data: payload })
  },
  bindExistingAccount(payload) {
    return request<MiniProgramProfile>({ url: '/miniapp/auth/bind', method: 'POST', data: payload })
  },
  setCredentials(payload) {
    return request<MiniProgramProfile>({ url: '/miniapp/auth/credentials', method: 'POST', data: payload })
  },
  unbindWechat(password) {
    return request<void>({ url: '/miniapp/auth/unbind', method: 'POST', data: { password } })
  }
}
