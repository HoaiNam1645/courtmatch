import { useState } from 'react'
import { CheckCircle2, LoaderCircle, Star } from 'lucide-react'
import { createPartnerReview } from '../../../api/partnerReviews'
import { classNames } from '../../../shared/utils'

function PartnerReviewForm({ matchId, player, onReviewed }) {
  const [rating, setRating] = useState(0)
  const [comment, setComment] = useState('')
  const [status, setStatus] = useState('idle')
  const [message, setMessage] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})

  async function submit(event) {
    event.preventDefault()
    if (rating < 1 || rating > 5) {
      setFieldErrors({ rating: 'Chọn từ 1 đến 5 sao' })
      return
    }
    setStatus('submitting')
    setMessage('')
    setFieldErrors({})
    try {
      await createPartnerReview({ matchId, reviewedUserId: player.id, rating, comment })
      setStatus('success')
      onReviewed(player.fullName)
    } catch (error) {
      setStatus('error')
      setMessage(error?.message ?? 'Không thể gửi đánh giá.')
      setFieldErrors(error?.fieldErrors ?? {})
    }
  }

  return (
    <form onSubmit={submit} className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_12px_32px_rgba(24,65,44,0.07)]">
      <div className="flex items-center gap-3">
        {player.avatarUrl ? <img src={player.avatarUrl} alt="" className="size-11 rounded-xl object-cover" /> : <span className="grid size-11 shrink-0 place-items-center rounded-xl bg-emerald-950 font-semibold text-lime-300">{player.fullName.slice(0, 1).toUpperCase()}</span>}
        <div><p className="text-xs font-bold uppercase tracking-[0.1em] text-emerald-600">Đánh giá sau trận</p><h3 className="font-semibold">{player.fullName}</h3></div>
      </div>
      <fieldset className="mt-4">
        <legend className="text-xs font-semibold text-stone-500">Trải nghiệm chơi cùng</legend>
        <div className="mt-2 flex gap-1" aria-label={`Điểm sao cho ${player.fullName}`}>
          {[1, 2, 3, 4, 5].map((value) => <button key={value} type="button" aria-label={`${value} sao`} onClick={() => { setRating(value); setFieldErrors((current) => ({ ...current, rating: '' })) }} className="rounded-lg p-1.5 focus:outline-none focus:ring-2 focus:ring-emerald-500"><Star className={classNames('size-7', value <= rating ? 'fill-amber-400 text-amber-400' : 'text-stone-300')} /></button>)}
        </div>
        {fieldErrors.rating && <p className="mt-1 text-xs font-medium text-red-600">{fieldErrors.rating}</p>}
      </fieldset>
      <label className="mt-4 block text-xs font-semibold text-stone-500" htmlFor={`partner-review-${player.id}`}>Nhận xét (không bắt buộc)</label>
      <textarea id={`partner-review-${player.id}`} value={comment} onChange={(event) => setComment(event.target.value)} maxLength={1000} placeholder="Tinh thần, thái độ và trải nghiệm chơi chung..." className="mt-2 min-h-24 w-full rounded-xl border border-stone-200 bg-stone-50 p-3 text-sm outline-none transition focus:border-emerald-500 focus:bg-white" />
      <div className="mt-1 flex justify-between gap-3"><span className="text-xs font-medium text-red-600">{fieldErrors.comment}</span><span className="text-[11px] text-stone-400">{comment.length}/1000</span></div>
      {message && <p className="mt-3 rounded-lg bg-red-50 p-3 text-xs font-medium text-red-700">{message}</p>}
      <button type="submit" disabled={status === 'submitting' || status === 'success'} className="mt-4 flex h-11 w-full items-center justify-center gap-2 rounded-xl bg-emerald-700 text-sm font-semibold text-white disabled:opacity-60">{status === 'submitting' ? <LoaderCircle className="size-4 animate-spin" /> : <CheckCircle2 className="size-4" />}Gửi đánh giá</button>
    </form>
  )
}

export default PartnerReviewForm
