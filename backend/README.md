# Courtly Backend

Backend của hệ thống đặt sân cầu lông Courtly. Hiện gồm **khung dự án, migration, seeder**,
**API xác thực** (2.1.1 kèm xác minh email, 2.1.2, 2.1.4, 2.1.5), **API hồ sơ người chơi** (2.1.6 → 2.1.12)
và **API duyệt sân, lịch trống, đặt sân** (2.1.13 → 2.1.32).

- Java 21, Spring Boot 3.5.5, Maven
- PostgreSQL 18 + PostGIS 3.6 (bắt buộc), Flyway
- Spring Data JPA (Hibernate 6), Lombok

## 1. Yêu cầu môi trường

| Thành phần | Phiên bản | Ghi chú |
|---|---|---|
| JDK | 21 | `brew install openjdk@21` |
| PostgreSQL | 17 hoặc 18 | PostGIS của Homebrew chỉ build cho 2 bản này |
| PostGIS | 3.6+ | `brew install postgis` |
| Maven | không cần cài | dùng `./mvnw` kèm theo repo |

Nếu dùng macOS + Homebrew:

```bash
brew install openjdk@21 postgresql@18 postgis
brew services start postgresql@18
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="/opt/homebrew/opt/postgresql@18/bin:$JAVA_HOME/bin:$PATH"
```

## 2. Chuẩn bị database

PostGIS là **extension riêng**, không đi kèm PostgreSQL, nên phải cài và bật thủ công.
Migration `V1` sẽ dừng với thông báo rõ ràng nếu thiếu `postgis` hoặc `btree_gist`.

```bash
psql -d postgres -c "CREATE ROLE courtly WITH LOGIN PASSWORD 'courtly' CREATEDB;"
psql -d postgres -c "CREATE DATABASE courtly OWNER courtly;"

# Hai lệnh dưới cần quyền superuser
psql -d courtly -c "CREATE EXTENSION postgis; CREATE EXTENSION btree_gist;"
psql -d courtly -c "GRANT ALL ON SCHEMA public TO courtly;"
```

## 3. Chạy dự án

```bash
# Chạy kèm seeder dữ liệu mẫu
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run

# Chạy không seed
./mvnw spring-boot:run
```

Ứng dụng chạy ở `http://localhost:8080`, health check tại `/actuator/health`.

