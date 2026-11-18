import { useState } from 'react'
import { Banknote } from 'lucide-react'
import { complainRefund, confirmRefundAsAdmin, confirmRefundAsPlayer, fetchDisputeRefund, REFUND_STATUS, reportOwnerRefund } from '../../api/disputeRefunds'
import { evidenceProblem } from '../../api/bookingDisputes'
import { useApiResource } from '../../hooks/useApiResource'
import { classNames, formatCurrency } from '../../shared/utils'
import { ConfirmDialog } from './ConfirmDialog'
import { EvidenceImage } from './EvidenceImage'
import { formatDateTime } from './disputeUi'

const BUTTON = 'inline-flex h-11 items-center rounded-lg px-5 text-sm font-semibold disabled:opacity-60'

/**
 * 2.3.108 - khối "Khoản hoàn tiền" của chi tiết báo cáo, dùng cho người chơi, chủ sân và quản trị viên.
 * Courtly không giữ tiền: chủ sân tự hoàn rồi báo kèm chứng từ, người chơi (hoặc admin) xác nhận.
 */
function RefundPanel({ disputeId, role, onChanged, onOpen }) {
  const { status, data, reload } = useApiResource(() => fetchDisputeRefund(disputeId), [disputeId])
  const [dialog, setDialog] = useState(null)
  const [file, setFile] = useState(null)
  const [text, setText] = useState('')
  const [notice, setNotice] = useState('')

  if (status !== 'success' || !data) return null
  const meta = REFUND_STATUS[data.status]
  const roleTitle = { player: 'Khoản hoàn tiền cho bạn', owner: 'Khoản bạn cần hoàn cho người chơi', admin: 'Khoản hoàn tiền' }[role]

  async function done(message) {
    setDialog(null)
    setFile(null)
    setText('')
    setNotice(message)
    reload()
    onChanged?.()
  }

  function open(kind) {
    setText('')
    setFile(null)
    setDialog(kind)
  }

  async function submitOwner() {
    if (!file) throw new Error('Hãy chọn ảnh chứng từ đã hoàn tiền.')
    const problem = evidenceProblem(file)
    if (problem) throw new Error(problem)
    await reportOwnerRefund(data.id, file, text)
    await done('Đã báo hoàn tiền. Người chơi sẽ xác nhận hoặc khiếu nại.')
  }

  async function submitPlayer() {
    await confirmRefundAsPlayer(data.id)
    await done('Đã xác nhận nhận tiền hoàn.')
  }

  async function submitComplaint() {
    if (text.trim().length < 5) throw new Error('Lý do từ 5 đến 1000 ký tự.')
    await complainRefund(data.id, text)
    await done('Đã gửi khiếu nại, chủ sân và quản trị viên sẽ được báo.')
  }

  async function submitAdmin() {
    if (text.trim().length < 5) throw new Error('Ghi chú từ 5 đến 1000 ký tự.')
    await confirmRefundAsAdmin(data.id, text)
    await done('Đã xác nhận khoản hoàn tiền.')
  }

  return (
    <section className="rounded-xl border border-emerald-200 bg-emerald-50/50 p-4 sm:p-5" data-testid="refund-panel" data-status={data.status}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="flex items-center gap-2 text-base font-semibold text-emerald-950"><Banknote className="size-5" />{roleTitle}</h3>
        <span className={classNames('rounded-full px-3 py-1 text-xs font-bold', meta?.className)} data-testid="refund-status">{meta?.label ?? data.status}</span>
      </div>
      {notice && <p role="status" data-testid="refund-notice" className="mt-3 rounded-lg bg-white p-2 text-sm font-semibold text-emerald-800">{notice}</p>}
      <p className="mt-3 text-lg font-semibold" data-testid="refund-amount">{formatCurrency(data.amount)}</p>
      <p className="mt-1 text-xs font-medium text-slate-600">
        {data.status === 'awaiting_owner_refund' && (role === 'owner' ? 'Hãy hoàn trực tiếp cho người chơi rồi báo lại kèm ảnh chứng từ. Courtly không giữ và không chuyển tiền thay bạn.' : 'Chủ sân hoàn trực tiếp cho người chơi; Courtly không giữ tiền.')}
        {data.status === 'owner_reported_refunded' && `Chủ sân báo đã hoàn lúc ${formatDateTime(data.ownerReportedAt)}. ${data.autoConfirmAt ? `Hệ thống tự xác nhận sau ${formatDateTime(data.autoConfirmAt)} nếu người chơi không phản hồi.` : ''}`}
        {data.status === 'confirmed' && `Hoàn tất lúc ${formatDateTime(data.confirmedAt)}${data.autoConfirmed ? ' (hệ thống tự xác nhận vì người chơi không phản hồi)' : ''}.`}
      </p>
      {data.playerComplaint && data.status === 'awaiting_owner_refund' && (
        <p className="mt-3 rounded-lg border border-red-200 bg-red-50 p-3 text-sm font-medium text-red-800" data-testid="refund-complaint">Người chơi khiếu nại chưa nhận được tiền: {data.playerComplaint}</p>
      )}
      {data.ownerNote && <p className="mt-3 text-sm font-medium text-slate-700" data-testid="refund-owner-note">Ghi chú của chủ sân: {data.ownerNote}</p>}
      {data.proofUrl && (
        <div className="mt-3 w-28" data-testid="refund-proof">
          <EvidenceImage evidence={{ id: data.id, originalName: 'Chứng từ hoàn tiền', url: data.proofUrl }} className="aspect-square w-full rounded-lg border border-slate-200" onOpen={onOpen} />
        </div>
      )}
      <div className="mt-4 flex flex-wrap gap-3">
        {data.canReport && <button type="button" data-testid="refund-report" className={`${BUTTON} bg-emerald-700 text-white`} onClick={() => open('owner')}>Báo đã hoàn tiền</button>}
        {data.canConfirm && <button type="button" data-testid="refund-confirm" className={`${BUTTON} bg-emerald-700 text-white`} onClick={() => open('player')}>Đã nhận tiền</button>}
        {data.canConfirm && <button type="button" data-testid="refund-complain" className={`${BUTTON} border border-red-200 bg-white text-red-700`} onClick={() => open('complain')}>Chưa nhận được</button>}
        {data.canAdminConfirm && <button type="button" data-testid="refund-admin-confirm" className={`${BUTTON} bg-slate-800 text-white`} onClick={() => open('admin')}>Xác nhận thay người chơi</button>}
      </div>

      {dialog === 'owner' && (
        <ConfirmDialog title="Báo đã hoàn tiền?" confirmLabel="Gửi báo cáo hoàn tiền" onConfirm={submitOwner} onClose={() => setDialog(null)} testId="refund-dialog">
          <p>Đính kèm ảnh chuyển khoản hoặc biên nhận hoàn {formatCurrency(data.amount)} cho người chơi.</p>
          <input type="file" accept="image/jpeg,image/png,image/webp" data-testid="refund-file" className="mt-3 block w-full text-xs" onChange={(event) => setFile(event.target.files?.[0] ?? null)} />
          <textarea data-testid="refund-text" aria-label="Ghi chú" placeholder="Ghi chú (không bắt buộc)" className="mt-3 h-20 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" maxLength={1000} value={text} onChange={(event) => setText(event.target.value)} />
        </ConfirmDialog>
      )}
      {dialog === 'player' && (
        <ConfirmDialog title="Xác nhận đã nhận tiền?" confirmLabel="Đã nhận tiền" onConfirm={submitPlayer} onClose={() => setDialog(null)} testId="refund-dialog">
          Bạn xác nhận đã nhận {formatCurrency(data.amount)} từ chủ sân. Sau khi xác nhận không thay đổi được.
        </ConfirmDialog>
      )}
      {dialog === 'complain' && (
        <ConfirmDialog title="Chưa nhận được tiền?" confirmLabel="Gửi khiếu nại" danger onConfirm={submitComplaint} onClose={() => setDialog(null)} testId="refund-dialog">
          <p>Chủ sân và quản trị viên sẽ được báo; chủ sân cần kiểm tra và báo lại.</p>
          <textarea data-testid="refund-text" aria-label="Lý do khiếu nại" placeholder="Ví dụ: tôi chưa thấy tiền về tài khoản" className="mt-3 h-24 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" maxLength={1000} value={text} onChange={(event) => setText(event.target.value)} />
        </ConfirmDialog>
      )}
      {dialog === 'admin' && (
        <ConfirmDialog title="Xác nhận khoản hoàn tiền?" confirmLabel="Xác nhận" onConfirm={submitAdmin} onClose={() => setDialog(null)} testId="refund-dialog">
          <p>Xác nhận người chơi đã nhận {formatCurrency(data.amount)}; phí nền tảng của đơn sẽ được điều chỉnh theo số tiền hoàn.</p>
          <textarea data-testid="refund-text" aria-label="Ghi chú xác nhận" placeholder="Ghi chú đối chiếu (bắt buộc)" className="mt-3 h-24 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" maxLength={1000} value={text} onChange={(event) => setText(event.target.value)} />
        </ConfirmDialog>
      )}
    </section>
  )
}

export { RefundPanel }
