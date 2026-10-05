# Thiết kế Sprint 2 — Dữ liệu & Hiệu suất Người chơi (2.2.1 → 2.2.10)

Tài liệu thiết kế, chưa phải hướng dẫn cài đặt. Đọc cùng `thiet-ke-csdl.text`
(nhóm 6 và nhóm 7), `QUY-TAC-LAM-CHUC-NANG.md` và `QUY-TAC-VIET-API.md`.

Mười chức năng này là **nền móng của toàn bộ Sprint 2**. Phân cụm (2.2.11 → 2.2.19)
và ghép cặp (2.2.20 → 2.2.34) đều ăn dữ liệu do nhóm này sinh ra. Sai ở đây thì
mọi thứ phía sau sai theo mà không có cách nào phát hiện — thuật toán vẫn chạy,
vẫn ra kết quả, chỉ là kết quả vô nghĩa.

---

## 0. Đọc trước: vấn đề lớn nhất không nằm ở thuật toán

> **Cập nhật:** phần lớn vấn đề nêu ở mục này đã được giải quyết bằng bộ dữ liệu
> BWF (xem mục 0b). Giữ nguyên phần phân tích bên dưới vì nó giải thích **vì sao**
> cần bộ dữ liệu đó, và vì phần đánh giá chất lượng phân cụm vẫn còn thiếu.

Ban đầu, dữ liệu trận đấu trong hệ thống chỉ có:

| Chỉ số | Giá trị |
|---|---|
| Số trận đấu | **6** |
| Số người chơi có trận | **8** |
| Số trận nhiều nhất của một người | **4** |
| Người chơi có tỷ lệ thắng 100% | **2** (một người 2 trận, một người 3 trận) |

**K-Means trên 8 điểm dữ liệu không có ý nghĩa thống kê.** Chạy thì vẫn ra cụm,
vẫn vẽ được biểu đồ, nhưng cụm đó phản ánh nhiễu chứ không phản ánh trình độ.
Với 8 người và k=3, mỗi cụm 2-3 người — đổi một trận đấu là cụm đảo lộn hoàn toàn.

Đây là rủi ro số một của Sprint 2, và nó là **vấn đề dữ liệu, không phải vấn đề
code**. Viết thuật toán hoàn hảo cũng không cứu được.

### Cần bao nhiêu dữ liệu

| Mục đích | Tối thiểu | Nên có |
|---|---|---|
| Người chơi trong tập | 50 | **150 – 300** |
| Số trận mỗi người | 5 | **10 – 20** |
| Tổng số trận | 150 | **1.000 – 3.000** |

Lý do con số: K-Means cần mỗi cụm đủ điểm để tâm cụm ổn định. Với k=4 và mong
muốn ≥30 điểm/cụm thì cần ≥120 người. Còn mỗi người cần ≥10 trận thì điểm Elo
mới thoát khỏi giai đoạn tạm tính (mục 2.2.6).

### Ba cách lấy dữ liệu, và cách nên chọn

**Cách 1 — Thu thập thật từ người dùng.** Chính xác nhất, nhưng cần hàng trăm
người chơi thật trong nhiều tháng. Không khả thi trong phạm vi đồ án.

**Cách 2 — Tìm bộ dữ liệu công khai.** Dữ liệu cầu lông phong trào công khai gần
như không có. Dữ liệu thi đấu chuyên nghiệp (BWF) có nhưng lệch hoàn toàn: toàn
vận động viên trình độ cao, không có người mới chơi, không có toạ độ hay khung
giờ rảnh.

**Cách 3 — Sinh dữ liệu mô phỏng có cấu trúc biết trước.** ← **Khuyến nghị**

Đây không phải cách chống chế. Với một đề tài nghiên cứu, nó **tốt hơn** dữ liệu
thật ở một điểm quyết định: bạn biết trước câu trả lời đúng.

Quy trình:

```
1. Sinh N = 200 người chơi, mỗi người thuộc một trong k = 4 nhóm trình độ ẩn
   (mới chơi / trung bình / khá / giỏi), mỗi nhóm có một mức kỹ năng thật µ
   và độ lệch σ. Đây là NHÃN THẬT, cất riêng, thuật toán không được nhìn thấy.

2. Mô phỏng 2.000 trận. Xác suất đội 1 thắng tính từ chênh lệch kỹ năng thật
   theo đúng công thức Elo (mục 2.2.6). Tỷ số từng set sinh sao cho hợp luật
   cầu lông (mục 2.2.4).

3. Chạy toàn bộ pipeline 2.2.3 → 2.2.8 trên dữ liệu đó như dữ liệu thật.

4. Chạy phân cụm (2.2.13, 2.2.15), rồi SO NHÃN CỤM VỚI NHÃN THẬT ở bước 1
   bằng Adjusted Rand Index hoặc Normalized Mutual Information.
```

Bước 4 chính là thứ biến "tôi có chạy K-Means" thành "tôi chứng minh được K-Means
khôi phục đúng 87% cấu trúc trình độ tiềm ẩn". Đó là bằng chứng nghiên cứu, và
chỉ có dữ liệu mô phỏng mới cho bạn thứ đó — với dữ liệu thật thì không ai biết
nhãn đúng là gì để mà so.

**Đề xuất thực hiện:** làm cả hai. Dữ liệu mô phỏng để đánh giá thuật toán và
viết vào báo cáo; dữ liệu thật (dù ít) để chứng minh hệ thống chạy được đầu-cuối.
Cột `matches.data_source` ở mục 8 dùng để tách hai nguồn này.

---

## 0b. Đã có: bộ dữ liệu BWF thật trong seeder

Nhận định ở mục 0 rằng "dữ liệu cầu lông phong trào công khai gần như không có"
vẫn đúng, nhưng dữ liệu **thi đấu** thì có, và đã được đưa vào hệ thống.

### Nguồn
`data/bwf-match-data` — 105.147 trận do Liên đoàn Cầu lông Thế giới công bố,
từ 01/2007 đến 07/2026, có tỷ số từng set và đội thắng.

### Đã bóc tách vào seeder

Script `data/bwf-to-seed.py` lọc và chuyển thành hai file
`backend/src/main/resources/seed/bwf-players.json` và `bwf-matches.json`, được
`BwfDatasetSeeder` nạp khi chạy `SEED_ENABLED=true`:

| Chỉ số | Trước | Sau |
|---|---|---|
| Người chơi có trận | 8 | **196** |
| Trận đấu | 6 | **2.561** |
| Trận mỗi người (trung vị) | 2 | **40** |
| Dòng lịch sử điểm | 20 | **7.912** |

Đối chiếu với ngưỡng đặt ra ở mục 0: cần 150–300 người, mỗi người 10–20 trận.
Bộ này cho **188 người, trung vị 40 trận** — vượt yêu cầu.

