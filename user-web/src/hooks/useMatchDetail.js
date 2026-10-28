import { useCallback, useEffect, useState } from 'react'
import { fetchMatchDetail } from '../api/matches'

export function useMatchDetail(matchId) {
  const [state, setState] = useState({ status: 'loading', match: null, error: '' })
  const [tick, setTick] = useState(0)

  useEffect(() => {
    let ignore = false
    fetchMatchDetail(matchId).then((match) => {
      if (!ignore) setState({ status: 'success', match, error: '' })
    }).catch((error) => {
      if (!ignore) setState({ status: error?.status === 404 ? 'not-found' : 'error', match: null, error: error?.message ?? 'Không tải được trận đấu.' })
    })
    return () => { ignore = true }
  }, [matchId, tick])

  const reload = useCallback(() => {
    setState({ status: 'loading', match: null, error: '' })
    setTick((value) => value + 1)
  }, [])

  return { ...state, reload }
}