Cấu hình đọc từ biến môi trường, xem `.env.example`:

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/courtly` | Chuỗi kết nối |
| `DB_USERNAME` / `DB_PASSWORD` | `courtly` / `courtly` | Tài khoản DB |
| `SERVER_PORT` | `8080` | Cổng HTTP |
| `SEED_ENABLED` | `false` | Bật seeder (profile `dev` tự bật) |
| `JWT_SECRET` | chuỗi dev | Khoá ký HS256, **bắt buộc đổi ở môi trường thật**, tối thiểu 32 ký tự |
| `JWT_EXPIRATION_MINUTES` | `1440` | Thời gian sống của access token |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Origin của frontend được phép gọi API |

## 4. Migration

Flyway chạy tự động khi khởi động, script nằm ở `src/main/resources/db/migration`.
Tổng cộng **49 bảng** chia theo 10 nhóm nghiệp vụ trong `thiet-ke-csdl.text`.

| File | Nhóm | Số bảng |
|---|---|---:|
| `V1__extensions_and_helpers.sql` | Extension, hàm trigger dùng chung | – |
| `V2__account.sql` | Tài khoản, đăng nhập, phân quyền | 11 |
| `V3__venue.sql` | Sân, bản đồ, tìm kiếm | 9 |
| `V4__booking.sql` | Đặt sân | 3 |
| `V5__payment.sql` | Thanh toán SePay, hoàn tiền | 3 |
| `V6__finance.sql` | Doanh thu, phí nền tảng, rút tiền | 4 |
| `V7__match.sql` | Trận đấu, thống kê người chơi | 6 |
| `V8__matching.sql` | Ghép cặp, phân cụm, gợi ý | 7 |
| `V9__review.sql` | Đánh giá, báo cáo | 3 |
| `V10__notification.sql` | Thông báo | 2 |
| `V11__audit.sql` | Nhật ký hệ thống | 1 |
| `V12__playing_style_constraint.sql` | Chuẩn hoá `playing_style`, thêm CHECK | – |
| `V13__venue_search_index.sql` | `pg_trgm` + `unaccent`, index tìm kiếm sân | – |

Vài điểm đáng chú ý trong schema:

- **Chống đặt trùng lịch ở tầng database**, không chỉ kiểm tra bằng code:
  `booking_items` có exclusion constraint trên `(court_id, tstzrange(start_time, end_time))`
  chỉ áp dụng cho bản ghi `pending`/`confirmed`. Cần extension `btree_gist`.
- **Cột `location geography(Point, 4326)`** của `venues` và `player_preferred_locations`
  do trigger `courtly_sync_location` tự sinh từ `latitude`/`longitude`, có GiST index
  để tìm sân quanh vị trí bằng `ST_DWithin`. Ứng dụng chỉ cần set toạ độ.
- **Giá trị các cột trạng thái viết thường** (`pending_payment`, `approved`…), khớp đúng
  `CHECK` constraint. Enum Java dùng hằng viết hoa và được quy đổi qua
  `AbstractEnumConverter`, nên không cần `@Enumerated`.
- Cột `updated_at` được trigger `courtly_set_updated_at` cập nhật, kể cả khi ghi
  trực tiếp bằng SQL.

`spring.jpa.hibernate.ddl-auto=validate`: Hibernate không tạo/sửa schema, chỉ kiểm tra
entity có khớp bảng không. Sai lệch sẽ làm ứng dụng dừng ngay khi khởi động.

## 5. API xác thực

Quy ước chung xem `QUY-TAC-VIET-API.md` ở thư mục gốc.

| Method | Đường dẫn | Quyền | Mô tả |
|---|---|---|---|
| `POST` | `/api/v1/auth/register` | công khai | 2.1.1 — đăng ký, trả `201`, **không** kèm token |
| `POST` | `/api/v1/auth/verify-email` | công khai | 2.1.1 — nhập mã xác minh, kích hoạt tài khoản và cấp token |
| `POST` | `/api/v1/auth/verify-email/resend` | công khai | 2.1.1 — gửi lại mã xác minh |
| `POST` | `/api/v1/auth/login` | công khai | 2.1.2 — đăng nhập bằng email **hoặc** số điện thoại |
| `POST` | `/api/v1/auth/logout` | cần token | 2.1.4 — đăng xuất |
| `GET` | `/api/v1/auth/me` | cần token | Tài khoản đang đăng nhập, dùng dựng lại header sau khi tải lại trang |
| `POST` | `/api/v1/auth/password-reset` | công khai | 2.1.5 bước 1 — gửi mã xác minh tới email |
| `POST` | `/api/v1/auth/password-reset/verify` | công khai | 2.1.5 bước 2 — đổi mã lấy token dùng một lần |
| `POST` | `/api/v1/auth/password-reset/confirm` | công khai | 2.1.5 bước 3 — đặt mật khẩu mới, trả `204` |

### Phiên đăng nhập

Access token JWT (HS256), mặc định sống 24 giờ, **không lưu ở server**. Vì vậy:

- Đăng xuất thực chất là frontend xoá token. Endpoint `/logout` tồn tại để frontend có một
  điểm gọi thống nhất và để sau này thêm thu hồi mà không phải đổi hợp đồng API.
- Token bị lộ vẫn dùng được cho tới khi hết hạn. Muốn thu hồi thật thì cần thêm bảng lưu
  refresh token — đây là đánh đổi đã được chọn có chủ đích.

### Xác minh email khi đăng ký (2.1.1)

Tài khoản mới **không dùng được ngay**. Luồng ba bước:

```
POST /auth/register          -> 201 {email, expiresInSeconds, resendAfterSeconds}
                                users.status = 'pending', KHÔNG có token
POST /auth/verify-email      -> 200 {accessToken, user}
                                users.status = 'active', email_verified_at = now
POST /auth/verify-email/resend -> 200 {email, expiresInSeconds, resendAfterSeconds}
```

**Email là bắt buộc khi đăng ký.** Trước đây chỉ cần email *hoặc* số điện thoại; không có
email thì không gửi được mã. Số điện thoại vẫn tuỳ chọn. Ràng buộc `users_identity_check`
của database không phải đổi vì email-only vẫn thoả.

**Đăng nhập khi chưa xác minh trả `403 EMAIL_NOT_VERIFIED`**, tách riêng khỏi
`ACCOUNT_DISABLED`. Hai trường hợp này cần hai cách xử lý khác nhau ở giao diện: một bên
đưa người dùng sang màn hình nhập mã, một bên bảo họ liên hệ quản trị viên.

**Xác minh xong cấp token luôn**, không bắt đăng nhập lại — người dùng vừa chứng minh mình
sở hữu email đó.

Bảng `email_verification_requests` (migration V14) tách riêng khỏi `password_reset_requests`:
hai luồng có vòng đời khác nhau, gộp chung thì phải thêm cột `purpose` và mọi truy vấn đều
phải nhớ lọc theo cột đó — sớm muộn sẽ quên một chỗ.

| Ngưỡng | Giá trị | Thuộc tính |
|---|---|---|
| Mã sống | 15 phút | `courtly.auth.email-verification.code-ttl-minutes` |
| Số lần nhập sai tối đa | 5, quá thì huỷ mã | `max-attempts` |
| Chờ giữa hai lần gửi | 60 giây | `resend-cooldown-seconds` |
| Số lần gửi tối đa mỗi giờ | 5 | `max-sends-per-hour` |

Hạn mức gửi của luồng này **tách khỏi** luồng quên mật khẩu (`OtpRateLimiter` dùng scope
`"verify"` và `"reset"`): xin lại mã đặt mật khẩu không được làm hết lượt gửi mã xác minh.

### Ràng buộc khi đăng ký

Frontend phải kiểm tra **đúng các điều kiện này** để không xảy ra tình trạng nút bấm được
nhưng server từ chối:

| Trường | Ràng buộc |
|---|---|
| `fullName` | bắt buộc, 2–150 ký tự |
| `email` | **bắt buộc**, đúng định dạng, tối đa 255 ký tự |
| `phone` | tuỳ chọn |
| `phone` | số di động Việt Nam 10 số, đầu `03/05/07/08/09`; server tự chuẩn hoá `+84…` và `84…` về `0…` |
| `password` | 8–72 ký tự, có ít nhất một chữ cái và một chữ số |
| `confirmPassword` | phải khớp `password` |

Đăng ký thành công sẽ tạo `users` + gán vai trò `player` + tạo `player_profiles` rỗng +
một dòng `email_verification_requests`. Tài khoản ở `status = pending` cho tới khi nhập
đúng mã xác minh.

### Quên & đặt lại mật khẩu (2.1.5)

Ba bước, mỗi bước một endpoint. Toàn bộ trạng thái nằm ở bảng `password_reset_requests`.

```
POST /password-reset          {channel:"email", destination}
  -> 200 {expiresInSeconds:600, resendAfterSeconds:60}

