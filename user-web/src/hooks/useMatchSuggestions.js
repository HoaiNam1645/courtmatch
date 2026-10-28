import { useCallback, useEffect, useState } from 'react'
import { fetchMatchSuggestions } from '../api/matchSuggestions'

export function useMatchSuggestions() {
  const [state, setState] = useState({ status: 'loading', suggestions: [], error: '' })
  const [tick, setTick] = useState(0)

  useEffect(() => {
    let ignore = false
    fetchMatchSuggestions().then(({ suggestions }) => {
      if (ignore) return
      setState({ status: suggestions.length ? 'success' : 'empty', suggestions, error: '' })
    }).catch((error) => {
      if (ignore) return
      setState({ status: 'error', suggestions: [], error: error?.message ?? 'Không tải được gợi ý.' })
    })
    return () => { ignore = true }
  }, [tick])

  const reload = useCallback(() => {
    setState({ status: 'loading', suggestions: [], error: '' })
    setTick((value) => value + 1)
  }, [])

  return { ...state, reload }
}