Cách chọn mẫu: chỉ lấy vòng đấu chính (bỏ vòng loại vì chênh lệch trình độ quá
lớn làm nhiễu Elo), từ 2024 trở đi, chọn 188 vận động viên nhiều trận nhất, rồi
**chỉ giữ những trận mà cả hai đội đều nằm trong tập đó**. Bước cuối quan trọng:
nó tạo ra một đồ thị đối đầu dày đặc, nhờ vậy điểm Elo của mọi người đều so sánh
được với nhau thay vì bị chia thành nhiều nhóm rời rạc.

### Phân bố điểm Elo thu được

```
715 – 828    ██                13 người
832 – 958    █████████         61 người
960 – 1086   ███████████       73 người
1096 – 1210  █████             34 người
1224 – 1330  █                  7 người
```

Dạng hình chuông, không dồn cục — đúng thứ K-Means cần.

Một dấu hiệu kiểm chứng: người có điểm Elo cao nhất là **AN Se Young**, đúng là
tay vợt nữ số 1 thế giới ngoài đời. Công thức Elo ở mục 2.2.6 được áp lên dữ liệu
thật mà xếp đúng thứ hạng thực tế — đó là bằng chứng công thức chạy đúng.

### Vẫn còn thiếu gì

Bộ BWF **không thay thế được** dữ liệu mô phỏng ở mục 0, vì hai lý do:

1. **Toàn vận động viên chuyên nghiệp.** Không có người mới chơi. Cụm tìm được sẽ
   là "giỏi" và "rất giỏi", không phải "mới chơi" đến "giỏi" như người dùng thật
   của Courtly.

2. **Không có nhãn đúng để đối chiếu.** Không ai biết 188 vận động viên này "thực
   sự" thuộc mấy nhóm trình độ, nên không đo được Adjusted Rand Index. Vẫn không
   trả lời được câu "K-Means phân cụm đúng bao nhiêu phần trăm".

Ngoài ra bộ này thiếu `playing_style`, `date_of_birth`, toạ độ và khung giờ rảnh
— những cột đó để trống, đúng nguyên tắc "không bịa dữ liệu" ở mục 2.2.4.

**Kết luận:** dùng BWF để kiểm chứng đường ống xử lý, công thức Elo, thống kê và
giao diện lịch sử trận đấu. Vẫn cần sinh thêm dữ liệu mô phỏng có nhãn biết trước
cho phần đánh giá chất lượng phân cụm.

---

## 1. Bức tranh tổng thể

Mười chức năng xếp thành một dây chuyền. Mũi tên là chiều phụ thuộc dữ liệu:

```
      NGUỒN VÀO                        XỬ LÝ                       ĐẦU RA
 ┌─────────────────┐
 │ 2.2.1 Thu thập  │──┐
 │  (từ booking)   │  │
 └─────────────────┘  │   ┌──────────────────┐
 ┌─────────────────┐  ├──►│ matches          │
 │ 2.2.2 Nhập tay  │──┤   │ match_players    │
 │  (người dùng)   │  │   │ match_games      │
 └─────────────────┘  │   │ match_results    │
 ┌─────────────────┐  │   └────────┬─────────┘
 │ Dữ liệu mô phỏng│──┘            │
 └─────────────────┘               ▼
                          ┌──────────────────┐
                          │ 2.2.4 Làm sạch   │  loại trận sai luật,
                          │                  │  thiếu kết quả, trùng
                          └────────┬─────────┘
                                   ▼
              ┌────────────────────┼────────────────────┐
              ▼                    ▼                    ▼
     ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
     │ 2.2.6 Điểm Elo  │  │ 2.2.7 Tỷ lệ     │  │ 2.2.8 Thống kê  │
     │ rating_score    │  │      thắng      │  │ điểm TB, chuỗi  │
     └────────┬────────┘  └────────┬────────┘  └────────┬────────┘
              └────────────────────┼────────────────────┘
                                   ▼
                          ┌──────────────────┐
                          │ player_statistics│ ← bảng đọc nhanh
                          └────────┬─────────┘
                                   │
              ┌────────────────────┼────────────────────┐
              ▼                    ▼                    ▼
     ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
     │ 2.2.9 Lịch sử   │  │ 2.2.10 Thống kê │  │ 2.2.3 Tiền xử lý│
     │      trận đấu   │  │      hiệu suất  │  │ 2.2.5 Đặc trưng │
     │   (giao diện)   │  │   (giao diện)   │  └────────┬────────┘
     └─────────────────┘  └─────────────────┘           ▼
                                              ┌──────────────────┐
                                              │player_match_     │
                                              │  profiles        │
                                              └────────┬─────────┘
                                                       ▼
                                          Sprint 2 phần sau: 2.2.11+
```

### Hai loại chức năng, đừng lẫn

| Loại | Gồm | Đặc điểm |
|---|---|---|
| **Có API và giao diện** | 2.2.2, 2.2.9, 2.2.10 | Người dùng nhìn thấy và thao tác |
| **Chạy nền, không giao diện người dùng** | 2.2.1, 2.2.3 → 2.2.8 | Job hoặc hàm nội bộ; chỉ admin có nút chạy tay |

Lẫn hai loại này dẫn tới thiết kế sai: ví dụ làm endpoint công khai
`POST /api/v1/ratings/calculate` cho 2.2.6 là mở đường cho người dùng tự gọi và
làm hỏng dữ liệu.

---

## 2.2.1 — Thu thập bộ dữ liệu trận đấu

### Mục đích
Đưa dữ liệu trận đấu vào hệ thống từ mọi nguồn, chuẩn hoá về cùng một hình dạng
trong bốn bảng `matches` / `match_players` / `match_games` / `match_results`.

### Ba nguồn

| Nguồn | `data_source` | Cách vào hệ thống |
|---|---|---|
| Đơn đặt sân đã hoàn thành | `booking` | Hệ thống gợi ý tạo trận sau khi đơn `completed` |
| Người dùng tự nhập | `manual` | Chức năng 2.2.2 |
| Dữ liệu mô phỏng / nhập khẩu | `simulated` | Lệnh CLI hoặc seeder riêng, chỉ chạy ở môi trường phát triển |

### Từ đơn đặt sân → trận đấu

Đây là nguồn dữ liệu chất lượng cao nhất vì thời gian, địa điểm và ít nhất một
người chơi đã được hệ thống xác thực.

```
booking.status = 'completed'
        │
        ▼
Gợi ý người đặt: "Bạn vừa chơi ở Sân Cầu Lông Minh Khai. Ghi lại kết quả?"
        │
        ▼
Tạo matches (booking_id, venue_id, scheduled_at = booking_items.start_time,
             data_source = 'booking', status = 'scheduled')
        │
        ▼
Người đặt thêm đối thủ và đồng đội → match_players
        │
        ▼
Nhập tỷ số → 2.2.2
```

**Quyết định thiết kế:** KHÔNG tự động tạo trận khi đơn hoàn thành. Nhiều đơn đặt
sân chỉ là tập luyện, không có trận nào. Tự tạo sẽ đẻ ra hàng loạt bản ghi rỗng
làm bẩn thống kê. Chỉ gợi ý, người dùng chủ động bấm.

