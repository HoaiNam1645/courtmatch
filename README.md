# Courtly — Hệ thống đặt sân cầu lông Đà Nẵng

Nền tảng đặt sân cầu lông trực tuyến kèm ghép cặp người chơi theo trình độ.
Phạm vi hoạt động giới hạn trong thành phố Đà Nẵng.

Bản đang chạy: <https://courtmatch.tech>

| Tầng | Công nghệ |
|---|---|
| Backend | Java 21 · Spring Boot 3.5.5 · Spring Security (JWT) · JPA |
| Database | PostgreSQL 17/18 · PostGIS · Flyway (50 bảng) |
| Frontend | React 19 · Vite 8 · Tailwind 4 · Leaflet |
| Thanh toán | SePay / VietQR |

---

# Cài đặt

Chạy local **không cần tạo file cấu hình nào**. Mọi biến đều có giá trị mặc định
trỏ về máy của bạn. Chỉ cần làm đúng 4 bước dưới.

## Yêu cầu

| Thành phần | Phiên bản | Bắt buộc |
|---|---|---|
| JDK | 21 | ✅ |
| PostgreSQL | 17 hoặc 18 | ✅ |
| PostGIS | 3.4+ | ✅ extension riêng, không đi kèm Postgres |
| Node.js | 20.19+ hoặc 22.12+ | ✅ Vite 8 yêu cầu |
| Maven | — | ❌ dùng `./mvnw` có sẵn trong repo |

## Bước 1 — Cài phụ thuộc

**macOS**

```bash
brew install openjdk@21 postgresql@18 postgis node
brew services start postgresql@18

# Thêm vào ~/.zshrc để khỏi phải set lại mỗi lần mở terminal
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="/opt/homebrew/opt/postgresql@18/bin:$JAVA_HOME/bin:$PATH"
```

**Ubuntu / Debian**

```bash
sudo apt install openjdk-21-jdk postgresql-16 postgresql-16-postgis-3 \
                 postgresql-contrib nodejs npm
```

> PostGIS phải khớp phiên bản Postgres. Cài Postgres 16 thì lấy `postgresql-16-postgis-3`.

## Bước 2 — Tạo database

```bash
psql -d postgres -c "CREATE ROLE courtly WITH LOGIN PASSWORD 'courtly' CREATEDB;"
psql -d postgres -c "CREATE DATABASE courtly OWNER courtly;"

# Hai lệnh này cần quyền superuser — PostGIS không tự bật được bằng tài khoản thường
psql -d courtly -c "CREATE EXTENSION postgis; CREATE EXTENSION btree_gist;"
psql -d courtly -c "GRANT ALL ON SCHEMA public TO courtly;"
```

Các extension còn lại (`pgcrypto`, `citext`) do Flyway tự bật khi chạy.

## Bước 3 — Chạy backend

