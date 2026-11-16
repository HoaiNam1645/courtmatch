import { Gift } from 'lucide-react'
import { formatCurrency } from '../../../shared/utils'
import { formatDateTime } from '../ownerDates'
import { OwnerCard } from './OwnerUi'

/**
 * Khoản Courtly còn nợ chủ sân: phần giảm voucher vượt hoa hồng của booking. Chủ sân vẫn nhận đủ giá gốc
 * từ người chơi nên mục này chỉ để đối chiếu; khoản này không cộng vào công nợ hoa hồng.
 */
function CourtlyOwedCard({ owed }) {
  if (!owed || owed.count === 0) return null
  return (
    <div data-testid="courtly-owed">
    <OwnerCard title="Courtly còn nợ bạn">
      <div className="flex items-start gap-3">
        <span className="grid size-10 shrink-0 place-items-center rounded-full bg-amber-50 text-amber-700"><Gift className="size-5" /></span>
        <div className="min-w-0">
          <p className="text-2xl font-semibold text-emerald-950" data-testid="courtly-owed-total">{formatCurrency(owed.amount)}</p>
          <p className="mt-1 text-sm font-medium text-stone-600">
            Người chơi dùng voucher do Courtly chịu, phần giảm lớn hơn hoa hồng của {owed.count} booking nên Courtly sẽ bù phần chênh lệch cho bạn khi đối soát. Khoản này không tính vào công nợ hoa hồng.
          </p>
        </div>
      </div>
      <ul className="mt-4 divide-y divide-emerald-50" data-testid="courtly-owed-items">
        {owed.items.map((item) => (
          <li key={item.bookingId} className="flex flex-wrap items-center justify-between gap-2 py-3 text-sm" data-testid={`courtly-owed-${item.bookingCode}`}>
            <span className="min-w-0">
              <span className="block font-semibold text-emerald-950">{item.bookingCode}</span>
              <span className="block text-xs font-medium text-stone-500">{item.venueName} · {formatDateTime(item.calculatedAt)}</span>
            </span>
            <span className="shrink-0 sm:text-right">
              <span className="block font-semibold text-amber-700">+{formatCurrency(item.owedAmount)}</span>
              <span className="block text-xs font-medium text-stone-500">Voucher giảm {formatCurrency(item.voucherDiscount)}</span>
            </span>
          </li>
        ))}
      </ul>
    </OwnerCard>
    </div>
  )
}

export { CourtlyOwedCard }
