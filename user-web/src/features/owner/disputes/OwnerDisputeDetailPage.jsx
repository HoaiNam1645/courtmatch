import { CircleUserRound, FileText, ShieldCheck, Store, X } from 'lucide-react'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { deleteOwnerEvidence, fetchOwnerDispute, OWNER_ACTOR_LABEL, OWNER_RESPONSE_LIMITS, OWNER_STATUS_LABEL, respondToDispute, uploadOwnerEvidence } from '../../../api/ownerDisputes'
import { RESOLUTION_LABEL } from '../../../api/bookingDisputes'
import { useApiResource } from '../../../hooks/useApiResource'
import { classNames, formatCurrency } from '../../../shared/utils'
import { ConfirmDialog } from '../../disputes/ConfirmDialog'
import { EvidenceImage } from '../../disputes/EvidenceImage'
import { EvidenceManager } from '../../disputes/EvidenceManager'
import { RefundPanel } from '../../disputes/RefundPanel'
import { formatDateTime, remainingText } from '../../disputes/disputeUi'
import { OwnerLayout } from '../components/OwnerLayout'
import { OwnerCard, OwnerError, OwnerLoading } from '../components/OwnerUi'
import { SuspendedCommissionsCard } from '../components/SuspendedCommissionsCard'
import { primaryButtonClass, textareaClass } from '../ownerStyles'

const ACTOR_ICON = { player: CircleUserRound, you: Store, admin: ShieldCheck, system: ShieldCheck }
const TYPE_TITLE = { created: 'Người chơi gửi báo cáo', supplement: 'Người chơi bổ sung thông tin', owner_response: 'Giải trình của bạn', resolution: 'Quản trị viên kết luận' }

