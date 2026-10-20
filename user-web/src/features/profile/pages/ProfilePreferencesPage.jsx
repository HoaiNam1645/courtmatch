import { useState } from 'react'
import { Award, CalendarDays, CheckCircle2, ChevronRight, Heart, MapPinned, MapPin, Plus, Save, Target, Trash2, Users, UserRound } from 'lucide-react'
import { updatePreferences as updatePreferencesApi } from '../../../api/profile'
import { ApiError } from '../../../api/client'
import { AppHeader, BottomNav } from '../../../app/AppLayout'
import { LocationPicker } from '../../../components/LocationPicker'
import { useProfile } from '../../../hooks/useProfile'
import { classNames } from '../../../shared/utils'
import { FormAlert } from '../../../shared/FormFeedback'
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

export default ProfilePreferencesPage
