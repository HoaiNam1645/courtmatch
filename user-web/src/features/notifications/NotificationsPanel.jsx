import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { CheckCheck } from 'lucide-react'
import {
  fetchNotificationPreferences, fetchNotifications, markAllNotificationsRead, markNotificationRead, NOTIFICATION_FILTERS,
  notificationTypeMeta, saveNotificationPreferences,
} from '../../api/notifications'
import { ApiError } from '../../api/client'
import { useApiResource } from '../../hooks/useApiResource'
import { FormAlert } from '../../shared/FormFeedback'
import { classNames } from '../../shared/utils'
import { OwnerCard, OwnerEmpty, OwnerError, OwnerLoading } from '../owner/components/OwnerUi'
import { formatDateTime } from '../owner/ownerDates'
import { inputClass, primaryButtonClass, secondaryButtonClass } from '../owner/ownerStyles'

function HistoryTab() {
  const navigate = useNavigate()
  const [type, setType] = useState('all')
  const [unreadOnly, setUnreadOnly] = useState(false)
  const [page, setPage] = useState(0)
  const [version, setVersion] = useState(0)
  const [notice, setNotice] = useState('')
  const { status, data, error, reload } = useApiResource(
    () => fetchNotifications({ type, unreadOnly, page }),
    [type, unreadOnly, page, version],
    { isEmpty: (result) => result.items.length === 0 },
  )

  async function openItem(item) {
    if (!item.read) {
      try { await markNotificationRead(item.id) } catch { /* vẫn cho đi tiếp */ }
    }
    if (item.route) navigate(item.route)
    else setVersion((current) => current + 1)
  }

  async function readAll() {
    try {
      const result = await markAllNotificationsRead(type)
      setNotice(result.updated > 0 ? `Đã đánh dấu ${result.updated} thông báo là đã đọc.` : 'Không có thông báo chưa đọc.')
      setVersion((current) => current + 1)
    } catch (failure) {
      setNotice(failure instanceof ApiError ? failure.message : 'Không đánh dấu được, vui lòng thử lại.')
    }
  }

  const filtered = type !== 'all' || unreadOnly
  return (
    <OwnerCard>
      <div className="flex flex-wrap items-end gap-3">
        <label className="block text-xs font-semibold text-stone-600">Loại thông báo
          <select className={`${inputClass} mt-1 sm:w-56`} aria-label="Lọc theo loại" value={type} onChange={(event) => { setType(event.target.value); setPage(0) }}>
            {NOTIFICATION_FILTERS.map((value) => <option key={value} value={value}>{value === 'all' ? 'Tất cả' : notificationTypeMeta(value).label}</option>)}
          </select>
        </label>
        <label className="flex h-11 items-center gap-2 rounded-lg border border-stone-300 bg-white px-3 text-sm font-semibold text-stone-700">
          <input type="checkbox" checked={unreadOnly} onChange={(event) => { setUnreadOnly(event.target.checked); setPage(0) }} data-testid="notif-unread-only" />Chỉ chưa đọc
        </label>
        {filtered && <button type="button" className={secondaryButtonClass} onClick={() => { setType('all'); setUnreadOnly(false); setPage(0) }} data-testid="notif-clear">Xóa bộ lọc</button>}
        <button type="button" className={`${secondaryButtonClass} sm:ml-auto`} disabled={!data || data.unreadCount === 0} onClick={readAll} data-testid="notif-read-all"><CheckCheck className="size-4" />Đánh dấu đã đọc{type !== 'all' ? ' loại này' : ' tất cả'}</button>
      </div>
      {notice && <p role="status" className="mt-4 rounded-lg bg-emerald-50 p-3 text-sm font-semibold text-emerald-800" data-testid="notif-notice">{notice}</p>}
      <div className="mt-5">
        {status === 'loading' && !data && <OwnerLoading rows={3} />}
        {status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
        {status === 'empty' && <OwnerEmpty title={filtered ? 'Không có thông báo phù hợp' : 'Bạn chưa có thông báo nào'} description={filtered ? 'Thử bỏ bớt bộ lọc.' : 'Thông báo về đặt sân, thanh toán, ghép cặp và trận đấu sẽ hiện ở đây.'} />}
        {data && status !== 'empty' && (
          <>
            <p className="mb-3 text-xs font-semibold text-stone-500" data-testid="notif-total">{data.totalElements.toLocaleString('vi-VN')} thông báo · <span data-testid="notif-unread-total">{data.unreadCount}</span> chưa đọc</p>
            <ul className="grid gap-2" data-testid="notif-list">
              {data.items.map((item) => {
                const meta = notificationTypeMeta(item.type)
                return (
                  <li key={item.id}>
                    <button type="button" onClick={() => openItem(item)} data-testid={`notif-${item.id}`} className={classNames('flex w-full gap-3 rounded-xl border p-4 text-left transition hover:bg-stone-50', item.read ? 'border-stone-200 bg-white' : 'border-emerald-200 bg-emerald-50/60')}>
                      <span className={classNames('mt-2 size-2 shrink-0 rounded-full', item.read ? 'bg-transparent' : 'bg-emerald-600')} aria-hidden="true" />
                      <span className="min-w-0 flex-1">
                        <span className="flex flex-wrap items-center gap-2">
                          <span className={classNames('rounded-full px-2.5 py-1 text-xs font-bold', meta.className)} data-testid="notif-type">{meta.label}</span>
                          {!item.read && <span className="text-xs font-bold text-emerald-700" data-testid="notif-unread">Mới</span>}
                          <span className="text-xs font-medium text-stone-400">{formatDateTime(item.createdAt)}</span>
                        </span>
                        <span className="mt-1.5 block text-sm font-semibold text-stone-900" data-testid="notif-title">{item.title}</span>
                        {item.content && <span className="mt-0.5 block text-sm font-medium text-stone-600" data-testid="notif-content">{item.content}</span>}
                      </span>
                    </button>
                  </li>
                )
              })}
            </ul>
            {data.totalPages > 1 && (
              <div className="mt-5 flex items-center justify-between gap-3">
                <button type="button" className={secondaryButtonClass} disabled={page === 0} onClick={() => setPage((current) => current - 1)}>Trang trước</button>
                <p className="text-sm font-semibold text-stone-600">Trang {data.page + 1}/{data.totalPages}</p>
                <button type="button" className={secondaryButtonClass} disabled={page + 1 >= data.totalPages} onClick={() => setPage((current) => current + 1)}>Trang sau</button>
              </div>
            )}
          </>
        )}
      </div>
    </OwnerCard>
  )
}

function Toggle({ checked, disabled, label, onChange, testId }) {
  return (
    <label className={classNames('inline-flex items-center gap-2 text-sm font-semibold', disabled ? 'text-stone-400' : 'text-stone-700')}>
      <input type="checkbox" className="size-5 accent-emerald-700" checked={checked} disabled={disabled} aria-label={label} data-testid={testId} onChange={(event) => onChange(event.target.checked)} />
    </label>
  )
}

function SettingsTab() {
  const { status, data, error, reload } = useApiResource(fetchNotificationPreferences, [])
  const [draft, setDraft] = useState(null)
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState('')
  const [formError, setFormError] = useState('')
  const rows = draft ?? data ?? []
  const dirty = draft !== null

  function change(type, patch) {
    setDraft((current) => (current ?? data).map((item) => (item.type === type ? { ...item, ...patch } : item)))
    setNotice('')
    setFormError('')
  }

  async function save() {
    setBusy(true)
    setFormError('')
    try {
      await saveNotificationPreferences(rows)
      setDraft(null)
      setNotice('Đã lưu tùy chọn nhận thông báo.')
      reload()
    } catch (failure) {
      setFormError(failure instanceof ApiError ? failure.message : 'Không lưu được, vui lòng thử lại.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <OwnerCard title="Tùy chọn nhận thông báo" description="Chọn loại thông báo bạn muốn nhận ở trong ứng dụng và qua email.">
      {status === 'loading' && <OwnerLoading rows={3} />}
      {status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
      {data && (
        <>
          <ul className="divide-y divide-stone-100" data-testid="pref-list">
            {rows.map((item) => (
              <li key={item.type} className="grid gap-3 py-4 sm:grid-cols-[1fr_auto_auto_auto] sm:items-center" data-testid={`pref-${item.type}`}>
                <div className="min-w-0">
                  <p className="text-sm font-semibold text-stone-900">{item.label}{item.mandatory && <span className="ml-2 rounded-full bg-red-100 px-2 py-0.5 text-[10px] font-bold text-red-700" data-testid="pref-mandatory">Bắt buộc</span>}</p>
                  <p className="mt-0.5 text-xs font-medium text-stone-500">{item.description}</p>
                </div>
                <div className="flex items-center gap-2 sm:flex-col sm:gap-0.5"><span className="text-xs font-semibold text-stone-500">Trong ứng dụng</span><Toggle checked={item.inAppEnabled} disabled={item.mandatory} label={`${item.label}: trong ứng dụng`} testId="pref-in-app" onChange={(value) => change(item.type, { inAppEnabled: value })} /></div>
                <div className="flex items-center gap-2 sm:flex-col sm:gap-0.5"><span className="text-xs font-semibold text-stone-500">Email</span><Toggle checked={item.emailEnabled} label={`${item.label}: email`} testId="pref-email" onChange={(value) => change(item.type, { emailEnabled: value })} /></div>
                <div className="flex items-center gap-2 sm:flex-col sm:gap-0.5"><span className="text-xs font-semibold text-stone-400">Đẩy</span><span className="text-xs font-medium text-stone-400" data-testid="pref-push">Chưa hỗ trợ</span></div>
              </li>
            ))}
          </ul>
          <div className="mt-4"><FormAlert message={formError} /></div>
          {notice && <p role="status" className="mt-4 rounded-lg bg-emerald-50 p-3 text-sm font-semibold text-emerald-800" data-testid="pref-notice">{notice}</p>}
          <div className="mt-4 flex justify-end gap-2">
            {dirty && <button type="button" className={secondaryButtonClass} disabled={busy} onClick={() => setDraft(null)} data-testid="pref-reset">Hoàn tác</button>}
            <button type="button" className={primaryButtonClass} disabled={!dirty || busy} onClick={save} data-testid="pref-save">{busy ? 'Đang lưu...' : 'Lưu thay đổi'}</button>
          </div>
        </>
      )}
    </OwnerCard>
  )
}

/** Nội dung dùng chung của trang thông báo ở cả ba khu (người chơi, chủ sân, quản trị): lịch sử (2.3.61) và tùy chọn (2.3.62). */
function NotificationsPanel() {
  const [params, setParams] = useSearchParams()
  const tab = params.get('tab') === 'settings' ? 'settings' : 'history'
  return (
    <div>
      <div className="mb-4 flex gap-2" role="tablist" aria-label="Thông báo">
        {[['history', 'Lịch sử'], ['settings', 'Tùy chọn']].map(([id, label]) => (
          <button key={id} type="button" role="tab" aria-selected={tab === id} data-testid={`notif-tab-${id}`}
            className={classNames('h-10 rounded-lg px-4 text-sm font-semibold transition', tab === id ? 'bg-emerald-700 text-white' : 'bg-white text-stone-700 hover:bg-stone-100')}
            onClick={() => setParams(id === 'settings' ? { tab: 'settings' } : {})}>{label}</button>
        ))}
      </div>
      {tab === 'history' ? <HistoryTab /> : <SettingsTab />}
    </div>
  )
}

export { NotificationsPanel }