### Bảng bị đụng
Chỉ `matches` (thêm dòng). Người chơi và tỷ số thuộc 2.2.2.

### Điểm cần lưu ý
- `booking_id` và `venue_id` đều nullable — trận nhập tay có thể không gắn đơn nào.
- `scheduled_at` nullable nhưng **nên bắt buộc với trận đã hoàn thành**: không có
  thời điểm thì không tính được độ mới của dữ liệu (2.2.5) và không sắp xếp được
  lịch sử (2.2.9).

---

## 2.2.2 — Nhập dữ liệu trận đấu

### Mục đích
Cho người chơi ghi lại một trận: ai đánh với ai, tỷ số từng set, đội nào thắng.

Đây là chức năng **quan trọng nhất về mặt chất lượng dữ liệu** trong cả nhóm. Mọi
con số phía sau đều bắt nguồn từ đây.

### Luồng có xác nhận hai phía

Vấn đề: nếu để một người tự nhập kết quả và hệ thống tin ngay, hai người có thể
bắt tay nhau tạo trận giả để đẩy điểm. Hoặc đơn giản là nhập nhầm mà không ai
sửa được.

```
Bước 1  Người A tạo trận, thêm người chơi, nhập tỷ số
        matches.status = 'scheduled'
        match_results.confirmed_at = NULL
        → CHƯA tính điểm, CHƯA vào thống kê

Bước 2  Hệ thống gửi thông báo cho tất cả người chơi còn lại
        "Nguyễn Văn A ghi nhận bạn thua 1-2 ở trận ngày 28/09. Xác nhận?"

Bước 3  Mỗi người bấm Xác nhận hoặc Báo sai
        → match_players.result_confirmed_at

Bước 4  Đủ điều kiện xác nhận → matches.status = 'completed'
                                match_results.confirmed_at = now
        → LÚC NÀY mới tính điểm và cập nhật thống kê
```

### Điều kiện xác nhận

**Quy tắc đề xuất:** mỗi đội phải có **ít nhất một người** xác nhận, và người tạo
trận được tính là đã xác nhận.

| Loại trận | Cần thêm bao nhiêu người xác nhận |
|---|---|
| Đơn (2 người) | 1 — chính là đối thủ |
| Đôi (4 người) | 1 — bất kỳ ai ở đội đối phương |

Vì sao không đòi tất cả xác nhận: đôi nam nữ 4 người, chỉ cần một người lười bấm
là trận treo mãi. Đòi một người mỗi đội đã đủ chặn việc tự bịa trận.

**Tự động xác nhận sau 72 giờ.** Không có mốc này thì dữ liệu kẹt vô hạn. Ghi rõ
trong `match_results.note` là xác nhận tự động, và **không tính vào rating** nếu
có người đã bấm Báo sai.

### Báo sai thì sao

`matches.status = 'disputed'`. Trận bị tranh chấp:
- Không tính vào rating, không vào thống kê.
- Người tạo sửa lại tỷ số → quay về bước 2, đặt lại mọi `result_confirmed_at`.
- Quá 7 ngày không giải quyết → `cancelled`, loại khỏi mọi tính toán.

### API

| Method | Đường dẫn | Mô tả |
|---|---|---|
| `POST` | `/api/v1/matches` | Tạo trận, kèm danh sách người chơi |
| `PUT` | `/api/v1/matches/{id}/games` | Nhập hoặc sửa tỷ số các set |
| `POST` | `/api/v1/matches/{id}/confirm` | Xác nhận kết quả |
| `POST` | `/api/v1/matches/{id}/dispute` | Báo sai kết quả |
| `DELETE` | `/api/v1/matches/{id}` | Huỷ trận, chỉ người tạo và chỉ khi chưa xác nhận |

Ví dụ thân request tạo trận:

```json
{
  "bookingId": null,
  "venueId": "…",
  "matchType": "double",
  "scheduledAt": "2026-09-28T12:00:00Z",
  "team1UserIds": ["…", "…"],
  "team2UserIds": ["…", "…"],
  "games": [
    { "gameNo": 1, "team1Score": 21, "team2Score": 18 },
    { "gameNo": 2, "team1Score": 19, "team2Score": 21 },
    { "gameNo": 3, "team1Score": 21, "team2Score": 17 }
  ]
}
```

**Không nhận `winningTeam` từ client.** Đội thắng suy ra từ tỷ số các set — nhận
từ client là mở đường cho dữ liệu mâu thuẫn (tỷ số nói đội 1 thắng, trường
`winningTeam` nói đội 2).

### Bảng bị đụng

```
matches          [THÊM] booking_id, venue_id, match_type, scheduled_at,
                        status='scheduled', created_by, data_source
match_players    [THÊM n dòng] match_id, user_id, team_no, position_no,
                        joined_status='joined'
match_games      [THÊM n dòng] match_id, game_no, team1_score, team2_score
match_results    [THÊM 1 dòng] match_id, winning_team (SUY RA), recorded_by,
                        confirmed_at=NULL
```

### Validate — ba lớp theo `QUY-TAC-VIET-API.md`

**Lớp 1 (DTO):** `matchType` thuộc danh sách; số người mỗi đội khớp loại trận
(đơn 1-1, đôi 2-2); `gameNo` từ 1 đến 3; điểm là số nguyên không âm; `scheduledAt`
không ở tương lai khi nhập kết quả.

**Lớp 2 (service):** toàn bộ mục 2.2.4 dưới đây.

**Lớp 3 (database):** `match_players_unique (match_id, user_id)` chặn một người
xuất hiện hai lần; `match_games_unique (match_id, game_no)` chặn trùng set.

---

## 2.2.3 — Tiền xử lý bộ dữ liệu người chơi

### Mục đích
Chuyển từ **dữ liệu theo trận** sang **dữ liệu theo người chơi** — hình dạng mà
thuật toán phân cụm cần.

Đây là bước đổi trục. Trước bước này, một dòng = một trận. Sau bước này, một dòng
= một người chơi kèm vector đặc trưng.

### Đầu vào → đầu ra

```
matches ⋈ match_players ⋈ match_games ⋈ match_results
        (n dòng, mỗi dòng một lượt tham gia)
                    │
                    ▼  gom nhóm theo user_id
        player_statistics  (1 dòng / người, số liệu thô)
                    │
                    ▼  + player_profiles + player_preferred_locations
                       + player_availability_slots
        player_match_profiles  (1 dòng / người, vector đặc trưng)
```

### Chạy khi nào

| Bảng | Cách cập nhật | Lý do |
|---|---|---|
| `player_statistics` | **Tăng dần**, ngay khi một trận được xác nhận | Người dùng mở hồ sơ là thấy số mới ngay |
| `player_match_profiles` | **Chạy lô**, theo lịch hoặc trước mỗi lần phân cụm | Chỉ thuật toán dùng, không cần tức thời |

