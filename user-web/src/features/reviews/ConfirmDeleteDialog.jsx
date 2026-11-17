import { LoaderCircle } from 'lucide-react'
import { useEffect, useState } from 'react'
import { FormAlert } from '../../shared/FormFeedback'
import { dangerButtonClass, secondaryButtonClass } from '../owner/ownerStyles'

/** Hộp xác nhận trước khi xóa đánh giá (2.3.79); lỗi từ server hiện ngay trong hộp. */
export function ConfirmDeleteDialog({ venueName, onConfirm, onClose }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    function onKey(event) {
      if (event.key === 'Escape' && !busy) onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose, busy])

  async function confirm() {
    setBusy(true)
    setError('')
    try {
      await onConfirm()
    } catch (failure) {
      setError(failure?.message ?? 'Không xóa được đánh giá, vui lòng thử lại.')
      setBusy(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-black/45 p-4" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !busy) onClose() }}>
      <div role="alertdialog" aria-modal="true" aria-label="Xóa đánh giá" data-testid="review-delete-dialog" className="w-full max-w-sm rounded-2xl bg-white p-5 shadow-2xl">
        <h2 className="text-lg font-semibold text-emerald-950">Xóa đánh giá?</h2>
        <p className="mt-2 text-sm font-medium leading-6 text-stone-600">Đánh giá của bạn về <strong>{venueName}</strong> sẽ bị xóa khỏi trang sân. Bạn có thể viết lại nếu còn trong thời hạn đánh giá.</p>
        <div className="mt-3"><FormAlert message={error} /></div>
        <div className="mt-4 flex justify-end gap-2">
          <button type="button" className={secondaryButtonClass} onClick={onClose} disabled={busy}>Giữ lại</button>
          <button type="button" className={dangerButtonClass} data-testid="review-delete-confirm" onClick={confirm} disabled={busy}>
            {busy && <LoaderCircle className="size-4 animate-spin" />}{busy ? 'Đang xóa...' : 'Xóa đánh giá'}
          </button>
        </div>
      </div>
    </div>
  )
}
