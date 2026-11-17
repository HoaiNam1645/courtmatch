# Courtly - Tai lieu ban giao cho AI Agent

Cap nhat: 13/11/2026

## 1. Tong quan du an

Courtly la nen tang dat san cau long tai Da Nang, ket hop quan ly booking, thanh toan
truc tiep cho chu san, du lieu thi dau, xep hang Elo va ghep cap nguoi choi theo muc do
phu hop.

San pham hien tap trung vao giao dien nguoi choi. He thong chi co **mot chu san duy
nhat**. Tai khoan chu san do quan tri vien tao va cap; khong co chuc nang dang ky chu
san cong khai.

Vai tro chinh:

- `player`: tim san, dat san, thanh toan, quan ly tran dau va tim doi tac.
- `owner`: quan ly san duy nhat, duyet booking, xac nhan thanh toan va thanh toan cong
  no hoa hong.
- `admin`: quan ly he thong, tai khoan chu san, chinh sach phi, tranh chap va doi soat.

Ban production hien tai: `https://courtmatch.tech`.

## 2. Cong nghe

| Tang | Cong nghe |
|---|---|
| Backend | Java 21, Spring Boot 3.5.5, Spring Security JWT, Spring Data JPA |
| Database | PostgreSQL 17/18, PostGIS, Flyway |
| Frontend | React 19, Vite 8, Tailwind CSS 4, Leaflet |
| Test backend | JUnit, Spring Boot Test, MockMvc |
| Test frontend | Playwright, bat buoc chay bang kenh Chrome |

Thu muc chinh:

```text
backend/          Backend Spring Boot, Flyway, entity, API, service, seeder
user-web/         Giao dien React cua nguoi choi
data/             Du lieu BWF va script chuyen doi seed
follow-chuc-nang/ Tai lieu luong chi tiet cua cac chuc nang Sprint 1
```

## 3. Kien truc backend

Ma nguon chinh nam tai `backend/src/main/java/com/courtly`:

```text
api/       Controller va DTO request/response
service/   Nghiep vu va transaction
domain/    Entity va repository theo tung mien nghiep vu
common/    Enum, converter, security config, loi dung chung
seed/      Du lieu mau idempotent
job/       Tac vu qua han va xu ly nen
```

Quy uoc quan trong:

- Controller chi nhan DTO, validate, goi service va tra response.
- Request/response dung DTO rieng, khong tra entity truc tiep.
- `userId` lay tu JWT, khong nhan tu request body.
- Enum Java duoc luu bang gia tri chu thuong trong database qua converter.
- `ddl-auto=validate`; khong duoc dung `update` hoac `create`.
- Migration moi phai tao file Flyway moi, khong sua migration da chay.
- API co tien to `/api/v1` va loi thong nhat gom `code`, `message`, `fieldErrors`,
  `timestamp`.

Database hien co migration den `V23`. Khong duoc tin con so bang trong README cu neu
khong doi chieu migration va `thiet-ke-csdl.text`.

## 4. Kien truc frontend

Frontend duoc chia theo feature, khong dua logic tro lai `App.jsx`:

```text
user-web/src/
  app/             Provider, layout va router tong
  features/        auth, venues, bookings, profile, partners, matches
  api/             Ham goi backend theo resource
  hooks/           Trang thai loading/success/empty/error
  shared/          Tien ich dung chung
```

Khi them chuc nang:

1. Tao page/component trong `features/<feature>`.
2. Dat ham HTTP trong `api/`, khong goi `fetch` truc tiep trong component.
3. Dat server-state dung lai trong `hooks/`.
4. Khai bao route trong file `routes.jsx` cua feature.
5. Giao dien phai xu ly du loading, success, empty va error.

## 5. Nghiep vu thanh toan va hoa hong da chot

Day la phan de nham nhat. Can doc `Vấn đề.docx`, `chuc-nang.text` va
`thiet-ke-csdl.text` truoc khi thay doi.

- Courtly **khong giu tien san** va **khong van hanh vi nguoi choi/chu san**.
- Nguoi choi thanh toan hoac dat coc truc tiep cho chu san.
- Nguoi choi bao da thanh toan; chu san xac nhan da nhan tien.
- Courtly chi luu trang thai, bang chung, tranh chap, hoan tien va cong no hoa hong.
- Hoa hong cua tung booking nam trong `booking_commissions`.
- Cong no duoc tong hop truc tiep tu `booking_commissions` va
  `commission_settlements`; khong tao so cai cong no rieng.
- Khong tao lai `owner_balance_transactions`, `owner_commission_ledger` hoac
  `commission_settlement_items`.
- Booking dang tranh chap chi tam hoan hoa hong cua booking do, khong khoa toan bo cong
  no chu san.

