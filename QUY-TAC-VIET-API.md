# Quy tắc khi viết API cho một chức năng

Đọc cùng `QUY-TAC-LAM-CHUC-NANG.md`. File này chỉ nói riêng về tầng API.
Phía frontend tiêu thụ API này: xem `QUY-TAC-TICH-HOP-API.md`.

## 1. Đường dẫn và HTTP method

- Tiền tố `/api/v1`, tên tài nguyên là **danh từ số nhiều**, `kebab-case`:
  `/api/v1/venues`, `/api/v1/bookings/{bookingId}/cancel`.
- Không đặt động từ trong URL, trừ hành động không phải CRUD (`/cancel`, `/confirm`).

| Method | Dùng khi | Thành công |
|---|---|---|
| `GET` | đọc dữ liệu, không đổi trạng thái | `200` |
| `POST` | tạo mới hoặc thực hiện hành động | `201` (tạo) / `200` (hành động) |
| `PUT` | thay thế toàn bộ | `200` |
| `PATCH` | sửa một phần | `200` |
| `DELETE` | xoá | `204` |

Mã lỗi: `400` sai dữ liệu đầu vào · `401` chưa đăng nhập · `403` không có quyền ·
`404` không tồn tại · `409` xung đột trạng thái hoặc trùng dữ liệu · `500` lỗi hệ thống.

## 2. DTO

- Request và Response là **hai DTO riêng**, dùng `record`. Không nhận và không trả entity.
- Response không bao giờ chứa `passwordHash`, token, `providerTransactionId` nội bộ.
- Tên field trong JSON dùng `camelCase`, khớp đúng cái frontend đang dùng.
- Controller chỉ: nhận DTO → gọi service → map sang DTO trả về. Không có logic nào khác.

## 3. Validate — ba lớp, không bỏ lớp nào

**Lớp 1 — cú pháp, ở DTO bằng Bean Validation.** Controller gắn `@Valid`.

```java
public record CreateBookingRequest(
        @NotNull UUID venueId,
        @NotNull UUID courtId,
        @NotNull @Future Instant startTime,
        @NotNull @Min(60) @Max(120) Integer durationMinutes,
        @Size(max = 500) String note) {}
```

**Lớp 2 — nghiệp vụ, ở service.** Kiểm tra theo thứ tự: tồn tại → quyền → trạng thái → xung đột.

**Lớp 3 — ràng buộc database.** Vẫn phải bắt `DataIntegrityViolationException` và đổi
thành lỗi rõ nghĩa. Lớp 2 chống được 99% trường hợp, nhưng hai request đồng thời vẫn lọt
xuống tới `EXCLUDE` constraint của `booking_items`.

### Những trường hợp phải nghĩ tới cho mọi API

| Nhóm | Trường hợp |
|---|---|
| Thiếu / rỗng | field `null`, chuỗi rỗng, chuỗi toàn khoảng trắng |
| Sai định dạng | UUID sai, ngày sai, email sai, enum không nằm trong danh sách |
| Giá trị biên | số âm, số 0, vượt độ dài tối đa, `page`/`size` quá lớn |
| Thời gian | ngày trong quá khứ, `endTime <= startTime`, lệch múi giờ |
| Tồn tại | id không có trong DB, hoặc đã bị xoá / `inactive` |
| Quan hệ | `courtId` không thuộc `venueId` gửi lên |
| Quyền | không phải chủ sở hữu, sai vai trò |
| Trạng thái | huỷ đơn đã `completed`, thanh toán đơn đã `expired` |
| Trùng lặp | slug trùng, đã đánh giá rồi, gửi trùng yêu cầu ghép cặp |
| Đồng thời | hai người đặt cùng khung giờ, webhook gửi lại hai lần |

### Ví dụ cụ thể: tạo yêu cầu đặt sân (2.1.28)

1. Venue tồn tại, `status = active` và `approval_status = approved`.
2. Court thuộc đúng venue đó và `status = active`.
3. Thời lượng thuộc `{60, 90, 120}`, giờ bắt đầu ở tương lai.
4. Khung giờ nằm trong `venue_operating_hours` của thứ tương ứng.
5. Không giao với `court_blocks` đang `active`.
6. Không giao với `booking_items` đang `pending`/`confirmed`.
7. Giá tính từ `court_price_rules` đang hiệu lực, **không nhận giá từ client**.
8. Bắt vi phạm `booking_items_no_overlap` → trả `409 BOOKING_SLOT_TAKEN`.

## 4. Cấu trúc lỗi thống nhất

Một `@RestControllerAdvice` cho toàn hệ thống. Mọi lỗi trả về cùng một hình dạng:

```json
{
  "code": "BOOKING_SLOT_TAKEN",
  "message": "Khung giờ này vừa có người đặt, vui lòng chọn giờ khác.",
  "fieldErrors": { "startTime": "Khung giờ đã được giữ" },
  "timestamp": "2026-09-23T11:30:00Z"
}
```

- `code` là chuỗi hằng để frontend xử lý theo nhánh, không dựa vào `message`.
- `message` viết bằng tiếng Việt, cho người dùng cuối đọc được.
- Không lộ stacktrace, câu SQL hay tên constraint ra ngoài.
- Lỗi đăng nhập / quên mật khẩu **không được tiết lộ** email có tồn tại hay không.

## 5. Phân trang và lọc

- Tham số `page` (từ 0), `size` (mặc định 20, **tối đa 100**), `sort`.
- Response bọc trong đối tượng có `content`, `page`, `size`, `totalElements`, `totalPages`.
- Mọi API trả danh sách đều phải phân trang. Không có endpoint trả toàn bộ bảng.

## 6. Bảo mật

- `userId` luôn lấy từ token, **không bao giờ nhận từ request body**.
- Kiểm tra quyền ở service, không dựa vào việc frontend ẩn nút.
- Webhook (SePay) phải kiểm tra chữ ký/API key và chống xử lý trùng bằng
  `sepay_webhook_logs.transaction_code`.
- Ghi `audit_logs` cho hành động của admin và chủ sân.

## 7. Transaction

- `@Transactional` đặt ở service, không đặt ở controller.
- API chỉ đọc dùng `@Transactional(readOnly = true)`.
- Không gọi API ngoài (SePay, gửi mail) khi đang trong transaction ghi DB.

## 8. Trước khi báo xong

- [ ] Có test cho **mọi nhánh validate**, không chỉ trường hợp thành công.
- [ ] Gọi thử bằng `curl` với: dữ liệu đúng, thiếu field, sai kiểu, id không tồn tại,
      sai quyền, sai trạng thái, và trường hợp trùng lặp.
- [ ] Kiểm tra response không lọt field nhạy cảm.
- [ ] Đối chiếu tên field JSON với chỗ frontend đang dùng.
