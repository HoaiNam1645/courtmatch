# Tài liệu mô tả bảng và quan hệ cơ sở dữ liệu Courtly

## 1. Tổng quan

Thiết kế hiện tại của Courtly gồm **49 bảng**, được chia thành 10 nhóm nghiệp vụ. Nguồn đối chiếu là file `thiet-ke-csdl.text`.

| Nhóm | Phạm vi nghiệp vụ | Số bảng |
|---|---|---:|
| 1 | Tài khoản, đăng nhập, phân quyền | 11 |
| 2 | Sân cầu lông, bản đồ, tìm kiếm | 9 |
| 3 | Đặt sân | 3 |
| 4 | Thanh toán SePay, hoàn tiền | 3 |
| 5 | Doanh thu, phí nền tảng, rút tiền | 4 |
| 6 | Trận đấu, thống kê người chơi | 6 |
| 7 | Ghép cặp, phân cụm, gợi ý đối tác | 7 |
| 8 | Đánh giá, báo cáo | 3 |
| 9 | Thông báo | 2 |
| 10 | Nhật ký hệ thống | 1 |
| **Tổng cộng** |  | **49** |

Quy ước quan hệ:

- `1-1`: một bản ghi ở bảng A tương ứng tối đa một bản ghi ở bảng B.
- `1-N`: một bản ghi ở bảng A có thể có nhiều bản ghi ở bảng B.
- `N-N`: quan hệ nhiều-nhiều, được triển khai qua một bảng trung gian.
- `Quan hệ logic`: dữ liệu có liên quan về nghiệp vụ nhưng thiết kế hiện tại không khai báo khóa ngoại trực tiếp.

## 2. Nhóm tài khoản, đăng nhập và phân quyền - 11 bảng

| Bảng | Chức năng | Quan hệ với các bảng khác |
|---|---|---|
| `users` | Bảng tài khoản trung tâm, lưu họ tên, email, số điện thoại, mật khẩu đã băm, ảnh đại diện, trạng thái và thời điểm đăng nhập. | `1-N` với `auth_providers`, `password_reset_requests`, `user_roles`, `player_availability_slots`, `player_preferred_locations`, `venues`, `bookings`, `notifications` và nhiều bảng nghiệp vụ khác. `1-1` với `player_profiles`, `court_owner_profiles`, `player_statistics`, `player_match_profiles`. |
| `auth_providers` | Lưu danh tính đăng nhập từ nhà cung cấp bên ngoài, trước mắt là Google OAuth. Giúp một người dùng đăng nhập Google mà không cần mật khẩu nội bộ. | Mỗi bản ghi thuộc một `users` qua `user_id`. Quan hệ `users 1-N auth_providers`; mỗi user chỉ có tối đa một tài khoản cho từng provider. |
| `password_reset_requests` | Lưu yêu cầu quên mật khẩu, mã OTP/token đã băm, kênh gửi, thời hạn, số lần thử và trạng thái sử dụng. | Mỗi yêu cầu thuộc một `users` qua `user_id`. Một user có thể có nhiều yêu cầu theo thời gian. |
| `roles` | Danh mục vai trò như người chơi, chủ sân, nhân viên và quản trị viên. | `N-N` với `users` qua `user_roles`; `N-N` với `permissions` qua `role_permissions`. |
| `permissions` | Danh mục quyền thao tác chi tiết của hệ thống. | `N-N` với `roles` qua `role_permissions`. |
| `role_permissions` | Bảng trung gian gán quyền cho vai trò. | Mỗi dòng tham chiếu một `roles` và một `permissions`; khóa chính ghép `role_id, permission_id`. |
| `user_roles` | Bảng trung gian gán một hoặc nhiều vai trò cho người dùng. | Mỗi dòng tham chiếu một `users` và một `roles`; tạo quan hệ `users N-N roles`. |
| `player_profiles` | Hồ sơ riêng của người chơi: giới tính, ngày sinh, trình độ, điểm kỹ năng, tay thuận, phong cách và hình thức chơi ưu tiên. | `1-1` với `users`; `user_id` vừa là khóa chính vừa là khóa ngoại. Dữ liệu được dùng để tạo `player_match_profiles` và tính gợi ý ghép cặp. |
| `court_owner_profiles` | Hồ sơ chủ sân: thông tin doanh nghiệp, định danh, tài khoản ngân hàng và trạng thái xác minh. | `1-1` với `users`. Người dùng có hồ sơ chủ sân có thể sở hữu nhiều `venues` và phát sinh các bảng doanh thu/rút tiền. |
| `player_availability_slots` | Lưu các ngày và khung giờ người chơi thường rảnh để tìm bạn chơi phù hợp. | `N-1` với `users`. Dữ liệu là đầu vào để tổng hợp `player_match_profiles.availability_score` và tính `partner_suggestions.time_score`. |
| `player_preferred_locations` | Lưu khu vực chơi ưu tiên, tọa độ và bán kính di chuyển của người chơi. | `N-1` với `users`. Dữ liệu vị trí là đầu vào cho `player_match_profiles` và điểm khoảng cách trong `partner_suggestions`. |

