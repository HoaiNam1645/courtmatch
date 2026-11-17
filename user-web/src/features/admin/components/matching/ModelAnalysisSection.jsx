import { fetchModelAnalysis, MODEL_STATUS_LABEL, MODEL_TYPE_LABEL } from '../../../../api/adminMatching'
import { useApiResource } from '../../../../hooks/useApiResource'
import { OwnerCard, OwnerEmpty, OwnerError, OwnerLoading } from '../../../owner/components/OwnerUi'
import { formatRate } from './matchingFormat'
import { SampleBadge } from './matchingUi'

const num = (value) => (value === null || value === undefined ? '—' : value.toLocaleString('vi-VN', { maximumFractionDigits: 1 }))

/** Phân bố điểm tổng: thanh nhỏ theo từng khoảng, cao nhất = khoảng đông nhất của model. */
function Distribution({ items }) {
  const max = Math.max(...items.map((item) => item.count), 1)
  return (
    <div className="flex h-12 items-end gap-1" data-testid="ma-distribution">
      {items.map((item) => (
        <div key={item.label} className="flex flex-1 flex-col items-center gap-0.5" title={`${item.label}: ${item.count}`} data-label={item.label} data-count={item.count}>
          <div className="w-full rounded-t bg-slate-600" style={{ height: `${Math.max((item.count / max) * 100, item.count > 0 ? 8 : 2)}%`, opacity: item.count > 0 ? 1 : 0.25 }} />
          <span className="text-[10px] font-medium text-slate-500">{item.label}</span>
        </div>
      ))}
    </div>
  )
}

const PARTNER_ROWS = [
  ['Lượt chấm gợi ý', (p) => p.scored.toLocaleString('vi-VN'), 'scored'],
  ['Điểm tổng trung bình', (p) => num(p.avgTotal), 'avg-total'],
  ['Trình độ (TB)', (p) => num(p.avgSkill), 'avg-skill'],
  ['Vị trí (TB)', (p) => num(p.avgLocation), 'avg-location'],
  ['Thời gian (TB)', (p) => num(p.avgTime), 'avg-time'],
  ['Phong cách (TB)', (p) => num(p.avgStyle), 'avg-style'],
  ['Thiếu dữ liệu lịch rảnh', (p) => formatRate(p.missingTimeRate), 'missing-time'],
  ['Thiếu dữ liệu vị trí', (p) => formatRate(p.missingLocationRate), 'missing-location'],
  ['Yêu cầu được gửi', (p) => p.requested.toLocaleString('vi-VN'), 'requested'],
  ['Tỷ lệ chấp nhận', (p) => `${formatRate(p.acceptanceRate)} (${p.accepted}/${p.answered})`, 'acceptance'],
]

const TEAM_ROWS = [
  ['Lượt chia đội', (t) => t.batches.toLocaleString('vi-VN'), 'batches'],
  ['Độ chênh TB phương án khuyến nghị', (t) => num(t.avgBestGap), 'avg-gap'],
  ['Cặp thiếu dữ liệu lịch rảnh', (t) => formatRate(t.missingTimeRate), 'team-missing-time'],
  ['Cặp thiếu dữ liệu vị trí', (t) => formatRate(t.missingLocationRate), 'team-missing-location'],
]

/** 2.3.72 - kết quả chấm điểm của từng model, đặt cạnh nhau trên cùng khoảng thời gian. */
export function ModelAnalysisSection({ range, source }) {
  const { status, data, error, reload } = useApiResource(
    () => fetchModelAnalysis({ ...range, source }),
    [range.from, range.to, source],
  )
  if (status === 'loading') return <OwnerLoading rows={2} />
  if (status === 'error') return <OwnerError message={error?.message} onRetry={reload} />
  const { models } = data
  if (models.length === 0) {
    return <OwnerEmpty title="Chưa có model nào để phân tích" description="Chưa có lượt chấm điểm nào trong khoảng này và không có model đang hoạt động." />
  }
  return (
    <OwnerCard title="Kết quả chấm điểm theo model" description={models.length === 1 ? 'Hiện chỉ có một model; khi có thêm phiên bản mới, các cột sẽ đặt cạnh nhau để so sánh.' : 'Các model đặt cạnh nhau trên cùng khoảng thời gian.'}>
      <div className="overflow-x-auto" data-testid="model-analysis">
        <table className="w-full min-w-[480px] text-left text-sm">
          <thead>
            <tr className="align-top">
              <th className="w-56 py-2 pr-3 text-xs font-semibold uppercase tracking-wide text-slate-500">Chỉ số</th>
              {models.map((model) => (
                <th key={model.id} className="px-3 py-2" data-testid={`ma-model-${model.version}`} data-model-id={model.id}>
                  <p className="font-semibold text-slate-900">{model.version}</p>
                  <p className="mt-0.5 text-xs font-medium text-slate-500">{MODEL_TYPE_LABEL[model.type] ?? model.type}</p>
                  <p className="mt-1 text-xs font-semibold text-slate-600">{MODEL_STATUS_LABEL[model.status] ?? model.status}</p>
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 font-medium text-slate-700">
            {PARTNER_ROWS.map(([label, render, key]) => (
              <tr key={key}>
                <td className="py-2.5 pr-3 text-slate-600">{label}</td>
                {models.map((model) => (
                  <td key={model.id} className="px-3 py-2.5 font-semibold text-slate-900" data-testid={`ma-${key}-${model.version}`}>{model.partner ? render(model.partner) : '—'}</td>
                ))}
              </tr>
            ))}
            <tr>
              <td className="py-2.5 pr-3 text-slate-600">Phân bố điểm tổng</td>
              {models.map((model) => <td key={model.id} className="px-3 py-2.5">{model.partner ? <Distribution items={model.partner.distribution} /> : '—'}</td>)}
            </tr>
            <tr>
              <td className="py-2.5 pr-3 text-slate-600">Độ tin cậy mẫu</td>
              {models.map((model) => <td key={model.id} className="px-3 py-2.5">{model.partner ? (model.partner.sufficient ? <span className="text-xs font-semibold text-emerald-700">Đủ mẫu</span> : <SampleBadge count={model.partner.scored} min={data.minSample} />) : '—'}</td>)}
            </tr>
            {TEAM_ROWS.map(([label, render, key]) => (
              <tr key={key}>
                <td className="py-2.5 pr-3 text-slate-600">{label}</td>
                {models.map((model) => (
                  <td key={model.id} className="px-3 py-2.5 font-semibold text-slate-900" data-testid={`ma-${key}-${model.version}`}>{model.team ? render(model.team) : '—'}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </OwnerCard>
  )
}
