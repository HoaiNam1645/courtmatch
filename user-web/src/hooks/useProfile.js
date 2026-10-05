import { useCallback, useEffect, useState } from 'react'
import { fetchProfile } from '../api/profile'

/**
 * Tải hồ sơ người chơi và quản lý bốn trạng thái loading / success / empty / error.
 *
 * Dùng chung cho /profile, /profile/edit và /profile/preferences.
 */
export function useProfile() {
  const [status, setStatus] = useState('loading')
  const [data, setData] = useState(null)
  const [error, setError] = useState('')

  // showSpinner chỉ bật khi người dùng bấm "Thử lại"; lần tải đầu đã ở trạng thái loading.
  const load = useCallback(async (showSpinner = false) => {
    if (showSpinner) {
      setStatus('loading')
      setError('')
    }
    try {
      setData(await fetchProfile())
      setStatus('success')
    } catch (loadError) {
      setError(loadError?.message ?? 'Không tải được hồ sơ.')
      setStatus('error')
    }
  }, [])

  useEffect(() => {
    // Effect nay dong bo voi he thong ngoai (goi API) - dung truong hop ma rule cho phep.
    // oxlint-disable-next-line react/set-state-in-effect
    load()
  }, [load])

  const reload = useCallback(() => load(true), [load])

  return { status, data, error, reload, setData }
}
