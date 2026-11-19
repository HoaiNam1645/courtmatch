import { useState } from 'react'
import { activateAiModel, fetchAiModels, importAiModel, KIND_LABEL, METRIC_LABEL, rollbackAiModel } from '../../../../api/adminAiModels'
import { MODEL_STATUS_LABEL } from '../../../../api/adminMatching'
import { useApiResource } from '../../../../hooks/useApiResource'
import { classNames } from '../../../../shared/utils'
import { ConfirmDialog } from '../../../disputes/ConfirmDialog'
import { formatDateTime } from '../../../owner/ownerDates'
import { OwnerCard, OwnerError, OwnerLoading } from '../../../owner/components/OwnerUi'

const STATUS_CLASS = { active: 'bg-emerald-100 text-emerald-700', ready: 'bg-sky-100 text-sky-700', archived: 'bg-slate-200 text-slate-600', failed: 'bg-red-100 text-red-700', training: 'bg-amber-100 text-amber-800' }
const IMPORT_TYPE = { partner: 'catboost_ranker', team: 'catboost_team_ranker' }

const metricText = (metrics) => (metrics?.metric ? `${METRIC_LABEL[metrics.metric] ?? metrics.metric}: model ${metrics.model} · baseline ${metrics.baseline}` : 'Chưa có chỉ số')

