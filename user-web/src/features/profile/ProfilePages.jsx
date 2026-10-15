import { useState } from 'react'
import { ArrowLeft, Award, BarChart3, CalendarDays, Camera, Check, CheckCircle2, ChevronRight, Edit3, Heart, History, MapPinned, MapPin, Plus, Save, SlidersHorizontal, Star, Swords, Target, Trash2, Users, UserRound } from 'lucide-react'
import { updatePreferences as updatePreferencesApi, updateProfile as updateProfileApi } from '../../api/profile'
import { AppHeader, BottomNav } from '../../app/AppLayout'
import { LocationPicker } from '../../components/LocationPicker'
import { useProfile } from '../../hooks/useProfile'
import { classNames } from '../../shared/utils'
import { FieldError, FormAlert } from '../../shared/FormFeedback'
import { weekDays } from './profileOptions'

const skillOptions = [
  { id: 'beginner', label: 'Mới chơi', description: 'Đang làm quen kỹ thuật và luật chơi' },
  { id: 'intermediate', label: 'Trung bình', description: 'Đánh ổn định, chơi phong trào thường xuyên' },
  { id: 'advanced', label: 'Nâng cao', description: 'Kỹ thuật tốt, có kinh nghiệm thi đấu' },
  { id: 'professional', label: 'Chuyên nghiệp', description: 'Tập luyện và thi đấu chuyên sâu' },
]

const playingStyleOptions = [
  { id: 'attacking', label: 'Tấn công', description: 'Ưu tiên tốc độ và những pha cầu chủ động' },
  { id: 'balanced', label: 'Cân bằng', description: 'Linh hoạt giữa tấn công và phòng thủ' },
  { id: 'defensive', label: 'Phòng thủ', description: 'Bền bỉ, điều cầu và chờ cơ hội phản công' },
]

const handOptions = [
  { id: 'right', label: 'Tay phải' },
  { id: 'left', label: 'Tay trái' },
  { id: 'both', label: 'Cả hai tay' },
]

const playTypeOptions = [
  { id: 'single', label: 'Đánh đơn', icon: UserRound },
  { id: 'double', label: 'Đánh đôi', icon: Users },
  { id: 'mixed', label: 'Đôi nam nữ', icon: Heart },
]

function getOptionLabel(options, value) {
  return options.find((item) => item.id === value)?.label ?? value
}

function ProfileSectionTitle({ icon: Icon, eyebrow, title, action, href }) {
  const actionClass = 'text-xs font-semibold text-emerald-700 hover:text-emerald-900'
  return (
    <div className="flex items-start justify-between gap-4">
      <div className="flex items-start gap-3">
        <span className="grid size-10 shrink-0 place-items-center rounded-lg bg-emerald-50 text-emerald-700 ring-1 ring-emerald-100">
          <Icon className="size-5" />
        </span>
        <div>
          <p className="text-[11px] font-bold uppercase tracking-[0.15em] text-emerald-600">{eyebrow}</p>
          <h2 className="mt-1 text-lg font-semibold text-emerald-950">{title}</h2>
        </div>
      </div>
      {action && (href ? <a className={actionClass} href={href}>{action}</a> : <span className={actionClass}>{action}</span>)}
    </div>
  )
}

function PageBackLink({ href, label = 'Quay lại', tone = 'light' }) {
  return (
    <a
      href={href}
      className={classNames(
        'inline-flex h-10 items-center gap-2 rounded-lg px-3.5 text-sm font-semibold transition',
        tone === 'dark'
          ? 'border border-white/18 bg-white/10 text-white backdrop-blur hover:bg-white/16'
          : 'border border-emerald-100 bg-white text-emerald-800 shadow-sm hover:bg-emerald-50',
      )}
    >
      <ArrowLeft className="size-4" />
      {label}
    </a>
  )
}

function ProfileEmptyState() {
  return (
    <main className="grid min-h-screen place-items-center bg-[linear-gradient(145deg,#effaf4,#f8faf4)] px-4 text-center text-emerald-950">
      <div className="max-w-md rounded-xl border border-emerald-100 bg-white p-8 shadow-[0_20px_50px_rgba(25,70,48,0.12)]">
        <span className="mx-auto grid size-16 place-items-center rounded-full bg-emerald-50 text-emerald-700">
          <UserRound className="size-8" />
        </span>
        <h1 className="mt-5 text-2xl font-semibold">Đăng nhập để xem hồ sơ</h1>
        <p className="mt-2 text-sm font-medium leading-6 text-stone-500">Thông tin và thiết lập chơi của bạn sẽ được lưu trong tài khoản Courtly.</p>
        <a className="mt-6 flex h-12 items-center justify-center rounded-lg bg-emerald-700 text-sm font-semibold text-white" href="/login">Đăng nhập</a>
      </div>
    </main>
  )
}

