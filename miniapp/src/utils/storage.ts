export const STORAGE_KEYS = {
  token: 'lamelo-miniapp-token',
  profile: 'lamelo-miniapp-profile',
  loginRoute: 'lamelo-miniapp-login-route'
} as const

type UniStorage = {
  setStorageSync?: (key: string, value: unknown) => void
  getStorageSync?: (key: string) => unknown
  removeStorageSync?: (key: string) => void
}

function storage(): UniStorage {
  return ((globalThis as { uni?: UniStorage }).uni || {})
}

export function setToken(token: string | null): void {
  if (token) storage().setStorageSync?.(STORAGE_KEYS.token, token)
  else storage().removeStorageSync?.(STORAGE_KEYS.token)
}

export function getToken(): string | null {
  const value = storage().getStorageSync?.(STORAGE_KEYS.token)
  return typeof value === 'string' && value ? value : null
}

export function setProfile(profile: unknown): void {
  if (profile == null) storage().removeStorageSync?.(STORAGE_KEYS.profile)
  else {
    const safeProfile = profile && typeof profile === 'object' ? Object.fromEntries(Object.entries(profile as Record<string, unknown>).filter(([key]) => key !== 'token')) : profile
    storage().setStorageSync?.(STORAGE_KEYS.profile, safeProfile)
  }
}

export function getProfile<T = unknown>(): T | null {
  return (storage().getStorageSync?.(STORAGE_KEYS.profile) as T | undefined) ?? null
}

export function clearAuthStorage(): void {
  setToken(null)
  setProfile(null)
}

export function setLoginRoute(route: string): void {
  storage().setStorageSync?.(STORAGE_KEYS.loginRoute, route)
}

export function consumeLoginRoute(): string | null {
  const route = storage().getStorageSync?.(STORAGE_KEYS.loginRoute)
  storage().removeStorageSync?.(STORAGE_KEYS.loginRoute)
  return typeof route === 'string' && route ? route : null
}
