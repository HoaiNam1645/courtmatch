import { useCallback, useEffect, useState } from 'react'
import { fetchMatchParticipants } from '../api/matches'
import { fetchMatchSuggestions } from '../api/matchSuggestions'
import { searchVenues } from '../api/venues'

export function useMatchSetup() {
  const [state, setState] = useState({ status: 'loading', suggestions: [], players: [], venues: [], error: '' })
  const [tick, setTick] = useState(0)

  useEffect(() => {
    let ignore = false
    Promise.all([
      fetchMatchSuggestions(),
      fetchMatchParticipants(),
      searchVenues({ page: 0, size: 50 }),
    ]).then(([suggestions, participants, venues]) => {
      if (ignore) return
      setState({
        status: 'success',
        suggestions: suggestions.suggestions,
        players: participants.players,
        venues: venues.venues,
        error: '',
      })
    }).catch((error) => {
      if (ignore) return
      setState((current) => ({ ...current, status: 'error', error: error?.message ?? 'Không tải được dữ liệu ghi trận.' }))
    })
    return () => { ignore = true }
  }, [tick])

  const reload = useCallback(() => {
    setState((current) => ({ ...current, status: 'loading', error: '' }))
    setTick((value) => value + 1)
  }, [])

  return { ...state, reload }
}
