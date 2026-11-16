import { useCallback, useEffect, useRef, useState } from 'react'
import { Bell, CheckCheck } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { fetchNotifications, markAllNotificationsRead, markNotificationRead, notificationTypeMeta } from '../../api/notifications'
import { classNames } from '../../shared/utils'
import { formatDateTime } from '../owner/ownerDates'

const POLL_MS = 60000

const THEMES = {
  green: 'text-white/90 hover:bg-white/8',
  slate: 'border border-white/22 text-white hover:bg-white/8',
}

/**
 * Chuông thông báo ở đầu trang (2.3.61): số chưa đọc, danh sách thông báo mới nhất và lối vào trang lịch sử.
 * Tự làm mới mỗi phút và khi cửa sổ được chọn lại; lỗi mạng chỉ làm số chưa đọc đứng yên, không chặn trang.
 */
function NotificationBell({ theme = 'green' }) {
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [unread, setUnread] = useState(0)
  const [items, setItems] = useState([])
  const [loading, setLoading] = useState(false)
  const [failed, setFailed] = useState(false)
  const rootRef = useRef(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const result = await fetchNotifications({ size: 6 })
      setItems(result.items)
      setUnread(result.unreadCount)
      setFailed(false)
    } catch {
      setFailed(true)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
    const timer = window.setInterval(load, POLL_MS)
    window.addEventListener('focus', load)
    return () => {
      window.clearInterval(timer)
      window.removeEventListener('focus', load)
    }
  }, [load])

  useEffect(() => {
    if (!open) return undefined
    function onPointer(event) {
      if (rootRef.current && !rootRef.current.contains(event.target)) setOpen(false)
    }
    function onKey(event) {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onPointer)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onPointer)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  async function openItem(item) {
    setOpen(false)
    if (!item.read) {
      try {
        await markNotificationRead(item.id)
        setUnread((current) => Math.max(current - 1, 0))
      } catch {
        // Đọc không đánh dấu được vẫn cho đi tiếp tới màn hình liên quan.
      }
    }
    navigate(item.route ?? '/notifications')
  }

  async function readAll() {
    try {
      const result = await markAllNotificationsRead()
      setUnread(result.unreadCount)
      setItems((current) => current.map((item) => ({ ...item, read: true })))
    } catch {
      setFailed(true)
    }
  }

  return (
    <div className="relative" ref={rootRef}>
      <button
        type="button"
        className={classNames('relative grid size-10 place-items-center rounded-lg', THEMES[theme] ?? THEMES.green)}
        aria-label={unread > 0 ? `Thông báo, ${unread} chưa đọc` : 'Thông báo'}
        aria-expanded={open}
        aria-haspopup="dialog"
        data-testid="bell"
        onClick={() => { setOpen((current) => !current); if (!open) load() }}
      >
        <Bell className="size-[18px]" />
        {unread > 0 && (
          <span className="absolute -right-0.5 -top-0.5 grid min-w-[18px] place-items-center rounded-full bg-red-600 px-1 text-[10px] font-bold leading-[18px] text-white" data-testid="bell-count">
            {unread > 99 ? '99+' : unread}
          </span>
        )}
      </button>
      {open && (
        <div role="dialog" aria-label="Thông báo mới nhất" className="fixed inset-x-4 top-[4.25rem] z-50 overflow-hidden sm:absolute sm:inset-x-auto sm:right-0 sm:top-12 sm:w-[22rem] rounded-xl border border-stone-200 bg-white text-stone-900 shadow-2xl" data-testid="bell-panel">
          <div className="flex items-center justify-between gap-2 border-b border-stone-100 px-4 py-3">
            <p className="text-sm font-semibold">Thông báo</p>
            <button type="button" className="inline-flex items-center gap-1 text-xs font-semibold text-emerald-700 disabled:text-stone-400" disabled={unread === 0} onClick={readAll} data-testid="bell-read-all">
              <CheckCheck className="size-3.5" />Đọc tất cả
            </button>
          </div>
          <ul className="max-h-96 overflow-y-auto" data-testid="bell-list">
            {items.length === 0 && !loading && <li className="px-4 py-8 text-center text-sm font-medium text-stone-500" data-testid="bell-empty">{failed ? 'Không tải được thông báo.' : 'Bạn chưa có thông báo nào.'}</li>}
            {items.map((item) => {
              const meta = notificationTypeMeta(item.type)
              return (
                <li key={item.id}>
                  <button type="button" className={classNames('flex w-full gap-3 border-b border-stone-50 px-4 py-3 text-left hover:bg-stone-50', !item.read && 'bg-emerald-50/50')} onClick={() => openItem(item)} data-testid={`bell-item-${item.id}`}>
                    <span className={classNames('mt-1.5 size-2 shrink-0 rounded-full', item.read ? 'bg-transparent' : 'bg-emerald-600')} aria-hidden="true" />
                    <span className="min-w-0">
                      <span className="flex flex-wrap items-center gap-2"><span className={classNames('rounded-full px-2 py-0.5 text-[10px] font-bold', meta.className)}>{meta.label}</span></span>
                      <span className="mt-1 block truncate text-sm font-semibold">{item.title}</span>
                      {item.content && <span className="mt-0.5 line-clamp-2 block text-xs font-medium text-stone-500">{item.content}</span>}
                      <span className="mt-1 block text-[11px] font-medium text-stone-400">{formatDateTime(item.createdAt)}</span>
                    </span>
                  </button>
                </li>
              )
            })}
          </ul>
          <a href="/notifications" className="block border-t border-stone-100 px-4 py-3 text-center text-sm font-semibold text-emerald-700 hover:bg-stone-50" data-testid="bell-all" onClick={() => setOpen(false)}>Xem tất cả thông báo</a>
        </div>
      )}
    </div>
  )
}

export { NotificationBell }
