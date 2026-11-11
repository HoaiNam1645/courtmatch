const partnerRequestStatus = {
  pending: { label: 'Đang chờ', className: 'bg-amber-100 text-amber-800' },
  accepted: { label: 'Đã chấp nhận', className: 'bg-emerald-100 text-emerald-800' },
  rejected: { label: 'Đã từ chối', className: 'bg-red-100 text-red-700' },
  cancelled: { label: 'Đã hủy', className: 'bg-stone-100 text-stone-600' },
  expired: { label: 'Đã hết hạn', className: 'bg-stone-100 text-stone-600' },
}

function statusUi(status) {
  return partnerRequestStatus[status] ?? { label: 'Trạng thái khác', className: 'bg-stone-100 text-stone-600' }
}

function formatPartnerRequestTime(value) {
  return new Intl.DateTimeFormat('vi-VN', {
    timeZone: 'Asia/Ho_Chi_Minh', dateStyle: 'short', timeStyle: 'short',
  }).format(new Date(value))
}

export { formatPartnerRequestTime, statusUi }
