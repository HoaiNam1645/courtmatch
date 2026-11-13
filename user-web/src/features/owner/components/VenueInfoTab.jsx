import { useState } from 'react'
import { ApiError } from '../../../api/client'
import { updateOwnerVenue } from '../../../api/ownerVenues'
import { FormAlert } from '../../../shared/FormFeedback'
import { primaryButtonClass } from '../ownerStyles'
import { validateVenueInfo } from '../venueFormRules'
import { OwnerCard } from './OwnerUi'
import { VenueInfoFields } from './VenueInfoFields'

/** 2.3.4 — sửa thông tin chung, vị trí và tiện ích của sân. */
function VenueInfoTab({ venue, options, onSaved }) {
  const [values, setValues] = useState(venue)
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event) {
    event.preventDefault()
    if (submitting) return
    const nextErrors = validateVenueInfo(values, options)
    setErrors(nextErrors)
    setFormError('')
    if (Object.keys(nextErrors).length > 0) {
      setFormError('Thông tin sân chưa hợp lệ, vui lòng kiểm tra các ô được đánh dấu.')
      return
    }

    setSubmitting(true)
    try {
      await updateOwnerVenue(venue.id, values)
      onSaved('Đã lưu thông tin sân.')
    } catch (error) {
      if (error instanceof ApiError) {
        setErrors(error.fieldErrors ?? {})
        setFormError(error.message)
      } else {
        setFormError('Đã có lỗi xảy ra, vui lòng thử lại.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form className="grid gap-5" noValidate onSubmit={handleSubmit}>
      <OwnerCard title="Thông tin sân" description="Thay đổi được ghi lại trong nhật ký hệ thống để quản trị viên đối chiếu.">
        <VenueInfoFields values={values} onChange={setValues} errors={errors} options={options} />
      </OwnerCard>
      <FormAlert message={formError} />
      <div className="flex justify-end">
        <button type="submit" className={primaryButtonClass} disabled={submitting}>
          {submitting ? 'Đang lưu...' : 'Lưu thông tin sân'}
        </button>
      </div>
    </form>
  )
}

export { VenueInfoTab }