## 3. Nhóm sân cầu lông, bản đồ và tìm kiếm - 9 bảng

| Bảng | Chức năng | Quan hệ với các bảng khác |
|---|---|---|
| `venues` | Lưu một địa điểm hoặc cụm sân: chủ sân, tên, địa chỉ, tọa độ PostGIS, liên hệ, trạng thái duyệt và điểm đánh giá. | Thuộc một chủ sân trong `users` qua `owner_id`. `1-N` với `courts`, `venue_images`, `venue_operating_hours`, `bookings`, `matches`, `venue_reviews`. `N-N` với `services` qua `venue_services`; `N-N` với người dùng qua `favorite_venues`. |
| `courts` | Lưu từng sân con cụ thể trong một địa điểm, ví dụ Sân 1, Sân VIP 2. | `N-1` với `venues`; `1-N` với `court_price_rules`, `court_blocks`, `booking_items`. |
| `venue_images` | Lưu bộ ảnh, ảnh bìa, chú thích và thứ tự hiển thị của địa điểm sân. | `N-1` với `venues`. |
| `services` | Danh mục tiện ích dùng chung như bãi xe, phòng tắm, thuê vợt và nước uống. | `N-N` với `venues` qua `venue_services`. |
| `venue_services` | Bảng trung gian gán tiện ích cho địa điểm sân, đồng thời cho phép lưu giá và ghi chú. | Tham chiếu `venues` và `services`; khóa chính ghép `venue_id, service_id`. |
| `venue_operating_hours` | Lưu giờ mở cửa, đóng cửa hoặc ngày nghỉ theo từng thứ trong tuần. | `N-1` với `venues`. Kết hợp với `court_price_rules`, `court_blocks` và `booking_items` để tạo danh sách giờ còn trống. |
| `court_price_rules` | Cấu hình giá theo sân con, thứ trong tuần, khung giờ và thời gian hiệu lực. | `N-1` với `courts`. Giá phù hợp được dùng để tính `booking_items.price` và `bookings.total_amount`. |
| `court_blocks` | Khóa một sân con trong một khoảng thời gian vì bảo trì, sự kiện hoặc thao tác của chủ sân. | `N-1` với `courts`; người tạo được tham chiếu tới `users` qua `created_by`. Được kiểm tra cùng `booking_items` khi tính lịch trống. |
| `favorite_venues` | Lưu danh sách sân yêu thích của người chơi. | Bảng trung gian giữa `users` và `venues`; khóa chính ghép `user_id, venue_id`. |

## 4. Nhóm đặt sân - 3 bảng