POST /password-reset/verify   {destination, code:"123456"}
  -> 200 {resetToken:"<uuid>.<chuỗi ngẫu nhiên>", expiresInSeconds:600}

POST /password-reset/confirm  {resetToken, password, confirmPassword}
  -> 204
```

**Không bước nào được tiết lộ email có tồn tại hay không.** Bước 1 luôn trả về cùng một
kết quả; giới hạn số lần gửi cũng đếm theo địa chỉ người dùng nhập *trước khi* tra cứu tài
khoản — nếu chỉ chặn khi tài khoản có thật thì so sánh phản hồi là biết email nào đã đăng ký.
Bước 2 gộp mọi lý do thất bại (chưa từng xin mã, mã hết hạn, nhập sai) vào cùng
`RESET_CODE_INVALID`.

**Mã gốc không bao giờ được lưu.** Cột `verification_hash` giữ hash BCrypt. Sau khi xác minh
đúng, hash của mã 6 số **bị ghi đè** bằng hash của token đặt mật khẩu: mã cũ chết ngay và
một yêu cầu chỉ đổi được đúng một token. Token có dạng `<id yêu cầu>.<32 byte ngẫu nhiên>`,
dùng một lần — `used_at` là chốt chặn ở database.

| Ngưỡng | Giá trị | Thuộc tính |
|---|---|---|
| Mã sống | 10 phút | `courtly.auth.password-reset.code-ttl-minutes` |
| Token sống sau khi xác minh | 10 phút | `token-ttl-minutes` |
| Số lần nhập sai tối đa | 5, quá thì huỷ mã | `max-attempts` |
| Chờ giữa hai lần gửi | 60 giây | `resend-cooldown-seconds` |
| Số lần gửi tối đa mỗi giờ | 5 | `max-sends-per-hour` |

Bộ đếm số lần nhập sai dùng `@Transactional(noRollbackFor = ApiException.class)`: nếu để
lỗi cuốn trôi transaction thì `attempt_count` không bao giờ tăng và giới hạn thành vô nghĩa.

Bộ đếm số lần gửi nằm **trong bộ nhớ** (`PasswordResetRateLimiter`), đủ cho một instance.
Chạy nhiều instance thì phải chuyển sang Redis hoặc một bảng trong database.

Hiện **chỉ hỗ trợ kênh email**; gửi qua SMS trả `RESET_CHANNEL_UNSUPPORTED` vì chưa có nhà
cung cấp. Cột `channel` đã sẵn cho việc mở rộng.

### Cấu hình gửi email

Thông tin SMTP để ở `backend/.env` (đã nằm trong `.gitignore`, xem `.env.example`).
`application.yml` nạp file này qua `spring.config.import`.

```properties
MAIL_ENABLED=true
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=dia-chi@gmail.com
MAIL_PASSWORD=app-password-16-ky-tu
MAIL_FROM_ADDRESS=dia-chi@gmail.com
MAIL_FROM_NAME=Courtly
```

Gmail bắt buộc dùng **App Password**, không dùng mật khẩu đăng nhập. Đặt `MAIL_ENABLED=false`
thì không gọi SMTP mà ghi mã ra log — dùng khi chạy test hoặc máy chưa cấu hình.

Mail được gửi **sau khi transaction commit và ở luồng khác** (`@TransactionalEventListener`
+ `@Async`): gọi SMTP trong lúc đang giữ transaction ghi database là điều quy tắc API cấm,
và người dùng không nên phải chờ SMTP trả lời. Gửi thất bại chỉ ghi log — người dùng đã nhận
phản hồi từ trước và vẫn bấm "Gửi lại mã" được.

### Mã lỗi

| `code` | HTTP | Khi nào |
|---|---|---|
| `VALIDATION_FAILED` | 400 | Sai dữ liệu đầu vào, kèm `fieldErrors` |
| `EMAIL_ALREADY_EXISTS` | 409 | Email đã được đăng ký |
| `PHONE_ALREADY_EXISTS` | 409 | Số điện thoại đã được đăng ký |
| `INVALID_CREDENTIALS` | 401 | Sai tài khoản **hoặc** sai mật khẩu — cùng một thông báo, không tiết lộ tài khoản nào tồn tại |
| `ACCOUNT_DISABLED` | 403 | Tài khoản bị khoá |
| `EMAIL_NOT_VERIFIED` | 403 | Đăng nhập khi chưa xác minh email — đưa sang màn hình nhập mã |
| `VERIFICATION_CODE_INVALID` | 400 | Mã xác minh sai, hết hạn, hoặc chưa từng đăng ký |
| `VERIFICATION_TOO_MANY_ATTEMPTS` | 429 | Nhập sai quá 5 lần, mã bị huỷ, phải gửi lại |
| `RESET_CHANNEL_UNSUPPORTED` | 400 | Xin gửi mã qua SMS — chưa hỗ trợ |
| `RESET_CODE_INVALID` | 400 | Mã sai, hết hạn, hoặc chưa từng xin mã — gộp chung một mã lỗi |
| `RESET_TOO_MANY_ATTEMPTS` | 429 | Nhập sai quá 5 lần, mã bị huỷ, phải xin mã mới |
| `RESET_TOO_MANY_REQUESTS` | 429 | Gửi lại quá sớm hoặc quá nhiều; `fieldErrors.retryAfterSeconds` cho biết còn phải đợi bao lâu |
| `RESET_TOKEN_INVALID` | 400 | Token đặt mật khẩu sai, hết hạn hoặc đã dùng |
| `PASSWORD_SAME_AS_OLD` | 400 | Mật khẩu mới trùng mật khẩu hiện tại |
| `UNAUTHORIZED` | 401 | Thiếu token hoặc token không hợp lệ |
| `INTERNAL_ERROR` | 500 | Lỗi hệ thống |

### Thử nhanh

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"emailOrPhone":"an.nguyen@example.com","password":"Courtly@123"}'
```

