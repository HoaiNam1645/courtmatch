import { ImagePlus, X } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { EVIDENCE_LIMITS, evidenceProblem } from '../../api/bookingDisputes'
import { FieldError } from '../../shared/FormFeedback'

/**
 * Chọn ảnh bằng chứng trước khi gửi (2.3.88): nhiều ảnh, xem trước, bỏ từng ảnh, báo lỗi rõ từng file.
 *
 * @param files   danh sách File đang chọn (do nơi dùng giữ)
 * @param limit   số ảnh tối đa còn được chọn
 */
export function EvidencePicker({ files, onChange, limit = EVIDENCE_LIMITS.maxInitial, disabled }) {
  const input = useRef(null)
  const [problems, setProblems] = useState([])
  // Ảnh xem trước dùng URL tạm; giải phóng khi danh sách đổi hoặc component đóng.
  const previews = useMemo(() => files.map((file) => URL.createObjectURL(file)), [files])
  useEffect(() => () => previews.forEach((url) => URL.revokeObjectURL(url)), [previews])

  function pick(event) {
    const chosen = Array.from(event.target.files ?? [])
    event.target.value = ''
    const accepted = []
    const messages = []
    for (const file of chosen) {
      const problem = evidenceProblem(file)
      if (problem) messages.push(`${file.name}: ${problem}`)
      else if (files.length + accepted.length >= limit) messages.push(`${file.name}: chỉ đính kèm tối đa ${limit} ảnh`)
      else accepted.push(file)
    }
    setProblems(messages)
    if (accepted.length) onChange([...files, ...accepted])
  }

  return (
    <div data-testid="evidence-picker">
      <p className="text-sm font-semibold">Ảnh bằng chứng <span className="font-medium text-stone-400">(không bắt buộc, tối đa {limit} ảnh JPEG/PNG/WebP, mỗi ảnh ≤ 5MB)</span></p>
      {files.length > 0 && (
        <ul className="mt-3 grid grid-cols-3 gap-2 sm:grid-cols-5" data-testid="evidence-previews">
          {files.map((file, index) => (
            <li key={`${file.name}-${index}`} className="relative" data-testid={`evidence-preview-${index}`}>
              {previews[index] && <img src={previews[index]} alt={file.name} className="aspect-square w-full rounded-lg border border-stone-200 object-cover" />}
              <button type="button" disabled={disabled} aria-label={`Bỏ ảnh ${file.name}`} data-testid={`evidence-remove-${index}`} onClick={() => onChange(files.filter((_, position) => position !== index))}
                className="absolute -right-1.5 -top-1.5 grid size-6 place-items-center rounded-full bg-stone-800 text-white"><X className="size-3.5" /></button>
            </li>
          ))}
        </ul>
      )}
      <input ref={input} type="file" accept="image/jpeg,image/png,image/webp" multiple className="sr-only" data-testid="evidence-input" onChange={pick} disabled={disabled} />
      {files.length < limit && (
        <button type="button" disabled={disabled} onClick={() => input.current?.click()} data-testid="evidence-add"
          className="mt-3 inline-flex h-10 items-center gap-2 rounded-lg border border-dashed border-stone-300 px-4 text-sm font-semibold text-stone-700"><ImagePlus className="size-4" />Thêm ảnh</button>
      )}
      <div data-testid="evidence-problems">{problems.map((message) => <FieldError key={message} message={message} />)}</div>
    </div>
  )
}