function ProfileLoading({ user, onLogout }) {
  return (
    <main className="min-h-screen bg-[#f4f7f1] pb-24">
      <AppHeader user={user} onLogout={onLogout} />
      <section className="mx-auto max-w-[1100px] px-4 py-14">
        <div className="grid gap-4">
          <div className="h-32 animate-pulse rounded-xl bg-emerald-100/70" />
          <div className="h-24 animate-pulse rounded-xl bg-emerald-100/50" />
          <div className="h-24 animate-pulse rounded-xl bg-emerald-100/40" />
        </div>
      </section>
    </main>
  )
}

function ProfileLoadError({ user, onLogout, message, onRetry }) {
  return (
    <main className="min-h-screen bg-[#f4f7f1] pb-24">
      <AppHeader user={user} onLogout={onLogout} />
      <section className="mx-auto max-w-[680px] px-4 py-16 text-center">
        <h2 className="text-xl font-semibold text-emerald-950">Không tải được hồ sơ</h2>
        <p className="mt-3 text-sm font-medium text-stone-600">{message}</p>
        <button
          className="mt-6 h-11 rounded-lg bg-emerald-700 px-6 text-sm font-semibold text-white"
          onClick={onRetry}
        >
          Thử lại
        </button>
      </section>
    </main>
  )
}

