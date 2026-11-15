import { useState } from 'react'
import { AlertTriangle, CheckCircle2, Plus, ShieldAlert } from 'lucide-react'
import { fetchFeePolicies, feeLabel, FEE_TYPE_LABEL, RESTRICTION_LEVEL_META, thresholdLabel } from '../../../api/adminFeePolicies'
import { useApiResource } from '../../../hooks/useApiResource'
import { classNames, formatCurrency } from '../../../shared/utils'
import { OwnerCard, OwnerError, OwnerLoading } from '../../owner/components/OwnerUi'
import { formatDateTime } from '../../owner/ownerDates'
import { AdminLayout } from '../components/AdminLayout'
import { FeePolicyDialog } from '../components/FeePolicyDialog'
import { primaryButtonClass } from '../../owner/ownerStyles'

function Stat({ label, value, testId }) {
  return (
    <div>
      <p className="text-xs font-semibold text-slate-500">{label}</p>
      <p className="mt-0.5 text-sm font-semibold text-slate-900" data-testid={testId}>{value}</p>
    </div>
  )
}

/** Chính sách đang áp dụng: nổi bật hơn các chính sách cũ. */
function ActivePolicy({ policy }) {
  if (!policy) {
    return (
      <div className="flex items-start gap-3 rounded-xl border border-red-200 bg-red-50 p-4" role="alert" data-testid="fee-no-active">
        <AlertTriangle className="mt-0.5 size-5 shrink-0 text-red-600" />
        <div>
          <p className="text-sm font-semibold text-red-800">Chưa có chính sách phí đang áp dụng</p>
          <p className="mt-1 text-xs font-medium text-red-700">Khi booking hoàn thành, hệ thống không ghi nhận được hoa hồng. Hãy thiết lập một chính sách phí.</p>
        </div>
      </div>
    )
  }
  return (
    <div className="rounded-xl border border-emerald-200 bg-emerald-50/50 p-5" data-testid="fee-active">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-100 px-2.5 py-1 text-xs font-bold text-emerald-700"><CheckCircle2 className="size-3.5" />Đang áp dụng</span>
        <span className="text-xs font-medium text-slate-500">Hiệu lực từ {formatDateTime(policy.effectiveFrom)}</span>
      </div>
      <p className="mt-3 text-sm font-semibold text-slate-600" data-testid="fee-active-name">{policy.name}</p>
      <p className="mt-1 text-3xl font-semibold text-slate-900" data-testid="fee-active-value">{feeLabel(policy.feeType, policy.feeValue)}</p>
      <p className="mt-1 text-xs font-medium text-slate-500">{FEE_TYPE_LABEL[policy.feeType]} · tạo bởi {policy.createdBy || 'hệ thống'}</p>
      <div className="mt-4 grid gap-3 border-t border-emerald-100 pt-4 sm:grid-cols-2">
        <Stat label="Hoa hồng đã ghi nhận theo chính sách này" value={policy.commissionCount.toLocaleString('vi-VN')} testId="fee-active-count" />
        <Stat label="Tổng hoa hồng" value={formatCurrency(policy.commissionTotal)} testId="fee-active-total" />
      </div>
    </div>
  )
}

function RestrictionCard({ restriction }) {
  const meta = RESTRICTION_LEVEL_META[restriction.level]
  return (
    <div className="rounded-xl border border-slate-200 p-4" data-testid={`restriction-${restriction.level}`}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm font-semibold text-slate-900">{meta.title}</p>
        <span className={classNames('rounded-full px-2.5 py-1 text-xs font-bold', restriction.enabled ? 'bg-red-100 text-red-700' : 'bg-slate-100 text-slate-600')} data-testid={`restriction-${restriction.level}-state`}>{restriction.enabled ? 'Đang bật' : 'Đang tắt'}</span>
      </div>
      <p className="mt-2 text-sm font-medium text-slate-700" data-testid={`restriction-${restriction.level}-threshold`}>Áp dụng khi {thresholdLabel(restriction)}.</p>
      <p className="mt-1 text-xs font-medium text-slate-500">{meta.effect}</p>
      {restriction.updatedBy && <p className="mt-2 text-xs font-medium text-slate-400">Cập nhật bởi {restriction.updatedBy} lúc {formatDateTime(restriction.updatedAt)}</p>}
    </div>
  )
}

