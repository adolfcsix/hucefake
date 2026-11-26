# HUCE — chuyển đổi backend Spring Boot (nhánh đang phát triển)

**Chưa thay thế backend hiện tại. Không dùng nhánh này để bàn giao.** Đây là phần chuyển đổi đầu tiên, không có proxy sang Express và không giả lập các API nghiệp vụ chưa chuyển.

- Java 21, Spring Boot 3.5.8, Spring MVC, Spring Security, Spring JDBC, Spring Session JDBC, Flyway, PostgreSQL.
- Dùng lại SQL migrations của repo và frontend React/Vite hiện tại.
- Đã chuyển mã nguồn cho health, CSRF, đăng nhập thường/demo, đăng xuất, me và đọc danh mục. Đã chuyển seed demo cho danh mục, tài khoản, lớp nháp, phân công, nhóm đối tượng và đăng ký mẫu. Dùng database demo mới để kiểm thử.
- Đã chuyển danh sách/phân trang, tạo/sửa/hủy/nhập lớp học phần, đọc lịch định kỳ và nền tảng chống lặp trong transaction. Đã chuyển ghi danh mục, cập nhật phân công/nhóm đối tượng, ghi lịch định kỳ, kiểm tra xung đột. Đã chuyển thêm xếp lịch tự động bằng Java (GA, hàng đợi, tiến trình, áp dụng/bỏ phương án). Đã chuyển phê duyệt/công bố, lịch cá nhân và các chế độ xem lịch, thống kê và thông báo. Điều chỉnh buổi học, đề nghị đổi lịch, khai báo bận, HUCEChat/Gemini và script nhập dữ liệu riêng vẫn cần chuyển từ Node.js sang Java. Không chạy chung hai backend trên cùng database.
- Migrations hiện chỉ hỗ trợ database PostgreSQL mới. Không baseline hoặc tự động nâng cấp database Node.js có dữ liệu; cần quy trình sao lưu, kiểm tra lịch sử migration và chuyển dữ liệu riêng.

## Khởi chạy thử nền tảng
Cài JDK 21, Maven 3.9+, Node.js 22.12+ và PostgreSQL 16 (hoặc Docker Compose).
Từ thư mục repo:

```sh
docker compose up -d postgres
docker compose exec postgres createdb -U fm05 huce_spring
cd spring-backend
```

Đặt biến môi trường DB_URL=jdbc:postgresql://127.0.0.1:5432/huce_spring, DB_USER=fm05, DB_PASSWORD theo PostgreSQL và DEMO=true **chỉ cho database thử**. Trên PowerShell dùng cú pháp $env:DEMO="true"; trên Bash dùng export DEMO=true. Mặc định DEMO=false. Sau đó:

```sh
mvn clean verify
mvn spring-boot:run
```

Frontend vẫn dùng cấu hình proxy hiện tại (cổng 8080):
```sh
cd frontend
npm ci
npm run dev
```

Chỉ kiểm tra các API đã chuyển. Không kỳ vọng các trang nghiệp vụ hoạt động đầy đủ. Không tự động gộp nhánh này vào main.

## Nghiệm thu trước khi thay backend
- [ ] Maven build/test và PostgreSQL migration mới thành công.
- [ ] Đối chiếu tất cả API và JSON response với Express, bao gồm lỗi/CSRF/phân quyền/idempotency.
- [ ] Chuyển đầy đủ setup tài khoản, demo/private, nhập xuất và các nghiệp vụ bốn vai trò.
- [ ] Chuyển worker thuật toán xếp lịch và HUCEChat cùng kiểm thử quyền dữ liệu, Gemini, xác nhận thao tác.
- [ ] Chạy kiểm thử workflow end-to-end bằng Spring Boot + PostgreSQL thật và build React.
- [ ] Chuyển Docker/README; gỡ backend Node.js cũ chỉ sau khi đạt toàn bộ gate. Node.js vẫn dùng cho Vite.

CI chạy bài kiểm tra hợp đồng API trong `tests/section-contract.mjs` trên PostgreSQL thật, bao gồm quyền từng vai trò, phạm vi khoa, dữ liệu ngày/mảng, phân trang, rollback nhập hàng loạt, version và idempotency. Kiểm tra trạng thái Actions của HEAD trước khi nghiệm thu.

Bổ sung kiểm thử phân công, nhóm đối tượng, xung đột phòng/giảng viên/lớp, khung tiết, phiên bản lịch và rollback sửa phòng đang sử dụng.

Kiểm thử GA trong section-contract.mjs: dữ liệu thiếu, phân quyền, theo dõi tiến độ, 0 vi phạm cứng, từ chối phương án lỗi thời, áp dụng nguyên tử và chống áp dụng trùng. GA Java giữ quy tắc nghiệp vụ và fitness, nhưng chưa chứng minh tương đương hiệu năng/điểm với worker JS trên bộ benchmark lớn.

Bài kiểm thử tiếp tục qua gửi duyệt, trả lại, gửi lại, phê duyệt, công bố, đọc lịch theo quyền và thông báo riêng trên PostgreSQL thật.
