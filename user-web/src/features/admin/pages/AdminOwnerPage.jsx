import { useState } from 'react'
import { CheckCircle2 } from 'lucide-react'
import { ApiError } from '../../../api/client'
import { fetchAdminOwner, reviewAdminOwner, verificationMeta } from '../../../api/adminOwner'
import { userStatusMeta } from '../../../api/adminUsers'
import { useApiResource } from '../../../hooks/useApiResource'
import { FieldError, FormAlert } from '../../../shared/FormFeedback'
import { classNames, formatCurrency } from '../../../shared/utils'
import { OwnerCard, OwnerEmpty, OwnerError, OwnerLoading } from '../../owner/components/OwnerUi'
import { formatDateTime } from '../../owner/ownerDates'
import { dangerButtonClass, primaryButtonClass, secondaryButtonClass, textareaClass } from '../../owner/ownerStyles'
import { AdminLayout } from '../components/AdminLayout'

const REVIEW_ACTIONS = {
  verified: { label: 'Xác minh hồ sơ', needsReason: false, className: primaryButtonClass },
  rejected: { label: 'Từ chối xác minh', needsReason: true, className: dangerButtonClass },
  pending: { label: 'Đặt lại chờ xác minh', needsReason: true, className: secondaryButtonClass },
}

function Row({ label, value, testId }) {
  return (
    <div className="flex items-start justify-between gap-3 py-1.5 text-sm">
      <dt className="shrink-0 font-medium text-slate-500">{label}</dt>
      <dd className="min-w-0 break-words text-right font-semibold text-slate-800" data-testid={testId}>{value || 'Chưa có'}</dd>
    </div>
  )
}

/** Hộp quyết định xác minh hồ sơ chủ sân. */
function ReviewPanel({ profile, onDone }) {
  const [pending, setPending] = useState(null)
  const [reason, setReason] = useState('')
  const [reasonError, setReasonError] = useState('')
  const [formError, setFormError] = useState('')
  const [busy, setBusy] = useState(false)
  const options = ['verified', 'rejected', 'pending'].filter((value) => value !== profile.verificationStatus)

  async function submit(event) {
    event.preventDefault()
    if (busy) return
    const trimmed = reason.trim()
    if (REVIEW_ACTIONS[pending].needsReason && trimmed.length < 3) return setReasonError('Vui lòng nhập lý do (ít nhất 3 ký tự)')
    setReasonError('')
    setFormError('')
    setBusy(true)
    try {
      const result = await reviewAdminOwner({ status: pending, reason: trimmed })
      setPending(null)
      setReason('')
      onDone(result, `Hồ sơ chủ sân: ${verificationMeta(pending).label}.`)
    } catch (failure) {
      if (failure instanceof ApiError) {
        setReasonError(failure.fieldErrors?.reason ?? '')
        setFormError(failure.message)
      } else {
        setFormError('Đã có lỗi xảy ra, vui lòng thử lại.')
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="mt-4 border-t border-slate-100 pt-4">
      <FormAlert message={pending ? '' : formError} />
      {!pending && (
        <div className="mt-2 flex flex-wrap gap-2">
          {options.map((value) => <button key={value} type="button" className={REVIEW_ACTIONS[value].className} data-testid={`owner-review-${value}`} onClick={() => { setPending(value); setReason(''); setReasonError(''); setFormError('') }}>{REVIEW_ACTIONS[value].label}</button>)}
        </div>
      )}
      {pending && (
        <form className="grid gap-3" noValidate onSubmit={submit} data-testid="owner-review-form">
          <p className="text-sm font-semibold text-slate-900">{REVIEW_ACTIONS[pending].label}</p>
          <label className="block">
            <span className="text-sm font-semibold text-slate-900">Lý do{REVIEW_ACTIONS[pending].needsReason ? <span className="text-red-600"> *</span> : ' (không bắt buộc)'}</span>
            <textarea className={`${textareaClass} mt-2 h-24`} aria-label="Lý do xác minh" maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)} aria-invalid={Boolean(reasonError)} />
            <FieldError message={reasonError} />
          </label>
          <FormAlert message={formError} />
          <div className="flex justify-end gap-2">
            <button type="button" className={secondaryButtonClass} disabled={busy} onClick={() => setPending(null)}>Hủy</button>
            <button type="submit" className={REVIEW_ACTIONS[pending].className} disabled={busy}>{busy ? 'Đang lưu...' : 'Xác nhận'}</button>
          </div>
        </form>
      )}
    </div>
  )
}