## 6. API hồ sơ người chơi

Đường dẫn dùng `/me`, **không nhận `userId` từ client**, nên không thể đọc hay sửa hồ sơ người khác.

| Method | Đường dẫn | Chức năng |
|---|---|---|
| `GET` | `/api/v1/users/me/profile` | 2.1.6 — xem hồ sơ |
| `PUT` | `/api/v1/users/me/profile` | 2.1.7 — chỉnh sửa hồ sơ |
| `PUT` | `/api/v1/users/me/preferences` | 2.1.8, 2.1.9, 2.1.10, 2.1.11, 2.1.12 — thiết lập chơi |

`GET` trả về năm khối dùng cho cả `/profile` lẫn `/profile/preferences`:

```json
{
  "user": { "id": "...", "fullName": "...", "email": "...", "phone": "...", "roles": ["player"] },
  "profile": { "gender": "male", "dateOfBirth": "1995-03-12", "bio": "...",
               "skillLevel": "advanced", "skillScore": 1420.00,
               "dominantHand": "right", "playingStyle": "attacking", "preferredPlayType": "double" },
  "statistics": { "totalMatches": 4, "totalWins": 1, "winRate": 25.00, "ratingScore": 1372.00 },
  "availability": [{ "dayOfWeek": 2, "startTime": "18:00", "endTime": "21:00" }],
  "preferredLocations": [{ "id": "...", "label": "Gần nhà", "radiusKm": 6.00, "isDefault": true }]
}
```

### Vì sao một endpoint gộp 5 chức năng

Màn hình `/profile/preferences` chỉ có **một nút "Lưu thiết lập"** cho cả trình độ, phong cách,
hình thức chơi, khung giờ và địa điểm. API nhận trọn một lần để tránh lưu dở dang khi
một trong các lời gọi thất bại.

`availability` và `preferredLocations` được **ghi đè toàn bộ danh sách**, không cập nhật từng
dòng — giao diện gửi lên trạng thái mới trọn vẹn, và hai bảng này chưa được bảng nào tham chiếu tới.

### Ràng buộc

| Trường | Ràng buộc |
|---|---|
| `fullName` | bắt buộc, 2–150 ký tự |
| `email` / `phone` | phải có ít nhất một trong hai; báo `409` nếu trùng với **tài khoản khác** |
| `dateOfBirth` | phải ở quá khứ, người chơi từ 10 đến 100 tuổi |
| `bio` | tối đa 240 ký tự (khớp `maxLength` của textarea) |
| `skillLevel` | `beginner` / `intermediate` / `advanced` / `professional` |
| `playingStyle` | `attacking` / `balanced` / `defensive` |
| `preferredPlayType` | `single` / `double` / `mixed` |
| `dominantHand` | `left` / `right` / `both` |
| `availability` | tối đa 7 khung, mỗi thứ **chỉ một khung**, `endTime > startTime`, `dayOfWeek` 1–7 |
| `preferredLocations` | tối đa 10, `radiusKm` 1–30 (khớp thanh trượt), **nhiều nhất một** `isDefault` |

`skillScore` do hệ thống tính từ kết quả trận đấu nên **không có trong request cập nhật**.

### Toạ độ của địa điểm ưu tiên

