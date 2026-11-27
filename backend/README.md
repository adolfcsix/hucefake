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


### Danh mục phòng học, ngày nghỉ và khung tiết

`CatalogMutationService` xử lý quyền theo cơ sở, dữ liệu đầu vào, ngày giờ hiệu lực và kiểm tra xung đột; không thực thi SQL trực tiếp. `CatalogMutationRepository` phụ trách các truy vấn và thao tác ghi. Ba model `RoomDraft`, `HolidayDraft`, `PeriodDraft` mô tả dữ liệu đã kiểm tra, trong đó ngày giờ sử dụng LocalDate và LocalTime. Việc ghi dữ liệu và nhật ký vẫn nằm trong giao dịch hiện có của MutationService.


### Phê duyệt và công bố thời khóa biểu

`ApprovalService` xử lý điều kiện gửi duyệt, quyền quyết định, đối chiếu phương án và sinh buổi học. `ApprovalRepository` sở hữu SQL của quy trình, `ApprovalSnapshot` giữ cấu trúc dữ liệu phương án đã gửi tương thích payload cũ. `NotificationService` và `NotificationRepository` phụ trách thông báo cho quản lý, người gửi và thành viên lớp; nghiệp vụ đổi lịch gọi trực tiếp NotificationService thay vì phụ thuộc ApprovalService. Các thao tác ghi vẫn dùng giao dịch và khóa chống lặp của MutationService. Một số projection Map và cấu trúc payload cũ được giữ để không phá vỡ hợp đồng API.


### Điều chỉnh buổi học và lịch rảnh giảng viên

`SessionMutationService` giữ nghiệp vụ đổi phòng, nghỉ học, học bù và xử lý đề nghị. `SessionChangeRepository` sở hữu truy vấn và thao tác ghi tương ứng. `AvailabilityService` kiểm tra quyền, học kỳ, định kiểu các khoảng lịch rảnh thành `AvailabilityEntry` và kiểm tra xung đột; `AvailabilityRepository` thực hiện truy vấn. Thông báo sử dụng NotificationService. Các thao tác ghi tiếp tục nằm trong giao dịch MutationService để rollback khi phát hiện xung đột. SessionReadService sử dụng SessionReadRepository; HUCEChat gọi phương thức nghiệp vụ thay vì nhận SQL.


### Truy vấn lịch và thông báo

`ScheduleFilter` biểu diễn bộ lọc đã kiểm tra bằng LocalDate và enum View. `SessionReadService` kiểm tra quyền và đầu vào, `SessionReadRepository` xây dựng và thực hiện SQL có tham số theo phạm vi tài khoản. Cả HTTP và HUCEChat dùng chung service cho lịch cá nhân, danh sách lớp và buổi tiếp theo. NotificationRepository phụ trách đọc và đánh dấu thông báo theo tài khoản. Các projection động và hợp đồng JSON cũ được giữ nguyên trong đợt này; phần lưu hội thoại và đề xuất của HUCEChat đã chuyển vào repository; vẫn cần chuẩn hóa lớp phân loại, điều phối phản hồi và DTO.


### Lưu hội thoại và đề xuất HUCEChat

`ChatRepository` lưu hội thoại, lịch sử trao đổi và phạm vi người dùng/role/khoa/giảng viên. `ProposalRepository` lưu đề xuất và khóa bản ghi khi xác nhận. `ProposalService` giữ kiểm tra quyền, trạng thái, thời hạn và chống thực thi lặp; `ChatService` điều phối nội dung. Thao tác thông báo sử dụng NotificationRepository. Các hợp đồng JSON và giao dịch MutationService vẫn được giữ; chưa hoàn tất tách lớp điều phối phản hồi và chuẩn hóa DTO.


### Phân loại và phản hồi HUCEChat

`ChatClassifier` kiểm tra phạm vi hội thoại, nhận diện ý định bằng quy tắc hoặc Gemini và đối chiếu quyền. `ChatClassification` là DTO kết quả phân loại; không phải giấy phép thực thi. `ChatReplyService` kiểm tra quyền lại và điều phối các phương thức nghiệp vụ theo từng nhóm phản hồi. `ChatService` lưu lịch sử và điều phối luồng tin nhắn. `ProposalService` tiếp tục yêu cầu xác nhận trước khi ghi nghiệp vụ. Cấu trúc JSON phía React không thay đổi; các Map tương thích cũ và việc định kiểu toàn bộ đề xuất vẫn cần rà soát.


### Xếp lịch và khung thời gian

