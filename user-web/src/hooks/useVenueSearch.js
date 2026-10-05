import { useCallback, useEffect, useRef, useState } from 'react'
import { searchVenues } from '../api/venues'

/** Chờ người dùng ngừng gõ rồi mới gọi API, tránh gọi mỗi lần nhấn phím. */
const DEBOUNCE_MS = 300

/**
 * Tìm kiếm sân qua API, quản lý bốn trạng thái loading / success / empty / error.
 *
 * @param {object} params tham số tìm kiếm; đổi params là tự gọi lại
 */
export function useVenueSearch(params) {
  const [status, setStatus] = useState('loading')
  const [result, setResult] = useState({ venues: [], totalElements: 0, totalPages: 0, page: 0 })
  const [error, setError] = useState('')
  const [reloadTick, setReloadTick] = useState(0)

  // So sánh theo nội dung để không gọi lại khi object mới nhưng giá trị như cũ.
  const key = JSON.stringify(params)
  const requestIdRef = useRef(0)

  useEffect(() => {
    const currentParams = JSON.parse(key)
    const requestId = ++requestIdRef.current
    const timer = window.setTimeout(() => {
      searchVenues(currentParams)
        .then((data) => {
          // Bỏ qua phản hồi của lời gọi cũ về sau lời gọi mới.
          if (requestId !== requestIdRef.current) return
          setResult(data)
          setStatus(data.venues.length === 0 ? 'empty' : 'success')
        })
        .catch((searchError) => {
          if (requestId !== requestIdRef.current) return
          setError(searchError?.message ?? 'Không tải được danh sách sân.')
          setStatus('error')
        })
    }, DEBOUNCE_MS)

    return () => window.clearTimeout(timer)
  }, [key, reloadTick])

  const reload = useCallback(() => {
    setStatus('loading')
    setError('')
    setReloadTick((tick) => tick + 1)
  }, [])

  return { status, ...result, error, reload }
}