| Bảng | Chức năng | Quan hệ với các bảng khác |
|---|---|---|
| `bookings` | Bảng đầu đơn đặt sân, lưu mã booking, người đặt, địa điểm, tổng tiền, trạng thái, hạn thanh toán và thông tin hủy. | `N-1` với `users` và `venues`. `1-N` với `booking_items`, `booking_status_history`, `payments`, `refunds`, `owner_balance_transactions`; có thể liên quan tới `matches` và `venue_reviews`. |
| `booking_items` | Chi tiết sân con và khung giờ trong booking. Đây là bảng trực tiếp dùng để chống đặt trùng sân. | `N-1` với `bookings` và `courts`. Ràng buộc exclusion ngăn các bản ghi `pending/confirmed` trùng thời gian trên cùng một sân. |
| `booking_status_history` | Lưu lịch sử thay đổi trạng thái booking để tạo timeline và phục vụ kiểm tra sau này. | `N-1` với `bookings`; `changed_by` tham chiếu `users` và có thể để trống khi trạng thái do hệ thống tự động cập nhật. |

## 5. Nhóm thanh toán SePay và hoàn tiền - 3 bảng

| Bảng | Chức năng | Quan hệ với các bảng khác |
|---|---|---|
| `payments` | Lưu từng giao dịch thanh toán của booking: nhà cung cấp SePay, số tiền, QR, tài khoản nhận, nội dung chuyển khoản, mã giao dịch và trạng thái. | `N-1` với `bookings`; `1-N` với `sepay_webhook_logs`, `refunds`, `owner_balance_transactions`. Một booking có thể có nhiều lần thanh toán nếu giao dịch cũ thất bại hoặc hết hạn. |
| `sepay_webhook_logs` | Lưu nguyên payload webhook từ SePay, kết quả xử lý và lỗi để chống xử lý trùng, đối soát và debug. | `N-1` tùy chọn với `payments`; `payment_id` có thể rỗng khi webhook mới nhận chưa xác định được giao dịch tương ứng. `transaction_code` là duy nhất. |
| `refunds` | Lưu yêu cầu và tiến trình hoàn tiền cho một giao dịch/booking. | `N-1` với `payments` và `bookings`; `requested_by` và `processed_by` tham chiếu `users`. |

## 6. Nhóm doanh thu, phí nền tảng và rút tiền - 4 bảng

| Bảng | Chức năng | Quan hệ với các bảng khác |
|---|---|---|
| `platform_fee_configs` | Lưu cấu hình phí nền tảng theo phần trăm hoặc số tiền cố định và khoảng thời gian áp dụng. | Người tạo cấu hình thuộc `users` qua `created_by`. Có quan hệ logic với `payments` và `owner_balance_transactions` khi tính phí, nhưng chưa có FK trực tiếp. |
| `owner_balance_transactions` | Sổ cái ghi mọi biến động số dư của chủ sân: doanh thu, phí nền tảng, rút tiền, hoàn tiền và điều chỉnh. | `N-1` với chủ sân trong `users`; có thể tham chiếu `bookings` và `payments`. Đây nên là nguồn dữ liệu chính khi tính số dư thay vì chỉ lưu một cột số dư tổng. |
| `withdrawal_requests` | Lưu yêu cầu rút tiền của chủ sân, tài khoản nhận, trạng thái duyệt và người xử lý. | `owner_id` và `reviewed_by` cùng tham chiếu `users`; `1-N` với `payout_transactions`. Khi được chi trả sẽ tạo biến động tương ứng trong `owner_balance_transactions` theo nghiệp vụ. |
| `payout_transactions` | Lưu giao dịch chuyển tiền thực tế cho chủ sân sau khi yêu cầu rút tiền được duyệt. | `N-1` với `withdrawal_requests` và chủ sân trong `users`. |

## 7. Nhóm trận đấu và thống kê người chơi - 6 bảng

