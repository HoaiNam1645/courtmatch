import { useSearchParams } from 'react-router-dom'
import { classNames } from '../../../shared/utils'
import { CommissionTab } from '../components/CommissionTab'
import { DebtTab } from '../components/DebtTab'
import { NetIncomeTab } from '../components/NetIncomeTab'
import { OwnerLayout } from '../components/OwnerLayout'
import { PaymentHistoryTab } from '../components/PaymentHistoryTab'
import { RevenueTab } from '../components/RevenueTab'
import { SettlementsTab } from '../components/SettlementsTab'

/** Các tab tài chính; thêm tab khi chức năng tương ứng hoàn thành. */
const FINANCE_TABS = [
  { id: 'revenue', label: 'Doanh thu', component: RevenueTab },
  { id: 'commissions', label: 'Hoa hồng', component: CommissionTab },
  { id: 'net', label: 'Thực nhận', component: NetIncomeTab },
  { id: 'debt', label: 'Công nợ', component: DebtTab },
  { id: 'settlements', label: 'Đối soát', component: SettlementsTab },
  { id: 'payment-history', label: 'Lịch sử thanh toán', component: PaymentHistoryTab },
]

/** Tài chính của chủ sân: doanh thu, hoa hồng và công nợ với Courtly. */
function OwnerFinancePage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const active = FINANCE_TABS.find((tab) => tab.id === searchParams.get('tab')) ?? FINANCE_TABS[0]
  const TabComponent = active.component

  return (
    <OwnerLayout eyebrow="Tài chính" title="Tài chính" description="Doanh thu bạn đã thu trực tiếp, hoa hồng và công nợ với Courtly.">
      <div className="mb-5 flex gap-1 overflow-x-auto rounded-xl border border-emerald-100 bg-white p-1.5" role="tablist" aria-label="Tài chính">
        {FINANCE_TABS.map((tab) => (
          <button key={tab.id} type="button" role="tab" aria-selected={tab.id === active.id} className={classNames('h-10 shrink-0 rounded-lg px-4 text-sm font-semibold', tab.id === active.id ? 'bg-emerald-700 text-white' : 'text-stone-600 hover:bg-emerald-50')} onClick={() => setSearchParams({ tab: tab.id }, { replace: true })}>{tab.label}</button>
        ))}
      </div>
      <TabComponent key={active.id} />
    </OwnerLayout>
  )
}

export default OwnerFinancePage