Luu y: README goc van con mot so dong noi ve SePay/VietQR. Nghiep vu hien hanh sau
migration `V15__direct_owner_payment.sql` la thanh toan truc tiep cho chu san; V15 da
loai webhook SePay khoi luong chinh. Phai uu tien code, migration va tai lieu nghiep vu
moi hon thay vi suy dien tu README cu.

## 6. Chinh sach tran dau, Elo va voucher moi chot

Tai lieu `Vấn đề.docx` da chot dinh huong nghiep vu nhung **chua duoc trien khai day
du trong code**:

- Tran gan voi booking da hoan thanh la tran chinh thuc.
- Chi tran chinh thuc moi duoc tinh Elo chinh thuc, thanh tich va dieu kien voucher.
- Tran nhap thu cong chi nen luu tham khao, khong anh huong xep hang chinh thuc.
- Voucher gan voi moc Elo, so tran hop le hoac chuong trinh khuyen khich.
- Cong no qua han co the dan den han che nhan booking moi hoac tam an san.
- Luong moi nguoi dat coc rieng, huy rieng va tim nguoi thay la pham vi mo rong lon,
  chua nen coi la da co san.

Code hien tai van cho phep tao tran thu cong va co the tinh hieu suat/Elo sau khi ket qua
duoc xac nhan. Neu trien khai chinh sach moi, phai cap nhat dong bo tai lieu chuc nang,
database neu can, backend, frontend va test hoi quy.

## 7. Trang thai chuc nang

### Sprint 1 - He thong cot loi

Da co cac nhom chinh:

- Dang ky, xac minh email, dang nhap, dang xuat, quen va dat lai mat khau.
- Ho so nguoi choi, ky nang, phong cach, lich ranh va vi tri uu tien.
- Tim kiem san, bo loc, ban do Leaflet, marker, chi tiet san va lich trong.
- Tao booking, xem lich su/chi tiet/trang thai va huy booking.
- Chu san duyet hoac tu choi booking.
- Thanh toan truc tiep, nguoi choi bao da tra va chu san xac nhan.
- Xu ly qua han, tranh chap, hoan tien va cong no da co nen database/nghiep vu; mot so
  giao dien owner/admin thuoc Sprint 3 chua phai trong tam giao dien hien tai.

### Sprint 2 - Du lieu, ghep cap va tran dau

Da trien khai backend va frontend cho cac nhom:

- `2.2.1-2.2.10`: du lieu tran, lam sach, dac trung, Elo, thong ke va hieu suat.
- `2.2.20-2.2.27`: tim kiem va loc doi tac theo vi tri, thoi gian, ky nang, phong cach.
- `2.2.28-2.2.30`: xep hang/de xuat doi tac co nguon diem va co che fallback. Khong
  duoc mac dinh rang da co mot AI service FastAPI doc lap hoan chinh neu chua kiem tra.
- `2.2.31-2.2.36`: gui, xem, chap nhan, tu choi, huy yeu cau va xem doi tac da ket noi.
- `2.2.37-2.2.42`: tao tran, ghi/xac nhan ket qua, cap nhat Elo, hieu suat, xem ket qua
  va lich su.
- `2.2.43-2.2.45`: cham sao, nhan xet va xem danh gia cong khai cua doi tac.

Du lieu BWF chi la nguon seed de tao du lieu mau, khong phai mot chuc nang nguoi dung.
Seed hien co khoang 188 van dong vien va 2.555 tran BWF.

### Sprint 3

Phan lon la danh sach chuc nang ke hoach: giao dien chu san/admin, doi soat cong no,
thong bao, moderation, tranh chap, danh gia san va toi uu ghep doi. Khong duoc danh dau
la hoan thanh chi vi schema da co bang ho tro.

## 8. API chinh dang co

- `/api/v1/auth`: dang ky, xac minh email, dang nhap, dang xuat, tai khoan hien tai.
- `/api/v1/auth/password-reset`: gui ma, xac minh va doi mat khau.
- `/api/v1/users/me`: ho so va so thich nguoi choi.
- `/api/v1/venues`: tim kiem, quan/huyen, chi tiet, review va lich trong.
- `/api/v1/bookings`: quote, tao, danh sach, chi tiet, huy, owner chap nhan/tu choi.
- `/api/v1/payments`: chi tiet, thanh toan theo booking, bao da tra, xac nhan/tu choi.
- `/api/v1/partners/search`: tim kiem doi tac.
- `/api/v1/partner-requests`: luong loi moi ghep cap.
- `/api/v1/partners/connections`: danh sach doi tac da ket noi.
- `/api/v1/matches`: tao tran, ket qua, xac nhan, tranh chap, huy va lich su.
- `/api/v1/players/me/performance`: thong ke hieu suat va lich su Elo.
- `/api/v1/partner-reviews`: tao danh gia doi tac.
- `/api/v1/players/{playerId}/partner-reviews`: xem danh gia cong khai.

## 9. Quy tac bat buoc truoc khi code

