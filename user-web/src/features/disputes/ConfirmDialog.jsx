import { LoaderCircle } from 'lucide-react'
import { useEffect, useState } from 'react'
import { FormAlert } from '../../shared/FormFeedback'

/** Hộp xác nhận cho thao tác khó hoàn tác (huỷ báo cáo, gửi giải trình...); lỗi từ server hiện ngay trong hộp. */
export function ConfirmDialog({ title, children, confirmLabel, cancelLabel = 'Quay lại', danger = false, onConfirm, onClose, testId = 'confirm-dialog' }) {
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
      setError(failure?.message ?? 'Đã có lỗi xảy ra, vui lòng thử lại.')
      setBusy(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-black/45 p-4" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !busy) onClose() }}>
      <div role="alertdialog" aria-modal="true" aria-label={title} data-testid={testId} className="w-full max-w-sm rounded-2xl bg-white p-5 shadow-2xl">
        <h2 className="text-lg font-semibold text-emerald-950">{title}</h2>
        <div className="mt-2 text-sm font-medium leading-6 text-stone-600">{children}</div>
        <div className="mt-3"><FormAlert message={error} /></div>
        <div className="mt-4 flex justify-end gap-2">
          <button type="button" className="h-11 rounded-lg border border-emerald-200 bg-white px-5 text-sm font-semibold text-emerald-800 disabled:opacity-60" onClick={onClose} disabled={busy}>{cancelLabel}</button>
          <button type="button" data-testid={`${testId}-confirm`} onClick={confirm} disabled={busy}
            className={`inline-flex h-11 items-center gap-2 rounded-lg px-5 text-sm font-semibold text-white disabled:opacity-60 ${danger ? 'bg-red-600' : 'bg-emerald-700'}`}>
            {busy && <LoaderCircle className="size-4 animate-spin" />}{busy ? 'Đang xử lý...' : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}