function ProfilePage({ user, onLogout }) {
  const { status, data, error, reload } = useProfile()

  if (!user) return <ProfileEmptyState />
  if (status === 'loading') return <ProfileLoading user={user} onLogout={onLogout} />
  if (status === 'error') {
    return <ProfileLoadError user={user} onLogout={onLogout} message={error} onRetry={reload} />
  }

  const profile = data.profile

  const availabilityLabels = profile.availability.map((slot) => {
    const day = weekDays.find((item) => item.value === slot.day)?.short
    return `${day} · ${slot.startTime}–${slot.endTime}`
  })
  const statistics = profile.statistics
  const completion = getProfileCompletion(data.user, profile)
  const defaultLocation = profile.locations.find((location) => location.isDefault) ?? profile.locations[0]

  return (
    <main className="min-h-screen bg-[linear-gradient(145deg,#eef8f2_0%,#f8faf5_46%,#edf7f3_100%)] pb-24 text-emerald-950">
      <AppHeader user={user} onLogout={onLogout} />

      <section className="relative overflow-hidden bg-emerald-950 px-4 pb-24 pt-10 text-white md:px-6 lg:px-8">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_78%_16%,rgba(190,242,100,0.18),transparent_24%),linear-gradient(128deg,rgba(16,185,129,0.18)_0_20%,transparent_20%_62%,rgba(52,211,153,0.12)_62%)]" />
        <div className="relative mx-auto mb-6 max-w-[1200px]">
          <PageBackLink href="/" label="Về trang chủ" tone="dark" />
        </div>
        <div className="relative mx-auto flex max-w-[1200px] flex-col gap-6 md:flex-row md:items-center md:justify-between">
          <div className="flex items-center gap-5">
            <div className="relative grid size-24 shrink-0 place-items-center rounded-2xl bg-lime-300 text-4xl font-semibold text-emerald-950 shadow-[0_18px_38px_rgba(163,230,53,0.18)]">
              {user.name.slice(0, 1).toUpperCase()}
              <span className="absolute -bottom-2 -right-2 grid size-8 place-items-center rounded-full border-4 border-emerald-950 bg-emerald-500 text-white">
                <Check className="size-4" />
              </span>
            </div>
            <div>
              <span className="inline-flex rounded-full bg-white/10 px-3 py-1 text-xs font-semibold text-lime-200 ring-1 ring-white/15">Hồ sơ người chơi</span>
              <h1 className="mt-3 text-3xl font-semibold tracking-tight md:text-4xl">{user.name}</h1>
              <p className="mt-2 text-sm font-medium text-emerald-100/75">Thành viên Courtly{defaultLocation?.address ? ` · ${defaultLocation.address}` : ''}</p>
            </div>
          </div>
          <div className="flex flex-wrap gap-3">
            <a className="flex h-11 items-center gap-2 rounded-lg border border-white/20 bg-white/8 px-5 text-sm font-semibold text-white backdrop-blur" href="/bookings">
              <History className="size-4" /> Lịch đặt sân
            </a>
            <a className="flex h-11 items-center gap-2 rounded-lg border border-white/20 bg-white/8 px-5 text-sm font-semibold text-white backdrop-blur" href="/profile/preferences">
              <SlidersHorizontal className="size-4" /> Thiết lập chơi
            </a>
            <a className="flex h-11 items-center gap-2 rounded-lg bg-lime-300 px-5 text-sm font-semibold text-emerald-950" href="/profile/edit">
              <Edit3 className="size-4" /> Chỉnh sửa hồ sơ
            </a>
          </div>
        </div>
      </section>

      <section className="relative mx-auto -mt-14 max-w-[1200px] px-4 md:px-6">
        <div className="grid overflow-hidden rounded-xl border border-emerald-100 bg-white shadow-[0_18px_46px_rgba(24,65,44,0.12)] sm:grid-cols-2 lg:grid-cols-4">
          {[
            { label: 'Trình độ', value: getOptionLabel(skillOptions, profile.skillLevel) || 'Chưa thiết lập', icon: Award },
            { label: 'Điểm kỹ năng', value: (profile.skillScore ?? statistics.ratingScore).toLocaleString('vi-VN'), icon: Activity },
            { label: 'Trận đã chơi', value: statistics.totalMatches.toLocaleString('vi-VN'), icon: Target },
            { label: 'Tỷ lệ thắng', value: `${statistics.winRate}% (${statistics.totalWins}/${statistics.totalMatches})`, icon: Star },
          ].map((item, index) => {
            const Icon = item.icon
            return (
              <div key={item.label} className={classNames('flex items-center gap-3 p-5', index > 0 && 'border-t border-emerald-100 sm:border-l sm:border-t-0')}>
                <span className="grid size-11 place-items-center rounded-lg bg-emerald-50 text-emerald-700"><Icon className="size-5" /></span>
                <div><p className="text-xs font-medium text-stone-500">{item.label}</p><p className="mt-1 text-lg font-semibold text-emerald-950">{item.value}</p></div>
              </div>
            )
          })}
        </div>

        <div className="mt-6 grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_360px]">
          <div className="grid gap-6">
            <section className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_10px_28px_rgba(34,52,40,0.06)]">
              <ProfileSectionTitle icon={UserRound} eyebrow="Giới thiệu" title="Thông tin người chơi" action="Chỉnh sửa" href="/profile/edit" />
              <p className="mt-5 text-sm font-medium leading-7 text-stone-600">{profile.bio || 'Chưa có giới thiệu. Bấm Chỉnh sửa để thêm.'}</p>
              <div className="mt-5 grid gap-3 sm:grid-cols-2">
                {[
                  ['Email', user.email],
                  ['Số điện thoại', user.phone],
                  ['Ngày sinh', profile.dateOfBirth ? new Date(profile.dateOfBirth).toLocaleDateString('vi-VN') : 'Chưa thiết lập'],
                  ['Tay thuận', getOptionLabel(handOptions, profile.dominantHand) || 'Chưa thiết lập'],
                ].map(([label, value]) => (
                  <div key={label} className="rounded-lg bg-stone-50 px-4 py-3 ring-1 ring-stone-100">
                    <p className="text-xs font-medium text-stone-400">{label}</p><p className="mt-1 text-sm font-semibold text-emerald-950">{value}</p>
                  </div>
                ))}
              </div>
            </section>

            <section className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_10px_28px_rgba(34,52,40,0.06)]">
              <ProfileSectionTitle icon={Target} eyebrow="Phong cách" title="Sở thích chơi" action="Thiết lập" href="/profile/preferences" />
              <div className="mt-5 grid gap-3 sm:grid-cols-3">
                {[
                  ['Trình độ', getOptionLabel(skillOptions, profile.skillLevel) || 'Chưa thiết lập'],
                  ['Phong cách', getOptionLabel(playingStyleOptions, profile.playingStyle) || 'Chưa thiết lập'],
                  ['Hình thức', getOptionLabel(playTypeOptions, profile.preferredPlayType) || 'Chưa thiết lập'],
                ].map(([label, value]) => (
                  <div key={label} className="rounded-lg border border-emerald-100 bg-emerald-50/60 p-4">
                    <p className="text-xs font-medium text-emerald-600">{label}</p><p className="mt-2 text-base font-semibold text-emerald-950">{value}</p>
                  </div>
                ))}
              </div>
            </section>

            <section className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_10px_28px_rgba(34,52,40,0.06)]">
              <ProfileSectionTitle icon={CalendarDays} eyebrow="Lịch ưu tiên" title="Thời gian thường chơi" action="Cập nhật" href="/profile/preferences#availability" />
              <div className="mt-5 flex flex-wrap gap-2">
                {availabilityLabels.length > 0
                  ? availabilityLabels.map((label) => <span key={label} className="rounded-lg bg-emerald-700 px-4 py-2.5 text-sm font-semibold text-white">{label}</span>)
                  : <p className="text-sm font-medium text-stone-500">Chưa thiết lập khung giờ nào.</p>}
              </div>
            </section>
          </div>

          <aside className="grid gap-6 lg:sticky lg:top-24">
            <section className="overflow-hidden rounded-xl bg-emerald-950 p-5 text-white shadow-[0_14px_34px_rgba(6,78,59,0.18)]">
              <p className="text-xs font-bold uppercase tracking-[0.15em] text-lime-300">Dữ liệu thi đấu · Sprint 2</p>
              <h2 className="mt-2 text-lg font-semibold">Theo dõi hành trình trên sân</h2>
              <p className="mt-2 text-xs font-medium leading-5 text-emerald-100/70">Ghi kết quả, xác nhận cùng đối thủ và xem hiệu suất được hệ thống đo.</p>
              <div className="mt-5 grid grid-cols-2 gap-3">
                <a className="flex h-11 items-center justify-center gap-2 rounded-lg bg-white/10 text-xs font-semibold text-white ring-1 ring-white/15" href="/matches"><Swords className="size-4 text-lime-300" />Trận đấu</a>
                <a className="flex h-11 items-center justify-center gap-2 rounded-lg bg-lime-300 text-xs font-semibold text-emerald-950" href="/performance"><BarChart3 className="size-4" />Hiệu suất</a>
              </div>
            </section>

            <section className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_10px_28px_rgba(34,52,40,0.06)]">
              <ProfileSectionTitle icon={MapPinned} eyebrow="Khu vực" title="Địa điểm ưu tiên" action="Quản lý" href="/profile/preferences#locations" />
              <div className="mt-5 grid gap-3">
                {profile.locations.length === 0 && (
                  <p className="rounded-lg bg-stone-50 p-4 text-xs font-medium leading-5 text-stone-500">
                    Chưa có khu vực ưu tiên. Bấm Quản lý để thêm.
                  </p>
                )}
                {profile.locations.map((location) => (
                  <div key={location.id} className="rounded-lg border border-emerald-100 p-4">
                    <div className="flex items-center justify-between gap-3"><p className="font-semibold text-emerald-950">{location.label}</p>{location.isDefault && <span className="rounded-full bg-lime-200 px-2.5 py-1 text-[10px] font-bold uppercase text-emerald-900">Mặc định</span>}</div>
                    <p className="mt-2 text-xs font-medium leading-5 text-stone-500">{location.address || 'Chưa nhập địa chỉ'}</p>
                    <p className="mt-2 text-xs font-semibold text-emerald-700">Trong bán kính {location.radiusKm} km</p>
                    {location.latitude == null && (
                      <p className="mt-2 text-xs font-medium leading-5 text-amber-700">Chưa ghim vị trí trên bản đồ, nên không dùng để tìm sân theo bán kính. Bấm <span className="font-semibold">Quản lý</span> để ghim.</p>
                    )}
                  </div>
                ))}
              </div>
            </section>

            <section className="overflow-hidden rounded-xl bg-emerald-900 p-5 text-white shadow-[0_14px_34px_rgba(6,78,59,0.18)]">
              <p className="text-xs font-bold uppercase tracking-[0.15em] text-lime-300">Mức độ hoàn thiện</p>
              <div className="mt-3 flex items-end justify-between"><p className="text-3xl font-semibold">{completion.percent}%</p><Award className="size-8 text-lime-300" /></div>
              <div className="mt-4 h-2 overflow-hidden rounded-full bg-white/15"><div className="h-full rounded-full bg-lime-300 transition-all" style={{ width: `${completion.percent}%` }} /></div>
              {completion.missing.length > 0 ? (
                <div className="mt-3">
                  <p className="text-xs font-medium text-emerald-100/75">Còn thiếu:</p>
                  <ul className="mt-2 grid gap-1.5">
                    {completion.missing.slice(0, 3).map((item) => (
                      <li key={item} className="flex items-start gap-2 text-xs font-medium leading-5 text-emerald-100/85">
                        <span className="mt-1.5 size-1.5 shrink-0 rounded-full bg-lime-300" />{item}
                      </li>
                    ))}
                  </ul>
                </div>
              ) : (
                <p className="mt-3 text-xs font-medium leading-5 text-emerald-100/75">Hồ sơ đã đầy đủ. Bạn sẽ được ghép cặp chính xác hơn.</p>
              )}
            </section>
          </aside>
        </div>
      </section>

      <BottomNav activeLabel="Tài khoản" />
    </main>
  )
}