`preferredLocations` nhận `latitude`/`longitude`; trigger `courtly_sync_location` sẽ dựng
cột `location` (PostGIS) từ đó, nên ghép cặp theo khoảng cách ở Sprint 2 dùng được ngay
bằng `ST_DWithin`.

Giao diện `/profile/preferences#locations` có bản đồ Leaflet để người chơi ghim vị trí
(bấm lên bản đồ, kéo ghim, hoặc bấm "Dùng vị trí hiện tại"). Hai trường này **vẫn nullable** —
địa điểm chưa ghim thì chỉ lọc được theo tên khu vực, và giao diện có cảnh báo rõ điều đó.

Lưu ý khi đọc response: `spring.jackson.default-property-inclusion=non_null` nên các trường
`null` bị **lược khỏi JSON** thay vì trả `null`. Phía frontend phải kiểm tra `== null`
(bắt được cả `undefined`), không dùng `'latitude' in location`.

## 7. API duyệt sân

Các endpoint này **công khai** — xem sân được trước khi đăng nhập.

| Method | Đường dẫn | Chức năng |
|---|---|---|
| `GET` | `/api/v1/venues` | 2.1.13 tìm kiếm · 2.1.14 quanh vị trí · 2.1.15–2.1.17 dữ liệu bản đồ · 2.1.19–2.1.21 bộ lọc |
| `GET` | `/api/v1/venues/districts` | 2.1.19 — khu vực có sân, kèm số lượng |
| `GET` | `/api/v1/venues/{slugOrId}` | 2.1.18, 2.1.23, 2.1.24 — chi tiết sân |
| `GET` | `/api/v1/venues/{slugOrId}/reviews` | 2.1.23 — đánh giá của sân |
| `GET` | `/api/v1/venues/{venueId}/availability` | 2.1.26 — lịch trống theo ngày |
| `POST` | `/api/v1/bookings/quote` | 2.1.27 — tạm tính tiền |

### Tham số tìm kiếm

| Tham số | Ý nghĩa |
|---|---|
| `q` | Tìm theo tên, quận hoặc địa chỉ. **Không phân biệt dấu**: `cau giay` ra `Cầu Giấy` |
| `district` | Lọc theo khu vực |
| `lat` + `lng` | Toạ độ người dùng. Phải gửi **cả hai hoặc không gửi** |
| `radiusKm` | Bán kính, mặc định 5, tối đa 50. Chỉ có tác dụng khi có toạ độ |
| `maxPricePerHour` | Giá mỗi giờ tối đa |
| `minRating` | Điểm đánh giá tối thiểu |
| `availableOnly` | 2.1.22 — chỉ sân hôm nay còn khung giờ trống |
| `page`, `size` | Phân trang, `size` tối đa 100 |

Có toạ độ thì kết quả được lọc trong bán kính, sắp theo khoảng cách tăng dần và mỗi thẻ
sân có thêm `distanceKm`. Không có toạ độ thì sắp theo điểm đánh giá giảm dần.
**API không có tham số `sort`** — thứ tự do ngữ cảnh quyết định, giao diện hiện cũng không
có ô chọn sắp xếp nào.

### Tối ưu

Đây là phần được chú ý nhất khi làm nhóm chức năng này:

| Điểm | Cách xử lý |
|---|---|
| Lọc và phân trang | Làm **toàn bộ ở database**, không tải hết rồi lọc ở tầng ứng dụng |
| Ảnh bìa, số sân con, khoảng giá | `LEFT JOIN LATERAL` trong **cùng một câu truy vấn** — danh sách 6 sân hay 600 sân đều là **1 câu SQL** |
| Chi tiết sân | 5 câu truy vấn **cố định**, không đổi theo số sân con. Trước khi tối ưu là 12 câu (N+1) |
| Lịch trống | 5 câu truy vấn cố định cho cả địa điểm, không phải mỗi sân con một câu |
| Tìm không dấu | `unaccent` + GIN trigram index. `ILIKE '%…%'` thường phải quét toàn bảng |
| Tìm quanh vị trí | `ST_DWithin` trên cột `geography`, dùng GiST index `venues_location_gix` |

Kiểm tra kế hoạch truy vấn:

```sql
SET enable_seqscan = off;   -- với vài chục bản ghi Postgres luôn chọn seq scan
EXPLAIN (COSTS OFF) SELECT id FROM venues
WHERE courtly_unaccent(name) ILIKE courtly_unaccent('%cau giay%');
-- Bitmap Index Scan on venues_name_unaccent_trgm_idx
```

### Lịch trống (2.1.26)

```
GET /api/v1/venues/{venueId}/availability?date=2026-09-25&durationMinutes=90
```

Trả về **tất cả sân con trong một lần gọi**, để người dùng đổi sân con không phải chờ tải lại.

Khung giờ trống = giờ mở cửa của venue − `court_blocks` đang active − `booking_items`
ở trạng thái `pending`/`confirmed`. Đúng tập trạng thái mà exclusion constraint
`booking_items_no_overlap` bảo vệ, nên kết quả khớp với cái database thực sự cho phép.

Mỗi khung giờ có một trong bốn trạng thái:

| Trạng thái | Nghĩa |
|---|---|
| `available` | Đặt được, kèm `totalPrice` |
| `booked` | Đã có người giữ |
| `blocked` | Chủ sân khoá (bảo trì, sự kiện) |
| `past` | Đã trôi qua trong ngày hôm nay |

`past` không có trong mô tả gốc nhưng cần thiết: khung giờ 08:00 của hôm nay lúc 14:00
thì không đặt được, mà lý do khác hẳn "đã có người đặt".

### Tạm tính (2.1.27)

```
POST /api/v1/bookings/quote
{ "courtId": "...", "startTime": "2026-09-25T08:00:00Z", "durationMinutes": 90 }
```

Chỉ nhận 60, 90 hoặc 120 phút. Trả về tổng tiền kèm `segments` — chi tiết từng đoạn giá.

**Một lần đặt có thể vắt qua hai khung giá.** Ví dụ 15:00–16:30 với bảng giá 100.000đ/giờ
trước 16:00 và 140.000đ/giờ sau đó:

```
15:00-16:00  100.000đ/giờ  = 100.000
16:00-16:30  140.000đ/giờ  =  70.000
                    tổng   = 170.000
```

Lấy giá tại giờ bắt đầu rồi nhân lên sẽ ra 150.000 — **sai**. `PricingCalculator` cộng
theo từng đoạn, có 8 test riêng cho việc này.

Giá **luôn tính ở server**. Frontend chỉ hiển thị, không tự nhân. Bước tạo đơn thật sẽ
tính lại một lần nữa chứ không tin số tiền client gửi lên.

### Bộ lọc "còn trống" (2.1.22)

Tính từ lịch thật: lấy cửa sổ mở cửa còn lại của hôm nay, trừ đi **hợp** các khoảng đã bị
giữ, còn khe nào chưa bị phủ thì sân được coi là còn trống.

Dùng `range_agg` của PostgreSQL để gộp các khoảng chồng lấn. Cộng dồn thời lượng sẽ **đếm
trùng** các khoảng giao nhau và cho kết quả sai.

Bộ lọc này chỉ chạy khi `availableOnly=true`, và làm câu truy vấn nặng hơn đáng kể.

### Điều cần biết về `courtCount`

Trường này là **số sân con đang hoạt động**, không phải số sân còn trống. Tính sân trống
cần dựng lịch từ `venue_operating_hours` − `court_blocks` − `booking_items`, đó là 2.1.26.
Giao diện đã đổi nhãn từ "N sân trống" thành "N sân" cho khớp.
Số sân **còn trống** thật có ở API lịch trống (`availableCount` của từng sân con).

## 8. API đặt sân

Tất cả đều **cần đăng nhập**. Đường dẫn không nhận `userId` — lấy từ token, nên không
xem hay huỷ được đơn của người khác.

| Method | Đường dẫn | Chức năng |
|---|---|---|
| `POST` | `/api/v1/bookings` | 2.1.28 — tạo yêu cầu đặt sân |
| `GET` | `/api/v1/bookings` | 2.1.32 — lịch sử, lọc theo `tab` và `q` |
| `GET` | `/api/v1/bookings/{id}` | 2.1.29, 2.1.30 — chi tiết kèm timeline |
| `POST` | `/api/v1/bookings/{id}/cancel` | 2.1.31 — huỷ đặt sân |

### Tạo đơn

```json
POST /api/v1/bookings
{ "courtId": "...", "startTime": "2026-09-30T08:00:00Z", "durationMinutes": 90, "note": "..." }
```

**Không nhận số tiền từ client.** Server tính lại từ `court_price_rules` và kiểm tra lại
khung giờ còn trống trước khi ghi.

Đơn được tạo ở trạng thái `pending_payment` với `expires_at = now + 10 phút`.

### Chống đặt trùng khi có tranh chấp

Hai lớp bảo vệ:

1. Tầng nghiệp vụ kiểm tra khung giờ trước khi ghi → trả `409 BOOKING_SLOT_TAKEN`.
2. Hai request đồng thời vẫn có thể cùng vượt qua lớp 1. Khi đó exclusion constraint
   `booking_items_no_overlap` ở database chặn bản thua, `DataIntegrityViolationException`
   được đổi thành cùng mã `409 BOOKING_SLOT_TAKEN`.

### Tab lịch sử (2.1.32)

| `tab` | Trạng thái |
|---|---|
| `upcoming` | `confirmed` |
| `pending` | `pending_payment` |
| `completed` | `completed` |
| `cancelled` | `cancelled`, `expired`, `refunded` |
| `all` | tất cả |

`q` tìm theo mã đơn hoặc tên sân, **không phân biệt dấu** (dùng `courtly_unaccent`).

### Huỷ đơn (2.1.31)

Chỉ huỷ được khi đơn đang `pending_payment`/`confirmed` **và chưa tới giờ chơi**.
Response có trường `cancellable` — **giao diện dựa vào trường này**, không tự suy từ trạng thái.

Huỷ xong: `booking_items` chuyển `cancelled` nên khung giờ mở lại ngay cho người khác.

### Job tự động hết hạn

