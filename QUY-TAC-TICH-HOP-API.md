# Quy tắc khi frontend tích hợp API

Backend đúng nghiệp vụ nhưng frontend gọi sai hoặc thể hiện sai luồng thì người dùng
vẫn gặp lỗi. File này dành cho phía `user-web`.

Đọc cùng `QUY-TAC-VIET-API.md` — frontend phải hiểu hợp đồng API trước khi gọi.

## 1. Nguyên tắc gốc

> **Backend là nguồn sự thật duy nhất. Frontend chỉ hiển thị và thu thập dữ liệu.**

- Không tính lại giá, phí, trạng thái, hay điểm số ở frontend. Hiển thị cái backend trả về.
- Nếu cần một con số mà API chưa trả → **yêu cầu backend bổ sung**, không tự tính.
- Không hardcode danh sách quận, dịch vụ, mức giá. Lấy từ API.
- Tính ở client chỉ được phép cho việc hiển thị tạm (ví dụ tạm tính khi người dùng đang
  chọn giờ), và phải **gọi API xác nhận lại trước khi tạo đơn**.

## 2. Tách tầng gọi API

Hiện `src/App.jsx` đang gộp mọi thứ trong một file 3600 dòng và đọc `localStorage` trực
tiếp. Khi tích hợp API, tách ra:

```
src/
├── api/
│   ├── client.js          # fetch wrapper: baseURL, token, parse lỗi
│   ├── venues.js          # các hàm gọi API sân
│   └── bookings.js
├── hooks/                 # useVenues, useBooking... quản lý loading/error/data
└── pages/                 # màn hình, chỉ nhận dữ liệu đã xử lý
```

- Component **không gọi `fetch` trực tiếp**. Luôn đi qua `api/`.
- Base URL đọc từ biến môi trường `VITE_API_BASE_URL`, không hardcode `localhost:8080`.
- Mọi lời gọi đi qua một wrapper chung để xử lý token, lỗi, và timeout ở một chỗ.

## 3. Mỗi lời gọi API phải xử lý đủ bốn trạng thái

Thiếu một trạng thái là một lỗi giao diện.

| Trạng thái | Giao diện phải có |
|---|---|
| `loading` | skeleton hoặc spinner, khoá nút submit |
| `success` | dữ liệu thật |
| `empty` | thông báo "không có dữ liệu" **khác** với lỗi |
| `error` | thông báo lỗi + nút thử lại |

- `empty` và `error` là hai thứ khác nhau. Không tìm thấy sân ≠ gọi API thất bại.
- Nút submit phải `disabled` trong lúc đang gửi, tránh double-submit tạo hai đơn.

## 4. Đọc lỗi từ backend cho đúng

Backend trả về `code`, `message`, `fieldErrors`.

- **Xử lý theo `code`**, không so sánh chuỗi `message` (message có thể đổi bất cứ lúc nào).
- `fieldErrors` phải hiển thị **ngay dưới ô nhập tương ứng**, không gom vào một toast.
- Không hiện thông báo kỹ thuật thô ra cho người dùng.

| HTTP | Frontend phải làm gì |
|---|---|
| `400` | hiện lỗi ngay tại field, giữ nguyên dữ liệu người dùng đã nhập |
| `401` | xoá token, chuyển về `/login`, giữ trang đang xem để quay lại sau |
| `403` | báo không có quyền, **không** chuyển về login |
| `404` | màn hình "không tìm thấy", có nút quay lại |
| `409` | báo xung đột và **tải lại dữ liệu mới** (ví dụ khung giờ vừa bị người khác đặt) |
| `500` | báo lỗi hệ thống + nút thử lại, không đổ lỗi cho người dùng |

## 5. Validate ở frontend — để trải nghiệm tốt, không phải để bảo mật

- Validate client chỉ giúp người dùng phát hiện sớm. **Backend vẫn validate lại toàn bộ.**
- Điều kiện validate ở hai phía phải **giống nhau** (độ dài, định dạng, khoảng giá trị).
  Lệch nhau sẽ tạo lỗi khó hiểu: nút bấm được nhưng server từ chối.
