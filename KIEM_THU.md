# Kiểm thử HUCE — Spring Boot

## Mốc bàn giao đã xác minh

PR #1 đã merge vào main, commit d97c622b525e27b42559eb4d389d17302cf0ef06. [CI main 3/3 PASS](https://github.com/adolfcsix/hucefake/actions/runs/45808094694); [CI PR 3/3 PASS](https://github.com/adolfcsix/hucefake/actions/runs/45803267908). Log main xác nhận 63 route, 63 lượt mở trang Chromium trên 3 kích thước, toàn bộ nhóm nghiệp vụ, bản trắng, bảo mật và npm audit 0 cả dev/runtime. Benchmark 48 lớp/96 buổi: 4907, 4362, 4295 ms với 0 vi phạm cứng, SQL độc lập PASS. Kết quả chỉ áp dụng cho SHA đã kiểm thử.

## Phạm vi kiểm thử

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

## Bổ sung sau bàn giao

Bài kiểm thử Chromium mới tại `frontend/e2e/workflow.mjs` chạy qua Vite proxy đến Java trong Docker, bao gồm tạo/sửa/hủy lớp, xung đột phiên bản giữa hai màn hình, phân công, lưu nhóm đối tượng và quản lý đọc dữ liệu sau reload. Chỉ chạy trên demo CI riêng. Chờ kết quả CI của PR bổ sung trước khi gộp main.

Bổ sung `frontend/e2e/scheduling.mjs`: chạy sau workflow CRUD trên database demo CI; xếp một buổi thủ công, chạy và áp dụng GA, trả lại/gửi lại/duyệt công bố, đối chiếu lịch bốn vai trò và kiểm tra CSV/ICS của sinh viên, giảng viên không chứa lớp ngoài phạm vi. Chưa có kết quả CI trên commit bổ sung.

Mốc 6bfa4d0: [PR #3 CI 3/3 PASS](https://github.com/adolfcsix/hucefake/actions/runs/45819722170), gồm Chromium 63 lượt, thao tác CRUD/xung đột phiên bản/đọc lại qua Vite proxy, xếp lịch thủ công/GA/trả lại/gửi lại/công bố, CSV/ICS đúng phạm vi. Bổ sung sau mốc này: pg_dump/pg_restore sang DB riêng, checksum dữ liệu nghiệp vụ, không phục hồi phiên, đăng nhập bốn vai trò và ghi dữ liệu sau restore; frontend phân biệt lỗi mạng với 401, tải lại danh mục, kiểm thử gián đoạn mạng mô phỏng và restart PostgreSQL/Java thật trong CI. Chờ nghiệm thu HEAD mới.

Mốc bb2ae40: [CI 3/3 PASS](https://github.com/adolfcsix/hucefake/actions/runs/45823815464); log xác nhận pg_dump/restore, checksum, bốn tài khoản, ghi sau restore, gián đoạn mạng mô phỏng và restart PostgreSQL/Java thực tế. Bổ sung `frontend/e2e/private-browser.sh` và `.mjs`: Compose project/volume riêng, bốn tài khoản qua Java CLI, kiểm thử 21 mục menu ở 1440/390px trước và sau khi nạp học kỳ (84 lượt), HUCEChat, không có nút mẫu, tạo lớp thật và đọc lại ở quản lý. Trạng thái chưa có học kỳ có hướng dẫn theo vai trò và nút tải lại. Chờ CI HEAD mới.
