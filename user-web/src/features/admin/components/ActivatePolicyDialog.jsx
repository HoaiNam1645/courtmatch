import { useEffect, useState } from 'react'
import { X } from 'lucide-react'
import { ApiError } from '../../../api/client'
import { activateFeePolicy, feeLabel } from '../../../api/adminFeePolicies'
import { FormAlert } from '../../../shared/FormFeedback'
import { primaryButtonClass, secondaryButtonClass } from '../../owner/ownerStyles'

/** Xác nhận kích hoạt lại một chính sách cũ (2.3.40), nêu rõ chính sách nào sẽ ngừng áp dụng. */
function ActivatePolicyDialog({ policy, current, onClose, onDone }) {
  const [busy, setBusy] = useState(false)
  const [formError, setFormError] = useState('')

  useEffect(() => {
    function onKey(event) {
      if (event.key === 'Escape' && !busy) onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose, busy])

  async function confirm() {
    if (busy) return
    setBusy(true)
    setFormError('')
    try {
      onDone(await activateFeePolicy(policy.id), `Đã kích hoạt lại chính sách "${policy.name}".`)
    } catch (failure) {
      setFormError(failure instanceof ApiError ? failure.message : 'Đã có lỗi xảy ra, vui lòng thử lại.')
      setBusy(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 grid place-items-center overflow-y-auto bg-black/40 p-4" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !busy) onClose() }}>
      <div role="dialog" aria-modal="true" aria-label="Kích hoạt lại chính sách" className="w-full max-w-md rounded-xl bg-white p-5 shadow-2xl" data-testid="activate-dialog">
        <div className="flex items-start justify-between gap-3">
          <h2 className="text-lg font-semibold text-slate-900">Kích hoạt lại chính sách</h2>
          <button type="button" className="grid size-9 place-items-center rounded-lg text-slate-500 hover:bg-slate-100" aria-label="Đóng" onClick={onClose} disabled={busy}><X className="size-5" /></button>
        </div>
        <p className="mt-3 text-sm font-medium leading-6 text-slate-700" data-testid="activate-summary">
          Từ lúc xác nhận, hoa hồng mới sẽ tính theo <strong>{policy.name}</strong> ({feeLabel(policy.feeType, policy.feeValue)})
          {current ? <>, thay cho <strong>{current.name}</strong> ({feeLabel(current.feeType, current.feeValue)}) sẽ ngừng áp dụng.</> : '.'}
        </p>
        <p className="mt-2 text-xs font-medium text-slate-500">Hoa hồng đã ghi nhận giữ nguyên mức phí lúc ghi nhận.</p>
        <div className="mt-3"><FormAlert message={formError} /></div>
        <div className="mt-4 flex justify-end gap-2">
          <button type="button" className={secondaryButtonClass} disabled={busy} onClick={onClose}>Hủy</button>
          <button type="button" className={primaryButtonClass} disabled={busy} onClick={confirm} data-testid="activate-confirm">{busy ? 'Đang kích hoạt...' : 'Kích hoạt'}</button>
        </div>
      </div>
    </div>
  )
}

export { ActivatePolicyDialog }
