import { logout as logoutApi } from '../api/auth'

const CACHED_USER_KEY = 'courtly_cached_user'
const RECOVERY_STORAGE_KEY = 'courtly_password_recovery'
const PENDING_VERIFICATION_KEY = 'courtly_pending_verification'
const PENDING_BOOKING_STORAGE_KEY = 'courtly_pending_booking'
const LEGACY_STORAGE_KEYS = {
  auth: 'badcourt_fake_user',
  recovery: 'badcourt_password_recovery',
  pendingBooking: 'badcourt_pending_booking',
}

export function readCachedUser() {
  try {
    const savedUser = window.localStorage.getItem(CACHED_USER_KEY)
    return savedUser ? JSON.parse(savedUser) : null
  } catch {
    return null
  }
}

export function saveCachedUser(user) {
  try {
    window.localStorage.setItem(CACHED_USER_KEY, JSON.stringify(user))
  } catch {
    // The current session still works when private browsing blocks storage.
  }
  window.dispatchEvent(new Event('courtly-auth-change'))
}

export function clearCachedUser() {
  try {
    window.localStorage.removeItem(CACHED_USER_KEY)
    window.localStorage.removeItem(LEGACY_STORAGE_KEYS.auth)
    window.localStorage.removeItem('courtly_fake_user')
  } catch {
    // Clearing the in-memory session is sufficient when storage is unavailable.
  }
  window.dispatchEvent(new Event('courtly-auth-change'))
}

export async function signOut() {
  await logoutApi()
  clearCachedUser()
}

export function readPendingBooking() {
  try {
    const value = window.sessionStorage.getItem(PENDING_BOOKING_STORAGE_KEY)
      ?? window.sessionStorage.getItem(LEGACY_STORAGE_KEYS.pendingBooking)
    return value ? JSON.parse(value) : null
  } catch {
    return null
  }
}

export function saveRecoveryRequest(request) {
  window.sessionStorage.setItem(RECOVERY_STORAGE_KEY, JSON.stringify(request))
}

export function readRecoveryRequest() {
  try {
    const value = window.sessionStorage.getItem(RECOVERY_STORAGE_KEY)
      ?? window.sessionStorage.getItem(LEGACY_STORAGE_KEYS.recovery)
    return value ? JSON.parse(value) : null
  } catch {
    return null
  }
}

export function clearRecoveryRequest() {
  try {
    window.sessionStorage.removeItem(RECOVERY_STORAGE_KEY)
    window.sessionStorage.removeItem(LEGACY_STORAGE_KEYS.recovery)
  } catch {
    // Nothing else is required when storage is unavailable.
  }
}

export function savePendingVerification(state) {
  try {
    window.sessionStorage.setItem(PENDING_VERIFICATION_KEY, JSON.stringify(state))
  } catch {
    // The verification page can ask for the email again.
  }
}

export function readPendingVerification() {
  try {
    const value = window.sessionStorage.getItem(PENDING_VERIFICATION_KEY)
    return value ? JSON.parse(value) : null
  } catch {
    return null
  }
}

export function clearPendingVerification() {
  try {
    window.sessionStorage.removeItem(PENDING_VERIFICATION_KEY)
  } catch {
    // The completed flow must not fail only because storage is unavailable.
  }
}

export function maskRecoveryDestination(method, destination) {
  if (!destination) return method === 'phone' ? '09•• ••• ••88' : 'n•••@email.com'
  if (method === 'phone') {
    const digits = destination.replace(/\D/g, '')
    return digits.length > 4 ? `${digits.slice(0, 2)}•• ••• ••${digits.slice(-2)}` : destination
  }

  const [name, domain] = destination.split('@')
  if (!domain) return destination
  return `${name.slice(0, 1)}${'•'.repeat(Math.max(3, name.length - 1))}@${domain}`
}
