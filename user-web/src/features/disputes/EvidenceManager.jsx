import { LoaderCircle, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { ApiError } from '../../api/client'
import { deleteDisputeEvidence, EVIDENCE_LIMITS, uploadDisputeEvidence } from '../../api/bookingDisputes'
import { FormAlert } from '../../shared/FormFeedback'
import { EvidenceImage } from './EvidenceImage'
import { EvidencePicker } from './EvidencePicker'

/**
 * Thêm hoặc xóa ảnh bằng chứng của báo cáo ban đầu khi chủ sân chưa giải trình (2.3.88).
 * Ảnh tải lỗi ở màn hình gửi báo cáo được tải lại tại đây.
 */
export function EvidenceManager({ disputeId, initial, evidenceRemaining, onChanged, onOpen, uploadFn = uploadDisputeEvidence, deleteFn = deleteDisputeEvidence, maxImages = EVIDENCE_LIMITS.maxInitial, title = 'Ảnh bằng chứng của bạn', hint = 'Bạn thêm hoặc xóa ảnh được cho tới khi chủ sân giải trình.' }) {
  const [files, setFiles] = useState([])
  const [busy, setBusy] = useState(false)
  const [progress, setProgress] = useState('')
  const [errors, setErrors] = useState([])
  const limit = Math.max(0, Math.min(maxImages - initial.length, evidenceRemaining))

  async function remove(evidence) {
    if (busy) return
    setBusy(true)
    setErrors([])
    try {
      await deleteFn(disputeId, evidence.id)
      onChanged()
    } catch (failure) {
      setErrors([failure instanceof ApiError ? failure.message : 'Không xóa được ảnh'])
    } finally {
      setBusy(false)
    }
  }

  async function upload() {
    if (busy || files.length === 0) return
    setBusy(true)
    setErrors([])
    const failed = []
    for (let index = 0; index < files.length; index += 1) {
      setProgress(`Đang tải ảnh ${index + 1}/${files.length}...`)
      try {
        await uploadFn(disputeId, files[index])
      } catch (failure) {
        failed.push({ file: files[index], message: `${files[index].name}: ${failure instanceof ApiError ? failure.message : 'Không tải được ảnh'}` })
      }
    }
    setProgress('')
    setFiles(failed.map((item) => item.file))
    setErrors(failed.map((item) => item.message))
    setBusy(false)
    onChanged()
  }

  return (
    <section className="mt-6 rounded-xl border border-stone-200 bg-white p-4" data-testid="evidence-manager">
      <h3 className="text-sm font-semibold">{title}</h3>
      <p className="mt-1 text-xs font-medium text-stone-500">{hint}</p>
      {initial.length === 0 && <p className="mt-3 text-xs font-medium text-stone-500" data-testid="evidence-manager-empty">Chưa có ảnh nào.</p>}
      {initial.length > 0 && (
        <ul className="mt-3 grid grid-cols-3 gap-2 sm:grid-cols-5" data-testid="evidence-manager-list">
          {initial.map((evidence) => (
            <li key={evidence.id} className="relative">
              <EvidenceImage evidence={evidence} className="aspect-square w-full rounded-lg border border-stone-200" onOpen={onOpen} />
              <button type="button" disabled={busy} aria-label={`Xóa ảnh ${evidence.originalName}`} data-testid={`evidence-delete-${evidence.id}`} onClick={() => remove(evidence)}
                className="absolute -right-1.5 -top-1.5 grid size-7 place-items-center rounded-full bg-red-600 text-white"><Trash2 className="size-3.5" /></button>
            </li>
          ))}
        </ul>
      )}
      {limit > 0 && (
        <div className="mt-4">
          <EvidencePicker files={files} onChange={setFiles} limit={limit} disabled={busy} />
          {files.length > 0 && (
            <button type="button" disabled={busy} onClick={upload} data-testid="evidence-upload" className="mt-3 inline-flex h-10 items-center gap-2 rounded-lg bg-emerald-700 px-5 text-sm font-semibold text-white disabled:opacity-60">
              {busy && <LoaderCircle className="size-4 animate-spin" />}{busy ? progress : `Tải lên ${files.length} ảnh`}
            </button>
          )}
        </div>
      )}
      {errors.length > 0 && <div className="mt-3" data-testid="evidence-manager-errors">{errors.map((message) => <FormAlert key={message} message={message} />)}</div>}
    </section>
  )
}