/** 2.3.41 — xem cấu hình phí nền tảng. */
function AdminFeePoliciesPage() {
  const { status, data, error, reload } = useApiResource(fetchFeePolicies, [])
  const [creating, setCreating] = useState(false)
  const [notice, setNotice] = useState('')
  const history = data?.policies.filter((policy) => policy.status !== 'active') ?? []

  return (
    <AdminLayout
      eyebrow="2.3.39 · Phí nền tảng"
      title="Phí nền tảng"
      description="Chính sách hoa hồng Courtly thu từ chủ sân và ngưỡng hạn chế khi công nợ quá hạn."
      actions={data && <button type="button" className={`${primaryButtonClass} inline-flex items-center gap-2`} data-testid="fee-create-open" onClick={() => setCreating(true)}><Plus className="size-4" />Tạo chính sách mới</button>}
    >
      {notice && <p role="status" className="mb-5 flex items-start gap-2 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm font-semibold text-emerald-800" data-testid="admin-notice"><CheckCircle2 className="mt-0.5 size-4 shrink-0" />{notice}</p>}
      {status === 'loading' && <OwnerLoading rows={3} />}
      {status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
      {data && (
        <div className="grid gap-5">
          <OwnerCard title="Chính sách đang áp dụng" description="Hoa hồng đã ghi nhận giữ nguyên loại phí và giá trị lúc ghi nhận; chính sách mới chỉ áp dụng cho hoa hồng ghi nhận sau đó.">
            <ActivePolicy policy={data.active} />
          </OwnerCard>

          <OwnerCard title="Hạn chế chủ sân có công nợ quá hạn" description="Đạt một trong hai ngưỡng (số tiền hoặc số ngày quá hạn) là áp dụng mức hạn chế tương ứng.">
            <div className="grid gap-3 lg:grid-cols-2">{data.restrictions.map((restriction) => <RestrictionCard key={restriction.level} restriction={restriction} />)}</div>
            {data.restrictions.every((restriction) => !restriction.enabled) && (
              <p className="mt-3 flex items-center gap-2 text-xs font-medium text-slate-500" data-testid="restriction-all-off"><ShieldAlert className="size-4" />Chưa bật mức hạn chế nào nên chủ sân quá hạn công nợ vẫn nhận booking bình thường.</p>
            )}
          </OwnerCard>

          <OwnerCard title="Lịch sử chính sách" description={history.length === 0 ? 'Chưa có chính sách nào trước đó.' : `${history.length} chính sách đã dừng áp dụng, mới nhất trước.`}>
            {history.length > 0 && (
              <ul className="grid gap-3" data-testid="fee-history">
                {history.map((policy) => (
                  <li key={policy.id} data-testid={`fee-policy-${policy.id}`} className="rounded-xl border border-slate-200 p-4">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <p className="text-sm font-semibold text-slate-900">{policy.name}</p>
                      <p className="text-base font-semibold text-slate-700">{feeLabel(policy.feeType, policy.feeValue)}</p>
                    </div>
                    <p className="mt-1 text-xs font-medium text-slate-500">{formatDateTime(policy.effectiveFrom)}{policy.effectiveTo ? ` → ${formatDateTime(policy.effectiveTo)}` : ''} · tạo bởi {policy.createdBy || 'hệ thống'}</p>
                    <p className="mt-2 text-xs font-semibold text-slate-600" data-testid="fee-policy-usage">{policy.commissionCount === 0 ? 'Chưa ghi nhận hoa hồng nào' : `${policy.commissionCount.toLocaleString('vi-VN')} hoa hồng · ${formatCurrency(policy.commissionTotal)}`}</p>
                  </li>
                ))}
              </ul>
            )}
          </OwnerCard>
        </div>
      )}
      {creating && <FeePolicyDialog current={data?.active} onClose={() => setCreating(false)} onCreated={(updated, message) => { setCreating(false); setNotice(message); reload() }} />}
    </AdminLayout>
  )
}

export default AdminFeePoliciesPage
