import { ChevronRight, ClipboardList, ImageIcon } from 'lucide-react'
import { useState } from 'react'
import { DISPUTE_TAB_FILTERS, fetchMyDisputes, RESOLUTION_LABEL } from '../../api/bookingDisputes'
import { AppHeader, BottomNav } from '../../app/AppLayout'
import { useApiResource } from '../../hooks/useApiResource'
import { classNames, formatCurrency } from '../../shared/utils'
import { PageBackLink, ProfileEmptyState } from '../profile/components/ProfileUi'
import { disputeStatusMeta, formatDateTime, remainingText } from './disputeUi'

/** 2.3.89 - danh sách báo cáo về đơn đặt sân đã gửi (/bao-cao). */
function DisputesPage({ user, onLogout }) {
  const [tab, setTab] = useState('all')
  const [page, setPage] = useState(0)
  const { status, data, error, reload } = useApiResource(
    () => fetchMyDisputes({ status: tab, page }),
    [tab, page],
    { isEmpty: (result) => result.items.length === 0 },
  )
  if (!user) return <ProfileEmptyState />

  return (
    <main className="min-h-screen bg-[linear-gradient(145deg,#eef8f2,#f9faf6)] pb-24 text-emerald-950">
      <AppHeader user={user} onLogout={onLogout} />
      <section className="mx-auto max-w-[900px] px-4 py-7 md:px-6 md:py-10">
        <PageBackLink href="/profile" label="Về hồ sơ" />
        <p className="mt-6 text-xs font-bold uppercase tracking-[0.15em] text-emerald-600">2.3.89 · Báo cáo đã gửi</p>
        <h1 className="mt-2 text-3xl font-semibold">Báo cáo của tôi</h1>
        <p className="mt-2 text-sm font-medium text-stone-500">Các báo cáo vấn đề về đơn đặt sân bạn đã gửi và tiến trình xử lý.</p>

        <div className="mt-6 flex gap-2 overflow-x-auto pb-1" role="tablist" aria-label="Trạng thái báo cáo">
          {DISPUTE_TAB_FILTERS.map(([id, label]) => (
            <button key={id} type="button" role="tab" aria-selected={tab === id} data-testid={`disputes-tab-${id}`} onClick={() => { setTab(id); setPage(0) }}
              className={classNames('h-11 shrink-0 rounded-lg px-4 text-sm font-semibold', tab === id ? 'bg-emerald-700 text-white' : 'bg-white text-stone-600 ring-1 ring-emerald-100')}>{label}</button>
          ))}
        </div>

        {status === 'loading' && <div className="mt-6 grid gap-3">{[0, 1].map((index) => <div key={index} className="h-28 animate-pulse rounded-xl bg-emerald-100/60" />)}</div>}
        {status === 'error' && (
          <div role="alert" className="mt-6 rounded-xl border border-red-200 bg-red-50 p-6 text-center">
            <p className="text-sm font-medium text-red-700">{error?.message ?? 'Không tải được danh sách báo cáo.'}</p>
            <button type="button" className="mt-3 h-10 rounded-lg bg-emerald-700 px-5 text-sm font-semibold text-white" onClick={reload}>Thử lại</button>
          </div>
        )}
        {status === 'empty' && (
          <div className="mt-6 grid min-h-48 place-items-center rounded-xl border border-dashed border-emerald-200 bg-white/75 p-6 text-center" data-testid="disputes-empty">
            <div><ClipboardList className="mx-auto size-9 text-emerald-300" /><p className="mt-3 text-sm font-semibold">{tab === 'active' ? 'Không có báo cáo nào đang xử lý' : tab === 'closed' ? 'Chưa có báo cáo nào đã đóng' : 'Bạn chưa gửi báo cáo nào'}</p><p className="mt-1 text-xs text-stone-500">Báo cáo vấn đề về một đơn ở trang chi tiết đơn đặt sân.</p></div>
          </div>
        )}
        {status === 'success' && (
          <>
            <ul className="mt-6 grid gap-3" data-testid="disputes-list">
              {data.items.map((item) => {
                const meta = disputeStatusMeta(item.status)
                return (
                  <li key={item.id}>
                    <a href={`/bao-cao/${item.id}`} data-testid={`dispute-${item.id}`} data-status={item.status} className="block rounded-xl border border-emerald-100 bg-white p-4 shadow-sm transition hover:border-emerald-300">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className={classNames('rounded-full px-2.5 py-1 text-xs font-bold', meta.className)} data-testid="dispute-status">{meta.label}</span>
                          <span className="rounded-full bg-stone-100 px-2.5 py-1 text-xs font-bold text-stone-700">{item.categoryLabel}</span>
                        </div>
                        <ChevronRight className="size-5 text-stone-400" />
                      </div>
                      <p className="mt-3 text-base font-semibold">{item.venueName}</p>
                      <p className="mt-1 text-xs font-medium text-stone-500">Đơn {item.bookingCode} · gửi lúc {formatDateTime(item.createdAt)}</p>
                      <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs font-semibold text-stone-600">
                        {item.refundRequested && <span data-testid="dispute-refund" className="text-emerald-800">Yêu cầu hoàn {formatCurrency(item.refundAmount)}</span>}
                        {item.evidenceCount > 0 && <span className="inline-flex items-center gap-1"><ImageIcon className="size-3.5" />{item.evidenceCount} ảnh</span>}
                        {item.ownerResponseDeadline && <span className="text-amber-800" data-testid="dispute-deadline">Chủ sân {remainingText(item.ownerResponseDeadline)}</span>}
                        {item.closed && item.resolution && <span data-testid="dispute-resolution" className="text-emerald-800">{RESOLUTION_LABEL[item.resolution] ?? item.resolution}</span>}
                      </div>
                    </a>
                  </li>
                )
              })}
            </ul>
            <div className="mt-5 flex items-center justify-between gap-3">
              <button type="button" disabled={page === 0} onClick={() => setPage(page - 1)} className="h-10 rounded-lg border border-emerald-200 bg-white px-4 text-sm font-semibold text-emerald-800 disabled:opacity-50">Trang trước</button>
              <p className="text-sm font-semibold text-stone-600" data-testid="disputes-pager">Trang {data.page + 1}/{data.totalPages} · {data.totalElements} báo cáo</p>
              <button type="button" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)} className="h-10 rounded-lg border border-emerald-200 bg-white px-4 text-sm font-semibold text-emerald-800 disabled:opacity-50">Trang sau</button>
            </div>
          </>
        )}
      </section>
      <BottomNav activeLabel="Tài khoản" />
    </main>
  )
}

export default DisputesPage
