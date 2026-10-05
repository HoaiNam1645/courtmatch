# React + Vite

This template provides a minimal setup to get React working in Vite with HMR and some Oxlint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Oxc](https://oxc.rs)
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/)

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the Oxlint configuration

If you are developing a production application, we recommend using TypeScript with type-aware lint rules enabled. Check out the [TS template](https://github.com/vitejs/vite/tree/main/packages/create-vite/template-react-ts) for information on how to integrate TypeScript and Oxlint's TypeScript related rules in your project.

## Gọi API backend

Quy tắc đầy đủ ở `QUY-TAC-TICH-HOP-API.md` (thư mục gốc).

```
src/api/
├── client.js   # fetch wrapper: baseURL, token, timeout, chuẩn hoá lỗi
├── auth.js     # 2.1.1 đăng ký + xác minh email, 2.1.2 đăng nhập, 2.1.4 đăng xuất, 2.1.5 quên mật khẩu
├── profile.js  # 2.1.6 xem, 2.1.7 sửa, 2.1.8–2.1.12 thiết lập chơi
├── venues.js   # 2.1.13 tìm kiếm, 2.1.14–2.1.17 bản đồ, 2.1.18 chi tiết
└── bookings.js # 2.1.28 tạo đơn, 2.1.29–2.1.31 xem/huỷ, 2.1.32 lịch sử

src/hooks/
├── useProfile.js       # tải hồ sơ
├── useVenueSearch.js   # tìm kiếm sân, có debounce 300ms và chống race
└── useVenueDetail.js   # chi tiết sân, phân biệt 404 với lỗi mạng

src/components/
└── LocationPicker.jsx   # ghim toạ độ khu vực ưu tiên bằng bản đồ Leaflet
```

### Luồng đăng ký có xác minh email (2.1.1)

```
/register         nhập thông tin  -> lưu {email, sentAt, resendAfterSeconds}
                                     vào sessionStorage courtly_pending_verification
/register/verify  nhập mã 6 số    -> xác minh xong có token, xoá sạch, về /
```

- **Đăng ký không còn tự đăng nhập.** `registerApi` trả `{email, expiresInSeconds,
  resendAfterSeconds}`, không có token. Component không được gọi `saveToken` ở bước này.
- Ô email ở `/register` là **bắt buộc**, đánh dấu `(*)`. Ô số điện thoại vẫn tuỳ chọn.
- Đăng nhập mà backend trả `EMAIL_NOT_VERIFIED` thì **chuyển thẳng** sang `/register/verify`
  chứ không hiện lỗi đỏ rồi bỏ mặc — người dùng không tự đoán được phải làm gì tiếp.
- Route `/register/verify` phải kiểm tra **trước** `isRegisterPage`, vì đường dẫn đó cũng
  chứa chuỗi `register`.
- Vào thẳng `/register/verify` mà không có dữ liệu thì hiện thẻ "chưa có yêu cầu nào" kèm
  nút về `/register`, **không** hiện ô nhập mã trống.

### Luồng quên mật khẩu (2.1.5)

Ba trang nối tiếp nhau, trạng thái đi qua `sessionStorage` khoá `courtly_password_recovery`
vì mỗi bước là một lần chuyển trang thật:

```
/forgot-password         nhập email    -> lưu {destination, sentAt, resendAfterSeconds}
/forgot-password/verify  nhập mã 6 số  -> lưu thêm {resetToken, tokenExpiresAt}
/reset-password          mật khẩu mới  -> xoá sạch, sang /reset-password/success
```

- **`sessionStorage` chứ không phải `localStorage`**: token đặt lại mật khẩu không được
  sống qua phiên trình duyệt.
- Vào thẳng `/forgot-password/verify` hay `/reset-password` mà không có dữ liệu thì hiện
  thẻ "cần bắt đầu lại" kèm nút về bước 1, **không** hiện form trống.
- Đếm ngược gửi lại tính từ `resendAfterSeconds` server trả về. Khi server trả `429`,
  đọc `fieldErrors.retryAfterSeconds` để đếm đúng số giây còn lại thay vì đoán lại 60 giây.
- Nhập sai quá số lần cho phép trả `RESET_TOO_MANY_ATTEMPTS` — mã đã bị huỷ ở server nên
  màn hình chuyển hẳn sang thẻ yêu cầu mã mới, không cho nhập tiếp.
- Danh sách điều kiện mật khẩu **khớp đúng** ràng buộc của backend (8–72 ký tự, có chữ cái,
  có chữ số). Giao diện cũ đòi "có chữ hoa và chữ thường" — điều kiện server không kiểm,
  gây chặn nhầm mật khẩu hợp lệ.
- Ô chọn "Số điện thoại" hiện nhãn **Sắp có** và không bấm được: backend chưa hỗ trợ SMS.

### Marker trên bản đồ

Luôn dùng `L.divIcon` với class CSS tự định nghĩa, **không dùng marker mặc định của Leaflet** —
ảnh marker trong package không được Vite bundle nên sẽ hiện icon vỡ. Xem `.leaflet-pick-pin`
và `.leaflet-venue-pin` trong `src/index.css`.

- **Component không gọi `fetch` trực tiếp** — luôn đi qua `src/api/`.
- Địa chỉ backend đọc từ `VITE_API_BASE_URL` (xem `.env.example`), không hardcode.
- Access token lưu ở `localStorage` khoá `courtly_access_token`.
- `courtly_cached_user` chỉ là bản sao để header hiện ngay khi mới mở trang;
  nguồn sự thật là `GET /api/v1/auth/me`, gọi lại mỗi lần tải trang.
- Backend trả `fullName`, giao diện dùng `user.name` — quy đổi trong `api/auth.js`,
  component không cần biết hình dạng response.
- Khi backend trả `401`, `client.js` xoá token và phát sự kiện `courtly-unauthorized`
  để `App` xoá phiên.

### Chạy cùng backend

```bash
cd ../backend && SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run   # cổng 8080
npm run dev                                                          # cổng 5173
```

Đăng nhập thử bằng tài khoản seed: `an.nguyen@example.com` / `Courtly@123`.

### Còn dùng dữ liệu giả

`courtly_fake_bookings` và `courtly_fake_payments` giờ **chỉ còn** màn hình `/payments`
dùng (2.1.34 chưa làm). Các màn hình đặt sân đã chuyển sang API thật.
`courtly_fake_player_profile` đã gỡ ở 2.1.6–2.1.12.

`public/api/home.json` giờ **chỉ còn** phục vụ slides, khuyến mãi và các trang đặt sân
(chưa nối API). Danh sách sân, khu vực và chi tiết sân đã lấy từ backend.

Lưới khung giờ trên `/san/:venueId` đã dùng lịch trống thật từ API (2.1.26), và số tiền
tạm tính do server trả về (2.1.27). Trang `/booking/checkout`, `/bookings` và `/bookings/:id` đã dùng API thật.
