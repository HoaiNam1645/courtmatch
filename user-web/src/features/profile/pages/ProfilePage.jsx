import { Award, BarChart3, CalendarDays, Check, Edit3, Heart, History, MapPinned, SlidersHorizontal, Star, Swords, Target, Users, UserRound } from 'lucide-react'
import { AppHeader, BottomNav } from '../../../app/AppLayout'
import { useProfile } from '../../../hooks/useProfile'
import { classNames } from '../../../shared/utils'
import { weekDays } from '../profileOptions'
import { PageBackLink, ProfileEmptyState, ProfileLoadError, ProfileLoading, ProfileSectionTitle } from '../components/ProfileUi'

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

export default ProfilePage