| Bảng | Chức năng | Quan hệ với các bảng khác |
|---|---|---|
| `matches` | Lưu một trận đấu đơn, đôi hoặc đôi nam nữ; có lịch thi đấu và trạng thái. | Có thể thuộc `bookings` và `venues`; người tạo thuộc `users`. `1-N` với `match_players`, `match_games`; `1-1` với `match_results`; được tham chiếu bởi `player_rating_history`, `double_team_suggestions`, `partner_reviews`. |
| `match_players` | Danh sách người tham gia trận, đội số mấy, vị trí và trạng thái lời mời/tham gia. | Bảng liên kết giữa `matches` và `users`; mỗi user chỉ xuất hiện một lần trong một trận. |
| `match_games` | Lưu tỷ số từng set/game trong trận. | `N-1` với `matches`; mỗi số game là duy nhất trong phạm vi một trận. |
| `match_results` | Lưu kết quả tổng cuối cùng và đội thắng của trận. | `1-1` với `matches`; `recorded_by` tham chiếu người ghi kết quả trong `users`. |
| `player_statistics` | Bảng tổng hợp nhanh số trận, thắng/thua, tỷ lệ thắng, điểm rating và điểm trung bình. | `1-1` với `users`. Được cập nhật từ `matches`, `match_players`, `match_games`, `match_results`; là nguồn đầu vào cho hồ sơ và thuật toán ghép cặp. |
| `player_rating_history` | Lưu lịch sử từng lần tăng/giảm rating của người chơi. | `N-1` với `users`; có thể tham chiếu `matches` để giải thích lần thay đổi điểm. |

## 8. Nhóm ghép cặp, phân cụm và gợi ý đối tác - 7 bảng

| Bảng | Chức năng | Quan hệ với các bảng khác |
|---|---|---|
| `player_match_profiles` | Vector đặc trưng đã tổng hợp cho thuật toán: kỹ năng, tỷ lệ thắng, số trận, vị trí, bán kính, lịch rảnh và phong cách chơi. | `1-1` với `users`. Dữ liệu được tổng hợp từ `player_profiles`, `player_statistics`, `player_availability_slots`, `player_preferred_locations`; sau đó dùng cho `player_clusters` và `partner_suggestions`. |
| `algorithm_runs` | Lưu metadata của mỗi lần chạy K-Means, DBSCAN hoặc thuật toán ghép cặp: phiên bản, tham số, metrics và trạng thái. | `1-N` với `player_clusters`, `partner_suggestions`, `double_team_suggestions`. |
| `player_clusters` | Lưu người chơi thuộc cụm nào và khoảng cách tới tâm cụm trong một lần chạy thuật toán. | `N-1` với `algorithm_runs` và `users`; mỗi user chỉ có một kết quả trong một lần chạy. |
| `partner_suggestions` | Danh sách người chơi được gợi ý, kèm điểm kỹ năng, vị trí, thời gian, phong cách và tổng độ phù hợp. | `run_id` có thể tham chiếu `algorithm_runs`; `user_id` là người nhận gợi ý và `suggested_user_id` là người được đề xuất, cả hai cùng tham chiếu `users`. |
| `partner_requests` | Yêu cầu kết nối/ghép cặp được gửi giữa hai người chơi. | `requester_id` và `receiver_id` cùng tham chiếu `users`; yêu cầu được chấp nhận có thể sinh `player_connections`. |
| `player_connections` | Danh sách quan hệ người chơi đã kết nối, bị chặn hoặc đã xóa. | `user_id` và `partner_id` cùng tham chiếu `users`; `source_request_id` có thể tham chiếu yêu cầu gốc trong `partner_requests`. |
| `double_team_suggestions` | Lưu kết quả đề xuất chia hai đội cân bằng, sức mạnh từng đội và điểm cân bằng. | `N-1` với `algorithm_runs`; có thể gắn với một `matches`. Danh sách thành viên hai đội hiện được lưu JSONB. |

## 9. Nhóm đánh giá và báo cáo - 3 bảng