- Không bao giờ dựa vào việc ẩn nút để chặn hành động. Người dùng có thể gọi API trực tiếp.
- Ẩn/hiện nút phải dựa trên **trạng thái backend trả về**, không dựa vào suy đoán.
  Ví dụ nút "Huỷ đặt sân" chỉ hiện khi `status` là `pending_payment`/`confirmed`
  và chưa tới giờ chơi — đúng điều kiện backend đang kiểm tra.

## 6. Thể hiện đúng trạng thái nghiệp vụ

Đây là chỗ hay sai nhất.

- Enum hiển thị phải **phủ hết** giá trị backend có thể trả. Thiếu một nhánh thì giao diện
  hiện trống. Booking có 6 trạng thái: `pending_payment`, `confirmed`, `cancelled`,
  `completed`, `expired`, `refunded`. Payment có 5: `pending`, `paid`, `failed`,
  `expired`, `refunded`.
- Luôn có nhánh `default` cho giá trị lạ, đừng để giao diện vỡ khi backend thêm trạng thái.
- Không tự suy ra trạng thái. `booking.status` và `payment.status` là **hai thứ khác nhau**,
  đơn `cancelled` vẫn có thể có payment `refunded`.
- Nhãn tiếng Việt định nghĩa ở **một chỗ duy nhất**, không lặp lại ở từng màn hình.

## 7. Tiền, thời gian, toạ độ

- **Tiền**: backend trả `numeric`, nhận dưới dạng chuỗi hoặc số nguyên đồng. Không dùng
  phép tính dấu phẩy động để cộng tiền. Chỉ định dạng khi hiển thị (`210.000đ`).
- **Thời gian**: backend trả ISO-8601 UTC (`2026-09-24T11:00:00Z`). Frontend **luôn đổi
  sang `Asia/Ho_Chi_Minh`** khi hiển thị, và gửi lại đúng định dạng ISO có múi giờ.
  Không cắt chuỗi ngày giờ bằng `substring`.
- **Toạ độ**: đúng thứ tự `latitude`, `longitude`. Leaflet dùng `[lat, lng]` còn
  GeoJSON/PostGIS dùng `[lng, lat]` — nhầm chỗ này thì marker rơi ra giữa biển.

## 8. Không dùng localStorage làm nguồn dữ liệu

Các key hiện tại (`courtly_fake_bookings`, `courtly_fake_payments`) là dữ liệu giả của
giai đoạn prototype. Khi tích hợp API phải **xoá hết**.

- Chỉ được lưu ở client: token, lựa chọn tạm chưa gửi đi (`courtly_pending_booking`),
  và tuỳ chọn hiển thị.
- Sau khi tạo đơn thành công, **xoá lựa chọn tạm ngay**, tránh tạo trùng khi người dùng
  bấm back.
- Dữ liệu nghiệp vụ luôn tải lại từ API, không đọc bản sao cũ trong localStorage.

## 9. Dữ liệu thay đổi thì phải tải lại

- Sau khi tạo / huỷ / thanh toán thành công → **tải lại** dữ liệu liên quan, không tự sửa
  state ở client cho "nhanh".
- Màn hình chọn lịch trống phải tải lại trước khi submit, vì người khác có thể vừa đặt.
- Màn hình chờ thanh toán: hỏi lại trạng thái theo chu kỳ (5–10 giây), dừng khi đã
  `paid`/`failed`/`expired` hoặc khi hết đếm ngược. Không hỏi liên tục không giới hạn.
- Đếm ngược hết hạn tính theo `expiredAt` backend trả về, **không** tự đếm 10 phút từ
  lúc mở trang — người dùng có thể mở lại trang sau đó.

## 10. Trước khi báo xong

- [ ] Thử với mạng chậm (DevTools → Network → Slow 3G): giao diện có hiện loading không?
- [ ] Tắt backend rồi mở trang: có hiện lỗi tử tế thay vì trắng trang không?
- [ ] Bấm submit hai lần thật nhanh: có tạo hai đơn không?
- [ ] Mở hai tab cùng đặt một khung giờ: tab thứ hai có nhận `409` và báo đúng không?
- [ ] Sửa `status` trong response thành giá trị lạ: giao diện có vỡ không?
- [ ] Tài khoản không có quyền gọi API của người khác: có bị chặn không?
- [ ] Đối chiếu từng field JSON với tài liệu API, không đoán tên field.
