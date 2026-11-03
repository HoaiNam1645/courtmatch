import { useMemo, useState } from 'react'
import {
  ArrowLeft, ArrowRight, CalendarDays, Clock3, Gauge,
  MapPin, RotateCcw, Search, ShieldCheck, Sparkles, Target, Users,
} from 'lucide-react'
import { AppHeader, BottomNav } from '../../../app/AppLayout'
import { usePartnerSearch } from '../../../hooks/usePartnerSearch'
import { useProfile } from '../../../hooks/useProfile'
import { classNames } from '../../../shared/utils'
import { ProfileEmptyState, ProfileSectionTitle } from '../../profile/components/ProfileUi'
import { CompatibilityMeter } from '../components/CompatibilityMeter'

const styleLabels = { attacking: 'Thiên công', balanced: 'Cân bằng', defensive: 'Phòng thủ' }
const playTypeLabels = { single: 'Đánh đơn', double: 'Đánh đôi', mixed: 'Đôi nam nữ' }
const durations = [60, 90, 120]
const ratingRanges = [100, 200, 300]

function localDateString(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function nextPreferredSlot(availability) {
  const now = new Date()
  for (let offset = 1; offset <= 7; offset += 1) {
    const candidate = new Date(now)
    candidate.setDate(now.getDate() + offset)
    const isoDay = candidate.getDay() === 0 ? 7 : candidate.getDay()
    const slot = availability.find((item) => item.day === isoDay)
    if (slot) return { date: localDateString(candidate), time: slot.startTime.slice(0, 5) }
  }
  const tomorrow = new Date(now)
  tomorrow.setDate(now.getDate() + 1)
  return { date: localDateString(tomorrow), time: '18:00' }
}

function SearchSkeleton() {
  return <div className="grid gap-4 md:grid-cols-2">{[1, 2, 3, 4].map((item) => <div key={item} className="h-60 animate-pulse rounded-2xl border border-emerald-100 bg-white/70" />)}</div>
}

function InitialAvatar({ name }) {
  return <span className="grid size-14 shrink-0 place-items-center rounded-2xl bg-emerald-900 text-xl font-semibold text-lime-300">{name.slice(0, 1).toUpperCase()}</span>
}

function PartnerCard({ partner }) {
  return (
    <article className="group relative overflow-hidden rounded-2xl border border-emerald-100 bg-white p-5 shadow-[0_12px_32px_rgba(26,72,49,0.07)] transition hover:-translate-y-0.5 hover:border-emerald-200 hover:shadow-[0_18px_38px_rgba(26,72,49,0.11)]">
      <div className="absolute right-0 top-0 h-28 w-28 rounded-bl-full bg-[radial-gradient(circle_at_top_right,rgba(190,242,100,0.48),rgba(236,253,245,0.25))]" />
      <div className="relative">
        <div className="flex items-start gap-4">
          {partner.avatarUrl ? <img className="size-14 rounded-2xl object-cover" src={partner.avatarUrl} alt="" /> : <InitialAvatar name={partner.fullName} />}
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <h3 className="truncate text-lg font-semibold text-emerald-950">{partner.fullName}</h3>
              {partner.provisional && <span className="rounded-full bg-amber-100 px-2.5 py-1 text-[10px] font-bold uppercase text-amber-800">Điểm tạm tính</span>}
            </div>
            <p className="mt-1 text-xs font-semibold text-stone-400">{partner.ratingLabel} · {partner.totalMatches} trận hợp lệ</p>
          </div>
        </div>

        <div className="mt-5 grid grid-cols-3 gap-2">
          <div className="rounded-xl bg-emerald-950 p-3 text-white"><p className="text-[10px] font-bold uppercase tracking-[0.1em] text-emerald-200/70">Elo</p><p className="mt-1 text-xl font-semibold text-lime-300">{partner.ratingScore.toFixed(0)}</p></div>
          <div className="rounded-xl bg-lime-100 p-3"><p className="text-[10px] font-bold uppercase tracking-[0.1em] text-emerald-700">Tương đồng</p><p className="mt-1 text-xl font-semibold text-emerald-950">{partner.skillSimilarity}%</p></div>
          <div className="rounded-xl bg-stone-50 p-3"><p className="text-[10px] font-bold uppercase tracking-[0.1em] text-stone-400">Chênh Elo</p><p className="mt-1 text-xl font-semibold text-emerald-950">{partner.ratingGap.toFixed(0)}</p></div>
        </div>

        <div className="mt-4 grid gap-2 text-xs font-semibold text-stone-600 sm:grid-cols-2">
          <span className="flex items-center gap-2 rounded-lg bg-emerald-50 px-3 py-2.5"><MapPin className="size-4 text-emerald-600" />Cách khoảng {partner.distanceKm.toFixed(1)} km</span>
          <span className="flex items-center gap-2 rounded-lg bg-emerald-50 px-3 py-2.5"><Clock3 className="size-4 text-emerald-600" />Rảnh {partner.availableFrom.slice(0, 5)}–{partner.availableUntil.slice(0, 5)}</span>
        </div>

        <div className="mt-4 rounded-2xl bg-[#f5f8f2] p-3">
          <div className="mb-2 flex items-center justify-between gap-3 px-0.5">
            <p className="text-[10px] font-bold uppercase tracking-[0.1em] text-stone-400">Mức độ tương thích</p>
            <span className="text-[10px] font-semibold text-stone-400">Backend đánh giá</span>
          </div>
          <div className="grid gap-2 sm:grid-cols-3">
            <CompatibilityMeter icon={MapPin} label="Vị trí" value={partner.locationCompatibility} accentClass="bg-emerald-600" />
            <CompatibilityMeter icon={Clock3} label="Thời gian" value={partner.timeCompatibility} accentClass="bg-lime-500" />
            <CompatibilityMeter icon={Sparkles} label="Phong cách" value={partner.styleCompatibility} accentClass="bg-amber-400" />
          </div>
        </div>

        <div className="mt-4 flex flex-wrap gap-2 border-t border-emerald-50 pt-4">
          <span className="rounded-full border border-emerald-100 px-3 py-1.5 text-[11px] font-semibold text-emerald-700">{styleLabels[partner.playingStyle] ?? 'Chưa chọn phong cách'}</span>
          <span className="rounded-full border border-emerald-100 px-3 py-1.5 text-[11px] font-semibold text-emerald-700">{playTypeLabels[partner.preferredPlayType] ?? 'Linh hoạt hình thức'}</span>
        </div>
      </div>
    </article>
  )
}

function PartnerSearchPage({ user, onLogout }) {
  const profile = useProfile()
  const results = usePartnerSearch()
  const suggestedSlot = useMemo(() => nextPreferredSlot(profile.data?.profile.availability ?? []), [profile.data])
  const [form, setForm] = useState(null)
  const [clientErrors, setClientErrors] = useState({})
  const activeForm = form ?? {
    preferredLocationId: profile.data?.profile.locations.find((item) => item.isDefault && item.latitude != null)?.id
      ?? profile.data?.profile.locations.find((item) => item.latitude != null)?.id
      ?? '',
    date: suggestedSlot.date,
    time: suggestedSlot.time,
    durationMinutes: 90,
    maxRatingDifference: 200,
  }

  if (!user) return <ProfileEmptyState />
  if (profile.status === 'loading') return <main className="min-h-screen bg-[#f4f7f1]"><AppHeader user={user} onLogout={onLogout} /><div className="mx-auto max-w-[1200px] px-4 py-12"><SearchSkeleton /></div></main>
  if (profile.status === 'error') return <main className="min-h-screen bg-[#f4f7f1]"><AppHeader user={user} onLogout={onLogout} /><div className="mx-auto grid min-h-[60vh] max-w-xl place-items-center px-4 text-center"><div><RotateCcw className="mx-auto size-10 text-red-500" /><h1 className="mt-4 text-2xl font-semibold">Không tải được thiết lập người chơi</h1><p className="mt-2 text-sm text-stone-500">{profile.error}</p><button onClick={profile.reload} className="mt-5 h-11 rounded-lg bg-emerald-700 px-5 text-sm font-semibold text-white">Thử lại</button></div></div></main>

  const pinnedLocations = profile.data.profile.locations.filter((item) => item.latitude != null && item.longitude != null)
  const setField = (field, value) => {
    setForm({ ...activeForm, [field]: value })
    setClientErrors((current) => ({ ...current, [field]: '' }))
  }

  const submit = async (event) => {
    event.preventDefault()
    const errors = {}
    if (!activeForm.preferredLocationId) errors.preferredLocationId = 'Hãy chọn một khu vực đã ghim tọa độ.'
    if (!activeForm.date) errors.scheduledAt = 'Hãy chọn ngày chơi.'
    if (!activeForm.time) errors.scheduledAt = 'Hãy chọn giờ chơi.'
    const scheduled = new Date(`${activeForm.date}T${activeForm.time}:00+07:00`)
    if (!Number.isNaN(scheduled.getTime()) && scheduled <= new Date()) errors.scheduledAt = 'Thời gian chơi phải ở tương lai.'
    setClientErrors(errors)
    if (Object.keys(errors).length > 0) return
    await results.search(activeForm, 0)
  }

  const changePage = (page) => results.search(activeForm, page)
  const fieldErrors = { ...results.fieldErrors, ...clientErrors }

  return (
    <main className="min-h-screen bg-[radial-gradient(circle_at_8%_3%,rgba(190,242,100,0.24),transparent_24%),linear-gradient(150deg,#eef8f2,#fbfcf8)] pb-24 text-emerald-950">
      <AppHeader user={user} onLogout={onLogout} />
      <section className="overflow-hidden bg-emerald-950 px-4 pb-24 pt-9 text-white md:px-6">
        <div className="mx-auto max-w-[1200px]">
          <a href="/matches" className="inline-flex items-center gap-2 text-xs font-semibold text-emerald-100/70"><ArrowLeft className="size-4" />Về dữ liệu thi đấu</a>
          <div className="mt-8 grid gap-8 lg:grid-cols-[1fr_360px] lg:items-end">
            <div><p className="text-xs font-bold uppercase tracking-[0.18em] text-lime-300">2.2.20–2.2.27 · Ghép cặp thông minh</p><h1 className="mt-3 max-w-3xl text-4xl font-semibold tracking-tight md:text-5xl">Tìm người vừa tầm, đúng nơi, đúng lúc.</h1><p className="mt-4 max-w-2xl text-sm font-medium leading-7 text-emerald-100/75">Courtly lọc theo khu vực đã ghim, lịch rảnh và Elo hệ thống đo được. Kết quả không công khai địa chỉ hay lịch cá nhân chi tiết.</p></div>
            <div className="grid grid-cols-3 overflow-hidden rounded-2xl border border-white/10 bg-white/7 backdrop-blur">{[[MapPin, 'Vị trí'], [Clock3, 'Lịch rảnh'], [Gauge, 'Elo']].map(([Icon, label], index) => <div key={label} className={classNames('grid place-items-center gap-2 p-4 text-center', index > 0 && 'border-l border-white/10')}><Icon className="size-5 text-lime-300" /><span className="text-[11px] font-bold uppercase tracking-[0.08em] text-emerald-100/75">{label}</span></div>)}</div>
          </div>
        </div>
      </section>

      <section className="mx-auto -mt-14 max-w-[1200px] px-4 md:px-6">
        <form onSubmit={submit} className="rounded-2xl border border-emerald-100 bg-white p-5 shadow-[0_20px_50px_rgba(19,78,52,0.13)] md:p-6">
          <div className="flex flex-wrap items-start justify-between gap-3"><ProfileSectionTitle icon={Target} eyebrow="Buổi chơi mong muốn" title="Thiết lập tìm kiếm" /><span className="rounded-full bg-lime-100 px-3 py-1.5 text-[11px] font-bold text-emerald-800">3 bộ lọc bắt buộc</span></div>

          {pinnedLocations.length === 0 ? (
            <div className="mt-5 rounded-xl border border-amber-200 bg-amber-50 p-5"><p className="text-sm font-semibold text-amber-900">Bạn chưa có khu vực đã ghim tọa độ.</p><p className="mt-1 text-xs font-medium leading-5 text-amber-700">Thêm vị trí và bán kính để hệ thống có thể lọc người chơi ở gần.</p><a href="/profile/preferences#locations" className="mt-4 inline-flex h-10 items-center rounded-lg bg-amber-900 px-4 text-xs font-semibold text-white">Thiết lập khu vực</a></div>
          ) : (
            <div className="mt-5 grid gap-4 lg:grid-cols-[1.35fr_1fr_1fr]">
              <label className="block"><span className="text-xs font-bold uppercase tracking-[0.08em] text-stone-400">Khu vực chơi</span><select value={activeForm.preferredLocationId} onChange={(event) => setField('preferredLocationId', event.target.value)} className="mt-2 h-12 w-full rounded-xl border border-emerald-100 bg-emerald-50/50 px-4 text-sm font-semibold outline-none focus:border-emerald-500">{pinnedLocations.map((location) => <option key={location.id} value={location.id}>{location.label} · bán kính {location.radiusKm} km</option>)}</select>{fieldErrors.preferredLocationId && <p className="mt-1.5 text-xs font-semibold text-red-600">{fieldErrors.preferredLocationId}</p>}</label>
              <label className="block"><span className="text-xs font-bold uppercase tracking-[0.08em] text-stone-400">Ngày chơi</span><input type="date" min={localDateString(new Date())} value={activeForm.date} onChange={(event) => setField('date', event.target.value)} className="mt-2 h-12 w-full rounded-xl border border-emerald-100 bg-white px-4 text-sm font-semibold outline-none focus:border-emerald-500" /></label>
              <label className="block"><span className="text-xs font-bold uppercase tracking-[0.08em] text-stone-400">Giờ bắt đầu</span><input type="time" value={activeForm.time} onChange={(event) => setField('time', event.target.value)} className="mt-2 h-12 w-full rounded-xl border border-emerald-100 bg-white px-4 text-sm font-semibold outline-none focus:border-emerald-500" />{fieldErrors.scheduledAt && <p className="mt-1.5 text-xs font-semibold text-red-600">{fieldErrors.scheduledAt}</p>}</label>
            </div>
          )}

          <div className="mt-5 grid gap-5 border-t border-emerald-100 pt-5 md:grid-cols-2">
            <div><p className="text-xs font-bold uppercase tracking-[0.08em] text-stone-400">Thời lượng</p><div className="mt-2 grid grid-cols-3 gap-2">{durations.map((duration) => <button key={duration} type="button" onClick={() => setField('durationMinutes', duration)} className={classNames('h-11 rounded-xl text-sm font-semibold ring-1', activeForm.durationMinutes === duration ? 'bg-emerald-700 text-white ring-emerald-700' : 'bg-stone-50 text-stone-500 ring-stone-200')}>{duration} phút</button>)}</div></div>
            <div><p className="text-xs font-bold uppercase tracking-[0.08em] text-stone-400">Độ lệch Elo tối đa</p><div className="mt-2 grid grid-cols-3 gap-2">{ratingRanges.map((range) => <button key={range} type="button" onClick={() => setField('maxRatingDifference', range)} className={classNames('h-11 rounded-xl text-sm font-semibold ring-1', activeForm.maxRatingDifference === range ? 'bg-lime-300 text-emerald-950 ring-lime-300' : 'bg-stone-50 text-stone-500 ring-stone-200')}>± {range}</button>)}</div></div>
          </div>

          {results.status === 'error' && <div className="mt-4 rounded-xl border border-red-100 bg-red-50 px-4 py-3 text-sm font-semibold text-red-700">{results.error}</div>}
          <button disabled={pinnedLocations.length === 0 || results.status === 'loading'} className="mt-6 flex h-12 w-full items-center justify-center gap-2 rounded-xl bg-emerald-700 text-sm font-semibold text-white shadow-[0_12px_24px_rgba(4,120,87,0.18)] disabled:cursor-not-allowed disabled:opacity-55"><Search className="size-4" />{results.status === 'loading' ? 'Đang lọc ứng viên...' : 'Tìm đối tác phù hợp'}</button>
        </form>

        <section className="mt-8">
          <div className="flex flex-wrap items-end justify-between gap-4"><div><p className="text-xs font-bold uppercase tracking-[0.15em] text-emerald-600">Kết quả đã kiểm tra điều kiện</p><h2 className="mt-2 text-2xl font-semibold">Đối tác phù hợp</h2></div>{['success', 'empty'].includes(results.status) && <span className="text-xs font-semibold text-stone-500">{results.totalElements} người vượt qua ba bộ lọc</span>}</div>
          {results.status === 'idle' && <div className="mt-5 grid min-h-52 place-items-center rounded-2xl border border-dashed border-emerald-200 bg-white/65 px-6 text-center"><div><Sparkles className="mx-auto size-8 text-emerald-500" /><p className="mt-3 text-sm font-semibold">Chọn buổi chơi rồi bắt đầu tìm kiếm</p><p className="mt-1 text-xs font-medium text-stone-500">Điểm tương đồng được backend tính từ Elo, không dựa trên trình độ tự khai.</p></div></div>}
          {results.status === 'loading' && <div className="mt-5"><SearchSkeleton /></div>}
          {results.status === 'empty' && <div className="mt-5 grid min-h-52 place-items-center rounded-2xl border border-dashed border-emerald-200 bg-white/70 px-6 text-center"><div><Users className="mx-auto size-9 text-stone-300" /><p className="mt-3 text-sm font-semibold">Chưa có người phù hợp trong khung giờ này</p><p className="mt-1 max-w-lg text-xs font-medium leading-5 text-stone-500">Thử tăng độ lệch Elo, chọn thời gian khác hoặc dùng khu vực có bán kính rộng hơn.</p></div></div>}
          {results.status === 'success' && <><div className="mt-5 grid gap-4 md:grid-cols-2">{results.partners.map((partner) => <PartnerCard key={partner.id} partner={partner} />)}</div>{results.totalPages > 1 && <div className="mt-7 flex items-center justify-center gap-3"><button disabled={results.page === 0 || results.status === 'loading'} onClick={() => changePage(results.page - 1)} className="grid size-11 place-items-center rounded-xl border border-emerald-200 bg-white text-emerald-700 disabled:opacity-40" aria-label="Trang trước"><ArrowLeft className="size-4" /></button><span className="min-w-24 text-center text-xs font-semibold text-stone-500">Trang {results.page + 1}/{results.totalPages}</span><button disabled={results.page + 1 >= results.totalPages || results.status === 'loading'} onClick={() => changePage(results.page + 1)} className="grid size-11 place-items-center rounded-xl border border-emerald-200 bg-white text-emerald-700 disabled:opacity-40" aria-label="Trang sau"><ArrowRight className="size-4" /></button></div>}</>}
        </section>

        <section className="mt-8 grid gap-4 md:grid-cols-3">{[[MapPin, 'Vị trí riêng tư', 'Chỉ dùng khoảng cách; không hiển thị tọa độ hay địa chỉ của người khác.'], [CalendarDays, 'Lịch rảnh thực tế', 'Ứng viên phải rảnh trọn vẹn trong toàn bộ thời lượng bạn chọn.'], [ShieldCheck, 'Kỹ năng đo được', 'Lọc và tính tương đồng bằng Elo từ các trận đã xác nhận.']].map(([Icon, title, description]) => <div key={title} className="rounded-2xl border border-emerald-100 bg-white/75 p-5"><Icon className="size-5 text-emerald-600" /><h3 className="mt-3 text-sm font-semibold">{title}</h3><p className="mt-1 text-xs font-medium leading-5 text-stone-500">{description}</p></div>)}</section>
      </section>
      <BottomNav activeLabel="Đối tác" />
    </main>
  )
}

export default PartnerSearchPage
