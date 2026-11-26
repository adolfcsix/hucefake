# Kiểm thử HUCE — Spring Boot

## Bản chính thức đã nghiệm thu

PR #1 đã merge vào main, commit `d97c622b525e27b42559eb4d389d17302cf0ef06`. [CI main 3/3 PASS](https://github.com/adolfcsix/hucefake/actions/runs/45808094694). PR #3 đã merge vào main tại commit `7f1c6d92c51c0c578963fc44cd8bc659e857a965`. [CI main sau merge 3/3 PASS](https://github.com/adolfcsix/hucefake/actions/runs/45837993682).

## Mốc xác minh gần nhất

| Commit | Kết quả thực tế | Bằng chứng |
| --- | --- | --- |
| d97c622 | main 3/3 PASS, 63 route, 63 lượt Chromium, GA 3/3, npm audit 0 | [CI](https://github.com/adolfcsix/hucefake/actions/runs/45808094694) |
| 6bfa4d0 | PR #3 3/3 PASS; CRUD qua Vite, chống ghi đè, xếp lịch, duyệt/công bố, CSV/ICS đúng phạm vi | [CI](https://github.com/adolfcsix/hucefake/actions/runs/45819722170) |
| bb2ae40 | PR #3 3/3 PASS; sao lưu/khôi phục, checksum, ghi sau restore, phục hồi kết nối và restart PostgreSQL/Java | [CI](https://github.com/adolfcsix/hucefake/actions/runs/45823815464) |
| d2ded5a | PR #3 3/3 PASS; source integrity, 147 lượt Chromium, mọi nhóm hồi quy | [CI](https://github.com/adolfcsix/hucefake/actions/runs/45825169200) |
| 7f1c6d9 | main sau merge 3/3 PASS; 63 demo + 84 private, 63 route, 34 import, 49 tham chiếu API tĩnh, backup/restore, recovery, npm audit 0, GA 3/3 | [CI](https://github.com/adolfcsix/hucefake/actions/runs/45837993682) |

Kết quả chỉ áp dụng cho SHA tương ứng; không mặc định kế thừa PASS sang commit mới. Benchmark main: 48 lớp, 96 buổi, 16 giảng viên, 12 nhóm; ba seed mất 4907, 4362, 4295 ms, 0 vi phạm cứng, kiểm tra SQL độc lập PASS. Không phải cam kết hiệu năng toàn trường.

## Ma trận nghiệm thu

| Nhóm | Phạm vi |
| --- | --- |
| API và mã nguồn | 63 route Spring; kiểm tra tệp theo dõi, mẫu trắng, Vite lock, import và tiền tố API tĩnh |
| Integration | PostgreSQL thật, CRUD, phân công, danh mục, xếp lịch, GA, gửi duyệt, công bố, lịch cá nhân, đổi lịch, thông báo, HUCEChat |
| Private | Java CLI, database trắng, bốn tài khoản riêng, rollback, chặn demo, dump/restore vào database khác |
| Container | React được Java phục vụ, PostgreSQL, health check, phiên đăng nhập |
| Chromium demo | 63 lượt mở trang ở 1440/390/320 px; đăng nhập, chatbot, CRUD, xung đột phiên bản, công bố, xuất lịch |
| Chromium private | 84 lượt đã đạt tại 7f1c6d9 ở 1440/390 px, trước/sau nạp học kỳ; tạo lớp riêng và đọc lại |
| Recovery | Mô phỏng lỗi mạng, restart PostgreSQL và Java, giữ phiên và dữ liệu |
| Dependency audit | npm audit toàn bộ và production; không bỏ qua mức cao/critical |

Các lệnh: Maven `mvn clean verify` trong backend; từ gốc `node backend/tests/api-surface.mjs` và `node backend/tests/source-integrity.mjs`; trong frontend `npm ci`, `npm test`, `npm run build`. Bài tích hợp ghi dữ liệu và bài Docker phục hồi chỉ dùng database CI/demo riêng, không chạy trên dữ liệu thật.

## Rà soát cuối

- Loại Express khỏi runtime; giữ các định danh nội bộ fm05 trong migration, giao thức import và UID lịch để không phá tương thích dữ liệu/nhập lịch cũ.
- Đổi tên tệp lớp nháp và PRODID lịch tải xuống sang HUCE; giữ UID cũ.
- Không xóa CSS cùng selector nếu thuộc tính bổ sung có chủ đích. Giữ giao diện hiện tại.
- Không đưa dữ liệu riêng, tệp cấu hình bí mật, dump hoặc báo cáo tạo tự động vào Git.
- Kiểm tra API tĩnh chỉ đối chiếu tiền tố, không chứng minh mọi đường dẫn động hoặc mọi hành vi; các bài HTTP/Chromium bổ sung bằng chứng.

## Giới hạn còn lại

Gemini API thật chưa kiểm thử do thiếu khóa hợp lệ; chuyển dữ liệu từ database Express cũ chưa nghiệm thu; chưa có benchmark quy mô toàn trường, kiểm toán đầy đủ dependency Java hay pentest độc lập. Không khẳng định hệ thống không thể phát sinh lỗi. CI trên main 7f1c6d9 đã PASS. Bản cập nhật tài liệu Gemini không thay đổi logic; vẫn kiểm tra CI theo SHA mới.

Lịch sử chi tiết các mốc sửa runner Ubuntu, Docker mirror, Maven 429, Vite/esbuild và các bản chuyển đổi có trong Git history và Actions.

## Khắc phục phụ thuộc Maven trong CI

Commit fc282f8 chỉ đổi tài liệu. [CI](https://github.com/adolfcsix/hucefake/actions/runs/45865316425): smoke và dependency-audit PASS; container thất bại tại bước build vì Maven Central HTTP 429 sau ba lần thử (chưa khởi động app). Cách sửa: chuyển repository Maven đã tải thành công trong job smoke qua artifact cùng workflow, kiểm tra SHA-256 rồi đóng gói Java trong Docker ở chế độ offline; vẫn build React và Java, chạy toàn bộ Chromium và private Docker. Artifact tạm giữ một ngày, không commit vào Git, không đưa vào runtime image. Build local không có artifact vẫn dùng Maven trực tuyến có cache/retry. Chưa xác nhận PASS cho đến khi CI mới kết thúc. PR #2 đã đóng vì nội dung README đã có trên main; giữ lịch sử các nhánh cũ.

Mốc 2016d49: [CI](https://github.com/adolfcsix/hucefake/actions/runs/45866266991) smoke và dependency-audit PASS nhưng Maven offline trong container không nhận repository ID của POM đã có. Thay thế hoàn toàn cách truyền cache bằng JAR đóng gói trên chính runner smoke sau build React. So sánh SHA-256 toàn bộ class/resource Java (trừ static) và dependency nhúng với JAR đã qua kiểm thử; đối chiếu từng asset React với dist. Docker target ci kiểm tra commit và checksum, chạy đúng artifact, vẫn thực hiện đầy đủ Chromium demo/private và recovery. Target local mặc định vẫn build trực tiếp từ source. Chưa có kết quả CI bản sửa mới.
