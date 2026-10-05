# Quy tắc khi làm một chức năng mới

Áp dụng cho mọi chức năng trong `chuc-nang.text`.
Khi làm tới tầng API, đọc thêm `QUY-TAC-VIET-API.md`.
Khi frontend tích hợp API, đọc thêm `QUY-TAC-TICH-HOP-API.md`.

## 1. Trước khi viết dòng code nào

- [ ] Tìm đúng mã chức năng trong `chuc-nang.text` (ví dụ `2.1.26`), đọc kỹ phần mô tả.
- [ ] Liệt kê các bảng liên quan, đối chiếu `thiet-ke-csdl.text`.
- [ ] Xem router và dữ liệu giả mà frontend đang dùng (`user-web/public/api/home.json`,
      `sessionStorage`/`localStorage`) để biết API cần trả về hình dạng nào.
- [ ] **Chốt phạm vi**: chức năng này cần tới tầng nào? Nếu chưa rõ thì hỏi, đừng đoán.

## 2. Thứ tự làm — không nhảy cóc

```
migration → entity → repository → service → controller → seeder → kiểm thử
```

Làm xong tầng nào thì chạy `./mvnw compile` tầng đó trước khi sang tầng sau.

## 3. Phạm vi — quy tắc quan trọng nhất

- Chỉ làm đúng phần được yêu cầu. Cần thêm gì ngoài phạm vi thì **hỏi trước**.
- Không tự thêm query method, helper, endpoint hay dependency "cho sau này dùng".
  Code chưa ai gọi là code chết.
- Một chức năng = một nhánh = một PR. Không gộp nhiều chức năng.

## 4. Database

- Migration mới luôn là **file mới** `V{n}__{ten}.sql`. Không sửa file đã chạy —
  Flyway kiểm tra checksum và sẽ báo lỗi.
- Ràng buộc nghiệp vụ đặt ở database, không chỉ kiểm tra bằng code
  (`CHECK`, `UNIQUE`, `EXCLUDE`, `FOREIGN KEY`).
- Giá trị cột trạng thái **viết thường**, `snake_case`, và phải có trong `CHECK` constraint.
- Thời gian dùng `timestamptz`; tiền dùng `numeric(12,2)`; khóa chính dùng `uuid`.
- Truy vấn theo vị trí dùng PostGIS (`ST_DWithin` trên cột `geography` + GiST index),
  không tự lọc latitude/longitude bằng tay.
- Thêm index cho mọi cột dùng để lọc hoặc sắp xếp trong chức năng đang làm.

## 5. Entity

- Kế thừa `BaseEntity` (id + `created_at`) hoặc `AuditedEntity` (thêm `updated_at`).
  Bảng không có cột đó thì viết entity độc lập.
- Mỗi enum mới: tạo trong `common/enums` kèm converter trong `common/converter`,
  giá trị khớp đúng `CHECK` constraint.
- Entity chỉ có field, mapping, getter/setter. **Không chứa business logic.**
- Quan hệ để `FetchType.LAZY`. Chỉ thêm `@OneToMany` ngược khi thật sự cần.
- `ddl-auto: validate` phải pass — sai lệch giữa entity và bảng sẽ làm app không khởi động.

## 6. Phân tầng

| Tầng | Được làm gì | Không được làm gì |
|---|---|---|
| Entity | mapping dữ liệu | tính toán nghiệp vụ |
| Repository | truy vấn | tính toán, gọi service |
| Service | business logic, transaction | nhận/trả entity ra ngoài |
| Controller | validate input, map DTO | business logic, gọi repository |

Controller **không bao giờ** trả entity trực tiếp — luôn qua DTO.

## 7. Seeder

- Dữ liệu là **hằng số viết sẵn**. Seeder không tính giá, không tính phí, không chạy thuật toán.
- Id sinh qua `SeedIds.of("...")` để chạy lại luôn ra cùng bộ id.
- Phải **idempotent**: đầu mỗi seeder kiểm tra `repository.count() > 0` thì bỏ qua.
- Thêm bảng mới thì bổ sung vào seeder tương ứng, đúng thứ tự `@Order`.
- Số liệu giữa các bảng phải nhất quán (tổng tiền, số dư, số trận…).

## 8. Trước khi báo xong

- [ ] `./mvnw clean compile` sạch, không lỗi.
- [ ] Chạy thật `SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run`, app khởi động được.
- [ ] Chạy seeder **hai lần**, lần hai phải bỏ qua hết.
- [ ] Vào `psql` kiểm tra dữ liệu thật, không tin log.
- [ ] Test ràng buộc quan trọng bằng câu lệnh SQL cố tình làm sai, xem có bị chặn không.
- [ ] Báo cáo trung thực: cái gì đã test, cái gì chưa, cái gì còn thiếu.

## 9. Không làm

- Không dùng `ddl-auto: update` hay `create`.
- Không sửa migration đã chạy trên máy người khác.
- Không hardcode secret trong code — đưa vào biến môi trường.
- Không commit dữ liệu thật hoặc mật khẩu thật vào seeder.
- Không bỏ qua lỗi validate của Hibernate bằng cách tắt validate.
