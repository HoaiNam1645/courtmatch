import { ImageOff } from 'lucide-react'
import { useEffect, useState } from 'react'
import { fetchDisputeEvidenceBlob } from '../../api/bookingDisputes'

/**
 * Ảnh bằng chứng riêng tư: tải bằng token rồi hiện qua URL tạm (thẻ <img> không gửi được header đăng nhập).
 * Hết vòng đời component thì giải phóng URL tạm.
 */
export function EvidenceImage({ evidence, className = '', onOpen }) {
  const [state, setState] = useState({ status: 'loading', url: null })

  useEffect(() => {
    let ignore = false
    let objectUrl = null
    fetchDisputeEvidenceBlob(evidence.url)
      .then((blob) => {
        if (ignore) return
        objectUrl = URL.createObjectURL(blob)
        setState({ status: 'ready', url: objectUrl })
      })
      .catch(() => {
        if (!ignore) setState({ status: 'error', url: null })
      })
    return () => {
      ignore = true
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [evidence.url])

  if (state.status === 'error') {
    return <span className={`grid place-items-center bg-stone-100 text-stone-400 ${className}`} data-testid={`evidence-missing-${evidence.id}`}><ImageOff className="size-6" /></span>
  }
  if (state.status === 'loading') return <span className={`animate-pulse bg-stone-100 ${className}`} aria-busy="true" />
  const image = <img src={state.url} alt={evidence.originalName} className={`object-cover ${className}`} data-testid={`evidence-image-${evidence.id}`} />
  return onOpen ? <button type="button" className="block" onClick={() => onOpen(state.url, evidence)} aria-label={`Xem ảnh ${evidence.originalName}`}>{image}</button> : image
}