/** 2.3.32 — tài khoản chủ sân duy nhất. */
function AdminOwnerPage() {
  const { status, data, error, reload } = useApiResource(fetchAdminOwner, [], { isEmpty: (result) => !result.exists })
  const [notice, setNotice] = useState('')
  const [override, setOverride] = useState(null)
  const view = override ?? data

  return (
    <AdminLayout eyebrow="2.3.32 · Chủ sân" title="Chủ sân" description="Hệ thống hiện chỉ có một chủ sân đối tác. Xem hồ sơ, tổng quan và quyết định xác minh.">
      {notice && <p role="status" className="mb-5 flex items-start gap-2 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm font-semibold text-emerald-800" data-testid="admin-notice"><CheckCircle2 className="mt-0.5 size-4 shrink-0" />{notice}</p>}
      {status === 'loading' && <OwnerLoading rows={3} />}
      {status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
      {status === 'empty' && <OwnerEmpty title="Hệ thống chưa có tài khoản chủ sân" description="Quản trị viên cấp tài khoản chủ sân ở chức năng tạo tài khoản chủ sân." />}
      {status === 'success' && view?.exists && (
        <div className="grid gap-5 lg:grid-cols-2">
          <OwnerCard title="Tài khoản">
            <dl className="divide-y divide-slate-100">
              <Row label="Họ tên" value={view.owner.fullName} testId="owner-name" />
              <Row label="Email" value={view.owner.email} />
              <Row label="Số điện thoại" value={view.owner.phone} />
              <Row label="Trạng thái" value={userStatusMeta(view.owner.status).label} testId="owner-account-status" />
              <Row label="Đăng nhập gần nhất" value={view.owner.lastLoginAt ? formatDateTime(view.owner.lastLoginAt) : 'Chưa đăng nhập'} />
              {view.owner.passwordChangeRequired && <Row label="Mật khẩu" value="Đang dùng mật khẩu tạm" />}
            </dl>
          </OwnerCard>

          <OwnerCard title="Hồ sơ doanh nghiệp">
            {view.profile ? (
              <>
                <span className={classNames('inline-flex rounded-full px-2.5 py-1 text-xs font-bold', verificationMeta(view.profile.verificationStatus).className)} data-testid="owner-verification">{verificationMeta(view.profile.verificationStatus).label}</span>
                <dl className="mt-2 divide-y divide-slate-100">
                  <Row label="Đơn vị kinh doanh" value={view.profile.businessName} />
                  <Row label="Mã số thuế" value={view.profile.taxCode} />
                  <Row label="Ngân hàng nhận tiền" value={[view.profile.bankName, view.profile.bankAccountNo, view.profile.bankAccountName].filter(Boolean).join(' · ')} />
                  {view.profile.verifiedBy && <Row label="Người quyết định" value={view.profile.verifiedBy} testId="owner-verified-by" />}
                </dl>
                {view.profile.verificationNote && <p className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-xs font-semibold text-red-800" data-testid="owner-verification-note">Lý do: {view.profile.verificationNote}</p>}
                <ReviewPanel profile={view.profile} onDone={(result, message) => { setOverride(result); setNotice(message) }} />
              </>
            ) : <p className="text-sm font-medium text-slate-500">Chủ sân chưa có hồ sơ doanh nghiệp.</p>}
          </OwnerCard>

          <OwnerCard title="Tổng quan" className="lg:col-span-2">
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <div className="rounded-xl border border-slate-200 p-4"><p className="text-xs font-semibold text-slate-500">Sân chờ duyệt</p><p className="mt-1 text-xl font-semibold text-amber-700" data-testid="overview-pending">{view.overview.venuesByApproval.pending ?? 0}</p></div>
              <div className="rounded-xl border border-slate-200 p-4"><p className="text-xs font-semibold text-slate-500">Sân đã duyệt</p><p className="mt-1 text-xl font-semibold text-emerald-700" data-testid="overview-approved">{view.overview.venuesByApproval.approved ?? 0}</p></div>
              <div className="rounded-xl border border-slate-200 p-4"><p className="text-xs font-semibold text-slate-500">Công nợ hoa hồng</p><p className="mt-1 text-xl font-semibold text-slate-900" data-testid="overview-commission">{formatCurrency(view.overview.outstandingCommission)}</p></div>
              <div className="rounded-xl border border-slate-200 p-4"><p className="text-xs font-semibold text-slate-500">Booking đang xử lý</p><p className="mt-1 text-xl font-semibold text-slate-900" data-testid="overview-bookings">{view.overview.activeBookings}</p></div>
            </div>
          </OwnerCard>
        </div>
      )}
    </AdminLayout>
  )
}

export default AdminOwnerPage