Hai cách khác nhau vì hai mục đích khác nhau. Nhưng cập nhật tăng dần có một rủi
ro: chỉ cần một lần lỗi giữa chừng là số liệu lệch vĩnh viễn và không ai biết.

**Bắt buộc có job đối soát.** Mỗi đêm tính lại toàn bộ `player_statistics` từ đầu
và so với giá trị đang lưu. Lệch thì ghi log cảnh báo và sửa lại. Không có job này
thì không có cách nào phát hiện dữ liệu đã trôi.

### Chỉ dùng trận hợp lệ

```sql
WHERE m.status = 'completed'
  AND mr.confirmed_at IS NOT NULL
  AND m.excluded_from_rating IS NOT TRUE
```

---

## 2.2.4 — Làm sạch dữ liệu bị thiếu & không hợp lệ

### Mục đích
Chặn dữ liệu rác trước khi nó ngấm vào thống kê. Rác ở đây không phải lỗi kỹ
thuật mà là **dữ liệu đúng cú pháp nhưng sai luật cầu lông**.

### Luật tính điểm cầu lông — cơ sở của mọi kiểm tra

| Quy tắc | Nội dung |
|---|---|
| Điểm thắng set | 21 |
| Cách biệt | Phải hơn ít nhất 2 điểm |
| Điểm trần | 30 — đạt 30 là thắng dù chỉ hơn 1 điểm (30-29) |
| Số set | Thắng 2 trong 3 |

Từ đó suy ra **tập tỷ số hợp lệ của một set**:

```
(21, x)   với 0 ≤ x ≤ 19
(x, 21)   với 0 ≤ x ≤ 19
(y, y-2)  với 22 ≤ y ≤ 30      ← giai đoạn giành cách biệt 2 điểm
(y-2, y)  với 22 ≤ y ≤ 30
(30, 29) và (29, 30)            ← chạm trần
```

Ví dụ **không hợp lệ**: `21-20` (chưa đủ cách biệt), `25-20` (hơn 5 điểm nhưng
vượt 21 — không thể xảy ra), `31-29` (vượt trần), `21-21` (hoà).

### Danh sách kiểm tra đầy đủ

| # | Kiểm tra | Xử lý khi vi phạm |
|---|---|---|
| 1 | Tỷ số từng set thuộc tập hợp lệ ở trên | Từ chối ngay khi nhập (400) |
| 2 | Số set từ 2 đến 3 | Từ chối |
| 3 | Đội thắng phải thắng đúng 2 set | Từ chối |
| 4 | Nếu đã có đội thắng 2 set thì không được có set thứ 3 | Từ chối |
| 5 | Một người không được xuất hiện ở cả hai đội | Từ chối (ràng buộc DB đã chặn trùng trong cùng trận) |
| 6 | Số người mỗi đội khớp `match_type` | Từ chối |
| 7 | Đôi nam nữ: mỗi đội một nam một nữ | **Cảnh báo, không chặn** — `player_profiles.gender` cho phép NULL |
| 8 | `scheduled_at` không ở tương lai khi trận đã hoàn thành | Từ chối |
| 9 | Hai người chơi với nhau quá 20 trận trong 7 ngày | Đánh dấu nghi vấn, loại khỏi rating |
| 10 | Trận không có `match_results` sau 7 ngày | Chuyển `cancelled`, loại khỏi mọi tính toán |
| 11 | Người chơi có < 3 trận | Giữ trong thống kê, **loại khỏi phân cụm** |

Kiểm tra 9 là chống gian lận: hai người tạo trận giả liên tục để đẩy điểm cho
nhau. 20 trận/tuần với cùng một đối thủ là bất thường với người chơi phong trào.

Kiểm tra 11 quan trọng cho Sprint 2 phần sau: người 1 trận có vector đặc trưng
gần như toàn nhiễu, đưa vào K-Means sẽ kéo lệch tâm cụm.

### Dữ liệu thiếu — xử lý từng cột

| Cột thiếu | Cách xử lý | Lý do |
|---|---|---|
| `playing_style` | Gán `balanced` khi phân cụm | Giá trị giữa, ít làm lệch nhất |
| `skill_level` | Suy từ `rating_score` theo mốc | Có rating thì không cần người dùng tự khai |
| `preferred_location` | Để trống | Người này không lọc được theo khoảng cách, nhưng vẫn phân cụm được theo kỹ năng |
| `date_of_birth` | Bỏ qua | Không dùng làm đặc trưng |
| `avg_points_scored/conceded` | 0 khi chưa có trận | Không dùng NULL, giao diện phải hiện được số |

**Nguyên tắc chung: không bịa dữ liệu.** Điền giá trị trung bình vào chỗ thiếu
làm người đó trông giống người trung bình, và thuật toán sẽ gom họ vào cụm giữa
một cách sai lệch. Thà đánh dấu thiếu và loại khỏi bước phân cụm.

### Nơi đặt code

Tách hẳn một lớp `MatchDataValidator` trong `service/match/`. Kiểm tra 1-8 gọi
từ API lúc nhập (2.2.2); kiểm tra 9-11 chạy trong job tiền xử lý (2.2.3). Không
rải logic luật cầu lông vào nhiều chỗ.

---

## 2.2.5 — Trích xuất các đặc trưng kỹ năng

### Mục đích
Biến mỗi người chơi thành một vector số để thuật toán so sánh được.

### Phân biệt hai loại thuộc tính — đây là quyết định thiết kế quan trọng nhất của mục này

| Loại | Gồm | Dùng ở đâu |
|---|---|---|
| **Đặc trưng phân cụm** | Kỹ năng, kinh nghiệm, phong cách | K-Means / DBSCAN (2.2.13, 2.2.15) |
| **Điều kiện lọc** | Vị trí, khung giờ rảnh | Lọc cứng lúc ghép cặp (2.2.21, 2.2.22) |

**Không đưa toạ độ và khung giờ vào vector phân cụm.** Lý do: khoảng cách Euclid
giữa "kỹ năng 1500" và "vĩ độ 10.73" không có ý nghĩa gì. Trộn hai loại đại lượng
không cùng bản chất vào một phép đo khoảng cách là sai lầm kinh điển khi dùng
K-Means, và kết quả sẽ là các cụm phản ánh địa lý chứ không phản ánh trình độ.

Đúng cách: phân cụm **chỉ theo kỹ năng**, rồi khi ghép cặp thì lọc trong cụm bằng
vị trí và thời gian.

Bảng `player_match_profiles` chứa cả hai loại — `preferred_location` và
`availability_score` nằm đó cho bước ghép cặp, **không phải** cho bước phân cụm.

### Vector đặc trưng đề xuất

