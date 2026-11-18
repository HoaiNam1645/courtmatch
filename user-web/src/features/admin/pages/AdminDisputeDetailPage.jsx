import { CircleUserRound, ShieldCheck, Store, X } from 'lucide-react'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ADMIN_ACTOR_LABEL, ADMIN_DISPUTE_STATUS, decideRefund, fetchAdminDispute, NOTE_LIMITS } from '../../../api/adminDisputes'
import { RESOLUTION_LABEL } from '../../../api/bookingDisputes'
import { useApiResource } from '../../../hooks/useApiResource'
import { classNames, formatCurrency } from '../../../shared/utils'
import { ConfirmDialog } from '../../disputes/ConfirmDialog'
import { EvidenceImage } from '../../disputes/EvidenceImage'
import { RefundPanel } from '../../disputes/RefundPanel'
import { formatDateTime } from '../../disputes/disputeUi'
import { OwnerCard, OwnerError, OwnerLoading } from '../../owner/components/OwnerUi'
import { AdminLayout } from '../components/AdminLayout'

const ENTRY_TITLE = { created: 'Báo cáo ban đầu', supplement: 'Bổ sung thông tin', owner_response: 'Giải trình', resolution: 'Kết luận của quản trị viên' }
const PAYMENT_STATUS = { awaiting_payment: 'Chờ thanh toán', payer_reported: 'Người chơi báo đã trả', confirmed: 'Chủ sân đã xác nhận', rejected: 'Bị từ chối', expired: 'Hết hạn', disputed: 'Đang tranh chấp', partially_refunded: 'Đã hoàn một phần', refunded: 'Đã hoàn' }
const COMMISSION_STATUS = { pending: 'Chưa đến hạn', due: 'Đến hạn', suspended: 'Đang tạm hoãn', adjusted: 'Đã điều chỉnh', waived: 'Đã miễn', settled: 'Đã thanh toán' }

function Entries({ entries, onOpen, side }) {
  if (entries.length === 0) {
    return <p className="rounded-xl border border-dashed border-slate-300 p-4 text-sm font-medium text-slate-500" data-testid={`add-${side}-empty`}>{side === 'owner' ? 'Chủ sân chưa giải trình.' : 'Không có nội dung.'}</p>
  }
  return (
    <ol className="grid gap-3" data-testid={`add-${side}-list`}>
      {entries.map((entry, index) => (
        <li key={`${entry.type}-${index}`} data-testid={`add-${side}-entry-${index}`} data-type={entry.type} className="rounded-xl border border-slate-200 bg-white p-4">
          <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
            <p className="font-semibold">{ENTRY_TITLE[entry.type]}</p>
            <span className="text-xs font-medium text-slate-500">{formatDateTime(entry.at)}</span>
          </div>
          <p className="mt-2 whitespace-pre-line text-sm font-medium leading-6 text-slate-700">{entry.text || 'Không có nội dung bằng chữ.'}</p>
          {entry.evidences.length > 0 && (
            <ul className="mt-3 grid grid-cols-3 gap-2" data-testid={`add-${side}-images-${index}`}>
              {entry.evidences.map((evidence) => <li key={evidence.id}><EvidenceImage evidence={evidence} className="aspect-square w-full rounded-lg border border-slate-200" onOpen={(url) => onOpen({ url, name: evidence.originalName })} /></li>)}
            </ul>
          )}
        </li>
      ))}
    </ol>
  )
}

