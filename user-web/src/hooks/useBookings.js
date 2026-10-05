import { useCallback, useEffect, useState } from 'react'
import { fetchBookings } from '../api/bookings'

/** Chờ người dùng ngừng gõ rồi mới gọi API. */
const DEBOUNCE_MS = 300

/** Lịch sử đặt sân theo tab và từ khoá (2.1.32). */
export function useBookings({ tab, q }) {
  const [status, setStatus] = useState('loading')
  const [bookings, setBookings] = useState([])
  const [totalElements, setTotalElements] = useState(0)
  const [error, setError] = useState('')
  const [tick, setTick] = useState(0)

  useEffect(() => {
    let ignore = false
    const timer = window.setTimeout(() => {
      fetchBookings({ tab, q })
        .then((data) => {
          if (ignore) return
          setBookings(data.bookings)
          setTotalElements(data.totalElements)
          setStatus(data.bookings.length === 0 ? 'empty' : 'success')
        })
        .catch((loadError) => {
          if (ignore) return
          setError(loadError?.message ?? 'Không tải được lịch sử đặt sân.')
          setStatus('error')
        })
    }, DEBOUNCE_MS)

    return () => {
      ignore = true
      window.clearTimeout(timer)
    }
  }, [tab, q, tick])

  const reload = useCallback(() => {
    setStatus('loading')
    setError('')
    setTick((value) => value + 1)
  }, [])

  return { status, bookings, totalElements, error, reload }
}
