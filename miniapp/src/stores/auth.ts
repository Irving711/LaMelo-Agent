import { authApi, type AuthApi, type MiniProgramProfile } from '../api/auth'
import { clearAuthStorage, consumeLoginRoute, getProfile, getToken, setLoginRoute, setProfile, setToken } from '../utils/storage'

type UniNavigation = {
  navigateTo?: (options: { url: string }) => void
  redirectTo?: (options: { url: string }) => void
  switchTab?: (options: { url: string }) => void
}

export interface AuthStore {
  token: string | null
  profile: MiniProgramProfile | null
  loading: boolean
  loginWithWechat(code: string): Promise<MiniProgramProfile>
  loginWithPassword(username: string, password: string): Promise<MiniProgramProfile>
  bindWechat(payload: { username: string; password: string; code: string }): Promise<void>
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
  let pendingBinding = false
  const store = {
    token: getToken(),
    profile: getProfile<MiniProgramProfile>(),
    loading: false,
    get isAuthenticated() { return Boolean(this.token) },
    get isAdmin() { return this.profile?.roles?.some((role) => ['ADMIN', 'ROLE_ADMIN'].includes(String(role).toUpperCase())) || false },
    async loginWithWechat(code: string) {
      this.loading = true
      try { const profile = assignProfile(this, await api.wechatLogin(code)); pendingBinding = profile.needsBinding || !profile.token; if (profile.token && !pendingBinding) this.consumeRequestedRoute(); return profile } finally { this.loading = false }
    },
    async loginWithPassword(username: string, password: string) {
      this.loading = true
      try { const profile = assignProfile(this, await api.passwordLogin(username, password)); if (profile.token && !pendingBinding && !profile.needsBinding) this.consumeRequestedRoute(); return profile } finally { this.loading = false }
    },
    async bindWechat(payload: { username: string; password: string; code: string }) {
      await api.bindWechat(payload)
      if (this.profile) {
        this.profile = { ...this.profile, token: this.token, needsBinding: false }
        const { token: _token, ...safeProfile } = this.profile
        setProfile(safeProfile)
      }
      pendingBinding = false
      if (this.token && !this.consumeRequestedRoute()) uni().redirectTo?.({ url: '/pages/chat/index' })
    },
    async unbindWechat(password: string) {
      await api.unbindWechat(password, this.profile?.username || undefined)
      if (this.profile) {
        this.profile = { ...this.profile, token: this.token, needsBinding: true }
        const { token: _token, ...safeProfile } = this.profile
        setProfile(safeProfile)
      }
    },
    logout() {
      this.token = null
      this.profile = null
      pendingBinding = false
      clearAuthStorage()
    },
    async restoreSession() {
      this.token = getToken()
      const stored = getProfile<MiniProgramProfile>()
      if (stored) {
        const { token: _token, ...safeProfile } = stored
        this.profile = { ...safeProfile, token: this.token }
        pendingBinding = Boolean(this.profile.needsBinding)
      } else { this.profile = null; pendingBinding = false }
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
      if (route.includes('/pages/')) uni().redirectTo?.({ url: route })
      return true
    }
  } as AuthStore
  return store
}

export const authStore = createAuthStore()
export const useAuthStore = createAuthStore
