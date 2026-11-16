import { useEffect, useState } from 'react'
import { X } from 'lucide-react'
import { ApiError } from '../../../api/client'
import { recordAdminPayment } from '../../../api/adminCommissionPayments'
import { fetchAdminSettlements } from '../../../api/adminSettlements'
import { settlementStatusMeta } from '../../../api/ownerSettlements'
import { FieldError, FormAlert } from '../../../shared/FormFeedback'
import { formatCurrency } from '../../../shared/utils'
import { formatIsoDate, shiftIsoDate, todayInVietnam } from '../../owner/ownerDates'
import { inputClass, primaryButtonClass, secondaryButtonClass, textareaClass } from '../../owner/ownerStyles'

function problemsOf(form) {
  const problems = {}
  const today = todayInVietnam()
  if (!form.ownerId) problems.ownerId = 'Chọn chủ sân.'
  if (!/^\d+$/.test(form.amount.trim()) || Number(form.amount) < 1) problems.amount = 'Nhập số tiền là số nguyên đồng, tối thiểu 1.'
  else if (form.amount.trim().length > 10) problems.amount = 'Số tiền quá lớn.'
  if (!form.paidOn) problems.paidOn = 'Chọn ngày thanh toán.'
  else if (form.paidOn > today) problems.paidOn = 'Ngày thanh toán không được sau hôm nay.'
  else if (form.paidOn < shiftIsoDate(today, -365)) problems.paidOn = 'Chỉ ghi nhận thanh toán trong 365 ngày gần đây.'
  const reference = form.referenceCode.trim()
  if (reference.length < 3) problems.referenceCode = 'Mã tham chiếu từ 3 đến 100 ký tự.'
  else if (reference.length > 100) problems.referenceCode = 'Mã tham chiếu tối đa 100 ký tự.'
  if (form.proofUrl.length > 500) problems.proofUrl = 'Đường dẫn tối đa 500 ký tự.'
  if (form.note.length > 500) problems.note = 'Ghi chú tối đa 500 ký tự.'
  return problems
}

/**
 * Ghi nhận giao dịch đối soát (2.3.49): chủ sân đã trả ngoài hệ thống (tiền mặt, chuyển khoản trực tiếp) nên không có
 * báo cáo. Ghi xong là đã xác nhận, áp dụng cùng quy tắc với 2.3.47. `fixed` cố định chủ sân và kỳ khi mở từ chi tiết kỳ.
 */
