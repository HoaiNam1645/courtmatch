import { LoaderCircle, PlusCircle } from 'lucide-react'
import { useState } from 'react'
import { ApiError } from '../../api/client'
import { addDisputeSupplement, EVIDENCE_LIMITS, uploadDisputeEvidence } from '../../api/bookingDisputes'
import { FieldError, FormAlert } from '../../shared/FormFeedback'
import { classNames } from '../../shared/utils'
import { EvidencePicker } from './EvidencePicker'

const MIN = 5
const MAX = 2000

/**
 * Bổ sung thông tin cho báo cáo đang xử lý (2.3.91). Gửi nội dung trước, rồi tải từng ảnh kèm supplementId;
 * ảnh lỗi được giữ lại để thử tải lại mà không gửi trùng nội dung.
 */
export function SupplementForm({ disputeId, supplementsRemaining, evidenceRemaining, onDone }) {
  const [content, setContent] = useState('')
  const [files, setFiles] = useState([])
  const [error, setError] = useState('')
  const [formError, setFormError] = useState('')
  const [busy, setBusy] = useState(false)
  const [progress, setProgress] = useState('')
  const [pending, setPending] = useState(null) // { supplementId, files } khi nội dung đã gửi nhưng còn ảnh chưa tải được
  const [failed, setFailed] = useState([])

  async function uploadAll(supplementId, list) {
    const stillFailed = []
    for (let index = 0; index < list.length; index += 1) {
      setProgress(`Đang tải ảnh ${index + 1}/${list.length}...`)
      try {
        await uploadDisputeEvidence(disputeId, list[index], supplementId)
      } catch (failure) {
        stillFailed.push({ file: list[index], message: `${list[index].name}: ${failure instanceof ApiError ? failure.message : 'Không tải được ảnh'}` })
      }
    }
    setProgress('')
    return stillFailed
  }

  async function submit(event) {
    event.preventDefault()
    if (busy) return
    if (pending) {
      setBusy(true)
      const left = await uploadAll(pending.supplementId, pending.files)
      setBusy(false)
      setFailed(left.map((item) => item.message))
      if (left.length === 0) return finish()
      return setPending({ supplementId: pending.supplementId, files: left.map((item) => item.file) })
    }
    const text = content.trim()
    if (text.length < MIN) return setError(`Vui lòng nhập nội dung (ít nhất ${MIN} ký tự)`)
    if (text.length > MAX) return setError(`Nội dung tối đa ${MAX} ký tự`)
    setError('')
    setFormError('')
    setBusy(true)
    try {
      const created = await addDisputeSupplement(disputeId, text)
      const left = await uploadAll(created.id, files)
      setBusy(false)
      if (left.length === 0) return finish()
      setPending({ supplementId: created.id, files: left.map((item) => item.file) })
      setFailed(left.map((item) => item.message))
    } catch (failure) {
      if (failure instanceof ApiError) {
        setError(failure.fieldErrors?.content ?? '')
        setFormError(failure.message)
      } else {
        setFormError('Đã có lỗi xảy ra, vui lòng thử lại.')
      }
      setBusy(false)
    }
  }

  function finish() {
    setContent('')
    setFiles([])
    setPending(null)
    setFailed([])
    onDone()
  }

  return (
    <form noValidate onSubmit={submit} className="mt-6 rounded-xl border border-emerald-200 bg-white p-4" data-testid="supplement-form">
      <h3 className="flex items-center gap-2 text-sm font-semibold"><PlusCircle className="size-4 text-emerald-700" />Bổ sung thông tin</h3>
      <p className="mt-1 text-xs font-medium text-stone-500">Nội dung đã gửi không sửa hoặc xóa được. Còn {supplementsRemaining} lần bổ sung.</p>
      {pending ? (
        <div className="mt-3 rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs font-medium text-amber-900" role="alert" data-testid="supplement-pending">
          <p className="font-semibold">Nội dung đã được gửi, nhưng {pending.files.length} ảnh chưa tải được:</p>
          <ul className="mt-1 list-disc pl-5">{failed.map((message) => <li key={message}>{message}</li>)}</ul>
        </div>
      ) : (
        <>
          <label className="mt-3 block">
            <span className="text-sm font-semibold">Nội dung bổ sung <span className="text-red-600">*</span></span>
            <textarea className="mt-2 h-28 w-full min-w-0 rounded-lg border border-stone-300 bg-white px-3 py-2 text-sm outline-none focus:border-emerald-700" aria-label="Nội dung bổ sung" data-testid="supplement-content" value={content} disabled={busy}
              placeholder="Cung cấp thêm chi tiết, thời điểm, tin nhắn hoặc người liên quan..." onChange={(event) => { setContent(event.target.value); setError('') }} aria-invalid={Boolean(error)} />
            <span className={classNames('mt-1 block text-right text-xs font-medium', content.length > MAX ? 'text-red-600' : 'text-stone-400')} data-testid="supplement-counter">{content.length}/{MAX}</span>
            <FieldError message={error} />
          </label>
          {evidenceRemaining > 0 && <div className="mt-3"><EvidencePicker files={files} onChange={setFiles} limit={Math.min(evidenceRemaining, EVIDENCE_LIMITS.maxInitial)} disabled={busy} /></div>}
        </>
      )}
      <div className="mt-3"><FormAlert message={formError} /></div>
      <button type="submit" disabled={busy} data-testid="supplement-submit" className="mt-3 inline-flex h-11 items-center gap-2 rounded-lg bg-emerald-700 px-5 text-sm font-semibold text-white disabled:opacity-60">
        {busy && <LoaderCircle className="size-4 animate-spin" />}{busy ? (progress || 'Đang gửi...') : pending ? 'Thử tải lại ảnh' : 'Gửi bổ sung'}
      </button>
      {pending && <button type="button" disabled={busy} data-testid="supplement-skip" className="ml-2 mt-3 h-11 rounded-lg border border-stone-300 bg-white px-4 text-sm font-semibold text-stone-700" onClick={finish}>Bỏ qua các ảnh này</button>}
    </form>
  )
}
