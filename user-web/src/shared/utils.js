export function classNames(...items) {
  return items.filter(Boolean).join(' ')
}

export function searchMatches(item, value) {
  const keyword = value.trim().toLowerCase()
  if (!keyword) return true
  return [item.name, item.address].some((text) => text.toLowerCase().includes(keyword))
}

export function formatCurrency(value) {
  if (value === null || value === undefined) return 'Miễn phí'
  return `${Number(value).toLocaleString('vi-VN')}đ`
}