Doc day du ba file sau:

1. `QUY-TAC-LAM-CHUC-NANG.md`
2. `QUY-TAC-VIET-API.md`
3. `QUY-TAC-TICH-HOP-API.md`

Thu tu trien khai mac dinh:

```text
migration -> entity -> repository -> service -> controller -> seeder -> test -> frontend -> E2E
```

Moi chuc nang phai:

- Test tung chuc nang sach truoc khi sang chuc nang tiep theo.
- Co test cho happy path, validate, quyen, trang thai, trung lap va canh tranh neu co.
- Chay Playwright tren Chrome that cho luong frontend.
- Kiem tra mobile khong bi tran ngang neu co giao dien moi.
- Commit rieng theo ten chuc nang bang tieng Viet khi nguoi dung yeu cau commit.
- Khong commit/push/deploy neu nguoi dung chua yeu cau.

## 10. Lenh chay va kiem thu

Backend tren may macOS hien tai can JDK 21:

```bash
cd backend
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw test
SPRING_PROFILES_ACTIVE=dev \
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
./mvnw spring-boot:run
```

Frontend:

```bash
cd user-web
npm install
npm run lint
npm run build
npm run dev
npx playwright test
```

Playwright da cau hinh `channel: chrome`, backend cong `8080` va frontend cong `5173`.

Moc test gan nhat sau chuc nang `2.2.45`:

- Backend: `224/224` test pass.
- E2E lien quan tran dau va danh gia: `11/11` pass tren Chrome.
- Frontend lint va production build pass.

Tai khoan mau:

```text
Email: an.nguyen@example.com
Password: Courtly@123
```

## 11. Tai lieu nguon su that

Uu tien doc theo thu tu:

1. Yeu cau moi nhat cua nguoi dung.
2. `Vấn đề.docx` cho cac quyet dinh nghiep vu thanh toan/Elo/voucher moi nhat.
3. `chuc-nang.text` cho danh sach chuc nang theo sprint.
4. `thiet-ke-csdl.text` va migration Flyway cho database.
5. Ba file quy tac code/API/tich hop.
6. Code va test hien tai.
7. README chi de setup; mot so mo ta nghiep vu co the da cu.

Tai lieu database PDF va proposal co the dung de nop do an, nhung khong nen sua neu
nguoi dung khong yeu cau cu the.

## 12. Git va trang thai worktree tai thoi diem ban giao

Branch: `main`.

Commit gan nhat:

```text
6062c1d 2.2.45. Xem danh gia va nhan xet ve doi tac
3afdac2 2.2.44. Viet nhan xet ve doi tac
f6e76dc 2.2.43. Danh gia doi tac choi
```

Worktree hien co du lieu local cua nguoi dung:

- `chuc-nang.text` dang co thay doi chua commit, gom mo ta cu cua `2.2.20-2.2.27`.
- `.vscode/` chua tracked.
- `C1SE13-Proposal_BPMP_ver1.1.docx` chua tracked.
- `Vấn đề.docx` chua tracked.

Khong duoc xoa, revert, ghi de hoac dua cac file tren vao commit khac neu chua kiem tra
pham vi voi nguoi dung. Neu phat hien thay doi bat ngo khong phai do agent tao, dung lai
va hoi nguoi dung.

## 13. Nguyen tac khi tiep tuc du an

- Khong coi schema co san la chuc nang da hoan thanh; phai co service, API, frontend va
  test theo dung pham vi.
- Khong tu mo rong sang giao dien admin/owner neu yeu cau dang uu tien nguoi choi.
- Khong dua lai mo hinh vi hoac luong Courtly giu tien neu chua co quyet dinh nghiep vu moi.
- Khong sua migration cu; tao migration moi.
- Khong dung localStorage lam nguon du lieu nghiep vu.
- Khong hardcode secret, URL backend hoac du lieu co the lay tu API.
- Bao cao trung thuc test da chay, test chua chay va gioi han con lai.

## 14. Goi y prompt khoi dong cho agent moi

```text
Ban dang lam viec tren du an Courtly tai /Users/mac/Booking.

Hay doc AI-AGENT-HANDOFF.md, QUY-TAC-LAM-CHUC-NANG.md,
QUY-TAC-VIET-API.md va QUY-TAC-TICH-HOP-API.md truoc khi thay doi code.
Sau do kiem tra git status va bao ve moi thay doi local chua commit cua nguoi dung.

He thong hien dung thanh toan truc tiep cho mot chu san duy nhat; Courtly khong giu
tien va khong dung vi. Cong no hoa hong duoc tinh tu booking_commissions va
commission_settlements. Khong tu y thay doi mo hinh nay.

Moi chuc nang can lam tu backend den frontend, test tung chuc nang rieng, va chay
Playwright bang Chrome truoc khi bao hoan thanh. Khong commit, push hoac deploy neu
chua duoc yeu cau.
```
