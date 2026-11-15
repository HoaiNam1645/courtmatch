import { useEffect, useState } from 'react'
import { X } from 'lucide-react'
import { ApiError } from '../../../api/client'
import { createFeePolicy, feeLabel, feeValueProblem, FEE_TYPE_LABEL } from '../../../api/adminFeePolicies'
import { FieldError, FormAlert } from '../../../shared/FormFeedback'
import { inputClass, primaryButtonClass, secondaryButtonClass } from '../../owner/ownerStyles'

/** Hộp thoại tạo chính sách phí mới (2.3.39); chính sách có hiệu lực ngay khi xác nhận. */
function FeePolicyDialog({ current, onClose, onCreated }) {
  const [form, setForm] = useState({ name: '', feeType: 'percentage', feeValue: '' })
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    function onKey(event) {
      if (event.key === 'Escape' && !busy) onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose, busy])

  function change(patch) {
    setForm((currentForm) => ({ ...currentForm, ...patch }))
    setErrors({})
  }

  async function submit(event) {
    event.preventDefault()
    if (busy) return
    const next = {}
    if (form.name.trim().length < 2) next.name = 'Tên chính sách tối thiểu 2 ký tự'
    else if (form.name.trim().length > 150) next.name = 'Tên chính sách tối đa 150 ký tự'
    const valueProblem = feeValueProblem(form.feeType, form.feeValue)
    if (valueProblem) next.feeValue = valueProblem
    setErrors(next)
    setFormError('')
    if (Object.keys(next).length > 0) return
    setBusy(true)
    try {
      const updated = await createFeePolicy(form)
      onCreated(updated, `Đã áp dụng chính sách "${form.name.trim()}".`)
    } catch (failure) {
      if (failure instanceof ApiError) {
        setErrors(failure.fieldErrors ?? {})
        setFormError(failure.message)
      } else {
        setFormError('Đã có lỗi xảy ra, vui lòng thử lại.')
      }
      setBusy(false)
    }
  }

  const valid = !feeValueProblem(form.feeType, form.feeValue) && form.name.trim().length >= 2
  return (
    <div className="fixed inset-0 z-50 grid place-items-center overflow-y-auto bg-black/40 p-4" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !busy) onClose() }}>
      <form role="dialog" aria-modal="true" aria-label="Tạo chính sách phí" noValidate onSubmit={submit} className="w-full max-w-md rounded-xl bg-white p-5 shadow-2xl" data-testid="fee-dialog">
        <div className="flex items-start justify-between gap-3">
          <h2 className="text-lg font-semibold text-slate-900">Tạo chính sách phí mới</h2>
          <button type="button" className="grid size-9 place-items-center rounded-lg text-slate-500 hover:bg-slate-100" aria-label="Đóng" onClick={onClose} disabled={busy}><X className="size-5" /></button>
        </div>
        <div className="mt-4 grid gap-4">
          <label className="block">
            <span className="text-sm font-semibold text-slate-900">Tên chính sách <span className="text-red-600">*</span></span>
            <input className={`${inputClass} mt-2`} aria-label="Tên chính sách" maxLength={150} value={form.name} onChange={(event) => change({ name: event.target.value })} aria-invalid={Boolean(errors.name)} data-testid="fee-name" />
            <FieldError message={errors.name} />
          </label>
          <fieldset>
            <legend className="text-sm font-semibold text-slate-900">Loại phí <span className="text-red-600">*</span></legend>
            <div className="mt-2 grid grid-cols-2 gap-2">
              {Object.entries(FEE_TYPE_LABEL).map(([value, label]) => (
                <label key={value} className={`flex cursor-pointer items-center gap-2 rounded-lg border p-3 text-sm font-semibold ${form.feeType === value ? 'border-slate-800 bg-slate-50' : 'border-slate-200'}`}>
                  <input type="radio" name="fee-type" value={value} checked={form.feeType === value} onChange={() => change({ feeType: value })} data-testid={`fee-type-${value}`} />{label}
                </label>
              ))}
            </div>
            <FieldError message={errors.feeType} />
          </fieldset>
          <label className="block">
            <span className="text-sm font-semibold text-slate-900">{form.feeType === 'percentage' ? 'Phần trăm hoa hồng (0 – 100)' : 'Số tiền mỗi booking (đồng)'} <span className="text-red-600">*</span></span>
            <div className="mt-2 flex items-center gap-2">
              <input className={inputClass} aria-label="Giá trị phí" inputMode="decimal" value={form.feeValue} onChange={(event) => change({ feeValue: event.target.value })} aria-invalid={Boolean(errors.feeValue)} data-testid="fee-value" />
              <span className="text-sm font-semibold text-slate-500">{form.feeType === 'percentage' ? '%' : 'đ'}</span>
            </div>
            <FieldError message={errors.feeValue} />
          </label>

          <div className="rounded-lg bg-slate-50 p-3 text-xs font-medium leading-5 text-slate-600" data-testid="fee-preview">
            {valid ? <>Từ lúc xác nhận, hoa hồng mới sẽ tính <strong className="text-slate-900">{feeLabel(form.feeType, Number(form.feeValue.replace(',', '.')))}</strong>{current ? <>, thay cho "{current.name}" ({feeLabel(current.feeType, current.feeValue)}).</> : '.'}</> : 'Nhập tên và giá trị hợp lệ để xem tác động.'}
            <br />Hoa hồng đã ghi nhận giữ nguyên mức phí cũ.
          </div>
          <FormAlert message={formError} />
          <div className="flex justify-end gap-2">
            <button type="button" className={secondaryButtonClass} disabled={busy} onClick={onClose}>Hủy</button>
            <button type="submit" className={primaryButtonClass} disabled={busy} data-testid="fee-submit">{busy ? 'Đang áp dụng...' : 'Áp dụng chính sách'}</button>
          </div>
        </div>
      </form>
    </div>
  )
}

export { FeePolicyDialog }
