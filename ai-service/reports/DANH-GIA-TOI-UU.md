# Đánh giá tối ưu model AI (2026-11-19)

Mọi con số là **NDCG@10 trên tập kiểm thử theo thời gian** (1.302 nhóm xếp hạng, 20 ứng viên âm mỗi nhóm) trừ khi ghi khác; khoảng tin cậy
95% bằng bootstrap. Baseline (công thức 0,4/0,2/0,2/0,2) đạt **0,296** ở mọi kịch bản dùng cùng tập kiểm thử; xếp theo trình độ đơn thuần đạt 0,339.
Tái hiện: `uv run python -m experiments.partner_scenarios [kịch_bản]`, `uv run python -m experiments.team_cv`.

Dữ liệu: 187 vận động viên, 5.954 trận (từ 2018; trước đây 2.555 trận từ 2024), người chơi seed đã được bổ sung hồ sơ (khu vực, lịch rảnh, phong cách).

## 1. Xếp hạng đối tác (CatBoostRanker)

| Kịch bản | Kết quả | Nhận xét |
|---|---|---|
| Ổn định theo hạt giống (5 seed) | 0,668 – 0,685 | Dao động ±0,008, không phụ thuộc may rủi |
| Siêu tham số (9 cấu hình: độ sâu 4/8, tốc độ học, l2, YetiRank, PairLogit, QueryRMSE) | 0,668 – 0,688 | Cấu hình mặc định (YetiRankPairwise, độ sâu 6) 0,687 gần như tốt nhất → đã bão hoà |
| Lượng dữ liệu huấn luyện (10% / 25% / 50% / 100%) | 0,678 / 0,675 / 0,683 / 0,687 | Thêm dữ liệu gần như không cải thiện thêm → đã bão hoà |
| Cửa sổ nhãn 30 / 60 / 90 ngày | 0,692 / 0,687 / 0,680 | Khác biệt nhỏ, giữ 60 ngày |
| Số ứng viên âm 5 / 10 / 20 / 50 | 0,82 / 0,76 / 0,69 / 0,52 (baseline 0,63 / 0,47 / 0,30 / 0,14) | Điểm giảm khi danh sách dài hơn nhưng model luôn hơn baseline gấp ≥ 2 |
| Người tìm CHƯA từng thấy khi huấn luyện (giữ lại 20% người) | 0,665 (so với 0,669 người đã thấy) | Khái quát hoá tốt sang người chơi mới |
| Người tìm mới (< 5 trận) | 0,560 (baseline 0,296) | Có thể ít hơn người dày dữ liệu nhưng vẫn hơn baseline rõ |
| Dịch chuyển: huấn luyện đơn → kiểm thử đôi / ngược lại | 0,659 / 0,644 | Chuyển giao tốt giữa hai hình thức |
| Chỉ huấn luyện trên nửa đầu (xa thời điểm kiểm thử) | 0,664 | Suy giảm nhẹ theo thời gian → cần tái huấn luyện định kỳ |
| Nhiễu Elo ±25 / ±50 / ±100 | 0,682 / 0,681 / 0,679 | Bền với sai số Elo |
| Che ngẫu nhiên các đặc trưng CHO PHÉP thiếu (ổn định, phong độ, vị trí, giờ…) 30% / 100% | 0,645 / 0,588 | Suy giảm êm, vẫn hơn baseline gấp 2 |
| Như trên nhưng huấn luyện có che 20% (**đã áp dụng**) | sạch 0,676 (−0,011); che 30% 0,657 (+0,012); che 100% 0,617 (+0,03) | Đánh đổi có lợi cho thực tế (người mới thiếu dữ liệu) |

### Bỏ bớt đặc trưng (phân tích đóng góp)
| Bỏ | NDCG@10 |
|---|---|
| Không bỏ | 0,687 |
| Vị trí / giờ rảnh / phong cách | 0,683 (gần như không đóng góp) |
| Phong độ (tỷ lệ thắng, ổn định, độ mới, chênh điểm) | 0,624 |
| `same_play_type` (loại trận ưa thích) | 0,498 |
| Cả loại trận ưa thích lẫn vị trí/giờ/phong cách (= không dùng hồ sơ seed) | 0,506 |
| Chỉ Elo và chênh Elo | 0,376 |

**Điều cần nói thẳng:** phần lớn mức hơn baseline đến từ `same_play_type`, mà loại trận ưa thích của người seed được suy từ loại trận họ chơi nhiều
nhất (vòng tròn), cộng với việc vị trí/giờ/phong cách của người seed sinh độc lập với kết quả trận nên không mang tín hiệu. Vì vậy con số 0,687
đúng cho dữ liệu hiện tại nhưng **chưa phải bằng chứng hơn baseline trên người dùng thật**; chỉ phản hồi thật (yêu cầu được chấp nhận và đã chơi) mới chứng minh được.
Phong độ thì là tín hiệu thật (bỏ đi mất 0,06).

## 2. Chia đội (CatBoostClassifier trên nền Elo)

Rolling-origin CV 5 mốc thời gian (1.481 trận kiểm thử gộp):

| Cấu hình | Log-loss model | Log-loss Elo | Cải thiện | 95% CI | Độ chính xác (model / Elo) |
|---|---|---|---|---|---|
| độ sâu 2 | 0,5951 | 0,5968 | +0,0016 | [−0,0008, +0,0042] | 0,683 / 0,679 |
| độ sâu 3 | 0,5954 | 0,5968 | +0,0013 | [−0,0018, +0,0042] | 0,685 / 0,679 |
| độ sâu 4 | 0,5948 | 0,5968 | +0,0020 | [−0,0015, +0,0053] | 0,686 / 0,679 |
| + phong độ 10 trận, đà tăng Elo, lịch sử đánh cặp (thử nghiệm) | 0,5940 | 0,5968 | +0,0028 | [−0,0008, +0,0062] | 0,687 / 0,679 |

Mở rộng seed từ 2.555 lên 5.954 trận (trận đôi từ ~1.400 lên ~3.300) và thêm đặc trưng phong độ/lịch sử đánh cặp chỉ nâng cải thiện từ **âm** lên +0,002 –
+0,003, khoảng tin cậy vẫn chứa 0. Kết luận: **Elo gần như đã là tối ưu cho bài toán dự đoán đội thắng với dữ liệu này**; model chia đội không có
lợi thế chứng minh được nên cổng chặn kích hoạt (đúng thiết kế 2.2.15). Cổng nay dùng CV nhiều mốc + khoảng tin cậy thay cho một tập kiểm thử 209 mẫu quá nhiễu.
Các đặc trưng phong độ/lịch sử cặp không được đưa vào schema vì chưa chứng minh được giá trị.

## 3. Kết luận "đã tối ưu chưa"
- Siêu tham số và lượng dữ liệu: **đã bão hoà** với dữ liệu hiện tại.
- Độ bền (thiếu dữ liệu, nhiễu Elo, người chưa thấy, người mới, dịch chuyển loại trận): **tốt**, đã bổ sung huấn luyện che dữ liệu.
- Hạn chế còn lại không nằm ở thuật toán mà ở **dữ liệu**: tín hiệu vị trí/giờ/phong cách chưa có dữ liệu thật để học, và phải theo dõi tỷ lệ chấp nhận thực tế (2.3.70) sau khi bật cho người chơi.
