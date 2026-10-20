import { useState } from 'react'
import { Camera, ChevronRight, Save } from 'lucide-react'
import { updateProfile as updateProfileApi } from '../../../api/profile'
import { ApiError } from '../../../api/client'
import { AppHeader, BottomNav } from '../../../app/AppLayout'
import { useProfile } from '../../../hooks/useProfile'
import { classNames } from '../../../shared/utils'
import { FieldError, FormAlert } from '../../../shared/FormFeedback'
import { saveCachedUser } from '../../../shared/session'
import { PageBackLink, ProfileEmptyState, ProfileLoadError, ProfileLoading } from '../components/ProfileUi'

function toEditForm(data) {
  return {
    name: data.user.name ?? '',
    email: data.user.email ?? '',
    phone: data.user.phone ?? '',
    gender: data.profile.gender ?? '',
    dateOfBirth: data.profile.dateOfBirth ?? '',
    bio: data.profile.bio ?? '',
  }
}

function ProfileEditPage({ user, onLogout }) {
  const { status, data, error, reload } = useProfile()
  const [editedForm, setEditedForm] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})

  if (!user) return <ProfileEmptyState />
  if (status === 'loading') return <ProfileLoading user={user} onLogout={onLogout} />
  if (status === 'error') {
    return <ProfileLoadError user={user} onLogout={onLogout} message={error} onRetry={reload} />
  }

  // Giá trị hiển thị được suy ra lúc render: dữ liệu từ API cho tới khi người dùng sửa.
  const form = editedForm ?? toEditForm(data)

  function updateField(field, value) {
    setEditedForm((current) => ({ ...(current ?? toEditForm(data)), [field]: value }))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (submitting) return

    // Các điều kiện dưới đây khớp đúng với UpdateProfileRequest ở backend.
    const nextFieldErrors = {}
    if (form.name.trim().length < 2) {
      nextFieldErrors.fullName = 'Họ và tên từ 2 đến 150 ký tự'
    }
    if (!form.email.trim() && !form.phone.trim()) {
      nextFieldErrors.identityProvided = 'Vui lòng nhập email hoặc số điện thoại'
    }
    if (form.email.trim() && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(form.email.trim())) {
      nextFieldErrors.email = 'Email không đúng định dạng'
    }
    if (form.bio.length > 240) {
      nextFieldErrors.bio = 'Giới thiệu tối đa 240 ký tự'
    }
    if (Object.keys(nextFieldErrors).length > 0) {
      setFieldErrors(nextFieldErrors)
      setFormError('')
      return
    }

    setSubmitting(true)
    setFormError('')
    setFieldErrors({})

    try {
      const updated = await updateProfileApi({
        fullName: form.name.trim(),
        email: form.email.trim(),
        phone: form.phone.trim(),
        gender: form.gender,
        dateOfBirth: form.dateOfBirth,
        bio: form.bio,
      })
      saveCachedUser(updated.user)
      window.location.href = '/profile'
    } catch (submitError) {
      if (submitError instanceof ApiError) {
        setFieldErrors(submitError.fieldErrors ?? {})
        setFormError(submitError.message)
      } else {
        setFormError('Không lưu được hồ sơ, vui lòng thử lại.')
      }
      setSubmitting(false)
    }
  }

  return (
    <main className="min-h-screen bg-[linear-gradient(145deg,#eef8f2,#f9faf6)] pb-24 text-emerald-950">
      <AppHeader user={user} onLogout={onLogout} />
      <section className="mx-auto max-w-[960px] px-4 py-7 md:px-6 md:py-10">
        <div className="mb-4"><PageBackLink href="/profile" label="Về hồ sơ" /></div>
        <div className="mb-6 flex items-center gap-3 text-sm font-semibold text-emerald-700"><a href="/profile">Hồ sơ</a><ChevronRight className="size-4" /><span className="text-stone-500">Chỉnh sửa</span></div>
        <form className="overflow-hidden rounded-xl border border-emerald-100 bg-white shadow-[0_16px_42px_rgba(25,70,48,0.09)]" onSubmit={handleSubmit}>
          <div className="border-b border-emerald-100 bg-[linear-gradient(130deg,#064e3b,#04793f)] px-6 py-7 text-white sm:px-8">
            <p className="text-xs font-bold uppercase tracking-[0.15em] text-lime-300">2.1.7 · Chỉnh sửa hồ sơ</p>
            <h1 className="mt-2 text-2xl font-semibold">Thông tin cá nhân</h1>
            <p className="mt-2 text-sm font-medium text-emerald-100/75">Thông tin này được dùng cho tài khoản, booking và hồ sơ người chơi.</p>
          </div>

          <div className="grid gap-8 p-6 sm:p-8 md:grid-cols-[180px_minmax(0,1fr)]">
            <div className="text-center">
              <div className="relative mx-auto grid size-32 place-items-center rounded-2xl bg-emerald-100 text-5xl font-semibold text-emerald-800">
                {form.name.slice(0, 1).toUpperCase() || 'B'}
                {/* Chưa có API tải ảnh đại diện. Vô hiệu hoá thay vì để nút không làm gì. */}
                <button type="button" disabled title="Chức năng đang được phát triển" className="absolute -bottom-3 -right-3 grid size-11 cursor-not-allowed place-items-center rounded-full border-4 border-white bg-emerald-700 text-white opacity-60 shadow-lg" aria-label="Đổi ảnh đại diện"><Camera className="size-5" /></button>
              </div>
              <p className="mt-5 text-sm font-semibold">Ảnh đại diện</p><p className="mt-1 text-xs font-medium leading-5 text-stone-400">JPG hoặc PNG, tối đa 5 MB</p>
            </div>

            <div className="grid gap-5 sm:grid-cols-2">
              {[
                { field: 'name', label: 'Họ và tên (*)', type: 'text', placeholder: 'Nhập họ và tên', required: true, error: fieldErrors.fullName },
                { field: 'phone', label: 'Số điện thoại', type: 'tel', placeholder: 'Nhập số điện thoại', required: false, error: fieldErrors.phone ?? fieldErrors.identityProvided },
                { field: 'email', label: 'Email', type: 'email', placeholder: 'name@email.com', required: false, error: fieldErrors.email ?? fieldErrors.identityProvided },
                { field: 'dateOfBirth', label: 'Ngày sinh', type: 'date', placeholder: '', required: false, error: fieldErrors.dateOfBirth ?? fieldErrors.ageReasonable },
              ].map((item) => (
                <label key={item.field} className="block"><span className="text-sm font-semibold">{item.label}</span><input className="mt-2.5 h-12 w-full rounded-lg border border-stone-200 px-4 text-sm outline-none focus:border-emerald-500 focus:ring-1 focus:ring-emerald-300" value={form[item.field]} onChange={(event) => updateField(item.field, event.target.value)} type={item.type} placeholder={item.placeholder} required={item.required} /><FieldError message={item.error} /></label>
              ))}

              <label className="block sm:col-span-2"><span className="text-sm font-semibold">Giới tính</span><div className="mt-2.5 grid grid-cols-3 gap-2">{[['male', 'Nam'], ['female', 'Nữ'], ['other', 'Khác']].map(([value, label]) => <button key={value} type="button" className={classNames('h-11 rounded-lg border text-sm font-semibold', form.gender === value ? 'border-emerald-600 bg-emerald-50 text-emerald-700 ring-1 ring-emerald-500' : 'border-stone-200 text-stone-500')} onClick={() => updateField('gender', value)}>{label}</button>)}</div></label>

              <label className="block sm:col-span-2"><span className="text-sm font-semibold">Giới thiệu bản thân</span><textarea className="mt-2.5 min-h-28 w-full resize-none rounded-lg border border-stone-200 p-4 text-sm leading-6 outline-none focus:border-emerald-500 focus:ring-1 focus:ring-emerald-300" value={form.bio} onChange={(event) => updateField('bio', event.target.value)} maxLength={240} /><span className="mt-1.5 block text-right text-xs font-medium text-stone-400">{form.bio.length}/240</span><FieldError message={fieldErrors.bio} /></label>
            </div>
          </div>

          <div className="border-t border-emerald-100 bg-stone-50 px-6 py-5 sm:px-8">
            <div className="mb-4"><FormAlert message={formError} /></div>
            <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
              <a className="flex h-12 items-center justify-center rounded-lg border border-stone-200 bg-white px-6 text-sm font-semibold text-stone-600" href="/profile">Hủy thay đổi</a>
              <button type="submit" disabled={submitting} className="flex h-12 items-center justify-center gap-2 rounded-lg bg-emerald-700 px-7 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-60"><Save className="size-4" />{submitting ? 'Đang lưu...' : 'Lưu hồ sơ'}</button>
            </div>
          </div>
        </form>
      </section>
      <BottomNav activeLabel="Tài khoản" />
    </main>
  )
}

export default ProfileEditPage
