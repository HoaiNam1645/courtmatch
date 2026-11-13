import { useState } from 'react'
import { Plus, Trash2 } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError } from '../../../api/client'
import { createOwnerVenue, fetchVenueOptions } from '../../../api/ownerVenues'
import { useApiResource } from '../../../hooks/useApiResource'
import { FieldError, FormAlert } from '../../../shared/FormFeedback'
import { CourtFields } from '../components/CourtFields'
import { OwnerLayout } from '../components/OwnerLayout'
import { OwnerCard, OwnerError, OwnerLoading } from '../components/OwnerUi'
import { VenueInfoFields } from '../components/VenueInfoFields'
import { primaryButtonClass, secondaryButtonClass } from '../ownerStyles'
import { emptyCourt, validateCourts, validateVenueInfo } from '../venueFormRules'

const initialValues = {
  name: '',
  description: '',
  address: '',
  ward: '',
  district: '',
  latitude: null,
  longitude: null,
  phone: '',
  email: '',
  services: [],
  courts: [{ ...emptyCourt(), name: 'Sân A', courtCode: 'A' }],
}

function CreateVenueForm({ options }) {
  const navigate = useNavigate()
  const [values, setValues] = useState(initialValues)
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  function updateCourt(index, court) {
    setValues((current) => ({ ...current, courts: current.courts.map((item, position) => (position === index ? court : item)) }))
  }

  function addCourt() {
    setValues((current) => ({ ...current, courts: [...current.courts, emptyCourt()] }))
  }

  function removeCourt(index) {
    setValues((current) => ({ ...current, courts: current.courts.filter((_, position) => position !== index) }))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (submitting) return
    const nextErrors = { ...validateVenueInfo(values, options), ...validateCourts(values.courts, options) }
    setErrors(nextErrors)
    setFormError('')
    if (Object.keys(nextErrors).length > 0) {
      setFormError('Thông tin sân chưa hợp lệ, vui lòng kiểm tra các ô được đánh dấu.')
      return
    }

    setSubmitting(true)
    try {
      const venue = await createOwnerVenue(values)
      navigate('/owner/venues', { replace: true, state: { createdVenueName: venue.name } })
    } catch (error) {
      if (error instanceof ApiError) {
        setErrors(error.fieldErrors ?? {})
        setFormError(error.message)
      } else {
        setFormError('Đã có lỗi xảy ra, vui lòng thử lại.')
      }
      setSubmitting(false)
    }
  }

  return (
    <form className="grid gap-5" noValidate onSubmit={handleSubmit}>
      <OwnerCard title="Thông tin sân" description="Sân mới sẽ ở trạng thái chờ quản trị viên duyệt và chưa hiển thị với người chơi.">
        <VenueInfoFields values={values} onChange={setValues} errors={errors} options={options} />
      </OwnerCard>

      <OwnerCard
        title="Sân con"
        description="Mỗi sân con là một sân cầu lông riêng mà người chơi có thể đặt."
        actions={values.courts.length < options.maxCourts && (
          <button type="button" className={secondaryButtonClass} onClick={addCourt}><Plus className="size-4" />Thêm sân con</button>
        )}
      >
        <div className="grid gap-4">
          {values.courts.map((court, index) => (
            <div key={index} className="rounded-lg border border-emerald-100 p-4" data-testid={`court-row-${index}`}>
              <div className="mb-3 flex items-center justify-between gap-3">
                <p className="text-sm font-semibold text-emerald-900">Sân con {index + 1}</p>
                {values.courts.length > 1 && (
                  <button type="button" className="inline-flex h-9 items-center gap-1.5 rounded-lg px-3 text-xs font-semibold text-red-700 hover:bg-red-50" onClick={() => removeCourt(index)} aria-label={`Xóa sân con ${index + 1}`}>
                    <Trash2 className="size-4" />Xóa
                  </button>
                )}
              </div>
              <CourtFields court={court} onChange={(next) => updateCourt(index, next)} errors={errors} errorPrefix={`courts[${index}].`} options={options} labelSuffix={` ${index + 1}`} />
            </div>
          ))}
        </div>
        <FieldError message={errors.courts} />
      </OwnerCard>

      <FormAlert message={formError} />
      <div className="flex flex-wrap justify-end gap-3">
        <Link to="/owner/venues" className={secondaryButtonClass}>Hủy</Link>
        <button type="submit" className={primaryButtonClass} disabled={submitting}>
          {submitting ? 'Đang gửi...' : 'Gửi sân chờ duyệt'}
        </button>
      </div>
    </form>
  )
}

/** 2.3.3 — thêm sân cầu lông mới. */
function OwnerVenueCreatePage() {
  const { status, data, error, reload } = useApiResource(fetchVenueOptions, [])
  return (
    <OwnerLayout eyebrow="2.3.3 · Thêm sân" title="Thêm sân cầu lông mới" description="Khai báo thông tin, vị trí và các sân con. Giờ hoạt động, bảng giá và hình ảnh được thiết lập sau khi tạo sân.">
      {status === 'loading' && <OwnerLoading rows={3} />}
      {status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
      {status === 'success' && <CreateVenueForm options={data} />}
    </OwnerLayout>
  )
}

export default OwnerVenueCreatePage
