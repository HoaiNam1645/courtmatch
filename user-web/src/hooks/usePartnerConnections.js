import { useCallback, useEffect, useState } from 'react'
import { getPartnerConnections } from '../api/partners'

export function usePartnerConnections() {
  const [state, setState] = useState({
    status: 'loading', data: [], page: 0, totalElements: 0, totalPages: 0, error: '',
  })
  const load = useCallback(async (page = 0) => {
    setState((current) => ({ ...current, status: 'loading', error: '' }))
    try {
      const result = await getPartnerConnections({ page })
      setState({
        status: result.content.length === 0 ? 'empty' : 'success',
        data: result.content,
        page: result.page,
        totalElements: result.totalElements,
        totalPages: result.totalPages,
        error: '',
      })
    } catch (error) {
      setState((current) => ({ ...current, status: 'error', error: error?.message ?? 'Không tải được danh sách đối tác.' }))
    }
  }, [])

  useEffect(() => {
    // Effect dong bo man hinh voi danh sach ket noi tu backend.
    // oxlint-disable-next-line react/set-state-in-effect
    load(0)
  }, [load])
  return { ...state, load }
}
