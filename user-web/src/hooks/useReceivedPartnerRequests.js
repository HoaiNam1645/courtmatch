import { useCallback, useEffect, useState } from 'react'
import { getReceivedPartnerRequests, getSentPartnerRequests } from '../api/partnerRequests'

export function usePartnerRequests(direction, status) {
  const [state, setState] = useState({
    status: 'loading', data: [], page: 0, totalElements: 0, totalPages: 0, error: '',
  })

  const load = useCallback(async (page = 0) => {
    setState((current) => ({ ...current, status: 'loading', error: '' }))
    try {
      const getRequests = direction === 'sent' ? getSentPartnerRequests : getReceivedPartnerRequests
      const result = await getRequests({ status, page })
      setState({
        status: result.content.length === 0 ? 'empty' : 'success',
        data: result.content,
        page: result.page,
        totalElements: result.totalElements,
        totalPages: result.totalPages,
        error: '',
      })
    } catch (error) {
      setState((current) => ({
        ...current, status: 'error', error: error?.message ?? 'Không tải được yêu cầu ghép cặp.',
      }))
    }
  }, [direction, status])

  useEffect(() => {
    // Dong bo bo loc hien tai voi API; lan dau hook da khoi tao o trang thai loading.
    // oxlint-disable-next-line react/set-state-in-effect
    load(0)
  }, [load])
  return { ...state, load }
}
