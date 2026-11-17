import { deleteE2eUsers, runSql } from './helpers'

export const email = (tag) => `e2e-${tag}@example.com`
let counter = 0

/** Một sân đã duyệt có sân con, dùng làm nơi diễn ra các đơn đánh giá; kèm điểm trung bình hiện tại để khôi phục. */
export function pickVenue() {
  const row = runSql(`SELECT v.id || '|' || v.slug || '|' || v.name || '|' || v.average_rating || '|' || v.review_count || '|' || (SELECT c.id FROM courts c WHERE c.venue_id = v.id LIMIT 1)
    FROM venues v WHERE v.approval_status = 'approved' AND v.status = 'active' AND EXISTS (SELECT 1 FROM courts c WHERE c.venue_id = v.id) ORDER BY v.created_at LIMIT 1`)
  const [id, slug, name, rating, count, court] = row.split('|')
  return { id, slug, name, court, snapshot: { rating, count } }
}

/** Đơn đã hoàn thành cách đây `daysAgo` ngày, tạo bằng SQL (không đi qua luồng thanh toán); trả về bookingId. */
export function completedBooking(venue, tag, code, daysAgo) {
  const shift = (counter += 1) * 3 // mỗi đơn lệch 3 giờ để không đụng ràng buộc chống trùng giờ của sân
  runSql(`
    WITH b AS (
      INSERT INTO bookings (booking_code, user_id, venue_id, status, total_amount, price_locked_at, completed_at)
      SELECT '${code}', u.id, '${venue.id}', 'completed', 180000, now(), now() - interval '${daysAgo} days'
      FROM users u WHERE u.email = '${email(tag)}' RETURNING id)
    INSERT INTO booking_items (booking_id, court_id, start_time, end_time, price, status)
    SELECT b.id, '${venue.court}', now() - interval '${daysAgo} days ${shift + 2} hours', now() - interval '${daysAgo} days ${shift} hours 30 minutes', 180000, 'confirmed' FROM b;`)
  return runSql(`SELECT id FROM bookings WHERE booking_code = '${code}'`)
}

export function insertReview(venue, tag, bookingId, rating, comment, status = 'visible') {
  return runSql(`INSERT INTO venue_reviews (venue_id, user_id, booking_id, rating, comment, status)
    SELECT '${venue.id}', u.id, '${bookingId}', ${rating}, ${comment ? `'${comment}'` : 'NULL'}, '${status}' FROM users u WHERE u.email = '${email(tag)}' RETURNING id`).split('\n')[0]
}

/** Dọn đơn, tài khoản E2E (kéo theo đánh giá) và khôi phục điểm trung bình của sân. */
export function cleanupReviews(venue, codePrefix) {
  runSql(`DELETE FROM bookings WHERE booking_code LIKE '${codePrefix}%'`)
  deleteE2eUsers()
  if (venue) runSql(`UPDATE venues SET average_rating = ${venue.snapshot.rating}, review_count = ${venue.snapshot.count} WHERE id = '${venue.id}'`)
}
