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

Mốc 5066a97: [CI 2/2 PASS và 63 lượt Chromium](https://github.com/adolfcsix/hucefake/actions/runs/45801085137). Mốc 8987d4b: [smoke PASS, container FAIL do Ubuntu mirror lệch kích thước gói](https://github.com/adolfcsix/hucefake/actions/runs/45801264899); smoke gồm thu hồi phiên, CSRF, giới hạn đăng nhập. Đã thay curl trong container bằng health probe Java; chờ CI HEAD. Bổ sung benchmark tích hợp 48 lớp/96 buổi, 3 seed, lưu thời gian và fitness, xác minh SQL độc lập; chưa có số đo trước khi CI chạy. Đây là tải kiểm thử trung bình, không phải cam kết hiệu năng quy mô toàn trường.

Mốc 1adeac0: [CI](https://github.com/adolfcsix/hucefake/actions/runs/45802501746) smoke PASS. Benchmark 3/3, 48 lớp, 96 buổi mỗi lần, quần thể 24, 40 thế hệ; seed 2026: 5851 ms, phạt mềm 64; seed 73: 5276 ms, phạt mềm 71; seed 911: 5661 ms, phạt mềm 47; cả ba 0 vi phạm cứng, SQL độc lập PASS. Container FAIL do Maven Central HTTP 429 khi tải parent POM. Bổ sung cache build, retry có giới hạn chỉ cho lỗi mạng, chạy container sau smoke và hủy CI cũ khi HEAD đổi. npm ci ghi nhận 1 cảnh báo mức cao chưa xác định thư viện; job dependency-audit xuất cả báo cáo đầy đủ lẫn runtime và không bỏ qua lỗi mức cao. Chưa được xem là đã xử lý lỗ hổng cho đến khi có báo cáo và kiểm thử lại.

Mốc aa26f83: npm audit xác định Vite 7.2.2 có cảnh báo mức cao trong dev server, production audit 0. Theo fixAvailable từ npm audit, nâng Vite lên 7.3.8 và esbuild lên 0.28.1 (theo range mới). Lockfile cập nhật bằng metadata và integrity chính thức registry.npmjs.org cho toàn bộ binary đa nền tảng. Chờ npm ci, audit, build và Chromium trên HEAD mới; không coi đã khắc phục cho đến khi kiểm thử đạt.
