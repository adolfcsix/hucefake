# HUCE — Lớp học phần và Thời khóa biểu

Ứng dụng học tập độc lập, không phải cổng chính thức của trường. Frontend ReactJS (Vite, JSX), backend Java 21 / Spring Boot 3.5.8, PostgreSQL 16. Spring Security xác thực, phân quyền và CSRF; JDBC thực hiện truy vấn, Flyway quản lý migration; Spring Session lưu phiên trong PostgreSQL. Node.js chỉ build React và chạy kiểm thử HTTP, không phục vụ backend.

Ảnh đăng nhập: [Hướng dẫn cho tân sinh viên HUCE](https://ttcntt.huce.edu.vn/huong-dan-cho-tan-sinh-vien).

## 1. Cấu trúc

```text
hucefake/
├── backend/                 Java Spring Boot
│   ├── pom.xml
│   ├── src/main/java/vn/huce/
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/
│   │       ├── migration/    SQL tạo bảng
│   │       └── demo/         SQL danh mục mẫu
│   └── tests/               Kiểm thử tích hợp
├── frontend/                ReactJS và Vite
│   ├── src/
│   ├── test/
│   ├── e2e/
│   └── package.json
├── config/local.template.json
├── Dockerfile
├── compose.yaml
├── compose.share.yaml
├── compose.private.yaml
└── README.md
```

## 2. Chọn cách chạy

| Cách | Công cụ cần cài | Địa chỉ |
| --- | --- | --- |
| Dùng thử nhanh | Docker Desktop (Compose v2) | http://localhost:8081 |
| Phát triển trên VS Code | JDK 21, Maven 3.9+, Node.js 22.12+, PostgreSQL 16 hoặc Docker | http://localhost:5173 |
| Dữ liệu riêng | Một trong hai cách trên, database riêng | Theo cấu hình |

Tải tại [JDK Temurin](https://adoptium.net/temurin/releases/?version=21), [Maven](https://maven.apache.org/download.cgi), [Node.js](https://nodejs.org/), [PostgreSQL](https://www.postgresql.org/download/), [VS Code](https://code.visualstudio.com/) hoặc [Docker Desktop](https://www.docker.com/products/docker-desktop/). Git tùy chọn nếu tải ZIP.

Mở VS Code → File → Open Folder → chọn thư mục chứa trực tiếp backend và frontend. Terminal → New Terminal. Với Git:
```powershell
git clone https://github.com/adolfcsix/hucefake.git
cd hucefake
```
Trong thời gian PR #1 chưa được gộp, chọn nhánh migration/spring-boot hoặc chạy `git switch migration/spring-boot` sau khi clone.

## 3. Dùng thử bằng Docker

Mở Docker Desktop và chờ Engine hoạt động. Từ thư mục gốc:
```powershell
docker compose -f compose.share.yaml up -d --build --wait
docker compose -f compose.share.yaml ps
```
Mở http://localhost:8081. Lần đầu cần Internet để tải image và dependency. PostgreSQL nằm trong volume demo, React được Spring Boot phục vụ trong cùng container ứng dụng.

| Vai trò | Tên đăng nhập | Mật khẩu mẫu |
| --- | --- | --- |
| Nhân viên đào tạo | daotao | Fm05Demo@2026 |
| Quản lý | quanly | Fm05Demo@2026 |
| Giảng viên | giangvien | Fm05Demo@2026 |
| Sinh viên | sinhvien | Fm05Demo@2026 |

Có thêm daotao2 và giangvien2 để kiểm tra phạm vi. Chỉ sử dụng mẫu trên máy local. Nếu cổng 8081 bận, đặt `$env:FM05_WEB_PORT='8082'` rồi chạy lại và mở cổng mới.

```powershell
docker compose -f compose.share.yaml logs --tail=100 app postgres
docker compose -f compose.share.yaml down
```
Lệnh down giữ volume. Không thêm -v nếu muốn giữ dữ liệu.

## 4. Chạy trên VS Code, không dùng Docker cho backend

Kiểm tra công cụ:
```powershell
java -version
mvn -version
node -v
npm -v
```
Maven phải dùng Java 21. Nếu Windows không nhận lệnh, thêm thư mục bin của JDK/Maven vào PATH, đặt JAVA_HOME về JDK 21 và mở lại terminal.

**Database:** dùng PostgreSQL 16 đã cài hoặc chạy `docker compose up -d --wait postgres` tại gốc. Compose tạo huce_spring với user fm05, mật khẩu mặc định fm05_local (chỉ local). Nếu dùng pgAdmin, kết nối bằng tài khoản quản trị và chạy từng câu riêng:
```sql
CREATE ROLE fm05 WITH LOGIN PASSWORD 'doi-mat-khau-nay';
CREATE DATABASE huce_spring OWNER fm05 ENCODING 'UTF8';
CREATE DATABASE huce_private OWNER fm05 ENCODING 'UTF8';
```
Nếu role đã có, không tạo lại. Với Docker local, tạo thêm database riêng bằng `docker compose exec postgres createdb -U fm05 huce_private`.

**Terminal 1 (PowerShell):**
```powershell
$env:DB_URL='jdbc:postgresql://127.0.0.1:5432/huce_spring'
$env:DB_USER='fm05'
$env:DB_PASSWORD='fm05_local'
$env:DEMO='true'
cd backend
mvn clean verify
mvn spring-boot:run
```
Thay mật khẩu đúng PostgreSQL. Mặc định backend lắng nghe localhost:8080. Chọn DEMO=false và database riêng để chạy bản trắng. Java không tự nạp .env; cần đặt biến môi trường trong terminal. Linux/macOS dùng export thay cho $env:.

**Terminal 2** (mở tại thư mục gốc):
```powershell
cd frontend
npm ci
npm run dev
```
Mở http://localhost:5173. Vite proxy /api và /health sang 8080. Nếu đổi cổng backend, cấu hình BACKEND_URL trong .env gốc cho Vite.

## 5. Dữ liệu trắng và tài khoản riêng

Không sử dụng chung database demo và database riêng. Bản trắng chỉ có schema và bốn vai trò; không có tài khoản hoặc lớp mẫu. Không bật DEMO=true trên database riêng.

Từ thư mục gốc, sao chép `config/local.template.json` thành `config/local.json`. Ví dụ dữ liệu (thay theo trường của bạn):
```json
{
  "format":"fm05-local","version":1,
  "units":[{"code":"CNTT","name":"Khoa Công nghệ thông tin"}],
  "campuses":[{"code":"CS1","name":"Cơ sở 1"}],
  "semesters":[{"yearCode":"2026-2027","code":"2026-2027.1","startDate":"2026-09-01","endDate":"2026-12-31"}],
  "subjects":[{"code":"CSDL","name":"Cơ sở dữ liệu","unitCode":"CNTT","credits":3}],
  "groups":[{"code":"CNTT-K26","curriculumCode":"K26","capacity":40}],
  "enrollments":[]
}
```
Tại backend, đặt DEMO=false, DB_URL trỏ huce_private và DB_USER/DB_PASSWORD đúng:
```powershell
mvn clean package
java -jar target/huce-backend-1.0.0.jar setup catalog ../config/local.json
java -jar target/huce-backend-1.0.0.jar setup account --username quanly_rieng --email manager@example.test --role MANAGER
java -jar target/huce-backend-1.0.0.jar setup account --username daotao_rieng --email staff@example.test --role STAFF --unit CNTT --campus CS1
java -jar target/huce-backend-1.0.0.jar setup account --username giangvien_rieng --email lecturer@example.test --role LECTURER --unit CNTT
java -jar target/huce-backend-1.0.0.jar setup account --username sinhvien_rieng --email student@example.test --role STUDENT
mvn spring-boot:run
```
CLI hỏi mật khẩu ẩn hai lần (ít nhất 10 ký tự, tối đa 72 byte). Không ghi mật khẩu vào JSON; không commit local.json. Khoa và cơ sở phải khớp tệp. Mỗi lần nhập danh mục được xử lý trong transaction; mã đã tồn tại giữ nguyên. Sau khi đăng nhập nhân viên, tạo phòng, khung tiết, lớp, phân công, nhóm lớp. Để gắn sinh viên, bổ sung `{"username":"sinhvien_rieng","semesterCode":"2026-2027.1","sectionCode":"CSDL.01"}` vào enrollments và nạp lại tệp sau khi lớp CSDL.01 đã tồn tại.

### Dữ liệu riêng bằng Docker

Dừng demo hoặc dùng cổng khác. Bản riêng có project/volume khác:
```powershell
docker compose -f compose.share.yaml -f compose.private.yaml up -d --build --wait
docker compose -f compose.share.yaml -f compose.private.yaml run --rm -v "${PWD}/config:/config:ro" app setup catalog /config/local.json
docker compose -f compose.share.yaml -f compose.private.yaml run --rm app setup account --username quanly_rieng --email manager@example.test --role MANAGER
```
Lặp lệnh setup account cho ba vai trò còn lại với tham số tương ứng ở trên. Đăng nhập bằng tên riêng. Giữ volume khi dừng. Trường hợp chạy song song đặt FM05_WEB_PORT khác cho bản riêng.

## 6. Xóa bốn nút đăng nhập nhanh

**Cách không sửa code:** đặt DEMO=false, dùng database riêng. Bốn nút không được render; API demo-login cũng từ chối. Đây là cách khuyến nghị.

**Cách xóa hẳn giao diện:** mở VS Code → frontend → src → main.jsx. Tìm `const demoRoles = [` và xóa toàn bộ khai báo mảng kết thúc tại dấu `]`. Trong function Login, tìm đoạn:
```jsx
{mode?.demo === true && <section className="demo-accounts" aria-label="Tài khoản dùng thử">
```
Xóa từ dòng này đến dấu `</section>}` tương ứng, ngay trước thẻ đóng login-inner. Không xóa form đăng nhập hoặc function signIn. Có thể xóa các selector .demo-accounts, .demo-grid, .demo-choice trong style.css nếu muốn dọn CSS; không xóa selector dùng chung. Nếu không cần đọc trạng thái demo nữa, xóa state mode/modeError, useEffect gọi /auth/mode và đổi ErrorBox thành error={error}.

Kiểm tra lại trong frontend:
```powershell
npm test
npm run build
npm run dev
```
Đăng nhập bằng tài khoản thường vẫn dùng POST /api/auth/login. Xóa nút không xóa dữ liệu, tài khoản hay API đăng nhập. Muốn khóa truy cập demo thực sự, dùng DEMO=false thay vì chỉ xóa giao diện. Nếu đóng gói Docker, build lại image. Bài e2e có kiểm tra 4 nút demo, cần điều chỉnh test này nếu đã chủ động gỡ nút.

## 7. Nghiệp vụ và HUCEChat

Nhân viên tạo/nhập lớp, phân công, xếp tay hoặc GA và gửi duyệt. Quản lý xem hồ sơ, trả lại hoặc công bố. Giảng viên xem lịch, khai báo bận, đề nghị đổi lịch. Sinh viên xem lịch cá nhân và thông báo. Thời khóa biểu hỗ trợ xuất CSV/ICS; dữ liệu riêng có thể sao lưu/khôi phục PostgreSQL bằng pg_dump/pg_restore.

HUCEChat dùng nhận diện tiếng Việt cục bộ, lưu hội thoại theo tài khoản và phạm vi vai trò. Tác vụ ghi chỉ thực hiện khi xác nhận đề xuất. Tùy chọn Gemini: đặt GEMINI_API_KEY và GEMINI_MODEL hợp lệ ở backend; không đặt key vào frontend. Docker Compose đọc biến môi trường hoặc .env. Mô hình chỉ phân loại ý định, không có quyền gọi SQL. Khi chưa cấu hình, chatbot vẫn tra cứu và thực hiện các nghiệp vụ cục bộ.

## 8. Kiểm thử và xử lý lỗi

- Maven: `cd backend`, `mvn clean verify`.
- Frontend: `cd frontend`, `npm ci`, `npm test`, `npm run build`.
- Hợp đồng API: từ gốc `node backend/tests/api-surface.mjs`.
- GitHub Actions chạy kiểm thử tích hợp PostgreSQL, bản trắng, container và Chromium. Xem [Actions](https://github.com/adolfcsix/hucefake/actions) và [KIEM_THU.md](KIEM_THU.md).
- Nếu 8080 không kết nối, kiểm tra backend đã khởi động, DB_URL và mật khẩu; nếu 5432 bận, kiểm tra dịch vụ PostgreSQL đang chạy. Lỗi demo/private nghĩa là đang dùng sai database, không xóa dữ liệu để sửa.
- Backup trước mọi thay đổi. Database Express cũ không được tự động baseline bởi Flyway; bản hiện tại được thiết kế cho PostgreSQL mới. Không nhập bản dump cũ trực tiếp nếu chưa đối chiếu migration và dữ liệu.
- Các tài khoản mẫu và Docker local không phải cấu hình production. Không đưa demo ra Internet; đã có giới hạn đăng nhập theo IP trong mỗi tiến trình Java (mặc định 40 yêu cầu/phút, tùy chỉnh HUCE_LOGIN_ATTEMPTS_PER_MINUTE). Triển khai thật cần HTTPS, bí mật riêng, giới hạn phân tán tại reverse proxy, backup và kiểm toán bảo mật. Không tin X-Forwarded-For do client tự gửi.

