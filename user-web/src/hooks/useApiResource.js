import { useCallback, useEffect, useRef, useState } from 'react'

/**
 * Tải một tài nguyên từ API và quản lý các trạng thái loading / success / empty / error.
 *
 * @param {() => Promise<any>} loader hàm gọi API; đổi `deps` thì tải lại
 * @param {unknown[]} deps các giá trị mà loader phụ thuộc
 * @param {{ isEmpty?: (data: any) => boolean }} options
 */
export function useApiResource(loader, deps, { isEmpty } = {}) {
  const [state, setState] = useState({ status: 'loading', data: null, error: null })
  const [tick, setTick] = useState(0)
  const loaderRef = useRef(loader)
  const isEmptyRef = useRef(isEmpty)

  useEffect(() => {
    loaderRef.current = loader
    isEmptyRef.current = isEmpty
  })

  useEffect(() => {
    let ignore = false
    loaderRef.current()
      .then((data) => {
        if (ignore) return
        const empty = isEmptyRef.current ? isEmptyRef.current(data) : false
        setState({ status: empty ? 'empty' : 'success', data, error: null })
      })
      .catch((error) => {
        if (ignore) return
        setState({ status: 'error', data: null, error })
      })
    return () => {
      ignore = true
    }
    // deps do nơi gọi truyền vào; loader mới nhất luôn đọc qua ref.
    // oxlint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick])

  const reload = useCallback(() => {
    setState((current) => ({ ...current, status: 'loading', error: null }))
    setTick((value) => value + 1)
  }, [])

  return { ...state, reload }
}
