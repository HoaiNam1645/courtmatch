import { deleteE2eUsers, runSql } from './helpers'

export const email = (tag) => `e2e-${tag}@example.com`
let counter = 0

/** Một sân đã duyệt có sân con, dùng làm nơi diễn ra các đơn đánh giá; kèm điểm trung bình hiện tại để khôi phục. */
export function pickVenue({ ownerEmail = null } = {}) {
  const row = runSql(`SELECT v.id || '|' || v.slug || '|' || v.name || '|' || v.average_rating || '|' || v.review_count || '|' || (SELECT c.id FROM courts c WHERE c.venue_id = v.id LIMIT 1)
    FROM venues v WHERE v.approval_status = 'approved' AND v.status = 'active' AND EXISTS (SELECT 1 FROM courts c WHERE c.venue_id = v.id)
      ${ownerEmail ? `AND v.owner_id = (SELECT id FROM users WHERE email = '${ownerEmail}')` : ''} ORDER BY v.created_at LIMIT 1`)
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
    SELECT '${venue.id}', u.id, ${bookingId ? `'${bookingId}'` : 'NULL'}, ${rating}, ${comment ? `'${comment}'` : 'NULL'}, '${status}' FROM users u WHERE u.email = '${email(tag)}' RETURNING id`).split('\n')[0]
}

/** Dọn đơn, tài khoản E2E (kéo theo đánh giá) và khôi phục điểm trung bình của sân. */
export function cleanupReviews(venue, codePrefix) {
  // Báo cáo về đánh giá của tài khoản E2E (người báo cáo là chủ sân seed nên không tự xóa theo tài khoản E2E).
  runSql(`DELETE FROM reports WHERE target_type = 'venue_review' AND target_id IN (SELECT r.id FROM venue_reviews r JOIN users u ON u.id = r.user_id WHERE u.email LIKE 'e2e-%@example.com')`)
  runSql(`DELETE FROM bookings WHERE booking_code LIKE '${codePrefix}%'`)
  deleteE2eUsers()
  if (venue) runSql(`UPDATE venues SET average_rating = ${venue.snapshot.rating}, review_count = ${venue.snapshot.count} WHERE id = '${venue.id}'`)
}

/** Đơn của `tag` ở trạng thái bất kỳ, giờ chơi kết thúc `endedDaysAgo` ngày trước (âm = tương lai); trả về bookingId. */
export function bookingWithStatus(venue, tag, code, status, endedDaysAgo) {
  const shift = (counter += 1) * 3
  runSql(`
    WITH b AS (
      INSERT INTO bookings (booking_code, user_id, venue_id, status, total_amount, price_locked_at)
      SELECT '${code}', u.id, '${venue.id}', '${status}', 180000, now() FROM users u WHERE u.email = '${email(tag)}' RETURNING id)
    INSERT INTO booking_items (booking_id, court_id, start_time, end_time, price, status)
    SELECT b.id, '${venue.court}', now() - interval '${endedDaysAgo} days ${shift + 2} hours', now() - interval '${endedDaysAgo} days ${shift} hours', 180000, 'confirmed' FROM b;`)
  return runSql(`SELECT id FROM bookings WHERE booking_code = '${code}'`)
}

/** Dọn báo cáo đơn của các đơn E2E (khóa ngoại RESTRICT nên phải xóa trước đơn và tài khoản) cùng thông báo cho chủ sân. */
export function cleanupDisputes(codePrefix) {
  runSql(`
    DELETE FROM notifications WHERE type = 'booking_dispute_opened'
      AND data_json ->> 'disputeId' IN (SELECT d.id::text FROM booking_disputes d JOIN bookings b ON b.id = d.booking_id WHERE b.booking_code LIKE '${codePrefix}%');
    DELETE FROM booking_disputes WHERE booking_id IN (SELECT id FROM bookings WHERE booking_code LIKE '${codePrefix}%');
    DELETE FROM payments WHERE booking_id IN (SELECT id FROM bookings WHERE booking_code LIKE '${codePrefix}%');`)
}

/** Khoản thanh toán của đơn do chủ sân của sân nhận (trạng thái tùy chọn); cần cho điều kiện yêu cầu hoàn tiền (2.3.87). */
export function addPayment(bookingId, status = 'confirmed') {
  runSql(`INSERT INTO payments (booking_id, amount, status, recipient_owner_id, method)
    SELECT b.id, 180000, '${status}', v.owner_id, 'cash' FROM bookings b JOIN venues v ON v.id = b.venue_id WHERE b.id = '${bookingId}'`)
}