/** 2.3.99 - chi tiết báo cáo: hai phía đặt cạnh nhau cùng đơn, thanh toán, hoa hồng và lịch sử để quyết định. */
function AdminDisputeDetailPage() {
  const { disputeId } = useParams()
  const { status, data, error, reload } = useApiResource(() => fetchAdminDispute(disputeId), [disputeId])
  const [lightbox, setLightbox] = useState(null)
  const [deciding, setDeciding] = useState(null)
  const [note, setNote] = useState('')
  const [notice, setNotice] = useState('')
  const meta = ADMIN_DISPUTE_STATUS[data?.status] ?? { label: data?.status, className: 'bg-slate-100 text-slate-600' }
  const playerEntries = data?.timeline.filter((entry) => entry.actor === 'player') ?? []
  const ownerEntries = data?.timeline.filter((entry) => entry.actor === 'owner') ?? []
  async function confirmDecision() {
    const clean = note.trim()
    if (clean.length < NOTE_LIMITS.min || clean.length > NOTE_LIMITS.max) throw new Error(`Ghi chú quyết định từ ${NOTE_LIMITS.min} đến ${NOTE_LIMITS.max} ký tự.`)
    await decideRefund(disputeId, deciding, clean)
    setNotice({ full: 'Đã quyết định hoàn tiền toàn phần.', half: 'Đã quyết định hoàn tiền một nửa.', reject: 'Đã bác bỏ báo cáo.' }[deciding] + ' Chủ sân và người chơi đã được thông báo.')
    setDeciding(null)
    setNote('')
    reload()
  }

  const adminEntries = data?.timeline.filter((entry) => entry.actor === 'admin') ?? []

  return (
    <AdminLayout eyebrow="2.3.99 · Chi tiết báo cáo" title="Chi tiết báo cáo đơn đặt sân" description="So sánh nội dung và bằng chứng của người chơi và chủ sân trước khi quyết định.">
      <Link to="/admin/disputes" className="mb-4 inline-block text-sm font-semibold text-slate-700 underline">Về danh sách báo cáo</Link>
      {status === 'loading' && <OwnerLoading rows={3} />}
      {status === 'error' && <OwnerError message={error?.status === 404 ? 'Không tìm thấy báo cáo.' : error?.message} onRetry={reload} />}
      {status === 'success' && (
        <div className="grid gap-5" data-testid="add-detail">
          <OwnerCard>
            <div className="flex flex-wrap items-center gap-2">
              <span className={classNames('rounded-full px-3 py-1 text-xs font-bold', meta.className)} data-testid="add-status">{meta.label}</span>
              <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-bold text-slate-700" data-testid="add-category">{data.categoryLabel}</span>
              {data.resolution && <span className="rounded-full bg-emerald-100 px-3 py-1 text-xs font-bold text-emerald-800" data-testid="add-resolution">{RESOLUTION_LABEL[data.resolution] ?? data.resolution}</span>}
            </div>
            <h2 className="mt-3 text-xl font-semibold" data-testid="add-venue">{data.venueName}</h2>
            <p className="mt-1 text-sm font-medium text-slate-500" data-testid="add-booking">Đơn {data.bookingCode}{data.courtName ? ` · ${data.courtName}` : ''}{data.startTime ? ` · ${formatDateTime(data.startTime)}` : ''}</p>
            <dl className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <div><dt className="text-xs font-semibold text-slate-500">Người chơi</dt><dd className="mt-1 text-sm font-semibold" data-testid="add-player">{data.player.fullName}<span className="block text-xs font-medium text-slate-500">{[data.player.phone, data.player.email].filter(Boolean).join(' · ')}</span></dd></div>
              <div><dt className="text-xs font-semibold text-slate-500">Chủ sân</dt><dd className="mt-1 text-sm font-semibold" data-testid="add-owner">{data.owner.fullName}<span className="block text-xs font-medium text-slate-500">{[data.owner.phone, data.owner.email].filter(Boolean).join(' · ')}</span></dd></div>
              <div><dt className="text-xs font-semibold text-slate-500">Yêu cầu hoàn tiền</dt><dd className="mt-1 text-sm font-semibold" data-testid="add-refund">{data.refundRequested ? formatCurrency(data.refundAmount) : 'Không (chỉ phản ánh)'}</dd></div>
              <div><dt className="text-xs font-semibold text-slate-500">Gửi lúc</dt><dd className="mt-1 text-sm font-semibold">{formatDateTime(data.createdAt)}</dd></div>
            </dl>
          </OwnerCard>

          <RefundPanel disputeId={data.id} role="admin" onChanged={reload} onOpen={(url) => setLightbox({ url, name: 'Chứng từ hoàn tiền' })} />
          {notice && <p role="status" data-testid="add-notice" className="rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-sm font-semibold text-emerald-900">{notice}</p>}
          {data.canDecide && (
            <OwnerCard title="Quyết định" description="Quyết định không sửa được sau khi gửi. Chủ sân sẽ hoàn tiền trực tiếp cho người chơi.">
              <div className="flex flex-wrap gap-3">
                <button type="button" data-testid="add-reject" onClick={() => { setNote(''); setDeciding('reject') }} className="inline-flex h-11 items-center rounded-lg border border-red-200 bg-white px-5 text-sm font-semibold text-red-700 hover:bg-red-50">{data.refundRequested ? 'Bác bỏ yêu cầu hoàn tiền' : 'Kết luận không hoàn tiền'}</button>
              </div>
              {data.canRefund ? (
                <div className="mt-3 flex flex-wrap gap-3">
                  <button type="button" data-testid="add-refund-full" onClick={() => { setNote(''); setDeciding('full') }} className="inline-flex h-11 items-center rounded-lg bg-emerald-700 px-5 text-sm font-semibold text-white hover:bg-emerald-800">Hoàn tiền toàn phần · {formatCurrency(data.fullAmount)}</button>
                  <button type="button" data-testid="add-refund-half" onClick={() => { setNote(''); setDeciding('half') }} className="inline-flex h-11 items-center rounded-lg bg-slate-800 px-5 text-sm font-semibold text-white hover:bg-slate-900">Hoàn tiền một nửa · {formatCurrency(data.halfAmount)}</button>
                </div>
              ) : (
                <p className="text-sm font-medium text-slate-600" data-testid="add-no-refund">
                  {data.refundRequested ? 'Không hoàn tiền được: đơn chưa có khoản thanh toán được chủ sân xác nhận hoặc số đã thu đã được hoàn hết.' : 'Người chơi không yêu cầu hoàn tiền nên không có quyết định hoàn tiền.'}
                </p>
              )}
            </OwnerCard>
          )}
          {deciding === 'reject' && (
            <ConfirmDialog title={data.refundRequested ? 'Bác bỏ yêu cầu hoàn tiền?' : 'Kết luận không hoàn tiền?'} confirmLabel="Bác bỏ báo cáo" danger
              onConfirm={confirmDecision} onClose={() => setDeciding(null)} testId="add-confirm">
              <p data-testid="add-confirm-amount" className="font-semibold text-slate-900">Chủ sân không phải hoàn tiền; hoa hồng của đơn quay lại công nợ.</p>
              <label htmlFor="add-note" className="mt-3 block text-xs font-semibold text-slate-700">Ghi chú quyết định (bắt buộc, gửi cho cả hai bên)</label>
              <textarea id="add-note" data-testid="add-note" className="mt-1 h-24 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-800 outline-none focus:border-slate-700" maxLength={NOTE_LIMITS.max} value={note} onChange={(event) => setNote(event.target.value)} />
            </ConfirmDialog>
          )}
          {(deciding === 'full' || deciding === 'half') && (
            <ConfirmDialog title={deciding === 'full' ? 'Hoàn tiền toàn phần?' : 'Hoàn tiền một nửa?'} confirmLabel={`Quyết định hoàn ${formatCurrency(deciding === 'full' ? data.fullAmount : data.halfAmount)}`}
              onConfirm={confirmDecision} onClose={() => setDeciding(null)} testId="add-confirm">
              <p data-testid="add-confirm-amount" className="font-semibold text-slate-900">
                Chủ sân phải hoàn {formatCurrency(deciding === 'full' ? data.fullAmount : data.halfAmount)} cho người chơi.
              </p>
              <p className="mt-1 text-xs">
                {deciding === 'full'
                  ? `Toàn bộ số người chơi yêu cầu (${formatCurrency(data.refundAmount)}), không vượt số đã thu còn hoàn được (${formatCurrency(data.money.refundable)}).`
                  : `50% của ${formatCurrency(data.refundAmount)} = ${formatCurrency(data.refundAmount / 2)}, làm tròn xuống bội số 1.000đ (tối thiểu 1.000đ).`}
              </p>
              <label htmlFor="add-note" className="mt-3 block text-xs font-semibold text-slate-700">Ghi chú quyết định (bắt buộc, gửi cho cả hai bên)</label>
              <textarea id="add-note" data-testid="add-note" className="mt-1 h-24 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-800 outline-none focus:border-slate-700" maxLength={NOTE_LIMITS.max} value={note} onChange={(event) => setNote(event.target.value)} />
            </ConfirmDialog>
          )}

          <div className="grid gap-5 lg:grid-cols-2">
            <section aria-label="Người chơi">
              <h3 className="mb-3 flex items-center gap-2 text-base font-semibold"><CircleUserRound className="size-5" />Người chơi</h3>
              <Entries entries={playerEntries} onOpen={setLightbox} side="player" />
            </section>
            <section aria-label="Chủ sân">
              <h3 className="mb-3 flex items-center gap-2 text-base font-semibold"><Store className="size-5" />Chủ sân</h3>
              <Entries entries={ownerEntries} onOpen={setLightbox} side="owner" />
            </section>
          </div>

          <div className="grid gap-5 lg:grid-cols-3">
            <OwnerCard title="Thanh toán">
              <dl className="grid gap-2 text-sm" data-testid="add-money">
                <div className="flex justify-between gap-3"><dt className="text-slate-500">Phải trả</dt><dd className="font-semibold">{formatCurrency(data.money.payable)}</dd></div>
                <div className="flex justify-between gap-3"><dt className="text-slate-500">Chủ sân xác nhận đã thu</dt><dd className="font-semibold" data-testid="add-paid">{formatCurrency(data.money.confirmedPaid)}</dd></div>
                <div className="flex justify-between gap-3"><dt className="text-slate-500">Đã hoàn trước đó</dt><dd className="font-semibold" data-testid="add-refunded">{formatCurrency(data.money.refundedBefore)}</dd></div>
                <div className="flex justify-between gap-3"><dt className="text-slate-500">Còn hoàn được</dt><dd className="font-semibold" data-testid="add-refundable">{formatCurrency(data.money.refundable)}</dd></div>
              </dl>
              {data.money.payments.length === 0 ? <p className="mt-3 text-xs font-medium text-slate-500">Đơn chưa có khoản thanh toán.</p> : (
                <ul className="mt-3 grid gap-1 text-xs font-medium text-slate-600">
                  {data.money.payments.map((payment, index) => <li key={index}>{payment.method === 'cash' ? 'Tiền mặt' : payment.method} · {formatCurrency(payment.amount)} · {PAYMENT_STATUS[payment.status] ?? payment.status}</li>)}
                </ul>
              )}
            </OwnerCard>
            <OwnerCard title="Hoa hồng của đơn">
              {data.commission ? (
                <p className="text-sm font-medium" data-testid="add-commission">{formatCurrency(data.commission.amount)} · {COMMISSION_STATUS[data.commission.status] ?? data.commission.status}{data.commission.voucherDiscount > 0 ? ` · voucher giảm ${formatCurrency(data.commission.voucherDiscount)}` : ''}</p>
              ) : <p className="text-sm font-medium text-slate-500" data-testid="add-commission">Đơn chưa có hoa hồng.</p>}
            </OwnerCard>
            <OwnerCard title="Lịch sử để quyết định">
              <ul className="grid gap-1 text-sm font-medium" data-testid="add-history">
                <li>Sân có {data.history.venueDisputes} báo cáo, {data.history.venueUpheld} lần xử thua, {data.history.venueRejected} lần bác bỏ</li>
                <li>Người chơi đã gửi {data.history.reporterDisputes} báo cáo</li>
              </ul>
            </OwnerCard>
          </div>

          {adminEntries.length > 0 && (
            <OwnerCard title="Kết luận">
              {adminEntries.map((entry, index) => (
                <div key={index} data-testid="add-admin-entry">
                  <p className="flex items-center gap-2 text-sm font-semibold"><ShieldCheck className="size-4" />{ADMIN_ACTOR_LABEL.admin} · {RESOLUTION_LABEL[entry.label] ?? entry.label} · {formatDateTime(entry.at)}</p>
                  <p className="mt-2 whitespace-pre-line text-sm font-medium text-slate-700">{entry.text}</p>
                </div>
              ))}
            </OwnerCard>
          )}
        </div>
      )}
      {lightbox && (
        <div className="fixed inset-0 z-50 grid place-items-center bg-black/80 p-4" role="dialog" aria-modal="true" aria-label={lightbox.name} data-testid="add-lightbox" onClick={() => setLightbox(null)}>
          <button type="button" className="absolute right-4 top-4 grid size-10 place-items-center rounded-full bg-white/90" aria-label="Đóng" onClick={() => setLightbox(null)}><X className="size-5" /></button>
          <img src={lightbox.url} alt={lightbox.name} className="max-h-[88vh] max-w-full rounded-lg object-contain" />
        </div>
      )}
    </AdminLayout>
  )
}

export default AdminDisputeDetailPage