| Bảng | Chức năng | Quan hệ với các bảng khác |
|---|---|---|
| `venue_reviews` | Đánh giá địa điểm sân theo số sao và bình luận. | `N-1` với `venues` và `users`; có thể gắn với `bookings` để xác minh người đánh giá đã từng đặt sân. Có thể trở thành đối tượng của `reports` theo quan hệ logic. |
| `partner_reviews` | Đánh giá người chơi sau khi chơi cùng. | `reviewer_id` và `reviewed_user_id` tham chiếu `users`; có thể gắn với `matches`. Có thể trở thành đối tượng của `reports` theo quan hệ logic. |
| `reports` | Báo cáo vi phạm đối với người dùng, sân hoặc nội dung đánh giá. | `reporter_id` và `handled_by` tham chiếu `users`. Đối tượng bị báo cáo được xác định bằng `target_type + target_id`, là quan hệ đa hình nên không có FK trực tiếp tới `users`, `venues`, `venue_reviews` hoặc `partner_reviews`. |

## 10. Nhóm thông báo - 2 bảng

| Bảng | Chức năng | Quan hệ với các bảng khác |
|---|---|---|
| `notifications` | Lưu thông báo trong ứng dụng như xác nhận booking, trạng thái thanh toán, yêu cầu ghép cặp và nhắc lịch. | `N-1` với `users`. Trường `data_json` có thể chứa ID của booking, payment, request hoặc match theo quan hệ logic, không có FK trực tiếp. |
| `notification_preferences` | Cấu hình người dùng có nhận từng loại thông báo qua ứng dụng, email hoặc push hay không. | `N-1` với `users`; khóa chính ghép `user_id, type`. |

## 11. Nhóm nhật ký hệ thống - 1 bảng

| Bảng | Chức năng | Quan hệ với các bảng khác |
|---|---|---|
| `audit_logs` | Lưu hành động quan trọng của quản trị viên, chủ sân hoặc hệ thống, bao gồm dữ liệu trước/sau và thông tin thiết bị. | `actor_id` có thể tham chiếu `users`. Đối tượng bị thay đổi được xác định bằng `entity_type + entity_id`, là quan hệ đa hình nên không có FK trực tiếp tới từng bảng nghiệp vụ. |

## 12. Sơ đồ quan hệ nghiệp vụ chính

```text
users
  |-- 1-1 player_profiles
  |-- 1-1 court_owner_profiles
  |-- 1-N auth_providers
  |-- N-N roles -- N-N permissions
  |-- 1-N bookings -- 1-N booking_items -- N-1 courts -- N-1 venues
  |                  |-- 1-N payments -- 1-N sepay_webhook_logs
  |                  |               |-- 1-N refunds
  |                  |-- 1-N booking_status_history
  |
  |-- 1-N match_players -- N-1 matches -- 1-N match_games
  |                                   |-- 1-1 match_results
  |-- 1-1 player_statistics
  |-- 1-1 player_match_profiles
  |-- 1-N partner_suggestions
  |-- 1-N partner_requests
  |-- 1-N player_connections

venues
  |-- 1-N courts
  |-- 1-N venue_images
  |-- N-N services qua venue_services
  |-- 1-N venue_operating_hours
  |-- 1-N bookings
  |-- 1-N venue_reviews

algorithm_runs
  |-- 1-N player_clusters
  |-- 1-N partner_suggestions
  |-- 1-N double_team_suggestions
```

## 13. Luồng dữ liệu quan trọng

### 13.1. Tìm sân và tính lịch trống

`venues` → `courts` → `venue_operating_hours` + `court_price_rules` - `court_blocks` - `booking_items`

- PostGIS trên `venues.location` phục vụ tìm sân quanh vị trí hiện tại.
- Lịch trống chỉ hợp lệ khi nằm trong giờ hoạt động, có quy tắc giá, không bị khóa và không trùng booking đang giữ chỗ/đã xác nhận.

### 13.2. Đặt sân và thanh toán SePay

`bookings` → `booking_items` → `payments` → `sepay_webhook_logs` → cập nhật `payments` + `bookings` + `booking_status_history`

