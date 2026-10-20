import { useEffect, useMemo, useState } from 'react'
import { fetchCurrentUser } from '../api/auth'
import { readToken, UNAUTHORIZED_EVENT } from '../api/client'
import { clearCachedUser, readCachedUser, saveCachedUser, signOut } from '../shared/session'
import { AppContext } from './AppContext'

function AppProvider({ children }) {
  const [ready, setReady] = useState(false)
  const [user, setUser] = useState(() => (readToken() ? readCachedUser() : null))

  useEffect(() => {
    const timer = window.setTimeout(() => setReady(true), 1050)
    return () => window.clearTimeout(timer)
  }, [])

  useEffect(() => {
    function syncAuth() {
      setUser(readCachedUser())
    }

    function handleUnauthorized() {
      clearCachedUser()
      setUser(null)
    }

    window.addEventListener('storage', syncAuth)
    window.addEventListener('courtly-auth-change', syncAuth)
    window.addEventListener(UNAUTHORIZED_EVENT, handleUnauthorized)
    return () => {
      window.removeEventListener('storage', syncAuth)
      window.removeEventListener('courtly-auth-change', syncAuth)
      window.removeEventListener(UNAUTHORIZED_EVENT, handleUnauthorized)
    }
  }, [])

  useEffect(() => {
    if (!readToken()) {
      clearCachedUser()
      return
    }

    let ignore = false
    fetchCurrentUser()
      .then((currentUser) => {
        if (ignore) return
        saveCachedUser(currentUser)
        setUser(currentUser)
      })
      .catch(() => {})

    return () => {
      ignore = true
    }
  }, [])

  async function logout() {
    await signOut()
    setUser(null)
    window.location.href = '/'
  }

  const value = useMemo(() => ({ ready, user, logout }), [ready, user])
  return <AppContext.Provider value={value}>{children}</AppContext.Provider>
}

export { AppProvider }