/**
 * Mức độ hoàn thiện hồ sơ, tính từ những trường thực sự có trong dữ liệu.
 *
 * Dùng cho gợi ý người chơi bổ sung thông tin còn thiếu — càng đầy đủ thì
 * ghép cặp ở Sprint 2 càng chính xác.
 */
function getProfileCompletion(user, profile) {
  const checks = [
    { done: Boolean(user.avatarUrl), label: 'Thêm ảnh đại diện' },
    { done: Boolean(profile.bio), label: 'Viết giới thiệu bản thân' },
    { done: Boolean(profile.dateOfBirth), label: 'Bổ sung ngày sinh' },
    { done: Boolean(profile.gender), label: 'Chọn giới tính' },
    { done: Boolean(profile.skillLevel), label: 'Chọn trình độ' },
    { done: Boolean(profile.playingStyle), label: 'Chọn phong cách chơi' },
    { done: Boolean(profile.preferredPlayType), label: 'Chọn hình thức chơi' },
    { done: Boolean(profile.dominantHand), label: 'Chọn tay thuận' },
    { done: profile.availability.length > 0, label: 'Thiết lập khung giờ chơi' },
    { done: profile.locations.length > 0, label: 'Thêm khu vực ưu tiên' },
    {
      done: profile.locations.some((location) => location.latitude != null),
      label: 'Ghim toạ độ khu vực ưu tiên',
    },
  ]

  const done = checks.filter((check) => check.done).length
  return {
    percent: Math.round((done / checks.length) * 100),
    missing: checks.filter((check) => !check.done).map((check) => check.label),
  }
}