- Backend tạo booking ở trạng thái `pending_payment` và giữ lịch trong thời gian giới hạn.
- SePay gửi webhook; backend lưu log trước, chống trùng bằng `transaction_code`, sau đó đối chiếu số tiền và nội dung chuyển khoản.
- Khi hợp lệ, payment chuyển sang `paid`, booking chuyển sang `confirmed` và tạo thông báo cho người dùng.

### 13.3. Doanh thu chủ sân

`payments` + `bookings` + `platform_fee_configs` → `owner_balance_transactions` → `withdrawal_requests` → `payout_transactions`

- Số dư nên được tính từ sổ cái `owner_balance_transactions`.
- Mỗi khoản thu, phí, hoàn tiền hoặc rút tiền phải tạo một dòng biến động riêng để đối soát được.

### 13.4. Ghép cặp người chơi

`player_profiles` + `player_statistics` + `player_availability_slots` + `player_preferred_locations` → `player_match_profiles` → `algorithm_runs` → `player_clusters` + `partner_suggestions`

- Hồ sơ ghép cặp chứa dữ liệu đã tổng hợp để thuật toán không phải đọc lại toàn bộ lịch sử trận ở mỗi lần chạy.
- Khi người dùng đồng ý kết nối: `partner_suggestions` → `partner_requests` → `player_connections`.

### 13.5. Chuyển đổi dữ liệu BWF thành dữ liệu Courtly

CSV BWF → script làm sạch/chuyển đổi → `users` + `player_profiles` → `matches` + `match_players` + `match_games` + `match_results` → `player_statistics` + `player_match_profiles`

- File BWF được xử lý bên ngoài database, không tạo bảng BWF riêng.
- Tên vận động viên được chuyển thành tài khoản người chơi mẫu trong `users` và `player_profiles`.
- Trận, người tham gia, tỷ số và kết quả được chuyển vào các bảng trận đấu hiện có.
- Thống kê được tính lại theo cấu trúc Courtly rồi ghi vào `player_statistics` và `player_match_profiles`.
- Các dữ liệu BWF không cung cấp như lịch rảnh, vị trí và bán kính chơi không được tự suy diễn; cần để trống hoặc sinh dữ liệu mẫu có quy tắc riêng.

## 14. Các quan hệ cần lưu ý khi triển khai

1. `booking_items` phải có exclusion constraint ở database để chống đặt trùng, không chỉ kiểm tra bằng API.
2. `payments(provider, provider_transaction_id)` và `sepay_webhook_logs.transaction_code` phải unique để webhook không được xử lý hai lần.
3. `reports.target_id`, `audit_logs.entity_id` và `notifications.data_json` là quan hệ đa hình; backend phải kiểm tra đối tượng tồn tại vì database không thể tạo FK cố định.
4. Tài khoản được tạo từ BWF phải được đánh dấu là dữ liệu mẫu và không được phép đăng nhập; nên bổ sung trường nguồn dữ liệu trước khi triển khai import thật.
5. `double_team_suggestions.team1_players/team2_players` đang dùng JSONB. Nếu cần truy vấn thành viên thường xuyên, nên tách thêm bảng chi tiết đội.
6. Các trường số dư, thống kê và điểm rating là dữ liệu tổng hợp; cần cập nhật bằng transaction/job để tránh lệch với dữ liệu gốc.
7. Khi xóa dữ liệu cần quy định rõ `ON DELETE`: dữ liệu giao dịch, booking, thanh toán, webhook và audit nên ưu tiên giữ lịch sử thay vì xóa cứng.

## 15. Phạm vi triển khai đề xuất

- **MVP đặt sân:** tài khoản, phân quyền cơ bản, hồ sơ, sân, lịch hoạt động, giá, booking, payment SePay, thông báo và đánh giá sân.
- **Sprint ghép cặp:** lịch rảnh, địa điểm ưu tiên, trận đấu, thống kê, hồ sơ ghép cặp, thuật toán, gợi ý và kết nối người chơi.
- **Giai đoạn quản trị/doanh thu:** phí nền tảng, sổ cái chủ sân, rút tiền, payout, refund, report và audit.
