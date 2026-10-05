# TikGoon

TikGoon là module Xposed mã nguồn mở cho **TikTok 47.0.3** (`com.ss.android.ugc.trill`). Ứng dụng chạy với KernelSU Next, Zygisk Next và một framework Xposed hỗ trợ API legacy. Đây là APK cài như ứng dụng, không phải ZIP flash trong KernelSU.

Các chức năng hiện có gồm lọc feed, giả lập vùng, tùy chỉnh giao diện, nền ảnh/video cho các vùng tối và bình luận, đổi icon TikTok trên MIUI Home, tải video không watermark bằng nút tải sẵn có, tùy chỉnh thanh tua, và tắt tự dịch bình luận LIVE. Xem [FEATURES.md](FEATURES.md) để biết chi tiết và giới hạn kiểm thử.

## Cài đặt

1. Cài APK TikGoon từ [Releases](https://github.com/binxgtl/TikGoon/releases).
2. Bật module trong LSPosed và chọn TikTok (`com.ss.android.ugc.trill`). Nếu muốn đổi icon của TikTok trên MIUI Home, chọn thêm `com.miui.home`.
3. Cấp quyền root cho TikGoon trong KernelSU khi lưu cài đặt. Mở TikGoon để chỉnh tính năng, sau đó buộc dừng và mở lại TikTok.

Các hook dựa trên TikTok 47.0.3 và có thể không hoạt động sau khi TikTok cập nhật. Ảnh, video nền và icon tùy chọn chỉ lưu cục bộ trên máy. Tùy chọn đổi icon MIUI Home chỉ áp dụng cho launcher đó.

## Build cục bộ

Cần Python 3, JDK 21, Android SDK platform 36 và build-tools 36.1.0. Chạy `python build.py` ở thư mục gốc repo. Script tải Xposed API 82 và xác minh SHA-256 trước khi biên dịch. Bản build cục bộ mặc định được ký bằng khóa thử nghiệm ở `build/debug.keystore` và tạo `build/TikGoon-debug.apk`.

Giữ lại khóa ký nếu muốn APK tự cập nhật trên thiết bị đã cài. Không commit khóa vào Git. Package Android vẫn là `dev.tiktokrootmod` để các bản mới cập nhật bản cũ và giữ cài đặt.

## Phát hành

Push tag khớp với `versionName` trong `AndroidManifest.xml`, ví dụ `v0.8.6`. Workflow `.github/workflows/release.yml` build trên Windows, kiểm tra tag và chữ ký, rồi tạo GitHub Release với APK và SHA-256. Workflow dùng bốn GitHub Actions secrets: `TIKGOON_KEYSTORE_BASE64`, `TIKGOON_KEYSTORE_PASSWORD`, `TIKGOON_KEY_ALIAS`, `TIKGOON_KEY_PASSWORD`. Khóa ký của bản phát hành phải được giữ ổn định giữa các lần release.

## Giấy phép và phạm vi

Mã nguồn TikGoon do dự án viết được phát hành theo [MIT License](LICENSE). Repo không chứa APK, DEX, thư viện, hình ảnh, biểu tượng hoặc mã nguồn TikTok/TikTok You. Tên TikTok chỉ dùng để chỉ ứng dụng tương thích; TikGoon không liên kết hay được TikTok xác nhận. Giấy phép MIT chỉ áp dụng cho mã của dự án, không cấp quyền đối với phần mềm hoặc nhãn hiệu của bên thứ ba.