| # | Đặc trưng | Nguồn | Kiểu | Ghi chú |
|---|---|---|---|---|
| f1 | `rating_score` | 2.2.6 | Liên tục | Tín hiệu mạnh nhất |
| f2 | `win_rate_smoothed` | 2.2.7 | Liên tục 0-1 | **Đã làm trơn**, không dùng tỷ lệ thô |
| f3 | `experience` = ln(1 + total_matches) | 2.2.8 | Liên tục | Lấy log vì khác biệt 2→10 trận có ý nghĩa hơn 100→108 |
| f4 | `avg_point_diff` = scored − conceded | 2.2.8 | Liên tục | Thắng sát nút khác thắng áp đảo |
| f5 | `consistency` = độ lệch chuẩn chênh lệch điểm | 2.2.8 | Liên tục | Người ổn định vs người thất thường |
| f6 | `recency` = exp(−days_since_last / 90) | 2.2.8 | 0-1 | Người nghỉ lâu thì dữ liệu kém tin cậy |
| f7-f9 | `playing_style` one-hot | `player_profiles` | Nhị phân | attacking / balanced / defensive |

Chín chiều. Đủ giàu để phân biệt, đủ ít để K-Means không rơi vào hiện tượng mọi
điểm đều cách xa nhau như nhau ở số chiều cao.

**Không đưa vào vector:** `total_wins`, `total_losses` (đã gói trong f2, f3 — đưa
thêm là tính trùng một thông tin ba lần, làm nó nặng hơn thực tế), tuổi, giới tính
(không phải thước đo kỹ năng, và dùng để phân nhóm là có vấn đề về công bằng).

### Chuẩn hoá
Thuộc 2.2.12, không làm ở đây. Nhưng phải nhớ: f1 khoảng 800-2000, f2 khoảng 0-1.
Không chuẩn hoá thì `rating_score` áp đảo hoàn toàn mọi đặc trưng khác và K-Means
thực chất chỉ đang chia theo rating.

### Bảng bị đụng
`player_match_profiles` — ghi đè toàn bộ dòng của người chơi mỗi lần chạy lô.

---

## 2.2.6 — Tính toán điểm xếp hạng kỹ năng

### Mục đích
Một con số duy nhất thể hiện trình độ, tự điều chỉnh theo kết quả thi đấu.

### Chọn Elo — và vì sao

| Phương án | Ưu | Nhược | Kết luận |
|---|---|---|---|
| **Elo** | Đơn giản, giải thích được, kiểm chứng suốt 60 năm | Không mô hình hoá độ bất định | **Chọn** |
| Glicko-2 | Có thêm độ lệch RD và biến động | Phức tạp hơn nhiều, khó giải thích trong báo cáo | Ghi nhận là hướng mở rộng |
| TrueSkill | Thiết kế sẵn cho nhiều người/đội | Cần thư viện ngoài, toán Bayes nặng | Quá mức cần thiết |

Với một đồ án, **giải thích được quan trọng hơn tinh vi**. Elo viết được trong 20
dòng, vẽ được ra giấy khi bảo vệ, và người dùng hiểu được vì sao điểm mình tăng.

### Công thức

Điểm kỳ vọng của đội 1:

```
E₁ = 1 / (1 + 10^((R₂ − R₁) / 400))
```

Điểm mới:

```
R' = R + K × (S − E)

S = 1 nếu thắng, 0 nếu thua
```

Ý nghĩa `400`: chênh 400 điểm thì bên mạnh có xác suất thắng ≈ 91%. Giữ nguyên
hằng số chuẩn của Elo, không tự chế.

### Áp dụng cho đánh đôi

Cầu lông phần lớn là đánh đôi, mà Elo gốc dành cho một-đấu-một. Cách xử lý:

```
R_đội = (R_a + R_b) / 2          ← điểm đội là trung bình hai thành viên

Δ = K × (S − E_đội)              ← tính một lần cho cả đội

R_a' = R_a + Δ
R_b' = R_b + Δ                   ← cả hai nhận cùng một mức thay đổi
```

Tổng điểm toàn hệ thống được bảo toàn: đội thắng cộng 2Δ, đội thua trừ 2Δ.

**Hạn chế phải nói rõ trong báo cáo:** cách này không tách được đóng góp của từng
người. Người yếu ghép với người giỏi sẽ tăng điểm nhanh hơn thực lực. Muốn xử lý
đúng thì cần TrueSkill. Ghi nhận là giới hạn đã biết, không giấu.

### Hệ số K thay đổi theo kinh nghiệm

| Số trận đã chơi | K | Lý do |
|---|---|---|
| 0 – 9 | **40** | Người mới cần hội tụ nhanh về đúng trình độ |
| 10 – 29 | **24** | Giai đoạn ổn định dần |
| ≥ 30 | **16** | Điểm đã đáng tin, không để một trận làm xáo trộn |

Nếu hai đội có K khác nhau (một đội toàn người mới), lấy **K của từng người** chứ
không lấy K chung. Người mới biến động mạnh, người cũ biến động nhẹ, trong cùng
một trận. Khi đó tổng điểm không còn bảo toàn tuyệt đối — chấp nhận được và nên
ghi chú.

### Các tham số khác

| Tham số | Giá trị | Lý do |
|---|---|---|
| Điểm khởi tạo | **1000** | Khớp `player_statistics.rating_score DEFAULT 1000` |
| Sàn điểm | **100** | Không cho về 0 hay âm, tránh số vô nghĩa |
| Trần điểm | Không đặt | Để tự nhiên |
| Số trận tạm tính | **5 trận đầu** | Hiện nhãn "Điểm tạm tính", loại khỏi phân cụm |

### Có nên tính đến cách biệt điểm số

Thắng 21-5 và thắng 21-19 rất khác nhau về trình độ, nhưng Elo gốc coi như nhau.

**Khuyến nghị: bản đầu KHÔNG dùng.** Lý do: thêm hệ số cách biệt làm công thức
khó giải thích và khó kiểm chứng, trong khi lợi ích chưa rõ. Nếu muốn mở rộng,
công thức đề xuất:

```
mov = |điểm_thắng − điểm_thua| / (điểm_thắng + điểm_thua)   ← tính trên tổng cả trận
hệ_số = 1 + 0.5 × mov                                       ← nằm trong [1.0, 1.5]
Δ = K × hệ_số × (S − E)
```

Nếu làm, phải chạy cả hai phiên bản trên dữ liệu mô phỏng và so xem bản nào khôi
phục nhãn thật tốt hơn. **Đó chính là một kết quả nghiên cứu đáng viết vào báo cáo.**

### Thứ tự tính toán — dễ sai

Điểm phụ thuộc thứ tự trận. Tính trận ngày 10 trước trận ngày 5 sẽ ra kết quả
khác. Vì vậy:

- Tính tăng dần thì **luôn theo thứ tự `confirmed_at` tăng dần**.
- Tính lại toàn bộ thì **xoá hết và chạy lại từ trận đầu tiên theo thứ tự thời
  gian**, không vá từng phần.

### Chống tính hai lần

