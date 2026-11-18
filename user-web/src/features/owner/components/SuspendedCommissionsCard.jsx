import { Link } from 'react-router-dom'
import { fetchSuspendedCommissions } from '../../../api/ownerDisputes'
import { useApiResource } from '../../../hooks/useApiResource'
import { formatCurrency } from '../../../shared/utils'
import { formatDateTime } from '../ownerDates'
import { OwnerCard, OwnerError, OwnerLoading } from './OwnerUi'

/**
 * 2.3.97 - "Hoa hồng đang tạm hoãn". Ở trang Tài chính hiện toàn bộ; ở chi tiết báo cáo (`bookingId`) chỉ hiện khoản của đơn đó
 * và ẩn hẳn khi đơn không có khoản nào bị hoãn.
 */
function SuspendedCommissionsCard({ bookingId = null }) {
  const { status, data, error, reload } = useApiResource(fetchSuspendedCommissions, [])
  const items = (data?.items ?? []).filter((item) => !bookingId || item.bookingId === bookingId)
  const total = items.reduce((sum, item) => sum + item.commissionAmount, 0)
  if (bookingId && (status !== 'success' || items.length === 0)) return null

  return (
    <OwnerCard title="Hoa hồng đang tạm hoãn" description="Khoản này chưa tính vào công nợ đến hạn và chưa bị miễn; sẽ được tính lại hoặc điều chỉnh khi báo cáo có kết luận.">
      <div data-testid="suspended-card">
        {status === 'loading' && <OwnerLoading rows={1} />}
        {status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
        {status === 'success' && items.length === 0 && <p className="text-sm font-medium text-stone-500" data-testid="suspended-empty">Không có khoản hoa hồng nào đang tạm hoãn.</p>}
        {status === 'success' && items.length > 0 && (
          <>
            <p className="text-sm font-semibold text-emerald-950">Tổng đang hoãn: <span data-testid="suspended-total">{formatCurrency(total)}</span></p>
            <ul className="mt-3 grid gap-2" data-testid="suspended-list">
              {items.map((item) => (
                <li key={item.bookingId} className="rounded-lg border border-amber-200 bg-amber-50/60 p-3 text-sm" data-testid={`suspended-${item.bookingCode}`}>
                  <p className="font-semibold">{item.bookingCode} <span className="font-medium text-stone-500">· {item.venueName}</span></p>
                  <p className="mt-1 text-xs font-medium text-stone-600">
                    Hoa hồng hoãn {formatCurrency(item.commissionAmount)}
                    {item.voucherDiscount > 0 ? ` · voucher đã giảm ${formatCurrency(item.voucherDiscount)}` : ''} · từ {formatDateTime(item.suspendedAt)}
                  </p>
                  {item.disputeId && !bookingId && <Link to={`/owner/disputes/${item.disputeId}`} className="mt-1 inline-block text-xs font-semibold text-emerald-700 underline">Xem báo cáo</Link>}
                </li>
              ))}
            </ul>
          </>
        )}
      </div>
    </OwnerCard>
  )
}

export { SuspendedCommissionsCard }
