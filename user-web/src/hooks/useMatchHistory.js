import { useCallback, useEffect, useState } from 'react'
import { fetchMatchHistory } from '../api/matches'

const initialState = {
  requestKey: '',
  status: 'loading',
  matches: [],
  page: 0,
  totalElements: 0,
  totalPages: 0,
  pendingCount: 0,
  error: '',
}

export function useMatchHistory({ state = 'all', page = 0, size = 10 } = {}) {
  const [result, setResult] = useState(initialState)
  const [tick, setTick] = useState(0)
  const requestKey = `${state}:${page}:${size}:${tick}`

  useEffect(() => {
    let ignore = false

    Promise.all([
      fetchMatchHistory({ state, page, size }),
      fetchMatchHistory({ state: 'pending', page: 0, size: 1 }),
    ]).then(([history, pending]) => {
      if (ignore) return
      setResult({
        requestKey,
        status: history.matches.length === 0 ? 'empty' : 'success',
        ...history,
        pendingCount: pending.totalElements,
        error: '',
      })
    }).catch((error) => {
      if (ignore) return
      setResult({
        ...initialState,
        requestKey,
        status: 'error',
        error: error?.message ?? 'Không tải được lịch sử trận đấu.',
      })
    })

    return () => { ignore = true }
  }, [page, requestKey, size, state, tick])

  const reload = useCallback(() => setTick((value) => value + 1), [])
  if (result.requestKey !== requestKey) {
    return { ...initialState, pendingCount: result.pendingCount, reload }
  }
  return { ...result, reload }
}
