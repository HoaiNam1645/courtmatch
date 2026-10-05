# Courtly — Hệ thống đặt sân cầu lông khu vực Đà Nẵng

Đồ án: nền tảng đặt sân cầu lông trực tuyến kèm ghép cặp người chơi theo trình độ.
Phạm vi hoạt động giới hạn trong **thành phố Đà Nẵng**.

Đang chạy tại <https://courtmatch.tech>

## Công nghệ

| Tầng | Công nghệ |
|---|---|
| Backend | Java 21, Spring Boot 3.5.5, Spring Security (JWT HS256), Spring Data JPA |
| Database | PostgreSQL 16+ với PostGIS, Flyway quản lý 50 bảng nghiệp vụ |
| Frontend | React 19, Vite 8, Tailwind CSS 4, Leaflet |
| Thanh toán | SePay / VietQR (webhook đối soát tự động) |
| Gửi mail | SMTP qua Brevo relay |

## Chạy thử

```bash
# 1. Database
createdb courtly
psql -d courtly -c "CREATE EXTENSION postgis; CREATE EXTENSION pgcrypto;
                    CREATE EXTENSION citext;  CREATE EXTENSION btree_gist;"

# 2. Cấu hình
cp backend/.env.example backend/.env    # rồi điền giá trị thật

# 3. Backend — Flyway tự tạo bảng, SEED_ENABLED nạp dữ liệu mẫu
cd backend && SEED_ENABLED=true ./mvnw spring-boot:run

# 4. Frontend
cd user-web && npm install && npm run dev
```

Tài khoản mẫu: `an.nguyen@example.com` / `Courtly@123`

## Dữ liệu mẫu

Ngoài 6 sân và 8 người chơi tại Đà Nẵng, hệ thống nạp sẵn **188 vận động viên
và 2.555 trận đấu có thật** trích từ dữ liệu giải BWF, kèm điểm Elo tính trước.
Dùng để kiểm thử thuật toán ghép cặp trên dữ liệu đủ lớn thay vì dữ liệu bịa.

Xem `data/bwf-match-data/README.md` để biết nguồn gốc và `data/bwf-to-seed.py`
để biết cách bóc tách.

## Tài liệu

| File | Nội dung |
|---|---|
| `chuc-nang.text` | Đặc tả toàn bộ chức năng theo sprint |
| `thiet-ke-csdl.text` | Thiết kế 50 bảng, quan hệ, ràng buộc |
| `follow-chuc-nang/` | Mô tả cách từng chức năng chạy ở backend và database |
| `QUY-TAC-*.md` | Quy tắc viết chức năng, viết API, tích hợp API |
| `thiet-ke-sprint-2-du-lieu-va-hieu-suat.md` | Thiết kế phần ghép cặp |
