import { useCallback, useEffect, useState } from 'react'
import { fetchBookings } from '../api/bookings'

/**
 * Các đơn của người đang đăng nhập tại một sân mà còn viết được đánh giá (2.3.77).
 * Lỗi tải chỉ làm mất nút "Viết đánh giá", không ảnh hưởng phần còn lại của trang.
 */
export function useReviewableBookings(venueId, enabled) {
  const [bookings, setBookings] = useState([])
  const [tick, setTick] = useState(0)

  useEffect(() => {
    if (!enabled || !venueId) return undefined
    let ignore = false
    fetchBookings({ tab: 'completed', size: 100 })
      .then((data) => {
        if (!ignore) setBookings(data.bookings.filter((booking) => booking.venueId === venueId && booking.canReview))
      })
      .catch(() => {
        if (!ignore) setBookings([])
      })
    return () => {
      ignore = true
    }
  }, [venueId, enabled, tick])

  const reload = useCallback(() => setTick((value) => value + 1), [])
  return { bookings, reload }
}
