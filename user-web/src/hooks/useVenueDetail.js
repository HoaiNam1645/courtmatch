import { useCallback, useEffect, useState } from 'react'
import { fetchVenueDetail, toVenueDetailTables } from '../api/venues'

/**
 * Tải chi tiết một sân (2.1.18) theo slug hoặc id.
 *
 * Trả về `venue` (dạng thẻ sân) và `tables` (dạng bảng phẳng cho phần hiển thị cũ).
 */
export function useVenueDetail(slugOrId) {
  const [status, setStatus] = useState(slugOrId ? 'loading' : 'error')
  const [data, setData] = useState(null)
  const [error, setError] = useState(slugOrId ? '' : 'Đường dẫn không hợp lệ.')
  const [notFound, setNotFound] = useState(!slugOrId)

  const load = useCallback(async (showSpinner = false) => {
    if (!slugOrId) return
    if (showSpinner) {
      setStatus('loading')
      setError('')
      setNotFound(false)
    }
    try {
      const detail = await fetchVenueDetail(slugOrId)
      setData({ venue: detail, tables: toVenueDetailTables(detail) })
      setStatus('success')
    } catch (loadError) {
      // 404 là "sân không tồn tại", khác với lỗi mạng — giao diện hiển thị khác nhau.
      setNotFound(loadError?.status === 404)
      setError(loadError?.message ?? 'Không tải được thông tin sân.')
      setStatus('error')
    }
  }, [slugOrId])

  useEffect(() => {
    // Effect nay dong bo voi he thong ngoai (goi API) - dung truong hop rule cho phep.
    // oxlint-disable-next-line react/set-state-in-effect
    load()
  }, [load])

  return { status, data, error, notFound, reload: () => load(true) }
}
