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
Nhánh `main` hiện chứa bản Spring Boot đã được nghiệm thu. Nếu tải ZIP, dùng Code → Download ZIP ở nhánh `main`; giải nén rồi mở thư mục chứa `backend/` và `frontend/`. Không cần chuyển sang nhánh migration.

## 3. Dùng thử bằng Docker

Mở Docker Desktop và chờ Engine hoạt động. Từ thư mục gốc:
```powershell
docker compose -f compose.share.yaml up -d --build --wait
docker compose -f compose.share.yaml ps
```
Mở http://localhost:8081. Lần đầu cần Internet để tải image và dependency. Nếu Maven Central trả HTTP 429, chờ giới hạn phía máy chủ hết rồi chạy lại lệnh build; không xóa database hoặc volume. Trong CI, Java và React được đóng gói trên cùng runner đã kiểm thử; image dùng JAR có checksum từ cùng workflow để tránh tải lại Maven. Người dùng local không cần artifact CI; Compose mặc định dùng target local để build từ source. PostgreSQL nằm trong volume demo, React được Spring Boot phục vụ trong cùng container ứng dụng.

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

Không sử dụng chung database demo và database riêng. Bản trắng chỉ có schema và bốn vai trò; không có tài khoản hoặc lớp mẫu. Nếu chưa nạp học kỳ, giao diện hiển thị hướng dẫn theo vai trò; sau khi nạp bằng Java CLI, bấm Tải lại dữ liệu để tiếp tục. Thông báo và HUCEChat vẫn sử dụng được khi chưa có học kỳ. Không bật DEMO=true trên database riêng.

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

**Cách xóa hẳn giao diện:** mở VS Code → frontend → src → features → auth → Login.jsx. Tìm `const demoRoles = [` và xóa toàn bộ khai báo mảng kết thúc tại dấu `]`. Trong function Login, tìm đoạn:
```jsx
{mode?.demo === true && <section className="demo-accounts" aria-label="Tài khoản dùng thử">
```
Xóa từ dòng này đến dấu `</section>}` tương ứng, ngay trước thẻ đóng login-inner. Không xóa form đăng nhập hoặc function signIn. Có thể xóa các selector .demo-accounts, .demo-grid, .demo-choice trong frontend/src/styles/login.css nếu muốn dọn CSS; không xóa selector dùng chung. Nếu không cần đọc trạng thái demo nữa, xóa state mode/modeError, useEffect gọi /auth/mode và đổi ErrorBox thành error={error}.

Kiểm tra lại trong frontend:
```powershell
npm test
npm run build
npm run dev
```
Đăng nhập bằng tài khoản thường vẫn dùng POST /api/auth/login. Xóa nút không xóa dữ liệu, tài khoản hay API đăng nhập. Muốn khóa truy cập demo thực sự, dùng DEMO=false thay vì chỉ xóa giao diện. Nếu đóng gói Docker, build lại image. Bài e2e có kiểm tra 4 nút demo, cần điều chỉnh test này nếu đã chủ động gỡ nút.

## 7. Nghiệp vụ và HUCEChat

Nhân viên tạo/nhập lớp, phân công, xếp tay hoặc GA và gửi duyệt. Quản lý xem hồ sơ, trả lại hoặc công bố. Giảng viên xem lịch, khai báo bận, đề nghị đổi lịch. Sinh viên xem lịch cá nhân và thông báo. Thời khóa biểu hỗ trợ xuất CSV/ICS; dữ liệu riêng có thể sao lưu/khôi phục PostgreSQL bằng pg_dump/pg_restore.

