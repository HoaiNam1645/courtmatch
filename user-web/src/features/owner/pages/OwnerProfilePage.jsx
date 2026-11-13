import { useState } from 'react'
import { BadgeCheck, CheckCircle2 } from 'lucide-react'
import { fetchCurrentUser } from '../../../api/auth'
import { ApiError } from '../../../api/client'
import { fetchOwnerProfile, updateOwnerProfile, VERIFICATION_STATUS_META } from '../../../api/ownerProfile'
import { useApiResource } from '../../../hooks/useApiResource'
import { FormAlert } from '../../../shared/FormFeedback'
import { saveCachedUser } from '../../../shared/session'
import { classNames } from '../../../shared/utils'
import { OwnerFormField } from '../components/OwnerFormField'
import { OwnerLayout } from '../components/OwnerLayout'
import { OwnerCard, OwnerError, OwnerLoading } from '../components/OwnerUi'
import { inputClass, primaryButtonClass } from '../ownerStyles'

const VN_MOBILE = /^0[35789]\d{8}$/

function normalizePhone(raw) {
  const digits = raw.replace(/[^0-9+]/g, '')
  if (digits.startsWith('+84')) return `0${digits.slice(3)}`
  if (digits.startsWith('84') && digits.length === 11) return `0${digits.slice(2)}`
  return digits
}

/** Cùng điều kiện với UpdateOwnerProfileRequest và OwnerProfileService ở backend. */
function validate(values) {
  const errors = {}
  const fullName = values.fullName.trim()
  if (!fullName) errors.fullName = 'Vui lòng nhập tên người liên hệ'
  else if (fullName.length < 2 || fullName.length > 150) errors.fullName = 'Tên người liên hệ từ 2 đến 150 ký tự'
  if (!values.phone.trim()) errors.phone = 'Vui lòng nhập số điện thoại liên hệ'
  else if (!VN_MOBILE.test(normalizePhone(values.phone))) errors.phone = 'Số di động Việt Nam gồm 10 số, bắt đầu bằng 03, 05, 07, 08 hoặc 09'
  if (!values.businessName.trim()) errors.businessName = 'Vui lòng nhập tên doanh nghiệp hoặc hộ kinh doanh'
  else if (values.businessName.trim().length > 200) errors.businessName = 'Tên doanh nghiệp tối đa 200 ký tự'
  if (values.taxCode.trim() && !/^\d{10}(-\d{3})?$/.test(values.taxCode.trim())) errors.taxCode = 'Mã số thuế gồm 10 số, hoặc 10 số kèm -3 số chi nhánh'
  if (!values.bankName.trim()) errors.bankName = 'Vui lòng nhập tên ngân hàng'
  else if (values.bankName.trim().length > 100) errors.bankName = 'Tên ngân hàng tối đa 100 ký tự'
  if (!values.bankAccountNo.trim()) errors.bankAccountNo = 'Vui lòng nhập số tài khoản'
  else if (!/^\d{6,20}$/.test(values.bankAccountNo.replace(/\s/g, ''))) errors.bankAccountNo = 'Số tài khoản gồm 6 đến 20 chữ số'
  if (!values.bankAccountName.trim()) errors.bankAccountName = 'Vui lòng nhập tên chủ tài khoản'
  else if (values.bankAccountName.trim().length > 150) errors.bankAccountName = 'Tên chủ tài khoản tối đa 150 ký tự'
  return errors
}

