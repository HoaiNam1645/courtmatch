import { useCallback, useEffect, useState } from 'react'
import { fetchVenueReviews } from '../api/venues'

/** Đánh giá của sân hiện ở trang chi tiết (2.1.23). */
export function useVenueReviews(slugOrId) {
  const [reviews, setReviews] = useState([])
  const [totalElements, setTotalElements] = useState(0)
  const [status, setStatus] = useState('loading')
  const [tick, setTick] = useState(0)

  useEffect(() => {
    if (!slugOrId) return undefined
    let ignore = false

    fetchVenueReviews(slugOrId)
      .then((result) => {
        if (ignore) return
        setReviews(result.reviews)
        setTotalElements(result.totalElements)
        setStatus(result.reviews.length === 0 ? 'empty' : 'success')
      })
      .catch(() => {
        // Đánh giá không tải được thì ẩn khối đó đi, không chặn cả trang chi tiết.
        if (!ignore) setStatus('error')
      })

    return () => {
      ignore = true
    }
  }, [slugOrId, tick])

  const reload = useCallback(() => setTick((value) => value + 1), [])

  return { reviews, totalElements, status, reload }
}