`SchedulingRepository` phụ trách đọc dữ liệu xếp lịch, cơ sở được phép và truy vấn xung đột với buổi học thực tế. `ScheduleCalendar` tính ngày xuất hiện định kỳ, ngày nghỉ và khoảng giờ theo phiên bản khung tiết. `SchedulingService` giữ các phương thức nghiệp vụ hiện có cho xếp lịch tay, phê duyệt và Genetic Algorithm. Chưa thay đổi hàm mục tiêu, quy tắc xung đột hay tham số của thuật toán. Các record Slot, Span, Context và Result vẫn ở SchedulingService để tránh thay đổi hàng loạt bên gọi; việc định kiểu context và chuẩn hóa phần ràng buộc còn lại thuộc đợt sau.


### Vòng đời lần chạy xếp lịch

`RunRepository` sở hữu truy vấn trạng thái, phạm vi truy cập, phương án, tiến độ và kết quả. `RunService` kiểm tra quyền, điều kiện đưa vào hàng đợi, tính hợp lệ trước khi áp dụng và điều kiện hủy. `RunWorker` điều phối các giao dịch nhận việc, nạp đầu vào, lưu tiến độ và kết quả; thuật toán tiếp tục nằm trong GeneticScheduler. Khóa advisory, FOR UPDATE SKIP LOCKED, xử lý gián đoạn, trạng thái và công thức fitness không thay đổi. Phần đánh giá fitness, dữ liệu đầu vào và phân chia các thành phần nội bộ GeneticScheduler vẫn cần cải tổ riêng.


### Genetic Algorithm

`GeneticInputBuilder` chọn lớp thuộc phạm vi, đọc lịch công bố qua SchedulingRepository và xây dựng miền ứng viên theo các ràng buộc hiện có. `GeneticFitnessEvaluator` đánh giá vi phạm cứng/mềm và giữ cache cục bộ từng lần giải. `GeneticScheduler` điều phối tiến hóa và báo tiến độ. Không thay đổi trọng số, mặc định, thứ tự lấy số ngẫu nhiên, giới hạn thời gian hoặc công thức fitness. Bài kiểm thử seed cố định và trọng số được bổ sung; benchmark tích hợp hiện có vẫn cần chạy trên HEAD mới. Các thao tác tiến hóa được tách tiếp sang GeneticEvolution; record nội bộ vẫn được giữ để tránh thay đổi đồng loạt hợp đồng giữa các class.


`GeneticEvolution` thực hiện lấy mẫu miền, khởi tạo tham lam, tournament ba cá thể, lai ghép theo lớp học phần, đột biến và sửa vi phạm cứng cục bộ. Một Random theo seed được truyền vào để bảo toàn thứ tự lấy số ngẫu nhiên. GeneticScheduler duy trì elitism hai cá thể, giới hạn thế hệ/thời gian, báo tiến độ và ghi chú vi phạm. Bổ sung kiểm thử trực tiếp các toán tử; benchmark tích hợp phải được xác minh trên commit mới.


### Giao dịch và chống thực thi lặp

`MutationRepository` tập trung khóa advisory PostgreSQL dùng chung với RunWorker, đọc/ghi và dọn bản ghi chống lặp. `IdempotencyRecord` định kiểu fingerprint và JSON phản hồi. `MutationService` giữ giao dịch Spring, kiểm tra khóa yêu cầu, tạo SHA-256 theo thứ tự trường và cấu hình JSON cũ, gọi nghiệp vụ và lưu phản hồi. Không đổi bảng, mã khóa, HTTP contract hoặc thứ tự kiểm tra. Kiểm thử độc lập kiểm tra replay, xung đột khóa, yêu cầu lỗi và tương thích fingerprint cũ; rollback thực tế vẫn phải xác minh qua CI tích hợp.


### DTO xác thực và làm mới phiên

AuthController sử dụng LoginRequest, DemoLoginRequest, AuthModeResponse và CsrfResponse trong package dto. LoginRequest chỉ nhận mật khẩu khi đọc JSON, không trả mật khẩu khi serialize hoặc gọi toString. Accounts.Actor giữ nguyên tên lớp và các trường để tương thích dữ liệu phiên cũ. SessionPrincipalRefreshFilter phụ trách kiểm tra lại tài khoản và phạm vi quyền từng yêu cầu; được tạo và đăng ký một lần trong SecurityConfig, không đăng ký như filter servlet độc lập. Không thay đổi đường dẫn, mã HTTP, cookie, CSRF hoặc hành vi đăng nhập dùng thử. Kiểm thử mới bao gồm hợp đồng JSON và các trường hợp tài khoản bị khóa, thay thế hoặc thay đổi quyền.
