import { fetchPartnerAccuracy } from '../../../../api/adminMatching'
import { useApiResource } from '../../../../hooks/useApiResource'
import { OwnerCard, OwnerError, OwnerLoading } from '../../../owner/components/OwnerUi'
import { formatIsoDate } from '../../../owner/ownerDates'
import { formatRate, Kpi, SampleBadge } from './matchingUi'

function GroupTable({ title, groups, testId, minSample }) {
  return (
    <OwnerCard title={title}>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[560px] text-left text-sm" data-testid={testId}>
          <thead className="text-xs font-semibold uppercase tracking-wide text-slate-500">
            <tr><th className="py-2 pr-3">Nhóm</th><th className="px-3 py-2 text-right">Gợi ý</th><th className="px-3 py-2 text-right">Đã gửi yêu cầu</th><th className="px-3 py-2 text-right">Đã trả lời</th><th className="px-3 py-2 text-right">Chấp nhận</th><th className="py-2 pl-3" /></tr>
          </thead>
          <tbody className="divide-y divide-slate-100 font-medium text-slate-700">
            {groups.map((group) => (
              <tr key={group.key} data-testid={`${testId}-${group.key}`} data-suggestions={group.metrics.suggestions} data-accepted={group.metrics.accepted}>
                <td className="py-2.5 pr-3 font-semibold text-slate-900">{group.label}</td>
                <td className="px-3 py-2.5 text-right">{group.metrics.suggestions}</td>
                <td className="px-3 py-2.5 text-right">{group.metrics.requested} <span className="text-slate-400">({formatRate(group.metrics.requestRate)})</span></td>
                <td className="px-3 py-2.5 text-right">{group.metrics.answered}</td>
                <td className="px-3 py-2.5 text-right font-semibold text-slate-900" data-testid={`${testId}-${group.key}-rate`}>{formatRate(group.metrics.acceptanceRate)}</td>
                <td className="py-2.5 pl-3 text-right">{!group.metrics.sufficient && <SampleBadge count={group.metrics.suggestions} min={minSample} />}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </OwnerCard>
  )
}

/** 2.3.70 - độ chính xác ghép cặp người chơi, đo bằng kết quả thật của từng gợi ý. */
export function PartnerAccuracySection({ range, source }) {
  const { status, data, error, reload } = useApiResource(
    () => fetchPartnerAccuracy({ ...range, source }),
    [range.from, range.to, source],
  )
  if (status === 'loading') return <OwnerLoading rows={2} />
  if (status === 'error') return <OwnerError message={error?.message} onRetry={reload} />
  const { overall } = data
  return (
    <section className="grid gap-5" aria-label="Độ chính xác ghép cặp người chơi" data-testid="partner-accuracy">
      <div className="flex flex-wrap items-center gap-3">
        <h2 className="w-full text-lg font-semibold text-slate-900 sm:w-auto">Ghép cặp người chơi</h2>
        <span className="text-sm font-medium text-slate-500">{formatIsoDate(data.from)} – {formatIsoDate(data.to)}</span>
        {!overall.sufficient && <SampleBadge count={overall.suggestions} min={data.minSample} testId="pa-insufficient" />}
      </div>
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <Kpi label="Gợi ý đã hiển thị" value={overall.suggestions.toLocaleString('vi-VN')} testId="pa-suggestions" />
        <Kpi label="Tỷ lệ gửi yêu cầu" value={formatRate(overall.requestRate)} hint={`${overall.requested} yêu cầu từ gợi ý`} testId="pa-request-rate" />
        <Kpi label="Tỷ lệ chấp nhận" value={formatRate(overall.acceptanceRate)} hint={`${overall.accepted} chấp nhận / ${overall.answered} đã trả lời`} testId="pa-acceptance-rate" />
        <Kpi label="Tỷ lệ chơi thật" value={formatRate(overall.playedRate)} hint={`${overall.playedTogether} cặp có trận chung / ${overall.accepted} kết nối`} testId="pa-played-rate" />
      </div>
      <p className="text-sm font-medium text-slate-600" data-testid="pa-breakdown">
        Chưa có phản hồi: {overall.pending} · Từ chối: {overall.rejected} · Hết hạn: {overall.expired} · Đã hủy: {overall.cancelled}.
        Một yêu cầu được tính cho gợi ý mới nhất của cùng cặp, trong {data.attributionDays} ngày sau gợi ý.
      </p>
      <div className="grid gap-5 xl:grid-cols-2">
        <GroupTable title="Theo hạng gợi ý" groups={data.byRank} testId="pa-rank" minSample={data.minSample} />
        <GroupTable title="Theo khoảng điểm" groups={data.byScore} testId="pa-score" minSample={data.minSample} />
      </div>
    </section>
  )
}