/** 2.3.94 - chi tiết báo cáo kèm bằng chứng của người chơi. */
function OwnerDisputeDetailPage() {
  const { disputeId } = useParams()
  const { status, data, error, reload } = useApiResource(() => fetchOwnerDispute(disputeId), [disputeId])
  const [lightbox, setLightbox] = useState(null)
  const [content, setContent] = useState('')
  const [confirming, setConfirming] = useState(false)
  const [notice, setNotice] = useState('')
  const trimmed = content.trim()
  const tooShort = trimmed.length < OWNER_RESPONSE_LIMITS.min

  async function submit() {
    await respondToDispute(disputeId, content)
    setConfirming(false)
    setContent('')
    setNotice('Đã gửi giải trình. Quản trị viên sẽ xem xét báo cáo này.')
    reload()
  }

  return (
    <OwnerLayout eyebrow="Báo cáo của khách" title="Chi tiết báo cáo" description="Xem nội dung và bằng chứng người chơi gửi, giải trình của bạn và kết luận của quản trị viên.">
      <Link to="/owner/disputes" className="mb-4 inline-block text-sm font-semibold text-emerald-700 underline">Về danh sách báo cáo</Link>
      {status === 'loading' && <OwnerLoading rows={3} />}
      {status === 'error' && <OwnerError message={error?.status === 404 ? 'Không tìm thấy báo cáo hoặc báo cáo không thuộc sân của bạn.' : error?.message} onRetry={reload} />}
      {status === 'success' && (
        <div className="grid gap-5" data-testid="odd-detail">
          <OwnerCard>
            <div className="flex flex-wrap items-center gap-2">
              <span className="rounded-full bg-amber-100 px-3 py-1 text-xs font-bold text-amber-900" data-testid="odd-status">{OWNER_STATUS_LABEL[data.status] ?? data.status}</span>
              <span className="rounded-full bg-stone-100 px-3 py-1 text-xs font-bold text-stone-700" data-testid="odd-category">{data.categoryLabel}</span>
            </div>
            <h2 className="mt-3 text-xl font-semibold" data-testid="odd-venue">{data.booking.venueName}</h2>
            <p className="mt-1 text-sm font-medium text-stone-500" data-testid="odd-booking">Đơn {data.booking.bookingCode}{data.booking.courtName ? ` · ${data.booking.courtName}` : ''}{data.booking.startTime ? ` · ${formatDateTime(data.booking.startTime)}` : ''}</p>
            <dl className="mt-4 grid gap-3 sm:grid-cols-3">
              <div><dt className="text-xs font-semibold text-stone-500">Người chơi</dt><dd className="mt-1 text-sm font-semibold" data-testid="odd-player">{data.player.fullName}{data.player.phone ? ` · ${data.player.phone}` : ''}</dd></div>
              <div><dt className="text-xs font-semibold text-stone-500">Yêu cầu hoàn tiền</dt><dd className="mt-1 text-sm font-semibold" data-testid="odd-refund">{data.refundRequested ? formatCurrency(data.refundAmount) : 'Không (chỉ phản ánh)'}</dd></div>
              <div><dt className="text-xs font-semibold text-stone-500">{data.status === 'awaiting_owner_response' || data.status === 'open' ? 'Hạn giải trình' : 'Số tiền người chơi đã trả'}</dt>
                <dd className={classNames('mt-1 text-sm font-semibold', data.overdue ? 'text-red-700' : '')} data-testid="odd-deadline">{data.status === 'awaiting_owner_response' || data.status === 'open' ? `${formatDateTime(data.ownerResponseDeadline)} (${remainingText(data.ownerResponseDeadline)})` : formatCurrency(data.booking.payableAmount)}</dd></div>
            </dl>
          </OwnerCard>

          <RefundPanel disputeId={data.id} role="owner" onChanged={reload} onOpen={(url) => setLightbox({ url, name: 'Chứng từ hoàn tiền' })} />
          <SuspendedCommissionsCard bookingId={data.booking.id} />
          {notice && <p role="status" data-testid="odd-notice" className="rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-sm font-semibold text-emerald-900">{notice}</p>}
          {data.canRespond && (
            <OwnerCard title="Giải trình của bạn" description="Chỉ gửi được một lần và không sửa được sau khi gửi. Kèm ảnh bằng chứng nếu có.">
              <label htmlFor="odd-response" className="text-sm font-semibold">Nội dung giải trình</label>
              <textarea id="odd-response" data-testid="odd-response" className={`${textareaClass} mt-2 h-36`} maxLength={OWNER_RESPONSE_LIMITS.max} value={content} onChange={(event) => setContent(event.target.value)} placeholder="Nêu rõ sự việc và cách bạn đã xử lý" />
              <p className="mt-1 text-xs font-medium text-stone-500" data-testid="odd-response-count">{trimmed.length}/{OWNER_RESPONSE_LIMITS.max} ký tự (tối thiểu {OWNER_RESPONSE_LIMITS.min})</p>
              <EvidenceManager disputeId={disputeId} initial={data.draftEvidences} evidenceRemaining={OWNER_RESPONSE_LIMITS.maxImages} maxImages={OWNER_RESPONSE_LIMITS.maxImages}
                uploadFn={uploadOwnerEvidence} deleteFn={deleteOwnerEvidence} onChanged={reload} onOpen={(url, evidence) => setLightbox({ url, name: evidence.originalName })}
                title="Ảnh bằng chứng của bạn" hint="Tối đa 5 ảnh. Bạn thêm hoặc xóa ảnh được cho tới khi gửi giải trình." />
              <button type="button" data-testid="odd-submit" disabled={tooShort} onClick={() => setConfirming(true)} className={`${primaryButtonClass} mt-4`}>Gửi giải trình</button>
            </OwnerCard>
          )}
          {confirming && (
            <ConfirmDialog title="Gửi giải trình?" confirmLabel="Gửi giải trình" onConfirm={submit} onClose={() => setConfirming(false)} testId="odd-confirm">
              Giải trình chỉ gửi được một lần và không sửa được sau đó. Người chơi sẽ được thông báo và quản trị viên sẽ xem xét.
            </ConfirmDialog>
          )}

          <OwnerCard title="Nội dung trao đổi">
            <ol className="grid gap-4" data-testid="odd-timeline">
              {data.timeline.map((entry, index) => {
                const Icon = ACTOR_ICON[entry.actor] ?? FileText
                return (
                  <li key={`${entry.type}-${index}`} data-testid={`odd-entry-${index}`} data-type={entry.type} data-actor={entry.actor}
                    className={classNames('rounded-xl border p-4', entry.actor === 'player' ? 'border-stone-200 bg-white' : entry.actor === 'you' ? 'border-emerald-200 bg-emerald-50/60' : 'border-violet-200 bg-violet-50/60')}>
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <p className="flex items-center gap-2 text-sm font-semibold"><Icon className="size-4" />{entry.actor === 'system' ? 'Hệ thống tự xử lý' : TYPE_TITLE[entry.type] ?? OWNER_ACTOR_LABEL[entry.actor]}</p>
                      <span className="text-xs font-medium text-stone-500">{formatDateTime(entry.at)}</span>
                    </div>
                    {entry.label && <p className="mt-2 text-sm font-bold text-violet-800">{RESOLUTION_LABEL[entry.label] ?? entry.label}</p>}
                    <p className="mt-2 whitespace-pre-line text-sm font-medium leading-6 text-stone-700">{entry.text || 'Không có nội dung bằng chữ.'}</p>
                    {entry.evidences.length > 0 && (
                      <ul className="mt-3 grid grid-cols-3 gap-2 sm:grid-cols-5" data-testid={`odd-evidences-${index}`}>
                        {entry.evidences.map((evidence) => <li key={evidence.id}><EvidenceImage evidence={evidence} className="aspect-square w-full rounded-lg border border-stone-200" onOpen={(url) => setLightbox({ url, name: evidence.originalName })} /></li>)}
                      </ul>
                    )}
                  </li>
                )
              })}
            </ol>
          </OwnerCard>
        </div>
      )}
      {lightbox && (
        <div className="fixed inset-0 z-50 grid place-items-center bg-black/80 p-4" role="dialog" aria-modal="true" aria-label={lightbox.name} data-testid="odd-lightbox" onClick={() => setLightbox(null)}>
          <button type="button" className="absolute right-4 top-4 grid size-10 place-items-center rounded-full bg-white/90" aria-label="Đóng" onClick={() => setLightbox(null)}><X className="size-5" /></button>
          <img src={lightbox.url} alt={lightbox.name} className="max-h-[88vh] max-w-full rounded-lg object-contain" />
        </div>
      )}
    </OwnerLayout>
  )
}

export default OwnerDisputeDetailPage
