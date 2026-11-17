import { fetchTeamAccuracy } from '../../../../api/adminMatching'
import { useApiResource } from '../../../../hooks/useApiResource'
import { classNames } from '../../../../shared/utils'
import { OwnerCard, OwnerError, OwnerLoading } from '../../../owner/components/OwnerUi'
import { formatIsoDate } from '../../../owner/ownerDates'
import { formatRate, Kpi, SampleBadge } from './matchingUi'

const number = (value, digits = 1) => (value === null || value === undefined ? '—' : value.toLocaleString('vi-VN', { maximumFractionDigits: digits }))

function CompareRow({ label, metrics, testId }) {
  return (
    <tr data-testid={testId} data-reconciled={metrics.reconciled}>
      <td className="py-2.5 pr-3 font-semibold text-slate-900">{label}</td>
      <td className="px-3 py-2.5 text-right">{metrics.reconciled}</td>
      <td className="px-3 py-2.5 text-right font-semibold text-slate-900" data-testid={`${testId}-accuracy`}>{formatRate(metrics.accuracy)}</td>
      <td className="px-3 py-2.5 text-right">{number(metrics.brier, 3)}</td>
      <td className="px-3 py-2.5 text-right">{number(metrics.avgBalanceGap)}</td>
      <td className="py-2.5 pl-3 text-right">{number(metrics.avgPointMargin)}</td>
    </tr>
  )
}

/** 2.3.71 - độ chính xác ghép đội, đối chiếu với trận thật của đúng 4 người đó. */
export function TeamAccuracySection({ range, source }) {
  const { status, data, error, reload } = useApiResource(
    () => fetchTeamAccuracy({ ...range, source }),
    [range.from, range.to, source],
  )
  if (status === 'loading') return <OwnerLoading rows={2} />
  if (status === 'error') return <OwnerError message={error?.message} onRetry={reload} />
  const { overall } = data
  return (
    <section className="grid gap-5" aria-label="Độ chính xác ghép đội" data-testid="team-accuracy">
      <div className="flex flex-wrap items-center gap-3">
        <h2 className="w-full text-lg font-semibold text-slate-900 sm:w-auto">Ghép đội</h2>
        <span className="text-sm font-medium text-slate-500">{formatIsoDate(data.from)} – {formatIsoDate(data.to)}</span>
        {!overall.sufficient && <SampleBadge count={overall.reconciled} min={data.minSample} testId="ta-insufficient" />}
      </div>
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <Kpi label="Lượt chia đội" value={data.batches.toLocaleString('vi-VN')} hint={`${data.options} phương án đã đề xuất`} testId="ta-batches" />
        <Kpi label="Đã đối chiếu với trận thật" value={overall.reconciled.toLocaleString('vi-VN')} hint={`Trận đã xác nhận cùng cách chia đội, trong ${data.reconcileDays} ngày`} testId="ta-reconciled" />
        <Kpi label="Dự đoán đội thắng đúng" value={formatRate(overall.accuracy)} hint={`${overall.correct} đúng / ${overall.evaluated} có dự đoán rõ`} testId="ta-accuracy" />
        <Kpi label="Sai số Brier" value={number(overall.brier, 3)} hint="0 là hoàn hảo, 0,25 tương đương đoán ngẫu nhiên" testId="ta-brier" />
      </div>
      {overall.reconciled === 0 ? (
        <p className="rounded-xl border border-dashed border-slate-300 bg-white px-4 py-5 text-sm font-medium text-slate-600" data-testid="ta-empty">
          Chưa có trận nào được ghi và xác nhận kết quả với đúng cách chia đội đã đề xuất trong {data.reconcileDays} ngày, nên chưa thể đánh giá. Đề xuất không tự tạo trận.
        </p>
      ) : (
        <div className="grid gap-5 2xl:grid-cols-2">
          <OwnerCard title="Phương án khuyến nghị và phương án khác" description={data.recommendedChosenRate === null ? undefined : `Người chơi chọn phương án khuyến nghị ở ${formatRate(data.recommendedChosenRate)} số lượt chia đội đã có trận.`}>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[520px] text-left text-sm" data-testid="ta-compare">
                <thead className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                  <tr><th className="py-2 pr-3">Nhóm</th><th className="px-3 py-2 text-right">Đối chiếu</th><th className="px-3 py-2 text-right">Đúng</th><th className="px-3 py-2 text-right">Brier</th><th className="px-3 py-2 text-right">Chênh sức mạnh</th><th className="py-2 pl-3 text-right">Chênh điểm thật</th></tr>
                </thead>
                <tbody className="divide-y divide-slate-100 font-medium text-slate-700">
                  <CompareRow label="Khuyến nghị" metrics={data.recommended} testId="ta-recommended" />
                  <CompareRow label="Phương án khác" metrics={data.others} testId="ta-others" />
                </tbody>
              </table>
            </div>
          </OwnerCard>
          <OwnerCard title="Các lần đối chiếu gần nhất">
            <ul className="grid gap-2 text-sm font-medium text-slate-700" data-testid="ta-samples">
              {data.samples.map((item) => (
                <li key={`${item.batchId}-${item.rank}`} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-slate-100 px-3 py-2" data-testid={`ta-sample-${item.matchId}`} data-correct={item.correct}>
                  <span>Phương án {item.rank}{item.recommended ? ' (khuyến nghị)' : ''} · đội 1 thắng {number(item.team1WinProbability)}% · đội {item.winningTeam} thắng</span>
                  <span className={classNames('rounded-full px-2.5 py-0.5 text-xs font-semibold', item.predictedWinningTeam === 0 ? 'bg-slate-100 text-slate-600' : item.correct ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-700')}>
                    {item.predictedWinningTeam === 0 ? 'Không dự đoán' : item.correct ? 'Dự đoán đúng' : 'Dự đoán sai'}
                  </span>
                </li>
              ))}
            </ul>
          </OwnerCard>
        </div>
      )}
    </section>
  )
}
