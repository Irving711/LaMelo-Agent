import { authApi, type AuthApi, type MiniProgramProfile } from '../api/auth'
import { clearAuthStorage, consumeLoginRoute, getProfile, getToken, setLoginRoute, setProfile, setToken } from '../utils/storage'

type UniNavigation = {
  navigateTo?: (options: { url: string }) => void
  redirectTo?: (options: { url: string }) => void
  reLaunch?: (options: { url: string }) => void
}

export interface AuthStore {
  token: string | null
  profile: MiniProgramProfile | null
  loading: boolean
  loginWithWechat(code: string): Promise<MiniProgramProfile>
  loginWithPassword(username: string, password: string): Promise<MiniProgramProfile>
  bindExistingAccount(payload: { username: string; password: string }): Promise<MiniProgramProfile>
  setCredentials(payload: { username: string; password: string }): Promise<MiniProgramProfile>
  unbindWechat(password: string): Promise<void>
  logout(): void
  restoreSession(): Promise<boolean>
  guard(route?: string): Promise<boolean>
  redirectToLogin(route?: string): void
  consumeRequestedRoute(): boolean
  readonly isAuthenticated: boolean
  readonly isAdmin: boolean
}

function uni(): UniNavigation {
  return ((globalThis as { uni?: UniNavigation }).uni || {})
}

function enterApp(route = '/pages/chat/index') {
  const target = route && route.includes('/pages/') ? route : '/pages/chat/index'
  const path = target.split('?')[0]
  if (path === '/pages/chat/index') uni().reLaunch?.({ url: target })
  else uni().redirectTo?.({ url: target })
}

function assignProfile(store: AuthStore, profile: MiniProgramProfile): MiniProgramProfile {
  store.token = profile.token
  store.profile = { ...profile, roles: Array.isArray(profile.roles) ? profile.roles : [] }
  setToken(profile.token)
  const { token: _token, ...safeProfile } = store.profile
  setProfile(safeProfile)
  return profile
}

export function createAuthStore(options: { api?: AuthApi } = {}): AuthStore {
  const api = options.api || authApi
  const store = {
    token: getToken(),
    profile: getProfile<MiniProgramProfile>(),
    loading: false,
    get isAuthenticated() { return Boolean(this.token) },
    get isAdmin() { return this.profile?.roles?.some((role) => ['ADMIN', 'ROLE_ADMIN'].includes(String(role).toUpperCase())) || false },
    async loginWithWechat(code: string) {
      this.loading = true
      try {
        const profile = assignProfile(this, await api.wechatLogin(code))
        if (profile.token && !this.consumeRequestedRoute()) enterApp()
        return profile
      } finally { this.loading = false }
    },
    async loginWithPassword(username: string, password: string) {
      this.loading = true
      try {
        const profile = assignProfile(this, await api.passwordLogin(username, password))
        if (profile.token && !this.consumeRequestedRoute()) enterApp()
        return profile
      } finally { this.loading = false }
    },
    async bindExistingAccount(payload: { username: string; password: string }) {
      const profile = assignProfile(this, await api.bindExistingAccount(payload))
      if (profile.token && !this.consumeRequestedRoute()) enterApp()
      return profile
    },
    async setCredentials(payload: { username: string; password: string }) {
      return assignProfile(this, await api.setCredentials(payload))
    },
    async unbindWechat(password: string) {
      await api.unbindWechat(password)
      if (this.profile) {
        this.profile = { ...this.profile, token: this.token, needsBinding: true }
        const { token: _token, ...safeProfile } = this.profile
        setProfile(safeProfile)
      }
    },
    logout() {
      this.token = null
      this.profile = null
      clearAuthStorage()
    },
    async restoreSession() {
      this.token = getToken()
      const stored = getProfile<MiniProgramProfile>()
      if (stored) {
        const { token: _token, ...safeProfile } = stored
        this.profile = { ...safeProfile, token: this.token }
      } else { this.profile = null }
      return Boolean(this.token && this.profile)
    },
    async guard(route = '') {
      const authenticated = await this.restoreSession()
      if (!authenticated) this.redirectToLogin(route)
      return authenticated
    },
    redirectToLogin(route = '') {
      if (route) setLoginRoute(route)
      uni().navigateTo?.({ url: '/pages/auth/login' })
    },
    consumeRequestedRoute() {
      const route = consumeLoginRoute()
      if (!route) return false
      if (route.includes('/pages/')) enterApp(route)
      return true
    }
  } as AuthStore
  return store
}

export const authStore = createAuthStore()
export const useAuthStore = createAuthStore
