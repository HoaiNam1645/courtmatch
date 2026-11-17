import { useCallback, useEffect, useState } from 'react'
import { fetchVenueReviewSummary, fetchVenueReviews } from '../api/venues'

const PAGE_SIZE = 6

/**
 * Đánh giá của sân ở trang chi tiết (2.1.23, 2.3.80): tổng hợp theo sao, lọc theo sao và tải thêm theo trang.
 * Lỗi tải chỉ làm hỏng khối đánh giá, không chặn cả trang chi tiết.
 */
export function useVenueReviews(slugOrId) {
  const [rating, setRating] = useState(null)
  const [reviews, setReviews] = useState([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [summary, setSummary] = useState(null)
  const [status, setStatus] = useState('loading')
  const [loadingMore, setLoadingMore] = useState(false)
  const [tick, setTick] = useState(0)

  useEffect(() => {
    if (!slugOrId) return undefined
    let ignore = false
    Promise.all([fetchVenueReviews(slugOrId, { page: 0, size: PAGE_SIZE, rating }), fetchVenueReviewSummary(slugOrId)])
      .then(([list, stats]) => {
        if (ignore) return
        setReviews(list.reviews)
        setPage(0)
        setTotalPages(list.totalPages)
        setTotalElements(list.totalElements)
        setSummary(stats)
        setStatus(list.reviews.length === 0 ? 'empty' : 'success')
      })
      .catch(() => {
        if (!ignore) setStatus('error')
      })
    return () => {
      ignore = true
    }
  }, [slugOrId, rating, tick])

  const loadMore = useCallback(async () => {
    setLoadingMore(true)
    try {
      const next = await fetchVenueReviews(slugOrId, { page: page + 1, size: PAGE_SIZE, rating })
      setReviews((current) => [...current, ...next.reviews])
      setPage(next.page)
      setTotalPages(next.totalPages)
    } catch {
      // Giữ nguyên danh sách đã có; người dùng bấm lại để thử tiếp.
    } finally {
      setLoadingMore(false)
    }
  }, [slugOrId, page, rating])

  const reload = useCallback(() => setTick((value) => value + 1), [])

  return { reviews, totalElements, summary, status, rating, setRating, hasMore: page + 1 < totalPages, loadMore, loadingMore, reload }
}
