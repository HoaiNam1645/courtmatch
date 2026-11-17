const matchTypeLabels = {
  mens: 'Đôi nam',
  womens: 'Đôi nữ',
  mixed: 'Đôi nam nữ',
}

const genderLabels = { male: 'Nam', female: 'Nữ', other: 'Khác' }

/** Loại đôi nào cho phép người chơi có giới tính này (đôi nam nữ cần thêm kiểm tra tổng 2 nam 2 nữ ở backend). */
function genderFits(matchType, gender) {
  if (matchType === 'mens') return gender === 'male'
  if (matchType === 'womens') return gender === 'female'
  return gender === 'male' || gender === 'female'
}

const reasonLabels = {
  not_found: 'không tìm thấy người chơi này',
  not_player: 'không phải tài khoản người chơi',
  inactive: 'tài khoản đang không hoạt động',
  missing_gender: 'chưa khai báo giới tính trong hồ sơ',
  gender_mismatch: 'giới tính không phù hợp với loại đôi đã chọn',
  duplicate: 'bị chọn trùng',
  self: 'là chính bạn, không cần chọn lại',
  mixed_needs_two_each: 'Đôi nam nữ cần đúng 2 nam và 2 nữ (tính cả bạn)',
}

/** Chuyển fieldErrors của backend thành các dòng tiếng Việt, tên người chơi lấy từ danh sách đã chọn. */
function describeInvalidPlayers(fieldErrors, nameById, selfId) {
  return Object.entries(fieldErrors ?? {}).map(([key, code]) => {
    if (key === 'matchType') return reasonLabels[code] ?? code
    const name = key === selfId ? 'Bạn' : nameById[key] ?? 'Người chơi'
    return `${name}: ${reasonLabels[code] ?? code}`
  })
}

function formatPoints(value) {
  return value == null ? '—' : Number(value).toFixed(1).replace(/\.0$/, '')
}

export { describeInvalidPlayers, formatPoints, genderFits, genderLabels, matchTypeLabels }
