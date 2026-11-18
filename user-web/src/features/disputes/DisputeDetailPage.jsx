import { CircleUserRound, FileText, ShieldCheck, Store, X } from 'lucide-react'
import { useState } from 'react'
import { cancelMyDispute, DISPUTE_ACTOR_LABEL, fetchMyDispute, RESOLUTION_LABEL } from '../../api/bookingDisputes'
import { AppHeader, BottomNav } from '../../app/AppLayout'
import { useApiResource } from '../../hooks/useApiResource'
import { classNames, formatCurrency } from '../../shared/utils'
import { BookingEmptyState } from '../bookings/components/BookingEmptyState'
import { PageBackLink, ProfileEmptyState } from '../profile/components/ProfileUi'
import { disputeStatusMeta, formatDateTime, remainingText } from './disputeUi'
import { ConfirmDialog } from './ConfirmDialog'
import { EvidenceImage } from './EvidenceImage'
import { EvidenceManager } from './EvidenceManager'
import { RefundPanel } from './RefundPanel'
import { SupplementForm } from './SupplementForm'

const ACTOR_ICON = { you: CircleUserRound, owner: Store, admin: ShieldCheck }
const TYPE_TITLE = { created: 'Bạn gửi báo cáo', supplement: 'Bạn bổ sung thông tin', owner_response: 'Chủ sân giải trình', resolution: 'Quản trị viên kết luận' }