`BookingExpiryJob` chạy mỗi phút, đổi đơn quá `expires_at` sang `expired` và giải phóng
khung giờ. **Không có job này thì đơn bỏ dở giữ khung giờ vĩnh viễn.**

Chu kỳ một phút nghĩa là khung giờ có thể bị giữ thêm tối đa một phút sau khi hết hạn.
Tắt bằng `BOOKING_EXPIRY_JOB_ENABLED=false`; test tự tắt qua cấu hình surefire.

### Phí nền tảng không cộng vào hoá đơn người chơi

`bookings.total_amount` = **đúng tiền sân**. Phí nền tảng được trừ vào doanh thu chủ sân
qua `owner_balance_transactions` (loại `platform_fee`), đúng như nhóm 5 của thiết kế và
các chức năng 2.3.18, 2.3.19.

Giao diện prototype trước đây cộng thêm 5.000đ vào tổng người chơi phải trả — đã bỏ.

## 9. Seeder

Chạy khi `courtly.seed.enabled=true`. Gồm 10 bước theo thứ tự `@Order`, **idempotent** —
chạy lại trên database đã có dữ liệu thì bỏ qua, không nhân bản.

| Thứ tự | Seeder | Bảng được nạp |
|---:|---|---|
| 1 | `RoleAndPermissionSeeder` | roles, permissions, role_permissions |
| 2 | `UserSeeder` | users, user_roles, player_profiles, court_owner_profiles, auth_providers, password_reset_requests, player_availability_slots, player_preferred_locations, player_statistics, notification_preferences |
| 3 | `VenueSeeder` | venues, courts, venue_images, services, venue_services, venue_operating_hours, court_price_rules, court_blocks, favorite_venues |
| 4 | `BookingSeeder` | bookings, booking_items, booking_status_history, payments, sepay_webhook_logs, refunds |
| 5 | `FinanceSeeder` | platform_fee_configs, owner_balance_transactions, withdrawal_requests, payout_transactions |
| 6 | `MatchSeeder` | matches, match_players, match_games, match_results, player_statistics, player_rating_history |
| 7 | `MatchingSeeder` | player_match_profiles, algorithm_runs, player_clusters, partner_suggestions, partner_requests, player_connections, double_team_suggestions |
| 8 | `ReviewSeeder` | venue_reviews, partner_reviews, reports |
| 9 | `NotificationSeeder` | notifications |
| 10 | `AuditLogSeeder` | audit_logs |

Toàn bộ số liệu là **hằng số viết sẵn**. Seeder không tính giá, không tính phí nền tảng,
không chạy K-Means hay thuật toán ghép cặp — các kết quả đó chỉ là dữ liệu mẫu để đối chiếu.

### Id ổn định

Id được sinh bằng `SeedIds.of("venue:venue-01")` = `UUID.nameUUIDFromBytes(...)`, nên chạy lại
seeder luôn cho cùng một bộ id. Dữ liệu sân lấy từ `user-web/public/api/home.json`
(`backend/src/main/resources/seed/venues.json`), giữ nguyên `venue-01`…`venue-06` để frontend
dễ chuyển từ mock sang API thật.

### Tài khoản mẫu

Mật khẩu chung: `Courtly@123` (băng BCrypt). **Chỉ dùng ở môi trường dev.**

| Vai trò | Email |
|---|---|
| Quản trị viên | `admin@courtly.vn` |
| Nhân viên sân | `nhan.staff@example.com` |
| Chủ sân | `thang.owner@example.com`, `hieu.owner@example.com`, `mai.owner@example.com` |
| Người chơi | `an.nguyen@example.com` … `lam.bui@example.com` (8 tài khoản) |
| Đăng nhập Google | `huy.ngo@gmail.com` (không có mật khẩu nội bộ) |

### Dữ liệu đặt sân mẫu

7 đơn phủ đủ các trạng thái để test giao diện:

| Mã đơn | Trạng thái đơn | Trạng thái thanh toán |
|---|---|---|
| `CT-20260924-0001` | confirmed | paid |
| `CT-20260924-0002` | pending_payment | pending (còn đếm ngược 10 phút) |
| `CT-20260913-0003` | completed | paid |
| `CT-20260918-0004` | cancelled | refunded (có bản ghi `refunds`) |
| `CT-20260921-0005` | expired | expired |
| `CT-20260925-0006` | confirmed | paid |
| `CT-20260903-0007` | completed | paid |

## 10. Cấu trúc thư mục

```
src/main/java/com/courtly/
├── CourtlyApplication.java
├── common/
│   ├── BaseEntity.java          # khoá chính UUID + created_at
│   ├── AuditedEntity.java       # thêm updated_at
│   ├── GeoSupport.java          # tạo Point SRID 4326
│   ├── enums/                   # 32 enum, giá trị khớp CHECK constraint
│   ├── converter/               # AttributeConverter cho từng enum
│   └── config/SecurityBeans.java # chỉ khai báo PasswordEncoder
├── api/                         # controller + DTO (auth, profile)
├── service/                     # business logic (auth, profile)
├── domain/                      # 49 entity + 49 repository, chia theo 10 nhóm
│   ├── account/ venue/ booking/ payment/ finance/
│   └── match/ matching/ review/ notification/ system/
└── seed/                        # 10 seeder + DatabaseSeeder điều phối
```

