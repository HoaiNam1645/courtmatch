import { useCallback, useEffect, useState } from 'react'
import { fetchBookingDetail } from '../api/bookings'

/** Chi tiết một đơn đặt sân (2.1.29, 2.1.30). */
export function useBookingDetail(bookingId) {
  const [status, setStatus] = useState(bookingId ? 'loading' : 'error')
  const [booking, setBooking] = useState(null)
  const [error, setError] = useState(bookingId ? '' : 'Đường dẫn không hợp lệ.')
  const [notFound, setNotFound] = useState(!bookingId)

  const load = useCallback(async (showSpinner = false) => {
    if (!bookingId) return
    if (showSpinner) {
      setStatus('loading')
      setError('')
    }
    try {
      setBooking(await fetchBookingDetail(bookingId))
      setStatus('success')
    } catch (loadError) {
      // 404 là "đơn không tồn tại hoặc không phải của bạn", khác với lỗi mạng.
      setNotFound(loadError?.status === 404)
      setError(loadError?.message ?? 'Không tải được đơn đặt sân.')
      setStatus('error')
    }
  }, [bookingId])

  useEffect(() => {
    // Effect nay dong bo voi he thong ngoai (goi API).
    // oxlint-disable-next-line react/set-state-in-effect
    load()
  }, [load])

  return { status, booking, error, notFound, setBooking, reload: () => load(true) }
}
