import { useState } from 'react'
import { UsersRound, X } from 'lucide-react'
import { clearPendingPartner, readPendingPartner } from '../../../shared/session'

/**
 * Nhắc người chơi đang đặt sân để chơi cùng một đối tác từ kết quả ghép cặp (B5).
 * Chọn một sân và khung giờ như bình thường; có thể bỏ chọn bất cứ lúc nào.
 */
function PendingPartnerBanner({ onChange }) {
  const [partner, setPartner] = useState(() => readPendingPartner())
  if (!partner) return null
  return (
    <div className="flex items-center justify-between gap-3 rounded-xl border border-lime-300 bg-lime-50 px-4 py-3" role="status" data-testid="pending-partner">
      <p className="flex items-center gap-2 text-sm font-semibold text-emerald-950"><UsersRound className="size-4 shrink-0 text-emerald-700" /><span>Bạn đang đặt sân để chơi cùng <strong data-testid="pending-partner-name">{partner.name}</strong>. Chọn sân và khung giờ phù hợp.</span></p>
      <button type="button" className="inline-flex h-8 shrink-0 items-center gap-1 rounded-lg border border-emerald-200 bg-white px-2.5 text-xs font-semibold text-emerald-800" data-testid="pending-partner-remove" onClick={() => { clearPendingPartner(); setPartner(null); onChange?.(null) }}><X className="size-3.5" />Bỏ</button>
    </div>
  )
}

export { PendingPartnerBanner }