/** 2.3.90 - chi tiết báo cáo và toàn bộ nội dung trao đổi (/bao-cao/:reportId). */
function DisputeDetailPage({ reportId, user, onLogout }) {
  const { status, data, error, reload } = useApiResource(() => fetchMyDispute(reportId), [reportId])
  const [lightbox, setLightbox] = useState(null)
  const [cancelling, setCancelling] = useState(false)
  const [notice, setNotice] = useState('')
  if (!user) return <ProfileEmptyState />

  const shell = (children) => (
    <main className="min-h-screen bg-[#f4f7f1] pb-24 text-emerald-950">
      <AppHeader user={user} onLogout={onLogout} />
      <section className="mx-auto max-w-[820px] px-4 py-7 md:px-6 md:py-10">{children}</section>
      <BottomNav activeLabel="Tài khoản" />
    </main>
  )
  if (status === 'loading') return shell(<div className="h-56 animate-pulse rounded-xl bg-emerald-100/60" aria-busy="true" />)
  if (status === 'error') {
    const notFound = error?.status === 404
    return shell(<BookingEmptyState title={notFound ? 'Không tìm thấy báo cáo' : 'Không tải được báo cáo'} description={notFound ? 'Báo cáo có thể không tồn tại hoặc không thuộc về tài khoản này.' : error?.message} actionLabel="Xem báo cáo của tôi" actionHref="/bao-cao" />)
  }

  const meta = disputeStatusMeta(data.status)
  const waiting = data.status === 'awaiting_owner_response'
  return shell(
    <>
      <PageBackLink href="/bao-cao" label="Về danh sách báo cáo" />
      <div className="mt-6 flex flex-wrap items-center gap-2">
        <span className={classNames('rounded-full px-3 py-1 text-xs font-bold', meta.className)} data-testid="dd-status">{meta.label}</span>
        <span className="rounded-full bg-stone-100 px-3 py-1 text-xs font-bold text-stone-700" data-testid="dd-category">{data.categoryLabel}</span>
      </div>
      <h1 className="mt-3 text-2xl font-semibold" data-testid="dd-venue">{data.booking.venueName}</h1>
      <p className="mt-1 text-sm font-medium text-stone-500" data-testid="dd-booking">Đơn {data.booking.bookingCode}{data.booking.courtName ? ` · ${data.booking.courtName}` : ''}{data.booking.startTime ? ` · ${formatDateTime(data.booking.startTime)}` : ''}</p>
      <a href={`/bookings/${data.booking.id}`} className="mt-1 inline-block text-xs font-semibold text-emerald-700 underline">Xem đơn đặt sân</a>

      <div className="mt-5 grid gap-3 sm:grid-cols-2">
        <div className="rounded-xl border border-emerald-100 bg-white p-4">
          <p className="text-xs font-semibold text-stone-500">Yêu cầu hoàn tiền</p>
          <p className="mt-1 text-lg font-semibold" data-testid="dd-refund">{data.refundRequested ? formatCurrency(data.refundAmount) : 'Không yêu cầu (chỉ phản ánh)'}</p>
          {data.refundRequested && <p className="mt-1 text-xs font-medium text-stone-500">Trên số tiền đã trả {formatCurrency(data.booking.payableAmount)}</p>}
        </div>
        <div className="rounded-xl border border-emerald-100 bg-white p-4">
          <p className="text-xs font-semibold text-stone-500">{waiting ? 'Hạn chủ sân giải trình' : 'Tiến trình'}</p>
          <p className="mt-1 text-lg font-semibold" data-testid="dd-deadline">{waiting && data.ownerResponseDeadline ? `${formatDateTime(data.ownerResponseDeadline)} (${remainingText(data.ownerResponseDeadline)})` : meta.label}</p>
        </div>
      </div>

      {notice && <p role="status" data-testid="dd-notice" className="mt-4 rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-sm font-semibold text-emerald-800">{notice}</p>}
      {data.canCancel && <button type="button" data-testid="dd-cancel" onClick={() => { setNotice(''); setCancelling(true) }} className="mt-4 h-11 rounded-lg border border-red-200 bg-white px-5 text-sm font-semibold text-red-700">Huỷ báo cáo</button>}

      <h2 className="mt-8 text-lg font-semibold">Nội dung trao đổi</h2>
      <div className="mt-4"><RefundPanel disputeId={data.id} role="player" onChanged={reload} onOpen={(url) => setLightbox({ url, name: 'Chứng từ hoàn tiền' })} /></div>
      <ol className="mt-4 grid gap-4" data-testid="dd-timeline">
        {data.timeline.map((entry, index) => {
          const Icon = ACTOR_ICON[entry.actor] ?? FileText
          return (
            <li key={`${entry.type}-${index}`} data-testid={`dd-entry-${index}`} data-type={entry.type} data-actor={entry.actor} className={classNames('rounded-xl border p-4', entry.actor === 'you' ? 'border-emerald-200 bg-white' : entry.actor === 'owner' ? 'border-amber-200 bg-amber-50/60' : 'border-violet-200 bg-violet-50/60')}>
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="flex items-center gap-2 text-sm font-semibold"><Icon className="size-4" />{TYPE_TITLE[entry.type] ?? DISPUTE_ACTOR_LABEL[entry.actor]}</p>
                <span className="text-xs font-medium text-stone-500">{formatDateTime(entry.at)}</span>
              </div>
              {entry.label && <p className="mt-2 text-sm font-bold text-violet-800" data-testid={`dd-label-${index}`}>{RESOLUTION_LABEL[entry.label] ?? entry.label}</p>}
              <p className="mt-2 whitespace-pre-line text-sm font-medium leading-6 text-stone-700">{entry.text || 'Không có nội dung bằng chữ.'}</p>
              {entry.evidences.length > 0 && (
                <ul className="mt-3 grid grid-cols-3 gap-2 sm:grid-cols-5" data-testid={`dd-evidences-${index}`}>
                  {entry.evidences.map((evidence) => (
                    <li key={evidence.id}><EvidenceImage evidence={evidence} className="aspect-square w-full rounded-lg border border-stone-200" onOpen={(url) => setLightbox({ url, name: evidence.originalName })} /></li>
                  ))}
                </ul>
              )}
            </li>
          )
        })}
      </ol>
      {data.canCancel && <EvidenceManager disputeId={data.id} initial={data.timeline[0]?.evidences ?? []} evidenceRemaining={data.evidenceRemaining} onChanged={reload} onOpen={(url, evidence) => setLightbox({ url, name: evidence.originalName })} />}
      {data.canSupplement && <SupplementForm disputeId={data.id} supplementsRemaining={data.supplementsRemaining} evidenceRemaining={data.evidenceRemaining} onDone={reload} />}
      {!data.canSupplement && !data.closed && <p className="mt-4 text-xs font-medium text-stone-500" data-testid="dd-no-supplement">Báo cáo đã dùng hết số lần bổ sung.</p>}
      {waiting && <p className="mt-4 text-xs font-medium text-stone-500">Chủ sân chưa giải trình. Khi có phản hồi hoặc quyết định, nội dung sẽ hiện thêm ở đây.</p>}

      {cancelling && (
        <ConfirmDialog title="Huỷ báo cáo này?" confirmLabel="Huỷ báo cáo" cancelLabel="Giữ báo cáo" danger testId="dd-cancel-dialog" onClose={() => setCancelling(false)}
          onConfirm={async () => { await cancelMyDispute(data.id); setCancelling(false); setNotice('Đã huỷ báo cáo. Chủ sân không cần giải trình nữa.'); reload() }}>
          <p>Báo cáo sẽ đóng lại, <strong>kể cả yêu cầu hoàn tiền</strong>{data.refundRequested ? ` (${formatCurrency(data.refundAmount)})` : ''}. Nội dung và ảnh bạn đã gửi vẫn được lưu làm lịch sử.</p>
          <p className="mt-2">Nếu đơn còn trong thời hạn báo cáo, bạn có thể gửi báo cáo mới.</p>
        </ConfirmDialog>
      )}
      {lightbox && (
        <div className="fixed inset-0 z-50 grid place-items-center bg-black/80 p-4" role="dialog" aria-modal="true" aria-label={lightbox.name} data-testid="dd-lightbox" onClick={() => setLightbox(null)}>
          <button type="button" className="absolute right-4 top-4 grid size-10 place-items-center rounded-full bg-white/90" aria-label="Đóng" onClick={() => setLightbox(null)}><X className="size-5" /></button>
          <img src={lightbox.url} alt={lightbox.name} className="max-h-[88vh] max-w-full rounded-lg object-contain" />
        </div>
      )}
    </>,
  )
}

export default DisputeDetailPage