function RecordPaymentDialog({ owners, fixed = null, onClose, onDone }) {
  const [form, setForm] = useState({ ownerId: fixed?.ownerId ?? '', settlementId: fixed?.settlementId ?? '', amount: fixed?.remaining ? String(fixed.remaining) : '', paidOn: todayInVietnam(), referenceCode: '', proofUrl: '', note: '' })
  const [settlementOptions, setSettlementOptions] = useState([])
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

  // Các kỳ còn nợ của chủ sân đã chọn để chọn nơi ghi nhận khoản thanh toán.
  useEffect(() => {
    if (!form.ownerId || fixed) {
      setSettlementOptions([])
      return undefined
    }
    let active = true
    fetchAdminSettlements({ ownerId: form.ownerId, size: 100 })
      .then((result) => { if (active) setSettlementOptions(result.rows.filter((row) => row.remaining > 0 && row.status !== 'disputed')) })
      .catch(() => { if (active) setSettlementOptions([]) })
    return () => { active = false }
  }, [form.ownerId, fixed])

  function change(patch) {
    setForm((current) => ({ ...current, ...patch }))
    setErrors({})
    setFormError('')
  }

  async function submit(event) {
    event.preventDefault()
    if (busy) return
    const problems = problemsOf(form)
    setErrors(problems)
    if (Object.keys(problems).length > 0) return
    setBusy(true)
    try {
      const result = await recordAdminPayment(form)
      const offset = result.courtlyOffsetAmount > 0 ? ` Đã bù trừ ${formatCurrency(result.courtlyOffsetAmount)} Courtly còn nợ chủ sân.` : ''
      onDone(result, `Đã ghi nhận ${formatCurrency(result.amount)} chủ sân ${result.ownerName} thanh toán (${result.referenceCode}).${offset}`)
    } catch (failure) {
      if (failure instanceof ApiError) {
        setErrors({ ownerId: failure.fieldErrors?.ownerId, settlementId: failure.fieldErrors?.settlementId, amount: failure.fieldErrors?.amount, paidOn: failure.fieldErrors?.paidOn, referenceCode: failure.fieldErrors?.referenceCode, proofUrl: failure.fieldErrors?.proofUrl, note: failure.fieldErrors?.note })
        setFormError(failure.message)
      } else {
        setFormError('Đã có lỗi xảy ra, vui lòng thử lại.')
      }
      setBusy(false)
    }
  }

  return (
    <div className="fixed inset-0 z-[60] grid place-items-center overflow-y-auto bg-black/40 p-4" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !busy) onClose() }}>
      <form role="dialog" aria-modal="true" aria-label="Ghi nhận thanh toán công nợ" noValidate onSubmit={submit} className="w-full max-w-xl rounded-xl bg-white p-5 shadow-2xl" data-testid="record-dialog">
        <div className="flex items-start justify-between gap-3">
          <h2 className="text-lg font-semibold text-slate-900">Ghi nhận thanh toán công nợ</h2>
          <button type="button" className="grid size-9 place-items-center rounded-lg text-slate-500 hover:bg-slate-100" aria-label="Đóng" onClick={onClose} disabled={busy}><X className="size-5" /></button>
        </div>
        <p className="mt-2 text-sm font-medium text-slate-600">Dùng khi chủ sân đã trả hoa hồng ngoài hệ thống (tiền mặt, chuyển khoản trực tiếp) và không gửi báo cáo. Ghi xong là đã xác nhận, công nợ giảm ngay và không hoàn tác được.</p>
        <div className="mt-4 grid gap-4 sm:grid-cols-2">
          <label className="block sm:col-span-2">
            <span className="text-sm font-semibold text-slate-900">Chủ sân <span className="text-red-600">*</span></span>
            <select className={`${inputClass} mt-2`} aria-label="Chủ sân" value={form.ownerId} disabled={Boolean(fixed)} onChange={(event) => change({ ownerId: event.target.value, settlementId: '' })} aria-invalid={Boolean(errors.ownerId)} data-testid="record-owner">
              <option value="">Chọn chủ sân</option>
              {owners.map((owner) => <option key={owner.id} value={owner.id}>{owner.fullName} · {owner.email}</option>)}
            </select>
            <FieldError message={errors.ownerId} />
          </label>
          <label className="block sm:col-span-2">
            <span className="text-sm font-semibold text-slate-900">Áp dụng cho</span>
            {fixed
              ? <p className="mt-2 rounded-lg bg-slate-50 p-3 text-sm font-medium text-slate-700" data-testid="record-target">{fixed.label}</p>
              : (
                <select className={`${inputClass} mt-2`} aria-label="Áp dụng cho" value={form.settlementId} onChange={(event) => change({ settlementId: event.target.value })} disabled={!form.ownerId} data-testid="record-settlement">
                  <option value="">Công nợ chung (hoa hồng cũ nhất chưa thuộc kỳ)</option>
                  {settlementOptions.map((item) => <option key={item.id} value={item.id}>Kỳ {formatIsoDate(item.periodFrom)} – {formatIsoDate(item.periodTo)} · {settlementStatusMeta(item.status).label} · còn {formatCurrency(item.remaining)}</option>)}
                </select>
              )}
            <FieldError message={errors.settlementId} />
          </label>
          <label className="block">
            <span className="text-sm font-semibold text-slate-900">Số tiền (đồng) <span className="text-red-600">*</span></span>
            <input className={`${inputClass} mt-2`} aria-label="Số tiền" inputMode="numeric" value={form.amount} onChange={(event) => change({ amount: event.target.value })} aria-invalid={Boolean(errors.amount)} data-testid="record-amount" />
            <FieldError message={errors.amount} />
          </label>
          <label className="block">
            <span className="text-sm font-semibold text-slate-900">Ngày thanh toán <span className="text-red-600">*</span></span>
            <input type="date" className={`${inputClass} mt-2`} aria-label="Ngày thanh toán" max={todayInVietnam()} value={form.paidOn} onChange={(event) => change({ paidOn: event.target.value })} aria-invalid={Boolean(errors.paidOn)} data-testid="record-paid-on" />
            <FieldError message={errors.paidOn} />
          </label>
          <label className="block sm:col-span-2">
            <span className="text-sm font-semibold text-slate-900">Mã tham chiếu <span className="text-red-600">*</span></span>
            <input className={`${inputClass} mt-2`} aria-label="Mã tham chiếu" maxLength={100} value={form.referenceCode} onChange={(event) => change({ referenceCode: event.target.value })} aria-invalid={Boolean(errors.referenceCode)} data-testid="record-reference" />
            <span className="mt-1 block text-xs font-medium text-slate-500">Mã giao dịch ngân hàng hoặc số biên nhận; mỗi chủ sân chỉ dùng một lần.</span>
            <FieldError message={errors.referenceCode} />
          </label>
          <label className="block sm:col-span-2">
            <span className="text-sm font-semibold text-slate-900">Đường dẫn bằng chứng</span>
            <input className={`${inputClass} mt-2`} aria-label="Đường dẫn bằng chứng" maxLength={500} value={form.proofUrl} onChange={(event) => change({ proofUrl: event.target.value })} aria-invalid={Boolean(errors.proofUrl)} data-testid="record-proof" />
            <FieldError message={errors.proofUrl} />
          </label>
          <label className="block sm:col-span-2">
            <span className="text-sm font-semibold text-slate-900">Ghi chú</span>
            <textarea className={`${textareaClass} mt-2 h-16`} aria-label="Ghi chú giao dịch" maxLength={500} value={form.note} onChange={(event) => change({ note: event.target.value })} />
            <FieldError message={errors.note} />
          </label>
        </div>
        <div className="mt-4"><FormAlert message={formError} /></div>
        <div className="mt-4 flex justify-end gap-2">
          <button type="button" className={secondaryButtonClass} disabled={busy} onClick={onClose}>Hủy</button>
          <button type="submit" className={primaryButtonClass} disabled={busy} data-testid="record-submit">{busy ? 'Đang ghi nhận...' : 'Ghi nhận thanh toán'}</button>
        </div>
      </form>
    </div>
  )
}

export { RecordPaymentDialog }
