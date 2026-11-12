import { useCallback, useEffect, useState } from 'react'
import { getPartnerReviews } from '../api/partnerReviews'

export function usePartnerReviews(playerId) {
  const [state, setState] = useState({
    status: 'loading', player: null, reviews: [], averageRating: 0,
    totalReviews: 0, page: 0, totalPages: 0, error: '',
  })

  const load = useCallback(async (page = 0) => {
    setState((current) => ({ ...current, status: 'loading', error: '' }))
    try {
      const data = await getPartnerReviews(playerId, { page })
      setState({
        status: data.content.length === 0 ? 'empty' : 'success',
        player: data.player,
        reviews: data.content,
        averageRating: Number(data.averageRating),
        totalReviews: data.totalReviews,
        page: data.page,
        totalPages: data.totalPages,
        error: '',
      })
    } catch (error) {
      setState((current) => ({
        ...current,
        status: error?.status === 404 ? 'not-found' : 'error',
        error: error?.message ?? 'Không tải được đánh giá đối tác.',
      }))
    }
  }, [playerId])

  useEffect(() => {
    // oxlint-disable-next-line react/set-state-in-effect
    load(0)
  }, [load])

  return { ...state, load }
}