function ProfileForm({ profile, onSaved }) {
  const [values, setValues] = useState(profile)
  const [fieldErrors, setFieldErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  function handleChange(name, value) {
    setValues((current) => ({ ...current, [name]: value }))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (submitting) return
    const errors = validate(values)
    setFieldErrors(errors)
    setFormError('')
    if (Object.keys(errors).length > 0) return

    setSubmitting(true)
    try {
      await updateOwnerProfile(values)
      // Tên và số điện thoại hiện ở header lấy từ tài khoản: tải lại từ backend.
      saveCachedUser(await fetchCurrentUser())
      onSaved()
    } catch (error) {
      if (error instanceof ApiError) {
        setFieldErrors(error.fieldErrors ?? {})
        setFormError(error.message)
      } else {
        setFormError('Đã có lỗi xảy ra, vui lòng thử lại.')
      }
      setSubmitting(false)
    }
  }

  const status = VERIFICATION_STATUS_META[profile.verificationStatus]
    ?? { label: profile.verificationStatus ?? 'Không rõ', className: 'bg-stone-200 text-stone-700' }

  return (
    <form className="grid gap-5" noValidate onSubmit={handleSubmit}>
      <OwnerCard
        title="Thông tin liên hệ"
        description="Người chơi và quản trị viên dùng thông tin này để liên hệ với bạn."
        actions={(
          <span data-testid="verification-status" className={classNames('inline-flex items-center gap-1.5 rounded-full px-3 py-1.5 text-xs font-bold', status.className)}>
            <BadgeCheck className="size-4" />
            {status.label}
          </span>
        )}
      >
        <div className="grid gap-5 md:grid-cols-2">
          <OwnerFormField label="Người liên hệ" name="fullName" value={values.fullName} onChange={handleChange} error={fieldErrors.fullName} required maxLength={150} />
          <OwnerFormField label="Số điện thoại" name="phone" value={values.phone} onChange={handleChange} error={fieldErrors.phone} required inputMode="tel" />
          <label className="block md:col-span-2">
            <span className="text-sm font-semibold text-emerald-950">Email đăng nhập</span>
            <input className={`${inputClass} mt-2`} value={values.email} disabled readOnly name="email" />
            <span className="mt-1.5 block text-xs font-medium text-stone-500">Email là tài khoản đăng nhập, liên hệ quản trị viên nếu cần thay đổi.</span>
          </label>
          <OwnerFormField label="Tên doanh nghiệp / hộ kinh doanh" name="businessName" value={values.businessName} onChange={handleChange} error={fieldErrors.businessName} required maxLength={200} />
          <OwnerFormField label="Mã số thuế" name="taxCode" value={values.taxCode} onChange={handleChange} error={fieldErrors.taxCode} hint="Không bắt buộc. 10 số hoặc 10 số kèm -3 số chi nhánh." />
        </div>
      </OwnerCard>

      <OwnerCard
        title="Tài khoản nhận thanh toán"
        description="Người chơi chuyển khoản trực tiếp vào tài khoản này. Thay đổi chỉ áp dụng cho booking được chấp nhận sau khi lưu; booking cũ giữ nguyên thông tin đã gửi người chơi."
      >
        <div className="grid gap-5 md:grid-cols-3">
          <OwnerFormField label="Ngân hàng" name="bankName" value={values.bankName} onChange={handleChange} error={fieldErrors.bankName} required maxLength={100} />
          <OwnerFormField label="Số tài khoản" name="bankAccountNo" value={values.bankAccountNo} onChange={handleChange} error={fieldErrors.bankAccountNo} required inputMode="numeric" />
          <OwnerFormField label="Chủ tài khoản" name="bankAccountName" value={values.bankAccountName} onChange={handleChange} error={fieldErrors.bankAccountName} required maxLength={150} hint="Hệ thống tự viết hoa khi lưu." />
        </div>
      </OwnerCard>

      <FormAlert message={formError} />
      <div className="flex justify-end">
        <button type="submit" className={primaryButtonClass} disabled={submitting}>
          {submitting ? 'Đang lưu...' : 'Lưu hồ sơ'}
        </button>
      </div>
    </form>
  )
}

/** 2.3.2 — xem và cập nhật hồ sơ chủ sân. */
function OwnerProfilePage() {
  const { status, data, error, reload } = useApiResource(fetchOwnerProfile, [])
  const [savedCount, setSavedCount] = useState(0)

  function handleSaved() {
    setSavedCount((value) => value + 1)
    reload()
  }

  return (
    <OwnerLayout eyebrow="2.3.2 · Hồ sơ chủ sân" title="Hồ sơ chủ sân" description="Thông tin liên hệ và tài khoản ngân hàng nhận tiền sân trực tiếp từ người chơi.">
      {savedCount > 0 && status !== 'loading' && (
        <div role="status" className="mb-5 flex items-center gap-2 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-semibold text-emerald-800">
          <CheckCircle2 className="size-4" />
          Đã lưu hồ sơ chủ sân.
        </div>
      )}
      {status === 'loading' && <OwnerLoading rows={2} />}
      {status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
      {status === 'success' && <ProfileForm key={`${data.updatedAt}-${savedCount}`} profile={data} onSaved={handleSaved} />}
    </OwnerLayout>
  )
}

export default OwnerProfilePage
