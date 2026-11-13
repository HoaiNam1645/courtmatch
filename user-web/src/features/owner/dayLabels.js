/** Nhãn thứ trong tuần theo quy ước backend: 1 = Thứ hai ... 7 = Chủ nhật. */
export const DAY_OF_WEEK_LABELS = {
  1: 'Thứ hai',
  2: 'Thứ ba',
  3: 'Thứ tư',
  4: 'Thứ năm',
  5: 'Thứ sáu',
  6: 'Thứ bảy',
  7: 'Chủ nhật',
}

export function dayLabel(dayOfWeek) {
  return dayOfWeek == null ? 'Mọi ngày' : DAY_OF_WEEK_LABELS[dayOfWeek] ?? `Thứ ${dayOfWeek}`
}