Thêm cột `matches.rating_applied_at`. Job chỉ xử lý trận có cột này NULL, và đặt
giá trị ngay trong cùng transaction. Không có cột này thì job chạy lại lần hai sẽ
cộng điểm hai lần cho cùng một trận — lỗi rất khó phát hiện vì không có gì báo sai.

### Bảng bị đụng

```
player_statistics       [SỬA] rating_score
player_rating_history   [THÊM 1 dòng / người / trận]
                        user_id, match_id, old_rating, new_rating,
                        change_amount, reason = 'match_result'
matches                 [SỬA] rating_applied_at
```

`player_rating_history` cho phép dựng biểu đồ tiến bộ (2.2.10) và **tính lại từ
đầu khi cần kiểm chứng** — bảng này chính là bằng chứng để bảo vệ đồ án.

---

## 2.2.7 — Tính tỷ lệ thắng

### Mục đích
Tỷ lệ phần trăm số trận thắng.

Nghe đơn giản nhất nhóm, nhưng chứa cái bẫy nguy hiểm nhất.

### Cái bẫy — có thật trong dữ liệu hiện tại

```
Hoàng Quốc Đạt   2 trận, 2 thắng  →  100%
Vũ Ngọc Hà       3 trận, 3 thắng  →  100%
Lê Minh Châu     3 trận, 2 thắng  →   66.67%
```

Theo tỷ lệ thô, Đạt và Hà là hai người giỏi nhất hệ thống. Thực tế chúng ta gần
như **không biết gì** về họ — 2 trận thắng có thể chỉ là gặp đối thủ yếu, hoặc
may mắn.

Đưa thẳng con số này vào K-Means là đẩy mọi người mới thắng vài trận vào cụm
"trình độ cao". Cụm đó sẽ lẫn lộn giữa người giỏi thật và người mới may mắn, và
toàn bộ kết quả ghép cặp sai theo.

### Giải pháp: làm trơn Bayes (Bayesian shrinkage)

Kéo tỷ lệ của người ít trận về phía giá trị trung bình, kéo càng mạnh khi càng ít
dữ liệu:

```
                 thắng + C × p₀
win_rate_smooth = ───────────────
                   trận  +  C

p₀ = 0.5   ← niềm tin ban đầu: chưa biết gì thì coi như 50-50
C  = 5     ← sức nặng của niềm tin đó, tính bằng "số trận ảo"
```

Áp vào dữ liệu thật:

| Người chơi | Trận | Thắng | Tỷ lệ thô | Sau làm trơn |
|---|---|---|---|---|
| Hoàng Quốc Đạt | 2 | 2 | 100.00% | **64.3%** |
| Vũ Ngọc Hà | 3 | 3 | 100.00% | **68.8%** |
| Lê Minh Châu | 3 | 2 | 66.67% | **56.3%** |
| (giả định) | 50 | 40 | 80.00% | **77.3%** |

Người 50 trận gần như không bị kéo — dữ liệu của họ đủ nhiều để tự nói. Người 2
trận bị kéo mạnh về giữa. Đúng như mong muốn.

Chọn `C = 5` vì nó khớp với ngưỡng 5 trận tạm tính của rating (2.2.6): dưới 5
trận thì hệ thống chủ động không tin dữ liệu.

### Hai con số cho hai mục đích — tách bạch rõ

| Nơi lưu | Giá trị | Dùng cho |
|---|---|---|
| `player_statistics.win_rate` | **Tỷ lệ thô** | Hiển thị cho người dùng |
| `player_match_profiles.win_rate` | **Đã làm trơn** | Thuật toán phân cụm |

Người dùng thắng 2/2 trận mà màn hình hiện 64.3% sẽ thấy sai và mất niềm tin. Họ
phải thấy 100% — đó là sự thật về những gì đã xảy ra. Còn thuật toán cần con số
phản ánh **mức độ tin cậy**, không phải sự thật trần trụi.

Hai cột cùng tên `win_rate` ở hai bảng nhưng mang ý nghĩa khác nhau — **phải ghi
comment trong migration**, nếu không người sau sẽ dùng nhầm.

### Hiển thị kèm ngữ cảnh
Giao diện nên ghi `100% (2 trận)` chứ không chỉ `100%`. Con số phần trăm không
kèm mẫu số là con số dễ gây hiểu nhầm nhất.

---

## 2.2.8 — Tính toán thống kê trận đấu

### Mục đích
Tổng hợp mọi số liệu của một người vào `player_statistics` để đọc nhanh.

Bảng này là **read model**: không tính lại mỗi lần mở màn hình. Hồ sơ người chơi
(2.1.6) đã đọc từ đây.

### Các chỉ số

**Đã có sẵn trong bảng:**

| Cột | Cách tính |
|---|---|
| `total_matches` | Đếm trận đã xác nhận |
| `total_wins` / `total_losses` | Đếm theo `winning_team` so với `team_no` |
| `win_rate` | `total_wins / total_matches × 100`, làm tròn 2 chữ số |
| `rating_score` | Từ 2.2.6 |
| `avg_points_scored` | Trung bình tổng điểm đội mình ghi **mỗi trận** |
| `avg_points_conceded` | Trung bình tổng điểm đội đối phương ghi mỗi trận |
| `last_played_at` | `MAX(scheduled_at)` |

Chú ý `avg_points_scored`: tính trên **cả trận** (cộng mọi set), không phải trung
bình mỗi set. Trận 2 set và trận 3 set có tổng điểm rất khác nhau, nên con số này
phụ thuộc số set — cần ghi rõ để không hiểu nhầm. Nếu muốn so sánh công bằng hơn
thì dùng trung bình mỗi set, nhưng khi đó phải đổi tên cột.

**Cần bổ sung** (đề xuất migration ở mục 8):

| Cột mới | Ý nghĩa | Phục vụ |
|---|---|---|
| `current_streak` | Chuỗi hiện tại, dương là thắng, âm là thua | 2.2.10 |
| `longest_win_streak` | Chuỗi thắng dài nhất | 2.2.10 |
| `total_games_won` / `total_games_lost` | Số set, không phải số trận | Thống kê chi tiết hơn |
| `point_diff_stddev` | Độ lệch chuẩn chênh lệch điểm | Đặc trưng f5 ở 2.2.5 |
| `matches_single` / `matches_double` / `matches_mixed` | Số trận theo loại | 2.2.10, và để biết người này quen đánh gì |

### Cập nhật tăng dần

Khi một trận được xác nhận, với mỗi người chơi trong trận:

```
total_matches += 1
total_wins    += (thắng ? 1 : 0)
total_losses  += (thắng ? 0 : 1)
win_rate       = total_wins / total_matches × 100

avg_points_scored = (avg_cũ × (n−1) + điểm_trận_này) / n     ← trung bình động
current_streak    = thắng ? max(streak+1, 1) : min(streak−1, −1)
last_played_at    = max(last_played_at, scheduled_at)
```

Công thức trung bình động tránh phải quét lại toàn bộ lịch sử mỗi lần.

