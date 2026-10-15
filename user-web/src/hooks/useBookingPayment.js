import { useCallback, useEffect, useState } from 'react'
import { fetchPaymentByBooking } from '../api/payments'

/** Giao dịch mới nhất của booking, dùng để tiếp tục thanh toán từ trang chi tiết. */
export function useBookingPayment(bookingId, enabled = true) {
  const [status, setStatus] = useState(enabled && bookingId ? 'loading' : 'idle')
  const [payment, setPayment] = useState(null)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    if (!enabled || !bookingId) return null
    setStatus('loading')
    setError('')
    try {
      const next = await fetchPaymentByBooking(bookingId)
      setPayment(next)
      setStatus('success')
      return next
    } catch (loadError) {
      setError(loadError?.message ?? 'Không tải được giao dịch thanh toán.')
      setStatus('error')
      return null
    }
  }, [bookingId, enabled])

  useEffect(() => {
    // Effect nay dong bo voi he thong ngoai (goi API).
    // oxlint-disable-next-line react/set-state-in-effect
    load()
  }, [load])

  return { status, payment, error, reload: load }
}
