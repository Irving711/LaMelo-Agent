const ADMIN_TOKEN_KEY = 'lamelo-agent-admin-token'
const ADMIN_USER_KEY = 'lamelo-agent-admin-user'

const LEGACY_ADMIN_TOKEN_KEYS = [
  'super-agent-admin-token',
  'nexus-agent-admin-token'
]
const LEGACY_ADMIN_USER_KEYS = [
  'super-agent-admin-user',
  'nexus-agent-admin-user'
]

function readMigratedValue(currentKey, legacyKeys) {
  const storage = window.localStorage
  const currentValue = storage.getItem(currentKey)
  if (currentValue !== null) {
    legacyKeys.forEach((key) => storage.removeItem(key))
    return currentValue
  }

  const legacyValue = legacyKeys
    .map((key) => storage.getItem(key))
    .find((value) => value !== null && value !== '')

  if (legacyValue !== undefined) {
    storage.setItem(currentKey, legacyValue)
  }
  legacyKeys.forEach((key) => storage.removeItem(key))
  return legacyValue ?? null
}

export function getAdminAuthToken() {
  return readMigratedValue(ADMIN_TOKEN_KEY, LEGACY_ADMIN_TOKEN_KEYS) || ''
}

export function getAdminAuthUsername() {
  return readMigratedValue(ADMIN_USER_KEY, LEGACY_ADMIN_USER_KEYS) || 'admin'
}

export function saveAdminAuthStorage(payload = {}) {
  const storage = window.localStorage
  if (payload.token) {
    storage.setItem(ADMIN_TOKEN_KEY, payload.token)
    LEGACY_ADMIN_TOKEN_KEYS.forEach((key) => storage.removeItem(key))
  }
  if (payload.username) {
    storage.setItem(ADMIN_USER_KEY, payload.username)
    LEGACY_ADMIN_USER_KEYS.forEach((key) => storage.removeItem(key))
  }
}

export function clearAdminAuthStorage() {
  const storage = window.localStorage
  storage.removeItem(ADMIN_TOKEN_KEY)
  storage.removeItem(ADMIN_USER_KEY)
  LEGACY_ADMIN_TOKEN_KEYS.forEach((key) => storage.removeItem(key))
  LEGACY_ADMIN_USER_KEYS.forEach((key) => storage.removeItem(key))
}
