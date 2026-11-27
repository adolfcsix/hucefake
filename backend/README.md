# Backend HUCE

Java 21, Spring Boot, Spring Security, Spring JDBC, Spring Session JDBC, Flyway và PostgreSQL.

- `src/main/java/vn/huce/`: API, service nghiệp vụ, giải thuật di truyền, HUCEChat, bảo mật và CLI.
- `src/main/resources/db/migration/`: migration PostgreSQL.
- `src/main/resources/db/demo/`: danh mục mẫu, chỉ nạp trong chế độ demo.
- `tests/`: kiểm thử HTTP tích hợp chạy bằng Node.js như một công cụ kiểm thử, không phải backend.
- `pom.xml`: Maven.

Xem README gốc để cài đặt, cấu hình và chạy. Không dùng database Express cũ trực tiếp; cần sao lưu và quy trình chuyển dữ liệu riêng.


## Tổ chức mã nguồn Java

Mã nguồn nằm tại `src/main/java/vn/huce`. Lớp khởi động `HuceApplication` ở package gốc; `Accounts.Actor` giữ định danh đã sử dụng trong phiên Spring Session JDBC để tương thích với phiên cũ.

| Package | Trách nhiệm |
| --- | --- |
| controller | Ánh xạ HTTP, nhận dữ liệu, gọi service, trả trạng thái HTTP |
| service | Nghiệp vụ, phân quyền theo phạm vi, xử lý xếp lịch và chatbot |
| repository | Truy cập PostgreSQL; AccountRepository và CatalogRepository chứa truy vấn theo nghiệp vụ |
| dto | Kiểu dữ liệu truyền tải qua API |
| validation | Kiểm tra và chuẩn hóa dữ liệu |
| exception | Ngoại lệ và xử lý lỗi HTTP tập trung |
| config, security | Cấu hình Spring, xác thực và phiên đăng nhập |
| cli | Lệnh thiết lập dữ liệu riêng và healthcheck |

Luồng chính: React → Controller → Service → Repository → PostgreSQL. Các service không được gọi controller. Controller không được chứa SQL hoặc phụ thuộc trực tiếp repository/JdbcTemplate.

`SqlStore` là lớp tương thích cho các truy vấn động cũ. Một số service vẫn chứa SQL và các projection kiểu Map; các phần này cần được chuyển dần sang repository theo nghiệp vụ và kiểu dữ liệu cụ thể, giữ nguyên hợp đồng API và kiểm thử hồi quy. Không coi việc chia package là đã hoàn tất toàn bộ cải tổ.

Kiểm tra nhanh (từ thư mục backend): `node tests/api-surface.mjs` kiểm kê REST API và kiểm tra ranh giới kiến trúc; `mvn clean verify` biên dịch và chạy unit test. Kiểm thử PostgreSQL, trình duyệt và đóng gói chạy qua GitHub Actions.

Nhóm nghiệp vụ HUCEChat nằm trong `service/chat/` (nhận diện intent, quyền, khoảng ngày, Gemini, hội thoại và đề xuất); unit test tương ứng nằm trong `src/test/java/vn/huce/service/chat/`.


### Lớp học phần: phân tầng repository và model

- `model/SectionDraft.java`: dữ liệu nháp đã kiểm tra, với tên thuộc tính nghiệp vụ và ngày kiểu LocalDate.
- `repository/SectionRepository.java`: truy vấn lớp, lịch định kỳ, học kỳ, học phần, kiểm tra trùng mã và tạo bản ghi.
- `service/SectionService.java`: kiểm tra quyền, trạng thái, ngày hiệu lực và quy tắc lớp nháp; không trực tiếp thực thi SQL.
- `repository/AuditRepository.java`: ghi nhật ký thay đổi; adapter SqlStore cũ gọi chung repository này để tránh trùng câu lệnh.
- `service/SectionMutationService.java`: các thao tác sửa lớp và lịch; không thực thi SQL trực tiếp; gọi SectionRepository và SchedulePatternRepository.

Kiểm thử source-integrity rà cả import trong mã frontend, unit test và E2E để phát hiện đường dẫn cũ khi đổi thư mục.

`PatternDraft` định kiểu dữ liệu lịch định kỳ; `SchedulePatternRepository` phụ trách đọc, ghi, xóa lịch. Các thao tác ghi tiếp tục tham gia giao dịch và khóa của MutationService; không thay đổi hợp đồng HTTP hoặc schema.
