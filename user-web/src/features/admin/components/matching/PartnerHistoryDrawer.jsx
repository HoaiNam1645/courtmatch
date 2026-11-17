import { useEffect } from 'react'
import { X } from 'lucide-react'
import { fetchPartnerHistoryDetail, SOURCE_LABEL } from '../../../../api/adminMatching'
import { useApiResource } from '../../../../hooks/useApiResource'
import { OwnerError, OwnerLoading } from '../../../owner/components/OwnerUi'
import { formatDateTime } from '../../../owner/ownerDates'

const num = (value) => (value === null || value === undefined ? 'Thiếu dữ liệu' : value.toLocaleString('vi-VN', { maximumFractionDigits: 2 }))

const REASON_LABEL = { ratingGap: 'Chênh điểm trình độ', distanceKm: 'Khoảng cách (km)', provisional: 'Điểm tạm tính (ít trận)' }

/** Chi tiết một gợi ý: điểm thành phần, lý do, kết quả yêu cầu và lần tìm đã sinh ra gợi ý (2.3.75). */
export function PartnerHistoryDrawer({ suggestionId, onClose }) {
  const { status, data, error, reload } = useApiResource(() => fetchPartnerHistoryDetail(suggestionId), [suggestionId])
  useEffect(() => {
    function onKey(event) {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-black/40" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}>
      <aside role="dialog" aria-modal="true" aria-label="Chi tiết gợi ý ghép cặp" className="h-full w-full max-w-md overflow-y-auto bg-white p-5 shadow-2xl" data-testid="ph-drawer">
        <div className="flex items-start justify-between gap-3">
          <h2 className="text-lg font-semibold text-slate-900">Chi tiết gợi ý</h2>
          <button type="button" className="grid size-9 place-items-center rounded-lg text-slate-500 hover:bg-slate-100" aria-label="Đóng" onClick={onClose}><X className="size-5" /></button>
        </div>
        {status === 'loading' && <div className="mt-4"><OwnerLoading rows={2} /></div>}
        {status === 'error' && <div className="mt-4"><OwnerError message={error?.message} onRetry={reload} /></div>}
        {status === 'success' && (
          <div className="mt-4 grid gap-5 text-sm font-medium text-slate-700">
            <section>
              <p><span className="text-slate-500">Người xem:</span> <span className="font-semibold text-slate-900">{data.item.viewer.fullName}</span></p>
              <p><span className="text-slate-500">Được gợi ý:</span> <span className="font-semibold text-slate-900">{data.item.suggested.fullName}</span></p>
              <p className="mt-1 text-xs text-slate-500">{formatDateTime(data.item.createdAt)} · hạng {data.item.rankNo ?? '—'} · {SOURCE_LABEL[data.item.scoringSource] ?? data.item.scoringSource}{data.item.modelVersion ? ` · ${data.item.modelVersion}` : ''}</p>
            </section>
            <section>
              <h3 className="text-sm font-semibold text-slate-900">Điểm số</h3>
              <dl className="mt-2 grid gap-1" data-testid="ph-scores">
                {[['Tổng', data.item.totalScore, 'total'], ['Trình độ', data.skillScore, 'skill'], ['Vị trí', data.locationScore, 'location'], ['Thời gian', data.timeScore, 'time'], ['Phong cách', data.styleScore, 'style']].map(([label, value, key]) => (
                  <div key={key} className="flex justify-between border-b border-slate-100 py-1"><dt>{label}</dt><dd className="font-semibold text-slate-900" data-testid={`ph-score-${key}`}>{num(value)}</dd></div>
                ))}
                {data.confidenceScore !== null && <div className="flex justify-between py-1"><dt>Độ tin cậy</dt><dd className="font-semibold text-slate-900">{num(data.confidenceScore)}</dd></div>}
              </dl>
            </section>
            {data.reason && (
              <section>
                <h3 className="text-sm font-semibold text-slate-900">Lý do được gợi ý</h3>
                <ul className="mt-2 grid gap-1" data-testid="ph-reason">
                  {Object.entries(data.reason).map(([key, value]) => <li key={key} className="flex justify-between"><span>{REASON_LABEL[key] ?? key}</span><span className="font-semibold text-slate-900">{typeof value === 'boolean' ? (value ? 'Có' : 'Không') : String(value)}</span></li>)}
                </ul>
              </section>
            )}
            <section>
              <h3 className="text-sm font-semibold text-slate-900">Kết quả</h3>
              <p className="mt-2" data-testid="ph-request">
                {data.item.requestId
                  ? `Yêu cầu gửi lúc ${formatDateTime(data.item.requestedAt)}${data.item.respondedAt ? `, phản hồi lúc ${formatDateTime(data.item.respondedAt)}` : ', chưa có phản hồi'}.`
                  : 'Chưa có yêu cầu ghép cặp nào được gửi từ gợi ý này.'}
              </p>
            </section>
            {data.search && (
              <section>
                <h3 className="text-sm font-semibold text-slate-900">Lần tìm sinh ra gợi ý</h3>
                <p className="mt-2" data-testid="ph-search">{data.search.resultCount ?? '—'} kết quả · xử lý {data.search.durationMs ?? '—'} ms{data.search.params?.durationMinutes ? ` · ${data.search.params.durationMinutes} phút` : ''}</p>
              </section>
            )}
          </div>
        )}
      </aside>
    </div>
  )
}
