import { useEffect, useState } from 'react'
import { fetchAvailability } from '../api/venues'

/**
 * Lịch trống của một sân trong một ngày, theo thời lượng đang chọn (2.1.26).
 *
 * Đổi ngày hoặc thời lượng là tự gọi lại — lịch trống phụ thuộc cả hai.
 */
export function useAvailability(venueId, date, durationMinutes) {
  const [status, setStatus] = useState('loading')
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [tick, setTick] = useState(0)

  useEffect(() => {
    if (!venueId || !date) return undefined
    let ignore = false

    fetchAvailability(venueId, { date, durationMinutes })
      .then((result) => {
        if (ignore) return
        setData(result)
        setStatus('success')
      })
      .catch((loadError) => {
        if (ignore) return
        setError(loadError?.message ?? 'Không tải được lịch trống.')
        setStatus('error')
      })

    return () => {
      ignore = true
    }
  }, [venueId, date, durationMinutes, tick])

  // Người khác vừa đặt mất khung giờ thì tải lại để thấy trạng thái mới.
  const reload = () => {
    setStatus('loading')
    setError('')
    setTick((value) => value + 1)
  }

  return { status, data, error, reload }
}
