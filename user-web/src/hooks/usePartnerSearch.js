import { useCallback, useState } from 'react'
import { searchPartners } from '../api/partners'

export function usePartnerSearch() {
  const [status, setStatus] = useState('idle')
  const [data, setData] = useState({ partners: [], page: 0, totalElements: 0, totalPages: 0 })
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})

  const search = useCallback(async (filters, page = 0) => {
    setStatus('loading')
    setError('')
    setFieldErrors({})
    try {
      const result = await searchPartners({ ...filters, page })
      setData(result)
      setStatus(result.partners.length === 0 ? 'empty' : 'success')
      return true
    } catch (searchError) {
      setError(searchError?.message ?? 'Không tìm được đối tác lúc này.')
      setFieldErrors(searchError?.fieldErrors ?? {})
      setStatus('error')
      return false
    }
  }, [])

  return { ...data, status, error, fieldErrors, search }
}
