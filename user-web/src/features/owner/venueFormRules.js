/**
 * Kiểm tra form sân phía client (2.3.3, 2.3.4) — cùng điều kiện với backend
 * (CreateVenueRequest, CourtInput, OwnerVenueService). Backend vẫn kiểm tra lại toàn bộ.
 */

const VN_MOBILE = /^0[35789]\d{8}$/
const VN_LANDLINE = /^(02\d{9}|1[89]00\d{4,6})$/
const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/
const COURT_CODE = /^[A-Za-z0-9_-]{1,20}$/

export function normalizePhone(raw) {
  const digits = raw.replace(/[^0-9+]/g, '')
  if (digits.startsWith('+84')) return `0${digits.slice(3)}`
  if (digits.startsWith('84') && (digits.length === 11 || digits.length === 12)) return `0${digits.slice(2)}`
  return digits
}

export function validateVenueInfo(values, options) {
  const errors = {}
  const name = values.name.trim()
  if (!name) errors.name = 'Vui lòng nhập tên sân'
  else if (name.length < 3 || name.length > 200) errors.name = 'Tên sân từ 3 đến 200 ký tự'
  if (values.description.length > 2000) errors.description = 'Mô tả tối đa 2000 ký tự'
  if (!values.address.trim()) errors.address = 'Vui lòng nhập địa chỉ'
  else if (values.address.trim().length > 300) errors.address = 'Địa chỉ tối đa 300 ký tự'
  if (values.ward.trim().length > 100) errors.ward = 'Phường/xã tối đa 100 ký tự'
  if (!values.district) errors.district = 'Vui lòng chọn quận/huyện'
  else if (options && !options.districts.includes(values.district)) errors.district = 'Hệ thống chỉ phục vụ các quận/huyện của Đà Nẵng'

  if (values.latitude == null || values.longitude == null) {
    errors.latitude = 'Vui lòng chọn vị trí sân trên bản đồ'
  } else if (options) {
    const { bounds } = options
    if (values.latitude < bounds.minLatitude || values.latitude > bounds.maxLatitude
      || values.longitude < bounds.minLongitude || values.longitude > bounds.maxLongitude) {
      errors.latitude = 'Vị trí sân phải nằm trong thành phố Đà Nẵng'
    }
  }

  if (!values.phone.trim()) {
    errors.phone = 'Vui lòng nhập số điện thoại của sân'
  } else {
    const phone = normalizePhone(values.phone)
    if (!VN_MOBILE.test(phone) && !VN_LANDLINE.test(phone)) {
      errors.phone = 'Số di động 10 số hoặc số cố định 11 số (vd 0236 3xxx xxx)'
    }
  }
  if (values.email.trim() && !EMAIL.test(values.email.trim())) errors.email = 'Email không đúng định dạng'

  values.services.forEach((service, index) => {
    if (service.price !== '' && (Number.isNaN(Number(service.price)) || Number(service.price) < 0)) {
      errors[`services[${index}].price`] = 'Giá tiện ích không được âm'
    } else if (service.price !== '' && Number(service.price) > 10000000) {
      errors[`services[${index}].price`] = 'Giá tiện ích tối đa 10.000.000đ'
    }
    if (service.note.length > 255) errors[`services[${index}].note`] = 'Ghi chú tối đa 255 ký tự'
  })
  return errors
}

/** @param prefix tiền tố khóa lỗi, ví dụ `courts[0].` khi thêm sân, rỗng khi sửa một sân con */
export function validateCourt(court, options, prefix = '') {
  const errors = {}
  if (!court.name.trim()) errors[`${prefix}name`] = 'Vui lòng nhập tên sân con'
  else if (court.name.trim().length > 100) errors[`${prefix}name`] = 'Tên sân con tối đa 100 ký tự'
  if (!court.courtCode.trim()) errors[`${prefix}courtCode`] = 'Vui lòng nhập mã sân con'
  else if (!COURT_CODE.test(court.courtCode.trim())) errors[`${prefix}courtCode`] = 'Mã sân con gồm 1-20 ký tự chữ, số, gạch ngang hoặc gạch dưới'
  if (!court.courtType) errors[`${prefix}courtType`] = 'Vui lòng chọn loại sân'
  else if (options && !options.courtTypes.some((type) => type.value === court.courtType)) errors[`${prefix}courtType`] = 'Loại sân không hợp lệ'
  return errors
}

export function validateCourts(courts, options) {
  const errors = {}
  if (courts.length === 0) errors.courts = 'Sân phải có ít nhất một sân con'
  if (options && courts.length > options.maxCourts) errors.courts = `Tối đa ${options.maxCourts} sân con`
  const seen = new Set()
  courts.forEach((court, index) => {
    Object.assign(errors, validateCourt(court, options, `courts[${index}].`))
    const code = court.courtCode.trim().toUpperCase()
    if (code && seen.has(code) && !errors[`courts[${index}].courtCode`]) {
      errors[`courts[${index}].courtCode`] = 'Mã sân con đã tồn tại trong sân này'
    }
    seen.add(code)
  })
  return errors
}

export const emptyCourt = () => ({ name: '', courtCode: '', courtType: 'standard', surfaceType: '', indoor: true })
