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


## PR #4 — Kiểm toán kiến trúc Spring Boot và ReactJS

Mốc 000f45a: [CI 3/3 PASS](https://github.com/adolfcsix/hucefake/actions/runs/45968189635), gồm kiểm thử phân quyền và cấu trúc CSS/module.
Mốc 5d7834a: [CI 3/3 PASS](https://github.com/adolfcsix/hucefake/actions/runs/45969423750), gồm kiểm thử mutation đồng thời, rollback nhập lớp, công bố và tiến độ GA.
Mốc 69edb3e: [CI 3/3 PASS](https://github.com/adolfcsix/hucefake/actions/runs/45970442711); 161 Java test, 0 lỗi/skip; 63 module frontend reachable; PostgreSQL, GA, private CLI/restore, Chromium và cách ly phiên demo/private đã chạy thành công. Gemini thật vẫn chưa kiểm thử.

Kiểm toán cấu trúc: 9 Controller, 30 file Service, 20 Repository trước tách CLI; DTO/model, config, security, validation và exception riêng; 63 module JS/JSX/CSS. Giữ Accounts.Actor ở binary name cũ để tương thích phiên JDBC. SetupRepository tiếp nhận SQL của CLI, giữ TransactionTemplate, runtime-mode guard, tham số SQL và thứ tự ghi; cần kiểm thử private CLI và restore trên commit mới. Không đổi schema, API hay thiết kế UI. Chưa có nghiệm thu Gemini thật hoặc pentest độc lập, chưa merge main.


### Kiểm kê dependency Java

POM hiện có 6 dependency runtime/compile trực tiếp và spring-boot-starter-test ở scope test; phiên bản do Spring Boot parent quản lý. CI dùng dependency:copy-dependencies (plugin 3.8.1, includeScope=runtime, repository layout) và CycloneDX 1.5 đối chiếu từng JAR trong chính gói đã kiểm thử với bản Maven đã resolve, cả tọa độ và SHA-256; lỗi thiếu, khác hash hoặc JAR không rõ nguồn làm hỏng job. Artifact huce-maven-inventory chứa huce.cdx.json, không commit báo cáo hoặc bản sao thư viện vào repo. Ba test Python dùng JAR giả kiểm tra Spring Core, classifier, hash, thiếu/thừa JAR. Không thêm thư viện runtime. Kiểm kê không phải quét CVE: để kiểm tra lỗ hổng đã công bố, tải artifact và chạy OSV-Scanner v2: `osv-scanner scan source -L huce.cdx.json`. Xem https://google.github.io/osv-scanner/usage/scan-source . Cần xem kết quả, đánh giá ảnh hưởng và nâng cấp dependency có kiểm thử hồi quy; chưa có kết quả quét Java thật.


### Quét lỗ hổng dependency Java qua OSV

Job dependency-audit nhận SBOM của chính JAR được kiểm thử từ smoke, gửi danh sách PURL Maven có phiên bản đến API OSV công khai (https://google.github.io/osv.dev/post-v1-querybatch/), không gửi mã nguồn, khóa API, dữ liệu cá nhân hay cấu hình database. Kiểm tra toàn bộ kết quả và phân trang; lưu artifact huce-java-osv-audit. Mã thoát 0: đã quét đầy đủ và không có advisory trả về; 1: có advisory cần xử lý; 2: quét lỗi hoặc không đầy đủ. Không bỏ qua advisory, không tự nâng version khi chưa đánh giá. Giữ npm audit kể cả khi bước Java thất bại. Năm test Python dùng phản hồi giả lập, không cần cài scanner vào runtime Java. CI thực tế và kết quả OSV chưa xác minh ở commit này. OSV không thay thế kiểm toán JDK, hệ điều hành, cấu hình hay pentest.

Mốc dfb664b: [CI](https://github.com/adolfcsix/hucefake/actions/runs/45973287554) 165 Java test đạt, nhưng kiểm kê dạng tree thất bại với spring-core-6.2.14.jar; container/OSV bị skip. Thay tree bằng tập artifact runtime đã resolve theo layout Maven, đối chiếu cả byte hash; không loại trừ Spring Core, không đổi phiên bản thư viện. Chờ CI xác nhận.

Mốc f451a27: [CI](https://github.com/adolfcsix/hucefake/actions/runs/45973819732) Maven build và 165 Java test đạt, nhưng kiểm kê phát hiện bảy spring-boot-starter-* đã resolve không có trong BOOT-INF/lib; OSV/container bị skip. Chỉ chấp nhận starter đúng group, phiên bản Spring Boot, không classifier và kiểm tra nội dung JAR chỉ gồm metadata/giấy phép/POM. Các starter này vẫn được đưa vào SBOM/OSV với thuộc tính resolved-metadata-only-starter và hash bản resolve; thư viện thực sự đóng gói vẫn phải trùng hash, thiếu mã/tài nguyên runtime vẫn FAIL. Bổ sung test kiểm tra starter metadata, class và cấu hình auto-configuration. Chưa xác minh CI mới.


### Nâng cấp dependency sau kết quả OSV thực tế

Mốc eef367b: [CI](https://github.com/adolfcsix/hucefake/actions/runs/45986332644) smoke và container PASS; 165/165 Java test, 17/17 frontend test. Kiểm kê và OSV đã quét 51 thành phần Maven, phát hiện advisory ở Spring Boot, Framework, Security, Tomcat, Jackson, Logback, Log4j API và PostgreSQL JDBC; dependency-audit FAIL đúng chủ đích.

Bước vá đầu tiên: Spring Boot parent 3.5.8 -> 3.5.16 (bản OSS cuối cùng của nhánh 3.5 theo https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/), đồng thời dùng thuộc tính BOM/phiên bản quản lý để đồng bộ Jackson 2.21.9 LTS, Logback 1.5.37, Log4j 2.26.1, Tomcat 10.1.62, PostgreSQL JDBC 42.7.15. Nguồn phiên bản: https://github.com/FasterXML/jackson/wiki/Jackson-Release-2.21 ; https://central.sonatype.com/artifact/ch.qos.logback/logback-core/versions ; https://logging.apache.org/log4j/2.x/download.html ; https://tomcat.apache.org/security-10.html ; https://jdbc.postgresql.org/changelogs/2026-11-23-42.7.15-release/ . Java 21 và kiến trúc hiện tại không đổi; không thêm dependency trực tiếp, không sửa API/migration/UI, không bỏ qua advisory. Cần CI mới xác minh khả năng resolve/build và quét lại. Spring Boot 3.5 đã kết thúc hỗ trợ OSS; nếu còn advisory Spring không có bản vá OSS tương thích, phải đánh giá nâng major được hỗ trợ hoặc phương án hỗ trợ hợp pháp, không tuyên bố an toàn chỉ vì đã nâng patch.

Mốc 59f6f5f: [CI](https://github.com/adolfcsix/hucefake/actions/runs/45987934381) FAIL trước biên dịch: Maven Central trả artifact spring-boot-starter-parent:3.5.16 absent. Chưa kiểm thử được dependency mới. Chuyển parent sang 3.5.15 (https://spring.io/blog/2026/06/10/spring-boot-3-5-15-available-now/ ; https://central.sonatype.com/artifact/org.springframework.boot/spring-boot-dependencies/3.5.15), thêm relativePath rỗng để chỉ dùng parent đã khai báo, không lấy POM local. Giữ các bản vá dependency khác của commit trước để CI xác minh khả năng resolve. Không khẳng định 3.5.15 hoặc các bản override đã tải được trên runner cho tới khi có log, không bỏ qua OSV. Nếu còn lỗ hổng không có bản vá OSS trên nhánh 3.5, phải đánh giá chuyển dòng được hỗ trợ, kiểm tra Jackson/Spring Security/Servlet và tương thích session trước khi bàn giao.

### Chuyển sang dòng Spring còn hỗ trợ OSS

Mốc 9199c68: [CI](https://github.com/adolfcsix/hucefake/actions/runs/45990375656) smoke PASS, 165 Java test, 51 thành phần đã đối chiếu và quét OSV. Chỉ còn GHSA-j9f9-w8pj-32f8 (CVE-2026-47890) và GHSA-pc63-qcmh-9cmg (CVE-2026-47884) ở spring-webmvc 6.2.19. Advisory công khai: https://osv.dev/vulnerability/GHSA-j9f9-w8pj-32f8 và https://osv.dev/vulnerability/GHSA-pc63-qcmh-9cmg . Bản vá OSS từ Framework 7.0.9; bản 6.2.20 thuộc Enterprise, không trộn Spring 7 với Boot 3.5.

Chuyển parent sang Spring Boot 4.0.10 (phát hành 20/11/2026: https://spring.io/blog/2026/11/20/spring-boot-4-0-10-available-now/), BOM quản lý Framework 7.0.11 và Tomcat 11.0.27 (https://docs.spring.io/spring-boot/4.0/appendix/dependency-versions/coordinates.html). Áp dụng starter Web MVC, Session JDBC, Flyway, đổi package auto-configuration Security; giữ Jackson 2 qua module tương thích và cấu hình preferred-json-mapper theo https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide và https://docs.spring.io/spring-boot/4.0/reference/features/json.html . Bỏ override Tomcat 10 vì Servlet 6.1 cần Tomcat 11. Giữ Java 21, API, SQL migration, UI, 3 job CI và các bản vá Jackson 2/Logback/Log4j/PostgreSQL. Đây là bước chuyển major chưa kiểm thử; phiên đăng nhập serialize giữa Boot 3/4 chưa xác minh tương thích, cần sao lưu và đăng nhập lại khi nâng cấp; kiểm tra khởi động, authentication, CSRF, JDBC session, Flyway, JSON, transaction, restore và quét OSV trước merge. Jackson 2 compatibility module là giải pháp chuyển tiếp, không mặc định duy trì vô hạn.

Mốc 2ed248a: [CI](https://github.com/adolfcsix/hucefake/actions/runs/45990972378) không resolve được tools.jackson:jackson-bom:2.21.9. Boot 4 dùng jackson-bom.version cho Jackson 3, jackson-2-bom.version cho Jackson 2 theo https://docs.spring.io/spring-boot/4.0/appendix/dependency-versions/properties.html . Sửa đúng thuộc tính cho Jackson 2 và giữ BOM Jackson 3 do Boot quản lý. Chưa biên dịch/kiểm thử được Boot 4 ở mốc này; chờ CI mới, không bỏ OSV hoặc các bài hồi quy.
