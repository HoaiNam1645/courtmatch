import { useCallback, useEffect, useRef, useState } from 'react'
import { fetchPayment } from '../api/payments'

/** Hỏi lại khi đang chờ chủ sân xác nhận đã nhận tiền. */
const POLL_INTERVAL_MS = 5000

/**
 * Một giao dịch thanh toán, tự hỏi lại trạng thái khi còn đang chờ (2.1.34, 2.1.39).
 *
 * Sau khi người chơi báo đã thanh toán, chủ sân có thể xác nhận ở thiết bị khác.
 * Dừng hỏi ngay khi trạng thái khác `payer_reported` để không gọi API vô ích.
 */
export function usePayment(paymentId) {
  const [status, setStatus] = useState(paymentId ? 'loading' : 'error')
  const [payment, setPayment] = useState(null)
  const [error, setError] = useState(paymentId ? '' : 'Đường dẫn không hợp lệ.')
  const [notFound, setNotFound] = useState(!paymentId)
  // Giữ trạng thái mới nhất cho bộ đếm, tránh phải dựng lại interval mỗi lần đổi.
  const isPendingRef = useRef(true)

  const load = useCallback(async (showSpinner = false) => {
    if (!paymentId) return null
    if (showSpinner) {
      setStatus('loading')
      setError('')
    }
    try {
      const next = await fetchPayment(paymentId)
      isPendingRef.current = next.status === 'payer_reported'
      setPayment(next)
      setStatus('success')
      return next
    } catch (loadError) {
      setNotFound(loadError?.status === 404)
      setError(loadError?.message ?? 'Không tải được giao dịch thanh toán.')
      setStatus('error')
      return null
    }
  }, [paymentId])

  useEffect(() => {
    // Effect nay dong bo voi he thong ngoai (goi API).
    // oxlint-disable-next-line react/set-state-in-effect
    load()
  }, [load])

  useEffect(() => {
    if (!paymentId) return undefined
    const timer = window.setInterval(() => {
      if (isPendingRef.current) load()
    }, POLL_INTERVAL_MS)
    return () => window.clearInterval(timer)
  }, [paymentId, load])

  return { status, payment, error, notFound, setPayment, reload: () => load(true) }
}
