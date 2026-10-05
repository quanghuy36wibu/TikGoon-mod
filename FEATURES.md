# Đối chiếu TikTok You 4.4 và module cho TikTok gốc 47.0.3

Trạng thái ở đây dựa trên APK TikTok You 46.8.3 đã phân tích và module hiện tại. "Đã hook" chỉ xác nhận hook gắn được; tác dụng trong mọi màn hình cần thử trên thiết bị.

| Nhóm | Chức năng | Trạng thái |
| --- | --- | --- |
| Feed | Ẩn quảng cáo | Đã hook, mặc định bật |
| Feed | Ẩn live, ảnh, story, mini-series, nội dung trả phí | LIVE: bản 0.6.2 đã bổ sung loại bài 104/105 và dữ liệu phòng; log trên máy xác nhận đã lọc một LIVE. Các loại khác cần thử |
| LIVE | Tắt tự dịch bình luận trong phòng LIVE | Bản 0.6.7 hook trạng thái mặc định và cổng tự dịch của public-screen TikTok 47.0.3; cần thử trong phiên LIVE |
| Feed | Lọc từ khóa trong mô tả, lượt thích, lượt xem, ngày đăng | Đã viết, mặc định tắt; bộ lọc từ khóa chưa xét đầy đủ tag/nhạc/tác giả |
| Chia sẻ | Làm sạch link TikTok khi copy | Đã hook, mặc định bật |
| Vùng | Giả lập SIM/mạng; chọn 191 quốc gia/vùng hoặc nhập ISO/MCC-MNC/nhà mạng | Đã hook, mặc định Kazakhstan; chưa giả lập mọi tín hiệu vị trí |
| Khác | Bỏ chặn chụp/quay màn hình | Đã hook, mặc định bật |
| Video | Thanh tua luôn hiện | Bản 0.6.5 hook đúng `X.06l9.setSeekBarShowType`; đã thấy thanh tua trên ba video liên tiếp sau khi mở lại TikTok. Cần người dùng thử thêm các loại video |
| Giao diện | Tối giản và giảm lưu ảnh OLED | Bản 0.6.6 giữ lớp phủ feed ở trạng thái ẩn khi TikTok tái dùng view; đã kiểm tra qua hai video. Giảm lưu ảnh OLED làm mờ lớp phủ xuống 35% và dịch chuyển nhẹ; đã kiểm tra riêng trên máy. Khi bật cả hai, tối giản ẩn lớp phủ nên hiệu ứng làm mờ khó thấy |
| Tải media | Bỏ watermark trong luồng tải video có sẵn của TikTok | Bản 0.6.3 chọn URL tải trong `X.0ztG`; người dùng xác nhận video tải không còn watermark |
| Tải media | Ảnh không watermark, chọn chất lượng, tải âm thanh, sticker | Chưa làm |
| Tin nhắn | Tự duy trì streak, chọn người nhận/sticker/giờ gửi | Chưa làm; cần xác định API gửi tin nhắn an toàn trên bản gốc |
| Tin nhắn | Tăng giới hạn người nhận chia sẻ/ghim chat | Đã viết hook, mặc định tắt; cần kiểm thử |
| Offline | Tùy chọn số lượng video lưu offline | Chưa làm |
| Giao diện | Chọn font mặc định/serif/monospace | Đã viết hook, mặc định giữ nguyên; cần kiểm thử |
| Giao diện | Màu HEX, rainbow, ảnh/video thay nền tối và khung bình luận, icon TikTok trên MIUI Home | Bản 0.8.5: người dùng xác nhận ảnh/video nền và icon hoạt động; video khung bình luận đã sửa để không đẩy nội dung. Có nút về logo gốc |
| Hồ sơ | Mở cổng tải ảnh nền hồ sơ của TikTok (`profile_bg_in_allow_list`, `profile_bg_enable_consumption_group`) | Đã xác nhận nút chỉnh ảnh nền xuất hiện trên TikTok gốc 47.0.3; chưa kiểm tra upload và hiển thị trên máy khác |
| Hồ sơ | Banner trang trí từ `bannerUrl` của hệ thống badge TikTok You | Không thuộc tính năng ảnh nền hồ sơ TikTok; chưa làm |
| Khác | Dịch bình luận, link bio mở ngoài, các khóa cấu hình, badge | Chưa làm |
| Cập nhật | Tự cập nhật và gói mã động của TikTok You | Không đưa mã/máy chủ TikTok You vào module độc lập; cơ chế cập nhật riêng chưa làm |

Module chỉ nhắm package `com.ss.android.ugc.trill` 47.0.3. TikTok You dùng package và phiên bản khác, nên hook và tài nguyên của nó không thể chép trực tiếp sang TikTok gốc. Mọi tính năng mới cần xác minh class/method trên bản gốc và thử bằng tài khoản/thiết bị kiểm thử.
