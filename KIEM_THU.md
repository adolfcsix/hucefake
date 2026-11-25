# Kiểm thử HUCE

Đã chạy mã nguồn trên Node.js 24.22.3, PostgreSQL 16.16 và Chromium với các cơ sở dữ liệu kiểm thử tách riêng.

| Hạng mục | Kết quả |
| --- | --- |
| Kiểm thử tự động frontend và backend | 22/22 bài đạt. Build Vite thành công. |
| Chế độ dùng thử | Đăng nhập nhanh, đăng nhập bằng mật khẩu, đăng xuất và kiểm tra quyền cả bốn vai trò. |
| Chế độ dữ liệu riêng | Tài khoản mẫu bị từ chối. Kiểm tra tạo danh mục và đăng nhập tài khoản riêng bằng công cụ thiết lập local. |
| Bàn giao lịch giữa các vai trò | Nhân viên tạo và nhập lớp nháp, phân công, xếp tay và chạy xếp tự động. Quản lý có thể trả lại, sau đó nhận bản gửi lại và công bố. Giảng viên và sinh viên nhận đúng lịch. |
| Danh mục và nhập lớp | Kiểm tra thêm/sửa phòng, ngày không học, kiểm tra khung tiết chồng giờ và hoàn tác toàn bộ lượt nhập khi một dòng sai. |
| Đổi lịch | Giảng viên gửi đề nghị, nhân viên đúng khoa xử lý, lịch gốc được hủy, lịch bù xuất hiện và người liên quan nhận thông báo. Riêng ngày nghỉ, ngày ngoài học kỳ và tài khoản sai quyền đều bị chặn. |
| HUCEChat | Kiểm tra tra cứu đúng quyền, hỏi tiếp theo ngày, lưu và mở lại lịch sử, xem trước, hủy, xác nhận lặp không ghi trùng và chặn xác nhận sai chủ sở hữu. Kiểm tra tranh chấp mã lớp giữa lúc xem trước và xác nhận. |
| Trình duyệt | Mở 21 mục menu của bốn vai trò. Gửi câu hỏi, mở lại hội thoại, tạo lớp qua chat rồi mở trang danh sách, kiểm tra khung chat ở 320 và 390 px. |
| Nâng cấp cơ sở dữ liệu | Chạy migration V7 trên bản đã có V6, lớp học phần và đề xuất đang lưu. Chạy lại migration và kiểm tra dữ liệu. |
| Gỡ bốn nút đăng nhập nhanh | Áp dụng nguyên các đoạn xóa trong README trên bản sao, gồm tùy chọn gỡ endpoint. Build Vite thành công. Cả bốn vai trò đăng nhập bằng mật khẩu qua Express và qua proxy Vite. Kiểm thử luồng nghiệp vụ chạy lại trên database demo mới sau khi gỡ. |
| Docker | Hướng dẫn giữ cấu hình Compose của dự án. Đợt này xác minh bằng Node.js, Vite và PostgreSQL trực tiếp vì môi trường kiểm thử thiếu Docker. |
| Gemini | Đã thử kiểm tra hợp đồng phản hồi bằng trình cung cấp giả lập. Chưa gọi dịch vụ Gemini thật vì môi trường kiểm thử không có khóa. |

Chạy lại kiểm thử đơn vị bằng `npm test` tại từng thư mục `frontend/`, `backend/`. Xem mục **Chạy kiểm thử liên vai trò** trong README để chạy `test:workflows` trên một cơ sở dữ liệu demo mới. Tệp ZIP chỉ chứa mã nguồn, tài liệu, migration và dữ liệu mẫu dưới dạng kịch bản.
