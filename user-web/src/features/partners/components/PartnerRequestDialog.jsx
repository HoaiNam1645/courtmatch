import { CheckCircle2, LoaderCircle, Send, X } from 'lucide-react'

function PartnerRequestDialog({ partner, message, status, error, onMessageChange, onClose, onSubmit }) {
  if (!partner) return null

  return (
    <div className="fixed inset-0 z-[1000] grid place-items-center bg-emerald-950/55 p-4 backdrop-blur-sm" role="dialog" aria-modal="true" aria-labelledby="partner-request-title">
      <div className="w-full max-w-lg overflow-hidden rounded-3xl border border-white/60 bg-white shadow-[0_28px_90px_rgba(2,44,30,0.28)]">
        <div className="flex items-start justify-between gap-4 bg-[linear-gradient(135deg,#064e3b,#0f766e)] px-6 py-5 text-white">
          <div>
            <p className="text-[11px] font-bold uppercase tracking-[0.14em] text-lime-300">Gửi yêu cầu ghép cặp</p>
            <h2 id="partner-request-title" className="mt-1 text-xl font-semibold">Mời {partner.fullName} cùng chơi</h2>
          </div>
          <button type="button" onClick={onClose} className="grid size-9 place-items-center rounded-full bg-white/10" aria-label="Đóng"><X className="size-4" /></button>
        </div>

        {status === 'success' ? (
          <div className="px-6 py-9 text-center">
            <CheckCircle2 className="mx-auto size-11 text-emerald-600" />
            <p className="mt-4 text-lg font-semibold text-emerald-950">Đã gửi lời mời</p>
            <p className="mt-2 text-sm leading-6 text-stone-500">Bạn có thể theo dõi phản hồi trong mục yêu cầu ghép cặp.</p>
            <button type="button" onClick={onClose} className="mt-6 h-11 rounded-xl bg-emerald-700 px-6 text-sm font-semibold text-white">Hoàn tất</button>
          </div>
        ) : (
          <form onSubmit={onSubmit} className="p-6">
            <label className="block">
              <span className="text-xs font-bold uppercase tracking-[0.08em] text-stone-400">Lời nhắn</span>
              <textarea
                autoFocus
                required
                maxLength={500}
                value={message}
                onChange={(event) => onMessageChange(event.target.value)}
                placeholder="Ví dụ: Tối thứ Năm mình đánh đôi ở Hải Châu, bạn tham gia nhé?"
                className="mt-2 min-h-32 w-full resize-none rounded-2xl border border-emerald-100 bg-emerald-50/40 p-4 text-sm leading-6 outline-none focus:border-emerald-500"
              />
            </label>
            <div className="mt-2 flex items-center justify-between text-xs font-medium text-stone-400"><span>Lời nhắn giúp đối tác dễ phản hồi hơn.</span><span>{message.length}/500</span></div>
            {error && <p className="mt-4 rounded-xl border border-red-100 bg-red-50 px-4 py-3 text-sm font-semibold text-red-700">{error}</p>}
            <div className="mt-6 grid gap-2 sm:grid-cols-[1fr_1.6fr]">
              <button type="button" onClick={onClose} className="h-12 rounded-xl border border-stone-200 text-sm font-semibold text-stone-600">Để sau</button>
              <button disabled={status === 'loading' || !message.trim()} className="flex h-12 items-center justify-center gap-2 rounded-xl bg-emerald-700 text-sm font-semibold text-white disabled:opacity-50">
                {status === 'loading' ? <LoaderCircle className="size-4 animate-spin" /> : <Send className="size-4" />}
                {status === 'loading' ? 'Đang gửi...' : 'Gửi lời mời'}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  )
}

export { PartnerRequestDialog }
