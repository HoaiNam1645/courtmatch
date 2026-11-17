/** Hiển thị thời điểm đánh giá theo kiểu tương đối, dễ đọc hơn ngày đầy đủ. */
export function formatReviewDate(isoDate) {
  if (!isoDate) return ''
  const days = Math.floor((Date.now() - new Date(isoDate).getTime()) / 86400000)
  if (days <= 0) return 'Hôm nay'
  if (days === 1) return 'Hôm qua'
  if (days < 7) return `${days} ngày trước`
  if (days < 30) return `${Math.floor(days / 7)} tuần trước`
  if (days < 365) return `${Math.floor(days / 30)} tháng trước`
  return new Date(isoDate).toLocaleDateString('vi-VN')
}
