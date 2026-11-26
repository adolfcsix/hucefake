# Kiểm thử HUCE — Spring Boot

Mốc đã xác minh trước khi dọn backend: commit 482d6de, [CI 2/2 thành công](https://github.com/adolfcsix/hucefake/actions/runs/45795044098). Kết quả đó không tự động áp dụng cho commit mới.

| Kiểm thử | Phạm vi |
| --- | --- |
| API surface | Đối chiếu 63 route HTTP cũ với mapping Spring; không chứng minh tương đương mọi hành vi |
| Integration | PostgreSQL thật, lớp, phân công, danh mục, xếp lịch, GA, gửi duyệt, công bố, lịch cá nhân, đổi lịch, thông báo, HUCEChat |
| Private | Java CLI, database trắng, bốn tài khoản riêng, rollback, chặn demo |
| Container | React do Java phục vụ, PostgreSQL, đăng nhập |
| Chromium | Mở các mục menu của bốn vai trò tại 1440/390/320 px, đăng nhập, chatbot, đăng xuất |

Kiểm tra kết quả CI trên đúng SHA mới nhất trước khi bàn giao. Gemini API thật chưa kiểm thử do không có key; GA benchmark lớn và chuyển dữ liệu từ database Express cũ chưa nghiệm thu. Chromium smoke test không thay thế toàn bộ kiểm thử thao tác UI và kiểm toán bảo mật.

Các lệnh: Maven `mvn clean verify` trong backend; `node backend/tests/api-surface.mjs` tại gốc; `npm test` và `npm run build` trong frontend. Các test tích hợp ghi dữ liệu, chỉ chạy trên database demo/CI mới.