/** Dựng giá trị ban đầu của form /profile/edit từ phản hồi API. */
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

function ProfilePreferencesPage({ user, onLogout }) {
  const { status, data, error, reload } = useProfile()
  const [editedProfile, setEditedProfile] = useState(null)
  const [openMapId, setOpenMapId] = useState(null)
  const [saved, setSaved] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})

  if (!user) return <ProfileEmptyState />
  if (status === 'loading') return <ProfileLoading user={user} onLogout={onLogout} />
  if (status === 'error') {
    return <ProfileLoadError user={user} onLogout={onLogout} message={error} onRetry={reload} />
  }

  // Dữ liệu từ API cho tới khi người dùng chạm vào, sau đó là bản đang sửa.
  const profile = editedProfile ?? data.profile
  const setProfile = (updater) =>
    setEditedProfile((current) =>
      typeof updater === 'function' ? updater(current ?? data.profile) : updater)

  function selectPreference(field, value) {
    setSaved(false)
    setProfile((current) => ({ ...current, [field]: value }))
  }

  function toggleDay(day) {
    setSaved(false)
    setProfile((current) => {
      const exists = current.availability.some((slot) => slot.day === day)
      return {
        ...current,
        availability: exists
          ? current.availability.filter((slot) => slot.day !== day)
          : [...current.availability, { day, startTime: '18:00', endTime: '21:00' }].sort((a, b) => a.day - b.day),
      }
    })
  }

  function updateSlot(day, field, value) {
    setSaved(false)
    setProfile((current) => ({ ...current, availability: current.availability.map((slot) => slot.day === day ? { ...slot, [field]: value } : slot) }))
  }

  function updateLocation(id, field, value) {
    setSaved(false)
    setProfile((current) => ({ ...current, locations: current.locations.map((location) => location.id === id ? { ...location, [field]: value } : location) }))
  }

  function addLocation() {
    setSaved(false)
    setProfile((current) => ({ ...current, locations: [...current.locations, { id: `tam-${Date.now()}`, label: 'Địa điểm mới', address: '', latitude: null, longitude: null, radiusKm: 5, isDefault: current.locations.length === 0 }] }))
  }

  function removeLocation(id) {
    setSaved(false)
    setProfile((current) => {
      const remaining = current.locations.filter((location) => location.id !== id)
      // Nghiệp vụ yêu cầu nhiều nhất một mặc định; xoá đúng cái mặc định thì đẩy cho cái đầu tiên.
      if (remaining.length > 0 && !remaining.some((location) => location.isDefault)) {
        remaining[0] = { ...remaining[0], isDefault: true }
      }
      return { ...current, locations: remaining }
    })
  }

  /** Chỉ một địa điểm được là mặc định, nên đặt cái này thì bỏ các cái còn lại. */
  function setDefaultLocation(id) {
    setSaved(false)
    setProfile((current) => ({
      ...current,
      locations: current.locations.map((location) => ({ ...location, isDefault: location.id === id })),
    }))
  }

  function setLocationCoords(id, coords) {
    setSaved(false)
    setProfile((current) => ({
      ...current,
      locations: current.locations.map((location) =>
        location.id === id ? { ...location, ...coords } : location),
    }))
  }

  function toggleMapFor(id) {
    setOpenMapId((current) => (current === id ? null : id))
  }

  async function handleSave() {
    if (submitting) return

    // Khớp đúng UpdatePreferencesRequest ở backend.
    const nextFieldErrors = {}
    if (profile.availability.some((slot) => slot.endTime <= slot.startTime)) {
      nextFieldErrors.availability = 'Giờ kết thúc phải sau giờ bắt đầu'
    }
    if (profile.locations.some((location) => !location.label.trim())) {
      nextFieldErrors.locations = 'Tên địa điểm không được để trống'
    }
    if (profile.locations.filter((location) => location.isDefault).length > 1) {
      nextFieldErrors.locations = 'Chỉ được chọn một địa điểm mặc định'
    }
    if (Object.keys(nextFieldErrors).length > 0) {
      setFieldErrors(nextFieldErrors)
      setFormError('')
      setSaved(false)
      return
    }

    setSubmitting(true)
    setFormError('')
    setFieldErrors({})

    try {
      const updated = await updatePreferencesApi(profile)
      // Đọc lại từ phản hồi của server thay vì giữ state cũ ở client.
      setProfile(updated.profile)
      setSaved(true)
    } catch (saveError) {
      setSaved(false)
      if (saveError instanceof ApiError) {
        setFieldErrors(saveError.fieldErrors ?? {})
        setFormError(saveError.message)
      } else {
        setFormError('Không lưu được thiết lập, vui lòng thử lại.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="min-h-screen bg-[linear-gradient(145deg,#eef8f2,#f9faf6)] pb-28 text-emerald-950">
      <AppHeader user={user} onLogout={onLogout} />
      <section className="mx-auto max-w-[1100px] px-4 py-7 md:px-6 md:py-10">
        <div className="mb-4"><PageBackLink href="/profile" label="Về hồ sơ" /></div>
        <div className="mb-6 flex flex-wrap items-end justify-between gap-4"><div><div className="flex items-center gap-2 text-sm font-semibold text-emerald-700"><a href="/profile">Hồ sơ</a><ChevronRight className="size-4" /><span className="text-stone-500">Thiết lập chơi</span></div><h1 className="mt-3 text-3xl font-semibold tracking-tight">Thiết lập người chơi</h1><p className="mt-2 text-sm font-medium text-stone-500">Dữ liệu này giúp tìm sân và ghép người chơi phù hợp hơn.</p></div>{saved && <span className="inline-flex items-center gap-2 rounded-full bg-emerald-100 px-4 py-2 text-sm font-semibold text-emerald-700"><CheckCircle2 className="size-4" />Đã lưu thay đổi</span>}</div>

        <div className="grid gap-6">
          <section className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_10px_28px_rgba(34,52,40,0.06)] sm:p-6">
            <ProfileSectionTitle icon={Award} eyebrow="2.1.8 · Kỹ năng" title="Trình độ hiện tại" action={profile.skillScore == null ? 'Chưa có điểm kỹ năng' : `Điểm kỹ năng ${profile.skillScore}`} />
            <div className="mt-5 grid gap-3 md:grid-cols-2">{skillOptions.map((option) => <button key={option.id} className={classNames('flex items-start gap-4 rounded-lg border p-4 text-left transition', profile.skillLevel === option.id ? 'border-emerald-500 bg-emerald-50 ring-1 ring-emerald-400' : 'border-stone-200 hover:border-emerald-200')} onClick={() => selectPreference('skillLevel', option.id)}><span className={classNames('mt-0.5 grid size-6 shrink-0 place-items-center rounded-full border-2', profile.skillLevel === option.id ? 'border-emerald-500' : 'border-stone-300')}>{profile.skillLevel === option.id && <span className="size-3 rounded-full bg-emerald-500" />}</span><span><span className="block text-sm font-semibold text-emerald-950">{option.label}</span><span className="mt-1 block text-xs font-medium leading-5 text-stone-500">{option.description}</span></span></button>)}</div>
            <div className="mt-5 border-t border-emerald-100 pt-5"><p className="text-sm font-semibold">Tay thuận</p><div className="mt-3 flex gap-3">{handOptions.map((option) => <button key={option.id} type="button" className={classNames('h-11 flex-1 rounded-lg border text-sm font-semibold', profile.dominantHand === option.id ? 'border-emerald-600 bg-emerald-700 text-white' : 'border-stone-200 text-stone-500')} onClick={() => selectPreference('dominantHand', option.id)}>{option.label}</button>)}</div></div>
          </section>

          <section className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_10px_28px_rgba(34,52,40,0.06)] sm:p-6">
            <ProfileSectionTitle icon={Target} eyebrow="2.1.9 · Sở thích" title="Phong cách chơi" />
            <div className="mt-5 grid gap-3 md:grid-cols-3">{playingStyleOptions.map((option) => <button key={option.id} className={classNames('rounded-lg border p-4 text-left', profile.playingStyle === option.id ? 'border-emerald-500 bg-emerald-50 ring-1 ring-emerald-400' : 'border-stone-200')} onClick={() => selectPreference('playingStyle', option.id)}><span className="flex items-center justify-between"><span className="text-sm font-semibold">{option.label}</span>{profile.playingStyle === option.id && <CheckCircle2 className="size-5 text-emerald-600" />}</span><span className="mt-2 block text-xs font-medium leading-5 text-stone-500">{option.description}</span></button>)}</div>
          </section>

          <section className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_10px_28px_rgba(34,52,40,0.06)] sm:p-6">
            <ProfileSectionTitle icon={Users} eyebrow="2.1.12 · Hình thức" title="Bạn thích chơi theo hình thức nào?" />
            <div className="mt-5 grid gap-3 sm:grid-cols-3">{playTypeOptions.map((option) => { const Icon = option.icon; const active = profile.preferredPlayType === option.id; return <button key={option.id} className={classNames('flex items-center gap-3 rounded-lg border p-4 text-left', active ? 'border-emerald-500 bg-emerald-700 text-white ring-1 ring-emerald-400' : 'border-stone-200 text-emerald-950')} onClick={() => selectPreference('preferredPlayType', option.id)}><span className={classNames('grid size-10 place-items-center rounded-lg', active ? 'bg-white/15' : 'bg-emerald-50 text-emerald-700')}><Icon className="size-5" /></span><span className="text-sm font-semibold">{option.label}</span></button> })}</div>
          </section>

          <section id="availability" className="scroll-mt-24 rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_10px_28px_rgba(34,52,40,0.06)] sm:p-6">
            <ProfileSectionTitle icon={CalendarDays} eyebrow="2.1.10 · Thời gian" title="Khung giờ chơi ưu tiên" />
            <div className="mt-5 grid grid-cols-7 gap-2">{weekDays.map((day) => { const active = profile.availability.some((slot) => slot.day === day.value); return <button key={day.value} className={classNames('h-12 rounded-lg text-sm font-semibold ring-1', active ? 'bg-emerald-700 text-white ring-emerald-700' : 'bg-stone-50 text-stone-400 ring-stone-200')} onClick={() => toggleDay(day.value)} title={day.label}>{day.short}</button> })}</div>
            <div className="mt-5 grid gap-3">{profile.availability.map((slot) => <div key={slot.day} className="grid items-center gap-3 rounded-lg bg-emerald-50/70 p-4 ring-1 ring-emerald-100 sm:grid-cols-[140px_1fr_auto_1fr]"><p className="text-sm font-semibold">{weekDays.find((day) => day.value === slot.day)?.label}</p><input className="h-10 rounded-lg border border-emerald-100 bg-white px-3 text-sm font-semibold outline-none focus:border-emerald-500" type="time" value={slot.startTime} onChange={(event) => updateSlot(slot.day, 'startTime', event.target.value)} /><span className="text-center text-xs font-medium text-stone-400">đến</span><input className="h-10 rounded-lg border border-emerald-100 bg-white px-3 text-sm font-semibold outline-none focus:border-emerald-500" type="time" value={slot.endTime} onChange={(event) => updateSlot(slot.day, 'endTime', event.target.value)} /></div>)}</div>
          </section>

          <section id="locations" className="scroll-mt-24 rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_10px_28px_rgba(34,52,40,0.06)] sm:p-6">
            <ProfileSectionTitle icon={MapPinned} eyebrow="2.1.11 · Địa điểm" title="Khu vực chơi ưu tiên" />
            <div className="mt-5 grid gap-4">
              {profile.locations.length === 0 && (
                <p className="rounded-lg bg-stone-50 p-4 text-sm font-medium text-stone-500">
                  Chưa có địa điểm nào. Thêm khu vực bạn hay chơi để tìm sân gần hơn.
                </p>
              )}
              {profile.locations.map((location) => {
                const hasCoords = location.latitude != null && location.longitude != null
                const mapOpen = openMapId === location.id
                return (
                  <div key={location.id} className="rounded-lg border border-emerald-100 p-4">
                    <div className="flex items-center gap-3">
                      <span className="grid size-10 shrink-0 place-items-center rounded-lg bg-emerald-50 text-emerald-700"><MapPin className="size-5" /></span>
                      <div className="min-w-0 flex-1">
                        <input className="w-full bg-transparent text-sm font-semibold outline-none" value={location.label} onChange={(event) => updateLocation(location.id, 'label', event.target.value)} placeholder="Tên địa điểm" />
                        <input className="mt-1 w-full bg-transparent text-xs font-medium text-stone-500 outline-none" value={location.address} onChange={(event) => updateLocation(location.id, 'address', event.target.value)} placeholder="Nhập khu vực ưu tiên" />
                      </div>
                      {profile.locations.length > 1 && (
                        <button type="button" className="grid size-9 place-items-center rounded-lg text-red-500 hover:bg-red-50" onClick={() => removeLocation(location.id)} aria-label="Xóa địa điểm"><Trash2 className="size-4" /></button>
                      )}
                    </div>

                    <div className="mt-4 flex items-center gap-4">
                      <span className="text-xs font-medium text-stone-500">Bán kính</span>
                      <input className="min-w-0 flex-1 accent-emerald-600" type="range" min="1" max="30" value={location.radiusKm} onChange={(event) => updateLocation(location.id, 'radiusKm', Number(event.target.value))} />
                      <span className="w-12 text-right text-sm font-semibold text-emerald-700">{location.radiusKm} km</span>
                    </div>

                    <div className="mt-4 flex flex-wrap items-center gap-2 border-t border-emerald-100 pt-4">
                      {location.isDefault ? (
                        <span className="inline-flex h-9 items-center rounded-lg bg-lime-200 px-3 text-xs font-bold uppercase text-emerald-900">Mặc định</span>
                      ) : (
                        <button type="button" className="h-9 rounded-lg border border-stone-200 px-3 text-xs font-semibold text-stone-600 hover:border-emerald-300" onClick={() => setDefaultLocation(location.id)}>Đặt mặc định</button>
                      )}

                      <span className={classNames('inline-flex h-9 items-center gap-1.5 rounded-lg px-3 text-xs font-semibold', hasCoords ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700')}>
                        {hasCoords ? <CheckCircle2 className="size-3.5" /> : <MapPinned className="size-3.5" />}
                        {hasCoords ? `${location.latitude.toFixed(4)}, ${location.longitude.toFixed(4)}` : 'Chưa ghim toạ độ'}
                      </span>

                      <button type="button" className="ml-auto h-9 rounded-lg border border-emerald-200 bg-emerald-50 px-3 text-xs font-semibold text-emerald-700" onClick={() => toggleMapFor(location.id)}>
                        {mapOpen ? 'Đóng bản đồ' : hasCoords ? 'Sửa toạ độ' : 'Ghim trên bản đồ'}
                      </button>
                    </div>

                    {!hasCoords && !mapOpen && (
                      <p className="mt-2 text-xs font-medium leading-5 text-amber-700">
                        Bán kính {location.radiusKm} km chỉ có tác dụng khi đã ghim vị trí trên bản đồ.
                        Bấm <span className="font-semibold">Ghim trên bản đồ</span> để chọn điểm xuất phát.
                      </p>
                    )}

                    {mapOpen && (
                      <LocationPicker
                        latitude={location.latitude}
                        longitude={location.longitude}
                        radiusKm={location.radiusKm}
                        onChange={(coords) => setLocationCoords(location.id, coords)}
                      />
                    )}
                  </div>
                )
              })}
            </div>
            <button className="mt-4 flex h-11 w-full items-center justify-center gap-2 rounded-lg border border-dashed border-emerald-300 bg-emerald-50/50 text-sm font-semibold text-emerald-700" onClick={addLocation}><Plus className="size-4" />Thêm địa điểm ưu tiên</button>
          </section>
        </div>

        <div className="sticky bottom-20 z-30 mt-6 rounded-xl border border-emerald-100 bg-white/95 p-4 shadow-[0_14px_38px_rgba(24,65,44,0.16)] backdrop-blur">
          {(formError || fieldErrors.availability || fieldErrors.locations) && (
            <div className="mb-3"><FormAlert message={formError || fieldErrors.availability || fieldErrors.locations} /></div>
          )}
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <p className="text-xs font-medium text-stone-500">Các thiết lập sẽ được dùng cho tìm sân và ghép cặp thông minh.</p>
            <div className="flex gap-3">
              <a className="flex h-11 items-center justify-center rounded-lg border border-stone-200 px-5 text-sm font-semibold text-stone-600" href="/profile">Hủy</a>
              <button type="button" disabled={submitting} className="flex h-11 items-center justify-center gap-2 rounded-lg bg-emerald-700 px-6 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-60" onClick={handleSave}><Save className="size-4" />{submitting ? 'Đang lưu...' : 'Lưu thiết lập'}</button>
            </div>
          </div>
        </div>
      </section>
      <BottomNav activeLabel="Tài khoản" />
    </main>
  )
}

export { PageBackLink, ProfileEmptyState, ProfilePage, ProfileSectionTitle, ProfileEditPage, ProfilePreferencesPage }