```bash
cd backend
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

Lần đầu mất khoảng 2–3 phút: Maven tải thư viện, Flyway tạo 50 bảng, seeder nạp
dữ liệu mẫu (xem phần [Dữ liệu mẫu](#dữ-liệu-mẫu)).

Xong thì kiểm tra:

```bash
curl http://localhost:8080/actuator/health     # {"status":"UP"}
```

> Profile `dev` bật sẵn seeder. Muốn chạy không nạp dữ liệu mẫu thì bỏ
> `SPRING_PROFILES_ACTIVE=dev`, chỉ `./mvnw spring-boot:run`.

## Bước 4 — Chạy frontend

Mở terminal thứ hai:

```bash
cd user-web
npm install
npm run dev
```

Mở <http://localhost:5173>.

## Đăng nhập thử

```
Email:    an.nguyen@example.com
Mật khẩu: Courtly@123
```

Mọi tài khoản mẫu đều dùng chung mật khẩu này.

---

# Lỗi hay gặp

| Lỗi | Nguyên nhân | Cách sửa |
|---|---|---|
| `PostGIS chua duoc cai tren PostgreSQL server nay` | Thiếu extension | Chạy lại lệnh `CREATE EXTENSION` ở bước 2 bằng tài khoản superuser |
| `permission denied for schema public` | Postgres 15+ không cho role thường tạo bảng trong `public` | `psql -d courtly -c "GRANT ALL ON SCHEMA public TO courtly;"` |
| `FATAL: database "courtly" does not exist` | Chưa làm bước 2 | — |
| `Validation failed ... wrong column type` | Database cũ còn sót, lệch với entity | Xoá và tạo lại: `dropdb courtly` rồi làm lại bước 2 |
| `dropdb: database is being accessed by other users` | Backend còn đang chạy | Tắt backend (Ctrl-C) trước khi xoá |
| Frontend trắng trang, console báo CORS | Backend chưa chạy hoặc chạy khác cổng 8080 | Kiểm tra `curl localhost:8080/actuator/health` |
| `Unsupported class file major version` | Đang dùng JDK khác 21 | `java -version` phải ra 21, chỉnh lại `JAVA_HOME` |
| Đăng ký xong không nhận được email | Bình thường — mail tắt mặc định | Mã xác minh in ra **log của backend**, tìm dòng bắt đầu bằng `MAIL TAT` |

---

# Dữ liệu mẫu

Seeder nạp sẵn:

- 6 sân cầu lông ở 6 quận Đà Nẵng, kèm giá và lịch hoạt động
- 8 người chơi mẫu, có đơn đặt sân và thanh toán ở nhiều trạng thái
- **188 vận động viên và 2.555 trận đấu có thật** trích từ dữ liệu giải BWF,
  kèm điểm Elo tính sẵn — dùng để chạy thuật toán ghép cặp trên dữ liệu đủ lớn

Seeder **idempotent**: chạy lại lần hai sẽ bỏ qua, không nhân đôi dữ liệu.

Nguồn dữ liệu BWF và cách bóc tách: [`data/NGUON-DU-LIEU-BWF.md`](data/NGUON-DU-LIEU-BWF.md)
và [`data/bwf-to-seed.py`](data/bwf-to-seed.py).

---

# Cấu hình thêm (chỉ khi cần)

Bốn bước trên là đủ để chạy và xem toàn bộ giao diện. Ba tính năng dưới cần cấu
hình thật mới hoạt động — tạo file `backend/.env` theo mẫu
[`backend/.env.example`](backend/.env.example).

| Tính năng | Biến cần điền | Không điền thì sao |
|---|---|---|
| Gửi email thật | `MAIL_ENABLED=true` + `MAIL_HOST` / `MAIL_USERNAME` / `MAIL_PASSWORD` | Mã xác minh in ra log thay vì gửi mail — vẫn đăng ký được |
| Thanh toán SePay | `SEPAY_API_KEY`, `SEPAY_ACCOUNT_NO` | Mã QR vẫn hiện nhưng không ai xác nhận chuyển khoản |
| Đổi cổng frontend | `VITE_API_BASE_URL` trong `user-web/.env.local` | Mặc định trỏ `http://localhost:8080` |

> `.env` và `.env.local` đã nằm trong `.gitignore`. **Không commit.**

---

# Cấu trúc thư mục

```
backend/        Spring Boot — API, migration Flyway, seeder
user-web/       React — giao diện người chơi
data/           Script bóc tách dữ liệu BWF
follow-chuc-nang/   Mô tả cách từng chức năng chạy
```

# Tài liệu

| File | Nội dung |
|---|---|
| [`chuc-nang.text`](chuc-nang.text) | Đặc tả toàn bộ chức năng theo sprint |
| [`thiet-ke-csdl.text`](thiet-ke-csdl.text) | Thiết kế 50 bảng, quan hệ, ràng buộc |
| [`follow-chuc-nang/`](follow-chuc-nang/) | Mô tả chi tiết từng chức năng 2.1.1 → 2.1.42 |
| [`thiet-ke-sprint-2-du-lieu-va-hieu-suat.md`](thiet-ke-sprint-2-du-lieu-va-hieu-suat.md) | Thiết kế phần dữ liệu và xếp hạng người chơi |
| [`QUY-TAC-LAM-CHUC-NANG.md`](QUY-TAC-LAM-CHUC-NANG.md) | Quy trình làm một chức năng mới |
| [`QUY-TAC-VIET-API.md`](QUY-TAC-VIET-API.md) | Chuẩn viết API |
| [`QUY-TAC-TICH-HOP-API.md`](QUY-TAC-TICH-HOP-API.md) | Chuẩn gọi API từ frontend |
| [`backend/README.md`](backend/README.md) | Chi tiết kỹ thuật backend |