HUCEChat dùng nhận diện tiếng Việt cục bộ, lưu hội thoại theo tài khoản và phạm vi vai trò. Tác vụ ghi chỉ thực hiện khi xác nhận đề xuất. Tùy chọn Gemini: xem hướng dẫn chi tiết tại [mục 10](#10-tích-hợp-gemini-ai-vào-hucechat). Đặt GEMINI_API_KEY và GEMINI_MODEL hợp lệ ở backend; không đặt key vào frontend. Docker Compose đọc biến môi trường hoặc .env. Mô hình chỉ phân loại ý định, không có quyền gọi SQL. Khi chưa cấu hình, chatbot vẫn tra cứu và thực hiện các nghiệp vụ cục bộ.

## 8. Sao lưu và khôi phục PostgreSQL

Dùng PostgreSQL client 16 tương thích server. Dừng ứng dụng trước khi sao lưu nếu muốn một mốc dữ liệu bàn giao không có thao tác đang diễn ra. Chọn đúng database riêng, không dùng database demo. Bản dump có thể chứa hồ sơ, email, mật khẩu đã băm và hội thoại: lưu ngoài repository, hạn chế truy cập và mã hóa nơi lưu.

Ví dụ PowerShell (PostgreSQL local; thay host/user/database theo cấu hình thực tế; nhập mật khẩu khi được hỏi):
```powershell
pg_dump -h 127.0.0.1 -p 5432 -U fm05 -d huce_private -Fc --exclude-table-data=public.spring_session --exclude-table-data=public.spring_session_attributes -f huce-private.dump
pg_restore -l huce-private.dump
createdb -h 127.0.0.1 -p 5432 -U fm05 huce_restored
pg_restore -h 127.0.0.1 -p 5432 -U fm05 -d huce_restored --exit-on-error --single-transaction --no-owner --no-privileges huce-private.dump
```
Nếu tài khoản ứng dụng không có quyền tạo database, dùng pgAdmin/tài khoản quản trị để tạo database mới có owner fm05 trước khi restore. Không restore vào database đang chạy hoặc dùng `--clean` để ghi đè dữ liệu cũ. Đặt DB_URL thành `jdbc:postgresql://127.0.0.1:5432/huce_restored`, DEMO=false; giữ nguyên DB_USER/DB_PASSWORD, chạy lại backend. Đăng nhập mới bằng tài khoản riêng, kiểm tra danh mục, lớp và lịch. Hai bảng phiên chỉ sao lưu schema; người dùng phải đăng nhập lại sau khôi phục. Giữ database nguồn và bản dump đến khi kiểm tra xong. Không dùng quy trình này cho dump Node/Express cũ chưa chuyển đổi.

Docker (ví dụ database riêng mặc định là huce; nếu dùng FM05_DB_NAME khác, thay đúng tên nguồn). Chạy tại thư mục gốc:
```powershell
docker compose -f compose.share.yaml -f compose.private.yaml stop app
docker compose -f compose.share.yaml -f compose.private.yaml exec postgres pg_dump -U fm05 -d huce -Fc --exclude-table-data=public.spring_session --exclude-table-data=public.spring_session_attributes -f /tmp/huce-private.dump
docker compose -f compose.share.yaml -f compose.private.yaml cp postgres:/tmp/huce-private.dump ./huce-private.dump
docker compose -f compose.share.yaml -f compose.private.yaml cp ./huce-private.dump postgres:/tmp/huce-restore.dump
docker compose -f compose.share.yaml -f compose.private.yaml exec postgres createdb -U fm05 huce_restored
docker compose -f compose.share.yaml -f compose.private.yaml exec postgres pg_restore -U fm05 -d huce_restored --exit-on-error --single-transaction --no-owner --no-privileges /tmp/huce-restore.dump
$env:FM05_DB_NAME='huce_restored'
docker compose -f compose.share.yaml -f compose.private.yaml up -d --wait
```
Kiểm tra đăng nhập và dữ liệu, giữ nguyên database nguồn. Nếu cần quay lại nguồn, đặt FM05_DB_NAME về tên database nguồn và chạy lại up. Giữ nguyên project/volume; không dùng down -v. Sau khi kiểm tra, có thể xóa hai tệp tạm trong container bằng `docker compose -f compose.share.yaml -f compose.private.yaml exec postgres rm -f /tmp/huce-private.dump /tmp/huce-restore.dump`. Không chuyển binary dump qua toán tử chuyển hướng PowerShell cũ.

## 9. Kiểm thử và xử lý lỗi

- Maven: `cd backend`, `mvn clean verify`.
- Frontend: `cd frontend`, `npm ci`, `npm test`, `npm run build`.
- Hợp đồng API: từ gốc `node backend/tests/api-surface.mjs` và `node backend/tests/source-integrity.mjs` (trong bản clone Git).
- GitHub Actions chạy kiểm thử tích hợp PostgreSQL, bản trắng, container, Chromium và kiểm thử thao tác ghi qua Vite proxy. Xem trạng thái trên đúng commit trước khi bàn giao. Xem [Actions](https://github.com/adolfcsix/hucefake/actions) và [KIEM_THU.md](KIEM_THU.md).
- Nếu 8080 không kết nối, kiểm tra backend đã khởi động, DB_URL và mật khẩu; nếu 5432 bận, kiểm tra dịch vụ PostgreSQL đang chạy. Lỗi demo/private nghĩa là đang dùng sai database, không xóa dữ liệu để sửa.
- Backup trước mọi thay đổi. Database Express cũ không được tự động baseline bởi Flyway; bản hiện tại được thiết kế cho PostgreSQL mới. Không nhập bản dump cũ trực tiếp nếu chưa đối chiếu migration và dữ liệu.
- Các tài khoản mẫu và Docker local không phải cấu hình production. Không đưa demo ra Internet; đã có giới hạn đăng nhập theo IP trong mỗi tiến trình Java (mặc định 40 yêu cầu/phút, tùy chỉnh HUCE_LOGIN_ATTEMPTS_PER_MINUTE). Triển khai thật cần HTTPS, bí mật riêng, giới hạn phân tán tại reverse proxy, backup và kiểm toán bảo mật. Không tin X-Forwarded-For do client tự gửi.

## 10. Tích hợp Gemini AI vào HUCEChat

Gemini là tùy chọn đã có sẵn trong Spring Boot; không cần cài SDK mới, sửa React hoặc thay đổi PostgreSQL. HUCEChat ưu tiên nhận diện ý định bằng quy tắc tiếng Việt cục bộ. Chỉ câu hỏi chưa nhận diện được mới có thể chuyển đến Gemini để phân loại thành một hành động cho phép theo vai trò. Java thực hiện truy vấn và kiểm tra quyền; thao tác ghi cần người dùng xác nhận. Đây không phải chatbot đa năng trả lời mọi chủ đề. Nếu không cấu hình Gemini hoặc dịch vụ không phản hồi, chức năng học vụ cục bộ vẫn hoạt động.

### 10.1. Lấy API key và chọn model

1. Mở [Google AI Studio — API keys](https://aistudio.google.com/apikey), đăng nhập Google và chọn dự án của bạn.
2. Chọn Create API key (hoặc sử dụng khóa hợp lệ của dự án). Ưu tiên authorization key mới từ AI Studio theo [hướng dẫn chính thức](https://ai.google.dev/gemini-api/docs/api-key).
3. Sao chép khóa và giữ riêng; không gửi qua chat, ảnh chụp màn hình, GitHub hoặc mã frontend.
4. Chọn model văn bản có hỗ trợ `generateContent` và JSON structured output. Ví dụ `gemini-3.6-flash` nếu dự án của bạn được cấp quyền. Model thay đổi theo thời gian; xem [danh sách model](https://ai.google.dev/gemini-api/docs/models), [Models API](https://ai.google.dev/api/models) và [structured outputs](https://ai.google.dev/gemini-api/docs/generate-content/structured-output). Chỉ nhập ID model, không thêm `models/`.
5. Kiểm tra quota, điều kiện sử dụng và [giá](https://ai.google.dev/gemini-api/docs/pricing) trước khi gọi. Không mặc định API miễn phí hoặc không giới hạn.

### 10.2. Cấu hình trên VS Code (Windows PowerShell)

Mở thư mục gốc HUCE, dừng backend đang chạy bằng Ctrl+C. Trong **chính terminal chạy backend**, giữ cấu hình DB_URL, DB_USER, DB_PASSWORD và DEMO đã thiết lập ở mục 4–5. Nhập khóa bằng lời nhắc ẩn (không chép khóa thật vào lệnh):

```powershell
$secret = Read-Host 'Gemini API key' -AsSecureString
$env:GEMINI_API_KEY = [System.Net.NetworkCredential]::new('', $secret).Password
Remove-Variable secret
$env:GEMINI_MODEL = 'gemini-3.6-flash'
```

Có thể thay model bằng ID phù hợp với tài khoản. Để xác minh model/key trước khi khởi động, thực hiện một lần:

```powershell
$headers = @{ 'x-goog-api-key' = $env:GEMINI_API_KEY }
$models = Invoke-RestMethod -Uri 'https://generativelanguage.googleapis.com/v1beta/models?pageSize=100' -Headers $headers
$models.models | Where-Object { $_.supportedGenerationMethods -contains 'generateContent' } | Select-Object name
```

Nếu có nextPageToken, truy vấn trang tiếp theo theo Models API hoặc kiểm tra trực tiếp model:
```powershell
Invoke-RestMethod -Uri "https://generativelanguage.googleapis.com/v1beta/models/$($env:GEMINI_MODEL)" -Headers $headers | Select-Object name,supportedGenerationMethods
```

Kiểm thử đúng dạng JSON enum HUCEChat đang sử dụng (có thể tính quota/chi phí, không gửi dữ liệu cá nhân):

```powershell
$body = @{
  contents = @(@{ parts = @(@{ text = 'Phan loai: xem lich hoc. Chon SCHEDULE hoac UNRELATED.' }) })
  generationConfig = @{
    responseMimeType = 'application/json'
    responseJsonSchema = @{
      type = 'object'
      properties = @{ action = @{ type = 'string'; enum = @('SCHEDULE','UNRELATED') } }
      required = @('action')
    }
  }
} | ConvertTo-Json -Depth 10
$result = Invoke-RestMethod -Method Post -Uri "https://generativelanguage.googleapis.com/v1beta/models/$($env:GEMINI_MODEL):generateContent" -Headers $headers -ContentType 'application/json' -Body $body
$result.candidates[0].content.parts | Where-Object { -not $_.thought } | Select-Object -ExpandProperty text
Remove-Variable headers,body,result,models -ErrorAction SilentlyContinue
```

Kết quả mong đợi là JSON có action thuộc SCHEDULE hoặc UNRELATED; với câu thử này mong đợi SCHEDULE. Đây là phép thử của nhà cung cấp, không sửa dữ liệu HUCE. Nếu lỗi, xử lý ở mục 10.5.

Sau đó chạy backend, ví dụ từ thư mục gốc:
```powershell
cd backend
mvn spring-boot:run
```
Nếu đã ở backend thì không cần cd. Terminal thứ hai chạy `cd frontend`, `npm ci` (lần đầu), `npm run dev`. Mở http://localhost:5173. Mỗi terminal mới cần thiết lập lại biến môi trường nếu chưa được kế thừa. Trên Linux/macOS, nhập khóa bằng `read -rs GEMINI_API_KEY; export GEMINI_API_KEY`, đặt `export GEMINI_MODEL='gemini-3.6-flash'` rồi chạy Maven tương tự. Java không tự đọc .env.

### 10.3. Cấu hình khi chạy Docker

Từ thư mục gốc, dùng biến môi trường trong PowerShell như mục 10.2, hoặc mở tệp `.env` ở gốc (nếu chưa có, tạo tệp chỉ chứa hai biến Gemini bên dưới). Giữ nguyên mọi biến database/cổng hiện có. Không sao chép đè `.env.example` vào project Docker đang chạy vì mật khẩu mẫu local có thể khác mật khẩu PostgreSQL của volume Docker:

```dotenv
GEMINI_API_KEY=KHOA_RIENG_CUA_BAN
GEMINI_MODEL=gemini-3.6-flash
```

Không commit .env; tệp này đã được Git ignore. Biến môi trường của shell có thể ưu tiên hơn .env. Nếu dùng .env, xóa các biến Gemini cũ trong shell bằng `Remove-Item Env:GEMINI_API_KEY,Env:GEMINI_MODEL -ErrorAction SilentlyContinue` trước khi chạy. Chọn đúng project demo hoặc private đang sử dụng, không chạy cả hai lệnh cho cùng cổng.

**Demo:**
```powershell
docker compose -f compose.share.yaml up -d --no-deps --force-recreate --wait app
```
**Dữ liệu riêng:**
```powershell
docker compose -f compose.share.yaml -f compose.private.yaml up -d --no-deps --force-recreate --wait app
```
Các lệnh trên dành cho hệ thống đã build và PostgreSQL đang chạy. Lần đầu chạy, dùng lệnh `up -d --build --wait` tương ứng ở mục 3 hoặc 5. Chỉ tạo lại container ứng dụng, giữ database và volume; không dùng down -v. Mở http://localhost:8081 (hoặc cổng FM05_WEB_PORT đã đặt). Không dùng lệnh in cấu hình/container environment để chia sẻ log vì có thể hiện khóa.

### 10.4. Kiểm tra trên giao diện HUCE

1. Đăng nhập bằng một trong bốn vai trò. Chọn học kỳ nếu có.
2. Mở HUCEChat; kiểm tra xuất hiện thông báo câu hỏi nâng cao có thể được gửi đến Gemini.
3. Mở DevTools (F12) → Network, tìm GET /api/chat/status. Response `{"gemini":true}` xác nhận đã nạp cả hai biến, **không chứng minh khóa/model gọi thành công**.
4. Thử câu đơn giản như “Thông báo của tôi” để kiểm tra nghiệp vụ cục bộ. Tiếp theo tạo cuộc trò chuyện mới và thử diễn đạt khác, chẳng hạn “Có điều gì mới cần tôi lưu tâm không?”. Nếu quy tắc cục bộ không nhận ra, backend có thể gọi Gemini phân loại.
5. Để xác nhận thật sự có request bên ngoài, đối chiếu usage của project Google AI Studio trước/sau câu hỏi nâng cao (số liệu có thể cập nhật trễ). Nếu không có request, câu hỏi có thể đã được quy tắc cục bộ xử lý hoặc quota nội bộ đã hết. Kết quả bước 10.2 kiểm tra độc lập kết nối nhà cung cấp.
6. Khi dùng đề xuất tạo lớp hoặc chạy GA, kiểm tra nội dung rồi bấm Xác nhận. Không nhập mật khẩu, API key, mã định danh cá nhân hoặc nội dung nhạy cảm vào chat. Câu hỏi chưa nhận diện có thể được gửi đến Google; dữ liệu nghiệp vụ do Java truy vấn trong phạm vi quyền của tài khoản.

### 10.5. Lỗi thường gặp

| Hiện tượng | Xử lý |
| --- | --- |
| gemini=false | Kiểm tra đã đặt cả GEMINI_API_KEY và GEMINI_MODEL trong tiến trình Java; khởi động lại backend hoặc recreate app Docker |
| 400 khi gọi Google | Kiểm tra JSON, model có hỗ trợ structured output theo schema đang dùng |
| 401 / 403 | Kiểm tra authorization key, dự án, quyền API, hạn chế khóa, điều kiện khu vực; tạo khóa mới nếu cần |
| 404 model | Chọn ID model khả dụng từ Models API; không dùng tiền tố models/ |
| 429 | Kiểm tra quota/rate limit/billing trên AI Studio; HUCE cũng giới hạn tối đa 8 lượt phân loại Gemini/phút/tài khoản trong mỗi tiến trình |
| Timeout / 5xx | Kiểm tra mạng, proxy và trạng thái dịch vụ Google; Java có timeout yêu cầu 12 giây, không tự ghi dữ liệu nếu phân loại lỗi |
| “Gemini chưa phản hồi” | Dùng câu gợi ý cục bộ; chạy phép thử ở 10.2 để phân biệt lỗi key, model hoặc mạng |
| Mất kết nối web | Kiểm tra /health, PostgreSQL và Spring Boot trước; đây không nhất thiết là lỗi Gemini |

### 10.6. Tắt hoặc đổi khóa Gemini

VS Code: dừng backend, dùng `Remove-Item Env:GEMINI_API_KEY,Env:GEMINI_MODEL -ErrorAction SilentlyContinue` rồi chạy lại Maven. Docker: xóa giá trị hai biến khỏi .env và shell, recreate app theo mục 10.3. Không cần xóa dữ liệu hoặc cài lại thư viện. Khi nghi ngờ lộ khóa, thu hồi/đổi khóa trong AI Studio ngay.

**Vị trí mã nguồn:** `backend/src/main/java/vn/huce/service/chat/GeminiAdvisor.java` (HTTP, schema, timeout và kiểm tra kết quả), `backend/src/main/java/vn/huce/service/chat/ChatService.java` (phân loại cục bộ, giới hạn vai trò), `backend/src/main/java/vn/huce/controller/ChatController.java` (API), `frontend/src/features/chat/HuceChat.jsx` (giao diện), `compose.share.yaml` (biến môi trường). Key chỉ ở backend. CI đã nghiệm thu chatbot cục bộ; chưa có khóa thật để nghiệm thu live Gemini từ môi trường phát triển này. Không có cam kết model mẫu hoạt động trên mọi dự án Google.


### Cấu trúc frontend React

`frontend/src/main.jsx` chỉ khởi động React. Phần ứng dụng và điều hướng nằm trong `src/app/`; đăng nhập nằm trong `src/features/auth/`. Màn hình nghiệp vụ nằm trong `src/features/` theo danh mục, chatbot, lớp học phần, sinh viên, thời khóa biểu và quy trình. Component dùng chung nằm trong `src/components/`; hook trong `src/hooks/`; gọi API trong `src/services/`; hằng số, tiện ích, ảnh và CSS nằm trong các thư mục tương ứng. Các bài kiểm thử nằm ngoài mã nguồn ứng dụng.

Backend Spring Boot sử dụng các package controller, service, repository, dto, security, config, validation, exception và cli. Xem `backend/README.md` để biết trách nhiệm từng tầng và phần cải tổ chưa hoàn tất.


### Tổ chức phân hệ thời khóa biểu ReactJS

Các màn hình trong `frontend/src/features/timetable/` được tách theo nghiệp vụ: `Planning.jsx` xếp lịch thủ công, `PatternEditor.jsx` chỉnh sửa lịch định kỳ và xem trước xung đột, `Published.jsx` lịch đã công bố và xuất CSV/ICS, `SessionEditor.jsx` chi tiết/điều chỉnh buổi học, `ChangeRequests.jsx` xử lý đề nghị đổi lịch. `timetableFilters.js` dùng chung lọc đối tượng và so sánh ID. Không còn file Timetable.jsx cũ. Giữ nguyên JSX, CSS, URL API và luồng xử lý; kiểm thử build và trình duyệt trên commit mới vẫn bắt buộc.


### Tổ chức phân hệ Workflow ReactJS

Trong `frontend/src/features/workflow/`, `Automatic.jsx` quản lý tham số GA, theo dõi tiến độ và thao tác lần chạy; `RunPlan.jsx` hiển thị phương án; `CostChart.jsx` vẽ chi phí theo thế hệ; `Approvals.jsx` quản lý phiên bản và duyệt/công bố; `SubmissionReadiness.jsx` hiển thị điều kiện gửi duyệt; `Availability.jsx` quản lý thời gian bận/ưu tiên tránh xếp lịch. File Workflow.jsx cũ đã được loại bỏ, giữ nguyên logic component, CSS, API, quyền và chu kỳ polling. Cần kiểm thử CI trên commit mới.


### Tổ chức HUCEChat ReactJS

HuceChat.jsx điều phối trạng thái hội thoại, gọi API, tìm lịch sử, quản lý đề xuất và focus. ChatHistory.jsx hiển thị lịch sử; ChatExchange.jsx hiển thị câu hỏi, phản hồi, dữ liệu và liên kết nguồn; DraftFields.jsx là biểu mẫu lớp nháp; Proposal.jsx hiển thị đề xuất và nút xác nhận/hủy; ChatIcon.jsx và chatPresentation.js chứa icon, nhãn và gợi ý. Giữ nguyên hợp đồng API, nội dung JSX, CSS, thời hạn, quyền, xác nhận và cơ chế chống thao tác đồng thời. Cần nghiệm thu build và browser trên commit mới.


### Tổ chức quản lý lớp học phần ReactJS

Trong `frontend/src/features/sections/`, `Sections.jsx` quản lý danh sách, lọc, phân trang và xuất lớp nháp; `DraftImport.jsx` đọc tệp CSV/JSON, tải mẫu và gửi nhập dữ liệu; `SectionEditor.jsx` xử lý thêm/sửa/hủy lớp, phân công giảng viên và nhóm lớp đối tượng, gồm xử lý lỗi mã lớp và xung đột phiên bản; `SectionTransfer.js` giữ các hàm chuyển đổi tệp. Dùng một editor chung cho các thao tác để tránh lặp form, giữ nguyên JSX, CSS, hợp đồng API và điều kiện quyền. Cần nghiệm thu CI trên commit mới.


### Danh mục và kiểm thử lớp API ReactJS

`Catalog.jsx` hiển thị danh mục; `CatalogEditor.jsx` xử lý biểu mẫu phòng, khung tiết và ngày nghỉ; `catalogFields.js` chứa nhãn và xây dựng payload đúng từng nghiệp vụ. Ba test kiểm tra kiểu số, trường gửi API và campusId null cho ngày nghỉ toàn trường. Năm test API dùng fetch giả lập kiểm tra CSRF retry, idempotency khi lỗi 5xx/4xx, preview xung đột và xóa token khi logout; không gọi backend thật. Giữ nguyên useFetch/usePagedQuery và cơ chế API hiện tại trong đợt này, không thêm dependency.


### Ổn định Hooks và CSRF đồng thời

useFetch bắt cả lỗi đồng bộ từ loader và lỗi Promise, vẫn bỏ kết quả của effect đã cleanup. API giữ tham chiếu Promise CSRF đã dùng cho mỗi yêu cầu: một phản hồi CSRF_INVALID muộn không xóa token mới đang được yêu cầu khác sử dụng. Khi Promise tải token thất bại chỉ xóa cache nếu chính Promise đó còn hiện hành. Giữ nguyên giới hạn retry một lần, idempotency key và hợp đồng API. Bổ sung test hai mutation nhận lỗi CSRF cũ lệch thời điểm, cùng sử dụng token thay thế. Không đổi giao diện hay thêm dependency.


### Tổ chức CSS và kiểm tra module dư thừa

`frontend/src/styles/style.css` là điểm nhập CSS, giữ thứ tự cascade: base, workspace, timetable, dialogs, login, student, responsive, workflow-details, chat. Nội dung các quy tắc được chuyển nguyên văn, không đổi selector, giá trị, media query hoặc thứ tự. Không xóa token hoặc selector chỉ vì chưa thấy trong một màn hình; trạng thái động cần kiểm tra trình duyệt. Source Integrity kiểm tra tất cả module JS/JSX/CSS sản xuất đều có đường dẫn từ main.jsx qua import, phát hiện module bị bỏ lại sau cải tổ. Hướng dẫn xóa CSS nút demo sử dụng login.css. Cần build và nghiệm thu browser để xác nhận cascade sau Vite bundling.
