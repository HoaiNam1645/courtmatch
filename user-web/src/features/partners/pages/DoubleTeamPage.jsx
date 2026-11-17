import { ArrowLeft, History, LoaderCircle, RefreshCw, Swords, Users } from 'lucide-react'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AppHeader, BottomNav } from '../../../app/AppLayout'
import { ApiError } from '../../../api/client'
import { createDoubleTeamSuggestion } from '../../../api/doubleTeams'
import { usePartnerConnections } from '../../../hooks/usePartnerConnections'
import { classNames } from '../../../shared/utils'
import { describeInvalidPlayers, genderFits, genderLabels, matchTypeLabels } from '../doubleTeamUi'

const MAX_OTHERS = 3

/** 2.3.63: chọn loại đôi và ba người chơi cùng mình để chia đội. */
function DoubleTeamPage({ user, onLogout }) {
  const navigate = useNavigate()
  const connections = usePartnerConnections({ size: 100 })
  const [matchType, setMatchType] = useState('mens')
  const [selected, setSelected] = useState([])
  const [submitting, setSubmitting] = useState(false)
  const [problems, setProblems] = useState([])

  const nameById = Object.fromEntries(connections.data.map((partner) => [partner.partnerId, partner.fullName]))

  function chooseType(type) {
    setMatchType(type)
    // Bỏ những người không còn phù hợp với loại đôi mới.
    setSelected((current) => current.filter((id) => {
      const partner = connections.data.find((item) => item.partnerId === id)
      return partner && genderFits(type, partner.gender)
    }))
    setProblems([])
  }

  function toggle(partnerId) {
    setProblems([])
    setSelected((current) => {
      if (current.includes(partnerId)) return current.filter((id) => id !== partnerId)
      return current.length >= MAX_OTHERS ? current : [...current, partnerId]
    })
  }

  async function submit() {
    setSubmitting(true)
    setProblems([])
    try {
      const result = await createDoubleTeamSuggestion({ matchType, playerIds: selected })
      navigate(`/partners/double-teams/${result.batchId}`)
    } catch (error) {
      if (error instanceof ApiError && error.fieldErrors && Object.keys(error.fieldErrors).length > 0) {
        setProblems(describeInvalidPlayers(error.fieldErrors, nameById, user?.id))
      } else {
        setProblems([error?.message ?? 'Không tạo được đề xuất chia đội.'])
      }
      setSubmitting(false)
    }
  }

  return (
    <main className="min-h-screen bg-[radial-gradient(circle_at_88%_4%,rgba(190,242,100,0.24),transparent_24%),linear-gradient(145deg,#edf8f2,#fbfcf8)] pb-28 text-emerald-950">
      <AppHeader user={user} onLogout={onLogout} />
      <section className="bg-emerald-950 px-4 pb-16 pt-9 text-white md:px-6">
        <div className="mx-auto max-w-[1080px]">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <a href="/partners/connections" className="inline-flex items-center gap-2 text-xs font-semibold text-emerald-100/70"><ArrowLeft className="size-4" />Về đối tác của bạn</a>
            <a href="/partners/double-teams/history" data-testid="open-double-team-history" className="inline-flex items-center gap-2 rounded-lg border border-white/25 px-3 py-2 text-xs font-semibold text-white"><History className="size-4" />Lịch sử chia đội</a>
          </div>
          <p className="mt-7 text-xs font-bold uppercase tracking-[0.16em] text-lime-300">2.3.63 · Ghép đội đôi thông minh</p>
          <h1 className="mt-3 text-3xl font-semibold tracking-tight sm:text-4xl">Chia đội đôi cân bằng</h1>
          <p className="mt-3 max-w-xl text-sm leading-6 text-emerald-100/70">Chọn loại đôi và ba người chơi cùng bạn. Hệ thống chia thành hai đội có sức mạnh gần nhau nhất.</p>
        </div>
      </section>

      <section className="mx-auto -mt-8 max-w-[1080px] px-4 md:px-6">
        <div className="rounded-2xl border border-emerald-100 bg-white p-5 shadow-[0_18px_45px_rgba(20,70,48,0.12)]">
          <p className="text-xs font-bold uppercase tracking-[0.12em] text-emerald-700">Loại đôi</p>
          <div className="mt-3 grid grid-cols-3 gap-2 rounded-xl bg-stone-100 p-1" role="radiogroup" aria-label="Loại đôi">
            {Object.entries(matchTypeLabels).map(([value, label]) => (
              <button key={value} type="button" role="radio" aria-checked={matchType === value} data-testid={`dt-type-${value}`} onClick={() => chooseType(value)}
                className={classNames('h-11 rounded-lg text-xs font-semibold sm:text-sm', matchType === value ? 'bg-white text-emerald-800 shadow-sm' : 'text-stone-500')}>{label}</button>
            ))}
          </div>
          <p className="mt-4 text-sm font-semibold" data-testid="dt-counter">Đã chọn {selected.length}/{MAX_OTHERS} người chơi cùng bạn</p>
        </div>

        {connections.status === 'loading' && <div className="mt-5 grid min-h-40 place-items-center rounded-2xl border border-emerald-100 bg-white"><LoaderCircle className="size-8 animate-spin text-emerald-600" /></div>}
        {connections.status === 'error' && (
          <div className="mt-5 rounded-2xl border border-red-100 bg-white p-6 text-center">
            <p className="text-sm font-semibold text-red-700">{connections.error}</p>
            <button type="button" onClick={() => connections.load(0)} className="mt-4 inline-flex h-10 items-center gap-2 rounded-xl bg-emerald-700 px-4 text-xs font-semibold text-white"><RefreshCw className="size-4" />Thử lại</button>
          </div>
        )}
        {connections.status === 'empty' && (
          <div className="mt-5 grid min-h-40 place-items-center rounded-2xl border border-dashed border-emerald-200 bg-white/75 p-6 text-center">
            <div><Users className="mx-auto size-9 text-emerald-300" /><p className="mt-3 text-sm font-semibold">Bạn chưa có đối tác nào để lập đội</p><a href="/partners" className="mt-3 inline-block text-xs font-semibold text-emerald-700 underline">Tìm đối tác</a></div>
          </div>
        )}
        {connections.status === 'success' && (
          <ul className="mt-5 grid gap-3 sm:grid-cols-2" data-testid="dt-players">
            {connections.data.map((partner) => {
              const fits = genderFits(matchType, partner.gender)
              const checked = selected.includes(partner.partnerId)
              const full = !checked && selected.length >= MAX_OTHERS
              const disabled = !fits || full
              return (
                <li key={partner.partnerId}>
                  <button type="button" role="checkbox" aria-checked={checked} aria-disabled={disabled} disabled={disabled} data-testid={`dt-player-${partner.partnerId}`} onClick={() => toggle(partner.partnerId)}
                    className={classNames('flex w-full items-center gap-3 rounded-2xl border bg-white p-4 text-left transition', checked ? 'border-emerald-600 ring-2 ring-emerald-200' : 'border-emerald-100', disabled && 'opacity-50')}>
                    <span className="grid size-11 shrink-0 place-items-center rounded-xl bg-emerald-950 font-semibold text-lime-300">{partner.fullName.slice(0, 1).toUpperCase()}</span>
                    <span className="min-w-0 flex-1">
                      <span className="block truncate font-semibold">{partner.fullName}</span>
                      <span className="mt-0.5 block text-xs text-stone-500">
                        {partner.gender ? genderLabels[partner.gender] : 'Chưa khai báo giới tính'}
                        {!fits && partner.gender && ' · không phù hợp loại đôi này'}
                        {!fits && !partner.gender && ' · không thể chọn'}
                      </span>
                    </span>
                    <span className={classNames('grid size-6 place-items-center rounded-md border text-xs font-bold', checked ? 'border-emerald-600 bg-emerald-600 text-white' : 'border-stone-300')} aria-hidden="true">{checked ? '✓' : ''}</span>
                  </button>
                </li>
              )
            })}
          </ul>
        )}

        {problems.length > 0 && (
          <div role="alert" data-testid="dt-problems" className="mt-5 rounded-2xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
            <p className="font-semibold">Chưa thể chia đội:</p>
            <ul className="mt-1 list-disc pl-5">{problems.map((line) => <li key={line}>{line}</li>)}</ul>
          </div>
        )}

        <div className="sticky bottom-20 mt-6 flex justify-end">
          <button type="button" data-testid="dt-submit" disabled={selected.length !== MAX_OTHERS || submitting} onClick={submit}
            className="inline-flex h-12 items-center gap-2 rounded-xl bg-emerald-700 px-6 text-sm font-semibold text-white shadow-lg disabled:opacity-50">
            {submitting ? <LoaderCircle className="size-4 animate-spin" /> : <Swords className="size-4" />}{submitting ? 'Đang chia đội...' : 'Chia đội cân bằng'}
          </button>
        </div>
      </section>
      <BottomNav activeLabel="Đối tác" />
    </main>
  )
}

export default DoubleTeamPage
