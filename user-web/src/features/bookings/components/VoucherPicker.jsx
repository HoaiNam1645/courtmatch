import { useState } from 'react'
import { Ticket, X } from 'lucide-react'
import { ApiError } from '../../../api/client'
import { discountLabel, fetchMyVouchers, previewVoucher, unmetLabel } from '../../../api/vouchers'
import { useApiResource } from '../../../hooks/useApiResource'
import { classNames, formatCurrency } from '../../../shared/utils'
import { formatError } from '../../../shared/formatError'
import { ProfileSectionTitle } from '../../profile/components/ProfileUi'

const UNAVAILABLE = { used: 'Bạn đã dùng', exhausted: 'Đã hết lượt' }

/**
 * Chọn voucher khi đặt sân (B4). Số tiền giảm do server tính qua API xem trước; Courtly chịu phần giảm nên
 * người chơi chỉ trả phần sau giảm, chủ sân vẫn nhận đủ giá gốc.
 *
 * @param {{ pending: object, applied: object|null, onApply: (preview: object|null) => void }} props
 */
function VoucherPicker({ pending, applied, onApply }) {
  const { status, data, error, reload } = useApiResource(fetchMyVouchers, [])
  const [code, setCode] = useState('')
  const [busyCode, setBusyCode] = useState('')
  const [problem, setProblem] = useState('')

  async function apply(voucherCode) {
    const clean = voucherCode.trim()
    if (!clean || busyCode) return
    setBusyCode(clean)
    setProblem('')
    try {
      onApply(await previewVoucher({ voucherCode: clean, courtId: pending.courtId, startTime: pending.startAt, durationMinutes: pending.durationMinutes }))
      setCode('')
    } catch (failure) {
      onApply(null)
      if (failure instanceof ApiError) {
        const detail = failure.fieldErrors && Object.keys(failure.fieldErrors).length > 0 && failure.message.includes('dieu kien')
          ? ` (${Object.entries(failure.fieldErrors).map(([key, value]) => `${key}: ${value}`).join('; ')})`
          : ''
        setProblem(`${failure.message}${detail}`)
      } else {
        setProblem('Không kiểm tra được voucher, vui lòng thử lại.')
      }
    } finally {
      setBusyCode('')
    }
  }

  return (
    <section className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_12px_34px_rgba(24,65,44,0.08)]" data-testid="voucher-picker">
      <ProfileSectionTitle icon={Ticket} eyebrow="Ưu đãi" title="Voucher giảm giá" />
      {applied ? (
        <div className="mt-5 flex items-start justify-between gap-3 rounded-xl bg-lime-50 p-4 ring-1 ring-lime-200" data-testid="voucher-applied">
          <div>
            <p className="text-sm font-semibold text-emerald-950">{applied.name} <span className="text-xs font-bold text-emerald-700">({applied.code})</span></p>
            <p className="mt-1 text-sm font-medium text-emerald-800" data-testid="voucher-applied-discount">Giảm {formatCurrency(applied.discountAmount)} · Courtly chịu phần giảm này</p>
          </div>
          <button type="button" className="inline-flex h-9 items-center gap-1 rounded-lg border border-emerald-200 px-3 text-xs font-semibold text-emerald-800" data-testid="voucher-remove" onClick={() => { onApply(null); setProblem('') }}><X className="size-3.5" />Bỏ</button>
        </div>
      ) : (
        <>
          <div className="mt-5 flex gap-2">
            <input className="h-11 min-w-0 flex-1 rounded-lg border border-stone-200 px-3 text-sm font-semibold uppercase outline-none focus:border-emerald-500" aria-label="Mã voucher" placeholder="Nhập mã voucher" maxLength={40} value={code} onChange={(event) => { setCode(event.target.value); setProblem('') }} onKeyDown={(event) => { if (event.key === 'Enter') { event.preventDefault(); apply(code) } }} data-testid="voucher-code" />
            <button type="button" className="h-11 shrink-0 rounded-lg bg-emerald-700 px-4 text-sm font-semibold text-white disabled:opacity-50" disabled={!code.trim() || Boolean(busyCode)} data-testid="voucher-apply" onClick={() => apply(code)}>{busyCode ? 'Đang kiểm tra...' : 'Áp dụng'}</button>
          </div>
          {status === 'loading' && <p className="mt-3 text-xs font-medium text-stone-500">Đang tải voucher của bạn...</p>}
          {status === 'error' && <p className="mt-3 text-xs font-medium text-red-600" role="alert">{error?.message} <button type="button" className="font-semibold underline" onClick={reload}>Thử lại</button></p>}
          {status === 'success' && data.length === 0 && <p className="mt-3 text-xs font-medium text-stone-500" data-testid="voucher-none">Hiện chưa có voucher nào đang phát hành.</p>}
          {status === 'success' && data.length > 0 && (
            <ul className="mt-4 grid gap-2" data-testid="voucher-list">
              {data.map((voucher) => {
                const usable = voucher.status === 'active' && voucher.eligible
                return (
                  <li key={voucher.id} data-testid={`voucher-${voucher.code}`} className={classNames('rounded-xl border p-3', usable ? 'border-emerald-200' : 'border-stone-200 bg-stone-50')}>
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <div>
                        <p className="text-sm font-semibold text-emerald-950">{voucher.name}</p>
                        <p className="text-xs font-medium text-stone-500">{voucher.code} · {discountLabel(voucher, formatCurrency)} · hết hạn {new Date(voucher.validUntil).toLocaleDateString('vi-VN')}</p>
                      </div>
                      {usable
                        ? <button type="button" className="h-9 rounded-lg bg-lime-300 px-3 text-xs font-semibold text-emerald-950 disabled:opacity-50" disabled={Boolean(busyCode)} data-testid="voucher-use" onClick={() => apply(voucher.code)}>Dùng</button>
                        : <span className="rounded-full bg-stone-200 px-2.5 py-1 text-xs font-bold text-stone-600" data-testid="voucher-unavailable">{UNAVAILABLE[voucher.status] ?? 'Chưa đủ điều kiện'}</span>}
                    </div>
                    {voucher.status === 'active' && !voucher.eligible && <p className="mt-2 text-xs font-medium text-stone-500" data-testid="voucher-unmet">{voucher.unmet.map(unmetLabel).join('; ')}</p>}
                  </li>
                )
              })}
            </ul>
          )}
        </>
      )}
      {formatError(problem)}
    </section>
  )
}

export { VoucherPicker }
