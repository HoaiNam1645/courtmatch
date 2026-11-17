import { useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { SOURCE_LABEL } from '../../../api/adminMatching'
import { classNames } from '../../../shared/utils'
import { shiftIsoDate, todayInVietnam } from '../../owner/ownerDates'
import { inputClass } from '../../owner/ownerStyles'
import { AdminLayout } from '../components/AdminLayout'
import { TeamAccuracySection } from '../components/matching/TeamAccuracySection'
import { PartnerAccuracySection } from '../components/matching/PartnerAccuracySection'
import { customProblem, rangeOf, RangeBar } from '../components/matching/matchingUi'

/** Các tab của khu phân tích ghép cặp; thêm tab khi chức năng tương ứng hoàn thành. */
const TABS = [
  { id: 'accuracy', label: 'Độ chính xác' },
]

/** 2.3.70 - 2.3.76: phân tích và đánh giá hệ thống ghép cặp. */
function AdminMatchingPage() {
  const [params, setParams] = useSearchParams()
  const tab = TABS.some((item) => item.id === params.get('tab')) ? params.get('tab') : TABS[0].id
  const [preset, setPreset] = useState('30d')
  const [custom, setCustom] = useState({ from: shiftIsoDate(todayInVietnam(), -29), to: todayInVietnam() })
  const [source, setSource] = useState('')
  const range = useMemo(() => rangeOf(preset, custom), [preset, custom])
  const problem = customProblem(preset, range)

  return (
    <AdminLayout eyebrow="2.3.70 - 2.3.71 · Ghép cặp" title="Phân tích ghép cặp" description="Đo chất lượng gợi ý đối tác và chia đội bằng kết quả thật. Dữ liệu mẫu (@bwf.example) không được tính.">
      <div className="mb-4 flex max-w-full gap-1 overflow-x-auto rounded-lg border border-slate-200 bg-white p-1" role="tablist" aria-label="Nội dung phân tích">
        {TABS.map((item) => (
          <button key={item.id} type="button" role="tab" aria-selected={tab === item.id} data-testid={`mt-tab-${item.id}`} className={classNames('h-9 shrink-0 whitespace-nowrap rounded-md px-4 text-sm font-semibold', tab === item.id ? 'bg-slate-800 text-white' : 'text-slate-600 hover:bg-slate-100')} onClick={() => setParams({ tab: item.id })}>{item.label}</button>
        ))}
      </div>
      <RangeBar preset={preset} onPreset={setPreset} custom={custom} onCustom={setCustom} problem={problem}>
        <select className={`${inputClass} sm:max-w-56`} aria-label="Nguồn chấm điểm" data-testid="mt-source" value={source} onChange={(event) => setSource(event.target.value)}>
          <option value="">Mọi nguồn chấm điểm</option>
          {Object.entries(SOURCE_LABEL).map(([id, label]) => <option key={id} value={id}>{label}</option>)}
        </select>
      </RangeBar>
      {!problem && tab === 'accuracy' && (
        <div className="grid gap-8">
          <PartnerAccuracySection range={range} source={source} />
          <TeamAccuracySection range={range} source={source} />
        </div>
      )}
    </AdminLayout>
  )
}

export default AdminMatchingPage
