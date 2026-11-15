import { AlertTriangle, ShieldAlert } from 'lucide-react'
import { formatCurrency } from '../../../shared/utils'

const LEVEL_TEXT = {
  1: { title: 'Sân của bạn đang bị chặn nhận booking mới', effect: 'Người chơi chưa đặt thêm được sân mới. Các booking đã nhận trước đó vẫn được xử lý bình thường.' },
  2: { title: 'Sân của bạn đang bị tạm ẩn khỏi người chơi', effect: 'Người chơi không thấy và không đặt được sân của bạn. Các booking đã nhận trước đó vẫn được xử lý bình thường.' },
}

/** Điều kiện của một mức, ví dụ "công nợ quá hạn từ 1.000.000đ hoặc quá hạn từ 14 ngày". */
function thresholdText(level) {
  const parts = []
  if (level.minOverdueAmount != null) parts.push(`công nợ quá hạn từ ${formatCurrency(level.minOverdueAmount)}`)
  if (level.minOverdueDays != null) parts.push(`quá hạn từ ${level.minOverdueDays} ngày`)
  return parts.join(' hoặc ')
}

/**
 * Mức hạn chế do công nợ hoa hồng quá hạn (B2) và cảnh báo trước khi chạm ngưỡng.
 *
 * Đang bị hạn chế thì nêu rõ mức, lý do và cách gỡ; chưa bị nhưng đã có công nợ quá hạn thì báo mức đầu tiên
 * đang được bật để chủ sân thanh toán kịp.
 */
function RestrictionNotice({ restriction, overdueAmount }) {
  if (restriction.level > 0) {
    const text = LEVEL_TEXT[restriction.level]
    const config = restriction.levels.find((item) => item.level === restriction.level)
    const reasons = []
    if (restriction.byAmount && config?.minOverdueAmount != null) reasons.push(`công nợ quá hạn ${formatCurrency(restriction.overdueAmount)} đã đạt ngưỡng ${formatCurrency(config.minOverdueAmount)}`)
    if (restriction.byDays && config?.minOverdueDays != null) reasons.push(`đã quá hạn ${restriction.overdueDays} ngày, đạt ngưỡng ${config.minOverdueDays} ngày`)
    return (
      <div className="flex items-start gap-3 rounded-xl border border-red-300 bg-red-50 p-4" role="alert" data-testid="debt-restriction">
        <ShieldAlert className="mt-0.5 size-5 shrink-0 text-red-700" />
        <div>
          <p className="text-sm font-semibold text-red-800" data-testid="debt-restriction-title">{text.title}</p>
          {reasons.length > 0 && <p className="mt-1 text-sm font-medium text-red-700" data-testid="debt-restriction-reason">Lý do: {reasons.join('; ')}.</p>}
          <p className="mt-1 text-xs font-medium text-red-700">{text.effect}</p>
          <p className="mt-1 text-xs font-semibold text-red-800">Thanh toán công nợ quá hạn và chờ Courtly xác nhận để được gỡ hạn chế.</p>
        </div>
      </div>
    )
  }
  const first = restriction.levels.find((item) => item.enabled)
  if (overdueAmount > 0 && first) {
    return (
      <div className="flex items-start gap-3 rounded-xl border border-amber-200 bg-amber-50 p-4" data-testid="debt-restriction-warning">
        <AlertTriangle className="mt-0.5 size-5 shrink-0 text-amber-600" />
        <p className="text-sm font-medium text-amber-900">Khi {thresholdText(first)}, sân của bạn sẽ bị chặn nhận booking mới. Hãy thanh toán công nợ quá hạn sớm.</p>
      </div>
    )
  }
  return null
}

export { RestrictionNotice }
