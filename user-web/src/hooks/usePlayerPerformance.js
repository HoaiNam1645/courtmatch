import { useCallback, useEffect, useState } from 'react'
import { fetchPlayerPerformance, fetchPlayerRatingHistory } from '../api/playerPerformance'

export function usePlayerPerformance({ includeHistory = true } = {}) {
  const [state, setState] = useState({ status: 'loading', performance: null, history: [], error: '' })
  const [tick, setTick] = useState(0)

  useEffect(() => {
    let ignore = false
    const historyRequest = includeHistory
      ? fetchPlayerRatingHistory({ days: 90, size: 100 })
      : Promise.resolve({ history: [] })

    Promise.all([fetchPlayerPerformance(), historyRequest]).then(([performance, history]) => {
      if (ignore) return
      setState({
        status: performance.summary.totalMatches === 0 ? 'empty' : 'success',
        performance,
        history: history.history,
        error: '',
      })
    }).catch((error) => {
      if (!ignore) setState({
        status: 'error',
        performance: null,
        history: [],
        error: error?.message ?? 'Không tải được dữ liệu hiệu suất.',
      })
    })
    return () => { ignore = true }
  }, [includeHistory, tick])

  const reload = useCallback(() => {
    setState({ status: 'loading', performance: null, history: [], error: '' })
    setTick((value) => value + 1)
  }, [])

  return { ...state, reload }
}