### Vì sao entity cài `Persistable`

Id được gán ở tầng ứng dụng (không dùng `@GeneratedValue`) để seeder tạo được id ổn định.
Khi đó Spring Data hiểu nhầm entity là bản ghi cũ và gọi `merge()` thay vì `persist()`,
gây lỗi `StaleObjectStateException` lúc cascade sang entity dùng `@MapsId`.
`BaseEntity` và các entity khoá chính dùng chung đều cài `Persistable` để tránh việc này.

## 11. Lệnh hay dùng

```bash
./mvnw clean compile          # biên dịch
./mvnw test                   # chạy test (cần database đang chạy)
./mvnw clean package          # đóng gói jar

# Tạo lại database từ đầu
psql -d postgres -c "DROP DATABASE IF EXISTS courtly;"
psql -d postgres -c "CREATE DATABASE courtly OWNER courtly;"
psql -d courtly -c "CREATE EXTENSION postgis; CREATE EXTENSION btree_gist;"
```

## 12. Dữ liệu mẫu

`SEED_ENABLED=true` nạp hai lớp dữ liệu:

**Lớp 1 — dữ liệu demo viết tay.** 18 tài khoản, 6 sân, 6 trận đấu. Đủ để xem
giao diện và thử luồng đặt sân. Mọi giá trị là hằng số trong code seeder.

**Lớp 2 — bộ dữ liệu BWF thật** (`BwfDatasetSeeder`). 188 vận động viên và 2.555
trận đấu có thật, lấy từ 105.147 trận do Liên đoàn Cầu lông Thế giới công bố
(2007–2026), nằm ở `data/bwf-match-data`.

| | Lớp 1 | Cộng lớp 2 |
|---|---|---|
| Người chơi có trận | 8 | **196** |
| Trận đấu | 6 | **2.561** |
| Trận mỗi người (trung vị) | 2 | **40** |

Lý do cần: 8 người chơi không đủ để chạy phân cụm K-Means ở Sprint 2 — kết quả
sẽ phản ánh nhiễu chứ không phản ánh trình độ.

### Sinh lại dữ liệu BWF

```bash
python3 data/bwf-to-seed.py
```

Script đọc `data/bwf-match-data/matches.csv`, lọc vòng đấu chính từ 2024, chọn
188 vận động viên nhiều trận nhất, kiểm tra tỷ số đúng luật cầu lông, tính sẵn
điểm Elo và thống kê, rồi ghi ra `src/main/resources/seed/bwf-players.json` và
`bwf-matches.json`.

**Seeder không chạy thuật toán nào** — mọi con số đã tính sẵn trong JSON, đúng
quy tắc ở `QUY-TAC-LAM-CHUC-NANG.md`. Công thức Elo mô tả ở
`thiet-ke-sprint-2-du-lieu-va-hieu-suat.md` mục 2.2.6; khi cài đặt 2.2.6 bằng
Java, kết quả phải trùng với dữ liệu trong JSON — dùng làm bộ kiểm thử đối chiếu.

### Đăng nhập thử tài khoản BWF

Email theo tên, tên miền `@bwf.example` (TLD dành riêng cho tài liệu, không bao
giờ gửi thư thật tới đó). Mật khẩu giống mọi tài khoản mẫu: `Courtly@123`.

```
an.se.young@bwf.example / Courtly@123     ← điểm Elo cao nhất, 61/69 trận thắng
```

### Điều cần biết về bộ BWF

- Toàn vận động viên chuyên nghiệp, **không có người mới chơi**. Dùng để kiểm
  chứng đường ống xử lý và công thức xếp hạng, không đại diện cho người dùng thật.
- `venue_id` để trống: trận diễn ra ở giải đấu thật, không phải ở sân nào trong
  hệ thống. Tên giải và vòng đấu lưu ở `match_results.note`.
- `playing_style`, `date_of_birth`, toạ độ, khung giờ rảnh đều để trống — bộ dữ
  liệu gốc không có, và điền bừa sẽ làm sai kết quả phân cụm.

## 13. Chưa có trong giai đoạn này

- **Thanh toán SePay (2.1.33 → 2.1.42)** — đơn tạo xong dừng ở `pending_payment`
- API cho mọi chức năng ngoài xác thực, hồ sơ, duyệt sân, lịch trống và đặt sân
- **2.1.3 đăng nhập bằng Google** — đã thống nhất tạm bỏ qua
- Gửi mã đặt lại mật khẩu **qua SMS** — chưa có nhà cung cấp, mới hỗ trợ email
- Business logic (tính giá theo khung giờ, dựng lịch trống, xác minh webhook SePay,
  tính phí nền tảng, thuật toán phân cụm và ghép cặp)
- Phân quyền chi tiết theo `permissions` (hiện mới có vai trò trong token)
- Tài liệu OpenAPI / Swagger

Các repository khác vẫn chỉ kế thừa `JpaRepository`; query method chỉ được thêm khi có
chức năng thực sự dùng tới.