`point_diff_stddev` **không cộng dồn được** bằng cách này. Dùng thuật toán Welford
(cập nhật một lượt), hoặc chấp nhận tính lại trong job đêm.

### Job đối soát hằng đêm

```
Với mỗi người chơi:
   tính lại toàn bộ từ matches / match_games / match_results
   so với giá trị đang lưu
   lệch → ghi log WARN kèm cả hai giá trị, rồi ghi đè bằng giá trị đúng
```

Chạy lúc 3 giờ sáng. Log của job này là thứ đầu tiên phải xem khi nghi ngờ số
liệu sai.

---

## 2.2.9 — Xem lịch sử trận đấu

### Mục đích
Danh sách trận đã chơi, mới nhất trước.

### API

```
GET /api/v1/users/me/matches?page=0&size=20&status=completed&type=double
```

| Tham số | Mặc định | Ghi chú |
|---|---|---|
| `page` | 0 | |
| `size` | 20 | Tối đa 100 theo quy tắc chung |
| `status` | tất cả | `scheduled` / `completed` / `disputed` / `cancelled` |
| `type` | tất cả | `single` / `double` / `mixed_double` |

Xem chi tiết một trận: `GET /api/v1/matches/{id}` — chỉ người tham gia trận đó
mới xem được, người ngoài nhận **404 chứ không phải 403** (cùng nguyên tắc đã áp
dụng ở chi tiết đơn đặt sân 2.1.29: không tiết lộ trận có tồn tại hay không).

### Mỗi dòng trong danh sách

```json
{
  "id": "…",
  "matchType": "double",
  "scheduledAt": "2026-09-28T12:00:00Z",
  "venueName": "Sân Cầu Lông Minh Khai",
  "myTeamNo": 1,
  "won": true,
  "scoreSummary": "2-1",
  "games": [[21,18],[19,21],[21,17]],
  "teammates":  [{ "id": "…", "fullName": "…", "avatarUrl": "…" }],
  "opponents":  [{ "id": "…", "fullName": "…", "avatarUrl": "…" }],
  "ratingChange": 12.4,
  "status": "completed",
  "needsMyConfirmation": false
}
```

Hai trường đáng chú ý:

- `ratingChange` lấy từ `player_rating_history` — cho người dùng thấy **trận này
  ảnh hưởng gì tới điểm của mình**. Đây là thứ khiến hệ thống điểm trở nên minh
  bạch thay vì một hộp đen.
- `needsMyConfirmation` để giao diện nổi bật những trận đang chờ mình bấm xác
  nhận. Không có trường này thì người dùng không biết mình đang chặn dữ liệu của
  người khác.

### Tránh N+1
Một trận cần: thông tin sân, danh sách người chơi kèm tên và ảnh, các set, kết
quả, thay đổi điểm. Danh sách 20 trận mà nạp lười sẽ thành hơn 100 truy vấn.

Dùng đúng cách đã làm ở chi tiết sân (2.1.18): vài truy vấn có chủ đích, gom theo
lô — lấy toàn bộ `match_players` của 20 trận trong một câu, rồi nhóm ở Java.

### Giao diện
Router đề xuất `/matches` và `/matches/:matchId`. Chưa có trong bản dựng giao
diện hiện tại, cần thiết kế mới.

---

## 2.2.10 — Xem thống kê hiệu suất

### Mục đích
Trang tổng quan năng lực của người chơi: điểm, tỷ lệ thắng, xu hướng, điểm mạnh
điểm yếu.

### API

```
GET /api/v1/users/me/performance
```

```json
{
  "summary": {
    "ratingScore": 1372,
    "ratingLabel": "Khá",
    "isProvisional": false,
    "totalMatches": 24,
    "totalWins": 15,
    "totalLosses": 9,
    "winRate": 62.5,
    "currentStreak": 3,
    "longestWinStreak": 6,
    "lastPlayedAt": "2026-09-28T12:00:00Z"
  },
  "scoring": {
    "avgPointsScored": 42.25,
    "avgPointsConceded": 38.10,
    "avgPointDiff": 4.15,
    "totalGamesWon": 33,
    "totalGamesLost": 24
  },
  "byMatchType": [
    { "type": "single", "matches": 6,  "wins": 2,  "winRate": 33.3 },
    { "type": "double", "matches": 15, "wins": 11, "winRate": 73.3 },
    { "type": "mixed_double", "matches": 3, "wins": 2, "winRate": 66.7 }
  ],
  "ratingTrend": [
    { "date": "2026-07-01", "rating": 1000 },
    { "date": "2026-08-15", "rating": 1180 },
    { "date": "2026-09-28", "rating": 1372 }
  ]
}
```

`ratingTrend` lấy từ `player_rating_history`, gom theo ngày (lấy giá trị cuối
ngày). Giới hạn 90 ngày gần nhất để phản hồi không phình to.

### Bốn điều giao diện phải làm đúng

**1. Người chưa có trận nào.** Không hiện biểu đồ trống và một loạt số 0 —
người dùng sẽ tưởng hệ thống hỏng. Hiện một khối mời gọi: "Bạn chưa có trận nào.
Ghi lại trận đầu tiên để bắt đầu theo dõi tiến bộ."

**2. Điểm tạm tính.** Dưới 5 trận thì hiện rõ nhãn *Tạm tính* cạnh điểm số, kèm
giải thích ngắn. Không có nhãn này, người mới sẽ tin vào một con số mà chính hệ
thống chưa tin.

**3. Tỷ lệ thắng luôn kèm mẫu số.** `62.5% (15/24 trận)`, không phải `62.5%`.

**4. Nhãn trình độ suy từ điểm, không phải từ khai báo.** `player_profiles.skill_level`
là người dùng **tự khai** (2.1.8); `rating_score` là hệ thống **đo được**. Hai
thứ có thể lệch nhau, và trang này phải hiện cái đo được.

Mốc đề xuất — cần hiệu chỉnh lại sau khi có dữ liệu thật:

| Điểm | Nhãn |
|---|---|
| < 900 | Mới chơi |
| 900 – 1199 | Trung bình |
| 1200 – 1499 | Khá |
| ≥ 1500 | Giỏi |

Bốn mốc này **tạm thời**. Sau khi có dữ liệu, nên đặt lại theo phân vị (25%, 50%,
75%) để mỗi nhóm có số người hợp lý, thay vì cố định cứng.

### Quan hệ với 2.1.6
Hồ sơ người chơi đã hiện `totalMatches`, `winRate`, `ratingScore`. Trang này là
bản chi tiết. **Không nhân bản logic** — cùng đọc từ `player_statistics`, chỉ
khác mức độ chi tiết.

---

## 8. Thay đổi lược đồ cần thiết

Sáu bảng của nhóm 6 đã dựng từ V7, đủ cho phần lớn thiết kế. Cần bổ sung:

### Migration V15 — hỗ trợ luồng xác nhận và chống tính trùng