/** 2.2.16 / 2.2.19 - phiên bản model AI: chỉ số so với baseline, nhập, kích hoạt, quay lại. */
export function AiModelManagement() {
  const { status, data, error, reload } = useApiResource(fetchAiModels, [])
  const [dialog, setDialog] = useState(null)
  const [notice, setNotice] = useState('')

  async function run(action) {
    const label = { import: 'Đã nhập phiên bản model.', activate: 'Đã kích hoạt model. AI service đã nạp phiên bản này.', rollback: 'Đã quay lại phiên bản trước.' }[dialog.kind]
    await action()
    setDialog(null)
    setNotice(label)
    reload()
  }

  if (status === 'loading') return <OwnerLoading rows={2} />
  if (status === 'error') return <OwnerError message={error?.message} onRetry={reload} />

  return (
    <OwnerCard title="Phiên bản model AI" description="Nhập phiên bản đã huấn luyện, chỉ kích hoạt khi đạt kiểm thử so với baseline; có thể quay lại phiên bản trước.">
      <div data-testid="aim">
        {notice && <p role="status" data-testid="aim-notice" className="mb-4 rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm font-semibold text-emerald-900">{notice}</p>}
        <ul className="grid gap-2 sm:grid-cols-2" data-testid="aim-current">
          {data.current.map((item) => (
            <li key={item.kind} data-testid={`aim-current-${item.kind}`} data-source={item.source} className="rounded-lg border border-slate-200 p-3 text-sm">
              <p className="font-semibold">{KIND_LABEL[item.kind]}</p>
              <p className="mt-1 text-xs font-medium text-slate-600">{item.source === 'ai' ? `Đang chạy bằng AI · ${item.modelVersion}` : 'Đang chạy bằng baseline (công thức), chưa có model AI hoạt động'}</p>
            </li>
          ))}
        </ul>
        <p className={classNames('mt-3 text-xs font-semibold', data.service.reachable ? 'text-emerald-700' : 'text-red-700')} data-testid="aim-service">
          {!data.service.configured ? 'AI service chưa được cấu hình (thiếu token), hệ thống dùng baseline.' : data.service.reachable ? 'AI service đang kết nối.' : 'Không kết nối được AI service, hệ thống đang dùng baseline.'}
        </p>

        {data.importable.length > 0 && (
          <div className="mt-5" data-testid="aim-importable">
            <h3 className="text-sm font-semibold">Phiên bản có thể nhập</h3>
            <ul className="mt-2 grid gap-2">
              {data.importable.map((item) => (
                <li key={item.modelVersion} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-dashed border-slate-300 p-3 text-sm" data-testid={`aim-importable-${item.modelVersion}`}>
                  <span><span className="font-semibold">{item.modelVersion}</span> <span className="text-xs font-medium text-slate-500">· {KIND_LABEL[item.kind]}{item.trainedAt ? ` · ${formatDateTime(item.trainedAt)}` : ''} · {item.gatePasses ? 'đạt kiểm thử' : 'chưa đạt kiểm thử'}</span></span>
                  <button type="button" data-testid="aim-import" className="h-9 rounded-lg bg-slate-800 px-3 text-xs font-semibold text-white" onClick={() => setDialog({ kind: 'import', item })}>Nhập</button>
                </li>
              ))}
            </ul>
          </div>
        )}

        <div className="mt-5">
          <h3 className="text-sm font-semibold">Các phiên bản</h3>
          {data.models.length === 0 && <p className="mt-2 text-sm font-medium text-slate-500">Chưa có phiên bản nào.</p>}
          <ul className="mt-2 grid gap-2" data-testid="aim-list">
            {data.models.map((model) => (
              <li key={model.id} data-testid={`aim-${model.modelVersion}`} data-status={model.status} className="rounded-lg border border-slate-200 p-3 text-sm">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="font-semibold">{model.modelVersion} <span className="text-xs font-medium text-slate-500">· {KIND_LABEL[model.kind]}{model.modelType.startsWith('baseline') || model.modelType === 'double_team_ranker' ? ' (baseline)' : ''}</span></p>
                  <span className={classNames('rounded-full px-2.5 py-1 text-xs font-bold', STATUS_CLASS[model.status])}>{MODEL_STATUS_LABEL[model.status] ?? model.status}</span>
                </div>
                <p className="mt-1 text-xs font-medium text-slate-600" data-testid="aim-metric">{metricText(model.metrics)}</p>
                {model.gatePasses === false && <p className="mt-1 text-xs font-semibold text-red-700" data-testid="aim-gate">Chưa đạt kiểm thử: {model.gateReasons.join('; ')}</p>}
                {model.gatePasses === true && model.status !== 'active' && <p className="mt-1 text-xs font-semibold text-emerald-700" data-testid="aim-gate">Đạt kiểm thử so với baseline.</p>}
                <div className="mt-2 flex flex-wrap gap-2">
                  {model.canActivate && <button type="button" data-testid="aim-activate" className="h-9 rounded-lg bg-emerald-700 px-3 text-xs font-semibold text-white" onClick={() => setDialog({ kind: 'activate', item: model })}>Kích hoạt</button>}
                  {model.canRollback && <button type="button" data-testid="aim-rollback" className="h-9 rounded-lg border border-slate-300 bg-white px-3 text-xs font-semibold text-slate-800" onClick={() => setDialog({ kind: 'rollback', item: model })}>Quay lại phiên bản này</button>}
                </div>
              </li>
            ))}
          </ul>
        </div>

        {dialog?.kind === 'import' && (
          <ConfirmDialog title="Nhập phiên bản model?" confirmLabel="Nhập" onConfirm={() => run(() => importAiModel(IMPORT_TYPE[dialog.item.kind], dialog.item.modelVersion))} onClose={() => setDialog(null)} testId="aim-dialog">
            Hệ thống đọc metadata, kiểm tra checksum file model và lưu phiên bản ở trạng thái sẵn sàng. Chưa kích hoạt.
          </ConfirmDialog>
        )}
        {dialog?.kind === 'activate' && (
          <ConfirmDialog title="Kích hoạt model này?" confirmLabel="Kích hoạt" onConfirm={() => run(() => activateAiModel(dialog.item.id))} onClose={() => setDialog(null)} testId="aim-dialog">
            AI service sẽ nạp {dialog.item.modelVersion}; model đang hoạt động cùng loại chuyển sang lưu trữ. Người chơi bắt đầu nhận gợi ý từ model mới.
          </ConfirmDialog>
        )}
        {dialog?.kind === 'rollback' && (
          <ConfirmDialog title="Quay lại phiên bản này?" confirmLabel="Quay lại" danger onConfirm={() => run(() => rollbackAiModel(dialog.item.id))} onClose={() => setDialog(null)} testId="aim-dialog">
            Kích hoạt lại {dialog.item.modelVersion} và nạp vào AI service; model đang hoạt động chuyển sang lưu trữ.
          </ConfirmDialog>
        )}
      </div>
    </OwnerCard>
  )
}
