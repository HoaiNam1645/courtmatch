import { useEffect, useState } from 'react'
import { X } from 'lucide-react'
import { ApiError } from '../../../api/client'
import { createAdminVoucher, emptyVoucherForm, formFromVoucher, updateAdminVoucher, voucherProblems } from '../../../api/adminVouchers'
import { FieldError, FormAlert } from '../../../shared/FormFeedback'
import { inputClass, primaryButtonClass, secondaryButtonClass, textareaClass } from '../../owner/ownerStyles'

function Field({ label, hint, error, children, required }) {
  return (
    <label className="block">
      <span className="text-sm font-semibold text-slate-900">{label}{required && <span className="text-red-600"> *</span>}</span>
      {children}
      {hint && !error && <p className="mt-1 text-xs font-medium text-slate-500">{hint}</p>}
      <FieldError message={error} />
    </label>
  )
}

/**
 * Tạo hoặc sửa voucher (B4). Điều kiện nhận để trống nghĩa là không yêu cầu. Voucher đã có lượt dùng thì
 * không đổi được mức giảm; mã không đổi sau khi tạo.
 */
function VoucherDialog({ voucher = null, onClose, onSaved }) {
  const editing = Boolean(voucher)
  const frozen = editing && voucher.usedCount + (voucher.redemptions?.length ?? 0) > 0
  const [form, setForm] = useState(editing ? formFromVoucher(voucher) : emptyVoucherForm())
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
    setForm((current) => ({ ...current, ...patch }))
    setErrors({})
  }

  async function submit(event) {
    event.preventDefault()
    if (busy) return
    const problems = voucherProblems(form, { editing })
    setErrors(problems)
    setFormError('')
    if (Object.keys(problems).length > 0) return
    setBusy(true)
    try {
      const saved = editing ? await updateAdminVoucher(voucher.id, form) : await createAdminVoucher(form)
      onSaved(saved, editing ? `Đã cập nhật voucher ${saved.code}.` : `Đã tạo voucher ${saved.code}.`)
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

  return (
    <div className="fixed inset-0 z-50 grid place-items-center overflow-y-auto bg-black/40 p-4" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !busy) onClose() }}>
      <form role="dialog" aria-modal="true" aria-label={editing ? 'Sửa voucher' : 'Tạo voucher'} noValidate onSubmit={submit} className="w-full max-w-2xl rounded-xl bg-white p-5 shadow-2xl" data-testid="voucher-dialog">
        <div className="flex items-start justify-between gap-3">
          <h2 className="text-lg font-semibold text-slate-900">{editing ? `Sửa voucher ${voucher.code}` : 'Tạo voucher mới'}</h2>
          <button type="button" className="grid size-9 place-items-center rounded-lg text-slate-500 hover:bg-slate-100" aria-label="Đóng" onClick={onClose} disabled={busy}><X className="size-5" /></button>
        </div>
        <div className="mt-4 grid gap-4 sm:grid-cols-2">
          <Field label="Mã voucher" required error={errors.code} hint="Chữ, số, gạch nối; tự viết hoa. Không đổi được sau khi tạo.">
            <input className={`${inputClass} mt-2 uppercase`} aria-label="Mã voucher" maxLength={40} value={form.code} disabled={editing} onChange={(event) => change({ code: event.target.value })} aria-invalid={Boolean(errors.code)} data-testid="voucher-form-code" />
          </Field>
          <Field label="Tên voucher" required error={errors.name}>
            <input className={`${inputClass} mt-2`} aria-label="Tên voucher" maxLength={150} value={form.name} onChange={(event) => change({ name: event.target.value })} aria-invalid={Boolean(errors.name)} data-testid="voucher-form-name" />
          </Field>
          <div className="sm:col-span-2">
            <Field label="Mô tả" error={errors.description}>
              <textarea className={`${textareaClass} mt-2 h-16`} aria-label="Mô tả voucher" maxLength={1000} value={form.description} onChange={(event) => change({ description: event.target.value })} />
            </Field>
          </div>
          <Field label="Phần trăm giảm (%)" required error={errors.discountPercent} hint={frozen ? 'Voucher đã có lượt dùng nên không đổi được mức giảm.' : 'Courtly chịu phần giảm này.'}>
            <input className={`${inputClass} mt-2`} aria-label="Phần trăm giảm" inputMode="decimal" value={form.discountPercent} disabled={frozen} onChange={(event) => change({ discountPercent: event.target.value })} aria-invalid={Boolean(errors.discountPercent)} data-testid="voucher-form-percent" />
          </Field>
          <Field label="Trần giảm mỗi booking (đồng)" error={errors.maxDiscountAmount} hint="Để trống nếu không giới hạn.">
            <input className={`${inputClass} mt-2`} aria-label="Trần giảm" inputMode="numeric" value={form.maxDiscountAmount} disabled={frozen} onChange={(event) => change({ maxDiscountAmount: event.target.value })} aria-invalid={Boolean(errors.maxDiscountAmount)} data-testid="voucher-form-cap" />
          </Field>
          <Field label="Bắt đầu" required error={errors.validFrom}>
            <input type="datetime-local" className={`${inputClass} mt-2`} aria-label="Bắt đầu" value={form.validFrom} onChange={(event) => change({ validFrom: event.target.value })} data-testid="voucher-form-from" />
          </Field>
          <Field label="Hết hạn" required error={errors.validUntil}>
            <input type="datetime-local" className={`${inputClass} mt-2`} aria-label="Hết hạn" value={form.validUntil} onChange={(event) => change({ validUntil: event.target.value })} aria-invalid={Boolean(errors.validUntil)} data-testid="voucher-form-until" />
          </Field>
          <div className="sm:col-span-2">
            <Field label="Tổng số lượt phát hành" error={errors.totalLimit} hint="Mỗi người chơi chỉ dùng một lần. Để trống nếu không giới hạn tổng số lượt.">
              <input className={`${inputClass} mt-2 sm:max-w-xs`} aria-label="Tổng số lượt" inputMode="numeric" value={form.totalLimit} onChange={(event) => change({ totalLimit: event.target.value })} aria-invalid={Boolean(errors.totalLimit)} data-testid="voucher-form-limit" />
            </Field>
          </div>
          <fieldset className="rounded-xl border border-slate-200 p-4 sm:col-span-2">
            <legend className="px-1 text-sm font-semibold text-slate-900">Điều kiện nhận (để trống nếu không yêu cầu)</legend>
            <div className="grid gap-3 sm:grid-cols-3">
              <Field label="Elo tối thiểu" error={errors.minElo}>
                <input className={`${inputClass} mt-1`} aria-label="Elo tối thiểu" inputMode="decimal" value={form.minElo} onChange={(event) => change({ minElo: event.target.value })} aria-invalid={Boolean(errors.minElo)} data-testid="voucher-form-elo" />
              </Field>
              <Field label="Số trận có đặt sân" error={errors.minBookingMatches}>
                <input className={`${inputClass} mt-1`} aria-label="Số trận tối thiểu" inputMode="numeric" value={form.minBookingMatches} onChange={(event) => change({ minBookingMatches: event.target.value })} aria-invalid={Boolean(errors.minBookingMatches)} data-testid="voucher-form-matches" />
              </Field>
              <Field label="Tuần hoạt động liên tiếp" error={errors.minActiveWeeks}>
                <input className={`${inputClass} mt-1`} aria-label="Số tuần tối thiểu" inputMode="numeric" value={form.minActiveWeeks} onChange={(event) => change({ minActiveWeeks: event.target.value })} aria-invalid={Boolean(errors.minActiveWeeks)} data-testid="voucher-form-weeks" />
              </Field>
            </div>
            <p className="mt-2 text-xs font-medium text-slate-500">Chỉ tính trận gắn với booking trên Courtly, đã xác nhận kết quả. Trận nhập thủ công không tính.</p>
          </fieldset>
        </div>
        <div className="mt-4"><FormAlert message={formError} /></div>
        <div className="mt-4 flex justify-end gap-2">
          <button type="button" className={secondaryButtonClass} disabled={busy} onClick={onClose}>Hủy</button>
          <button type="submit" className={primaryButtonClass} disabled={busy} data-testid="voucher-form-submit">{busy ? 'Đang lưu...' : editing ? 'Lưu thay đổi' : 'Tạo voucher'}</button>
        </div>
      </form>
    </div>
  )
}

export { VoucherDialog }