```sql
ALTER TABLE matches
    ADD COLUMN data_source varchar(30) NOT NULL DEFAULT 'manual',
    ADD COLUMN rating_applied_at timestamptz,
    ADD COLUMN excluded_from_rating boolean NOT NULL DEFAULT false,
    ADD COLUMN exclusion_reason text,
    ADD CONSTRAINT matches_source_check
        CHECK (data_source IN ('booking', 'manual', 'simulated'));

-- Cho phép thêm trạng thái tranh chấp
ALTER TABLE matches DROP CONSTRAINT matches_status_check;
ALTER TABLE matches ADD CONSTRAINT matches_status_check
    CHECK (status IN ('scheduled', 'completed', 'disputed', 'cancelled'));

ALTER TABLE match_players
    ADD COLUMN result_confirmed_at timestamptz,
    ADD COLUMN result_disputed_at timestamptz;

-- Job tinh diem quet cac tran chua ap dung, theo dung thu tu thoi gian
CREATE INDEX matches_pending_rating_idx ON matches (scheduled_at)
    WHERE rating_applied_at IS NULL;
```

| Cột mới | Vì sao cần |
|---|---|
| `data_source` | Tách dữ liệu mô phỏng khỏi dữ liệu thật khi báo cáo kết quả |
| `rating_applied_at` | Chống cộng điểm hai lần khi job chạy lại |
| `excluded_from_rating` + lý do | Loại trận nghi gian lận mà vẫn giữ bản ghi để đối chiếu |
| `result_confirmed_at` | Luồng xác nhận hai phía của 2.2.2 |

### Migration V16 — bổ sung chỉ số thống kê

```sql
ALTER TABLE player_statistics
    ADD COLUMN current_streak int NOT NULL DEFAULT 0,
    ADD COLUMN longest_win_streak int NOT NULL DEFAULT 0,
    ADD COLUMN total_games_won int NOT NULL DEFAULT 0,
    ADD COLUMN total_games_lost int NOT NULL DEFAULT 0,
    ADD COLUMN point_diff_stddev numeric(8,2) NOT NULL DEFAULT 0,
    ADD COLUMN matches_single int NOT NULL DEFAULT 0,
    ADD COLUMN matches_double int NOT NULL DEFAULT 0,
    ADD COLUMN matches_mixed int NOT NULL DEFAULT 0;

COMMENT ON COLUMN player_statistics.win_rate IS
    'Ty le thang THO, dung de hien thi. Ban da lam tron nam o player_match_profiles.win_rate';
```

Dòng `COMMENT` không phải trang trí — đó là thứ ngăn người sau dùng nhầm cột.

---

## 9. Thứ tự thực hiện

Phụ thuộc dữ liệu quyết định thứ tự. Không đảo được.

| Đợt | Làm gì | Vì sao trước |
|---|---|---|
| **1** | Migration V15, V16 | Mọi thứ sau đều cần cột mới |
| **2** | 2.2.4 `MatchDataValidator` | Nhập dữ liệu phải có luật kiểm tra sẵn |
| **3** | 2.2.2 API nhập trận + xác nhận | Nguồn dữ liệu duy nhất |
| **4** | 2.2.1 Gợi ý tạo trận từ booking | Cần 2.2.2 xong trước |
| **5** | ~~Bộ sinh dữ liệu mô phỏng~~ → **đã có bộ BWF** (mục 0b); chỉ còn cần bộ mô phỏng cho phần đánh giá | Không có dữ liệu thì các bước sau không kiểm chứng được |
| **6** | 2.2.6 Elo + 2.2.7 tỷ lệ thắng + 2.2.8 thống kê | Ba cái cùng chạy trên một luồng dữ liệu |
| **7** | Job đối soát hằng đêm | Chốt chặn đúng đắn |
| **8** | 2.2.9 + 2.2.10 API và giao diện | Cần số liệu có sẵn để hiển thị |
| **9** | 2.2.3 + 2.2.5 dựng `player_match_profiles` | Bàn giao sang phân cụm |

Đợt 5 nằm giữa dây chuyền chứ không phải phụ lục. Bỏ qua nó thì đợt 6 trở đi
không có cách nào biết mình làm đúng hay sai.

---

## 10. Những chỗ cần quyết trước khi viết code

| # | Câu hỏi | Khuyến nghị | Ảnh hưởng nếu đổi |
|---|---|---|---|
| 1 | Elo hay Glicko-2 | **Elo** | Đổi sau tốn công tính lại toàn bộ lịch sử |
| 2 | Có tính cách biệt điểm số vào rating | **Không, ở bản đầu** | Dễ thêm sau, chỉ cần tính lại |
| 3 | Cần bao nhiêu người xác nhận | **Một người mỗi đội** | Ảnh hưởng thiết kế bảng và giao diện |
| 4 | Tự xác nhận sau bao lâu | **72 giờ** | Chỉ là một hằng số |
| 5 | Hệ số làm trơn C | **5** | Ảnh hưởng trực tiếp kết quả phân cụm |
| 6 | Ngưỡng tạm tính | **5 trận** | Nên trùng với C ở trên |
| 7 | Sinh dữ liệu mô phỏng hay chỉ dùng dữ liệu thật | **Sinh mô phỏng** | Quyết định cả phần đánh giá của báo cáo |
| 8 | Có cho sửa kết quả sau khi xác nhận | **Không** — phải huỷ và tạo lại | Nếu cho sửa thì phải tính lại rating từ mốc đó |

Câu 7 là câu quan trọng nhất. Bảy câu còn lại là chi tiết kỹ thuật, đổi được.
Câu 7 quyết định Sprint 2 có bằng chứng nghiên cứu hay chỉ có phần mềm chạy được.

---

## 11. Rủi ro

| Rủi ro | Mức | Cách giảm |
|---|---|---|
| Không đủ dữ liệu để phân cụm có nghĩa | ~~Cao~~ **Đã xử lý** | Bộ dữ liệu BWF, 188 người / 2.555 trận (mục 0b) |
| Không đánh giá được chất lượng phân cụm | **Cao** | Vẫn cần dữ liệu mô phỏng có nhãn biết trước (mục 0) |
| Người dùng nhập kết quả sai hoặc gian lận | Trung bình | Xác nhận hai phía, kiểm tra 9 ở 2.2.4 |
| `player_statistics` trôi khỏi dữ liệu gốc | Trung bình | Job đối soát hằng đêm |
| Cộng điểm hai lần khi job chạy lại | Trung bình | `rating_applied_at` |
| Tỷ lệ thắng mẫu nhỏ làm hỏng phân cụm | **Cao** | Làm trơn Bayes (2.2.7) |
| Trộn toạ độ vào vector phân cụm | **Cao** | Tách rõ đặc trưng và điều kiện lọc (2.2.5) |
| Elo đôi không phản ánh đóng góp cá nhân | Thấp | Ghi nhận là giới hạn đã biết trong báo cáo |
