# HUCE — Lớp học phần và Thời khóa biểu

Đây là ứng dụng học tập độc lập, với đặc tả trong **PTTK_LopHocPhan_ThoiKhoaBieu.docx**. Frontend React viết giao diện bằng JSX trong các file `.jsx`. JSX dùng cú pháp giống HTML, Vite biên dịch và React dựng giao diện trong trình duyệt. Backend hoàn toàn bằng JavaScript, dùng Node.js và Express. Hai phần trao đổi qua `/api`, còn PostgreSQL lưu dữ liệu.

Ảnh trang đăng nhập lấy từ [Hướng dẫn cho tân sinh viên của HUCE](https://ttcntt.huce.edu.vn/huong-dan-cho-tan-sinh-vien).

- [Cài và chạy trên máy mới](#cài-và-chạy-trên-máy-mới)
- [Tài khoản mẫu](#tài-khoản-mẫu)
- [Xóa bốn nút đăng nhập nhanh](#xóa-bốn-nút-đăng-nhập-nhanh)
- [HUCEChat](#hucechat)
- [Kết quả kiểm thử](KIEM_THU.md)

## Cài và chạy trên máy mới

HUCE là dự án dùng dữ liệu riêng hoặc dữ liệu mẫu. Tài khoản được tạo trong PostgreSQL của từng bản cài. Chọn một trong hai cách sau.

| Nhu cầu | Cần cài | Địa chỉ mặc định |
| --- | --- | --- |
| Mở bản dùng thử nhanh | Docker Desktop với Docker Compose v2 | `http://localhost:8081` |
| Sửa mã trên VS Code | VS Code, Node.js 22.12 trở lên (khuyến nghị Node.js 24), npm và PostgreSQL 16 | Frontend `http://localhost:5173`, backend `http://localhost:8080` |

### 1. Tải mã và mở đúng thư mục

Trên GitHub chọn **Code → Download ZIP**, giải nén, rồi trong VS Code chọn **File → Open Folder**. Mở thư mục chứa trực tiếp `frontend/`, `backend/`, `config/` và `README.md`. Với Git đã cài, có thể mở PowerShell và dùng:

```powershell
git clone https://github.com/adolfcsix/hucefake.git
cd hucefake
```

Các lệnh bên dưới bắt đầu tại thư mục này, trừ khi có ghi rõ thư mục khác. Chọn **Terminal → New Terminal** để mở PowerShell trong VS Code.

### 2. Cách nhanh nhất: chạy dùng thử bằng Docker Desktop

Mở Docker Desktop, đợi Docker Engine chạy, rồi thực hiện:

```powershell
docker compose -f compose.share.yaml up -d --build --wait
docker compose -f compose.share.yaml ps
```

Mở [http://localhost:8081](http://localhost:8081). Compose chạy frontend đã build, Express và PostgreSQL, đồng thời nạp tài khoản và dữ liệu mẫu. Có thể dùng bốn nút đăng nhập nhanh hoặc nhập tên đăng nhập và mật khẩu theo bảng bên dưới.

```powershell
# Xem lỗi khởi động nếu cần
docker compose -f compose.share.yaml logs --tail=100 app postgres
# Dừng ứng dụng và giữ dữ liệu trong volume
docker compose -f compose.share.yaml down
```

Nếu cổng 8081 đang bận, chạy `$env:FM05_WEB_PORT = '8082'` trong PowerShell trước lệnh `up` rồi mở [http://localhost:8082](http://localhost:8082). Lần chạy đầu cần Internet để tải image và phụ thuộc.

### 3. Chạy trong VS Code với Node.js và PostgreSQL

**Bước 1 · Tạo cơ sở dữ liệu.** Với PostgreSQL đã cài, mở pgAdmin, kết nối bằng tài khoản quản trị, mở **Query Tool** của database `postgres` và chạy từng câu riêng:

```sql
CREATE ROLE fm05 WITH LOGIN PASSWORD 'doi-mat-khau-nay';
CREATE DATABASE huce_demo OWNER fm05 ENCODING 'UTF8';
CREATE DATABASE huce_private OWNER fm05 ENCODING 'UTF8';
```

Tạo role một lần. Nếu role đã có, dùng mật khẩu của role hiện tại. Có thể chỉ tạo database tương ứng với chế độ muốn chạy. Nếu dùng Docker làm PostgreSQL cho Node.js, đặt `DB_PASSWORD` trong `.env`, chạy `docker compose up -d --wait postgres`, rồi tạo hai database trong container bằng `docker compose exec postgres createdb -U fm05 huce_demo` và `docker compose exec postgres createdb -U fm05 huce_private`. Mật khẩu của volume PostgreSQL đã tạo được thiết lập ở lần khởi tạo đầu tiên.

**Bước 2 · Tạo cấu hình.** Tại thư mục gốc, chạy:

```powershell
if (!(Test-Path .env)) { Copy-Item .env.example .env }
node -e "console.log(require('node:crypto').randomBytes(32).toString('hex'))"
```

Trong VS Code, mở tệp `.env` vừa tạo. Thay mật khẩu, tên database và chuỗi bí mật bằng giá trị của máy bạn. Nếu mật khẩu có ký tự đặc biệt như `@` hoặc `#`, mã hóa URL phần mật khẩu trong `DATABASE_URL`.

```dotenv
PORT=8080
DATABASE_URL=postgresql://fm05:doi-mat-khau-nay@127.0.0.1:5432/huce_private
DEMO=false
SESSION_SECRET=dan_chuoi_ngau_nhien_vua_tao
```

Để chạy demo, dùng database `huce_demo` và đặt `DEMO=true`. Để chạy dữ liệu riêng, dùng `huce_private` và `DEMO=false`. Chế độ được ghi vào database lúc khởi tạo. Muốn chuyển đổi, trỏ sang database khác.

**Bước 3 · Mở hai terminal.** Trong terminal 1, chạy backend:

```powershell
cd backend
npm ci
npm run dev
```

Kiểm tra [http://127.0.0.1:8080/health](http://127.0.0.1:8080/health). Kết quả dự kiến là `{"status":"ok"}`. Backend tự chạy migration khi khởi động.

Trong terminal 2, đi từ thư mục gốc và chạy frontend:

```powershell
cd frontend
npm ci
npm run dev
```

Mở địa chỉ Vite in trên terminal, thường là [http://localhost:5173](http://localhost:5173). Giữ cả hai terminal hoạt động. Nếu đã đổi `PORT` ở `.env`, khởi động lại cả hai. Vite chuyển tiếp `/api` và `/health` đến Express. Nếu `/health` ở cổng 8080 hoạt động nhưng ở cổng Vite bị lỗi, kiểm tra lại `PORT` và `BACKEND_URL` trong `.env`, rồi khởi động lại Vite.

**Chạy bằng một cổng.** Tại `frontend/`, chạy `npm ci` và `npm run build`. Tại `backend/`, chạy `npm ci` và `npm start`. Mở [http://localhost:8080](http://localhost:8080) để Express phục vụ cả giao diện và API.

### 4. Nạp dữ liệu riêng và tạo bốn tài khoản

Từ thư mục gốc, chạy `Copy-Item config/local.template.json config/local.json`. Mở `config/local.json` và điền dữ liệu thật của bạn. Ví dụ cấu trúc dưới đây dùng mã `KHOA1`, `CS1` và `CSDL`; chỉnh ngày học kỳ để phù hợp dữ liệu của bạn.

```json
{
  "format": "fm05-local",
  "version": 1,
  "units": [{"code":"KHOA1","name":"Khoa Công nghệ thông tin"}],
  "campuses": [{"code":"CS1","name":"Cơ sở 1"}],
  "semesters": [{"code":"2026-2027.1","yearCode":"2026-2027","startDate":"2026-08-31","endDate":"2026-12-26"}],
  "subjects": [{"code":"CSDL","name":"Cơ sở dữ liệu","unitCode":"KHOA1","credits":3}],
  "groups": [{"code":"CNTT-K26","curriculumCode":"CNTT-2026","capacity":40}],
  "enrollments": []
}
```

Với Node.js, mở terminal tại `backend/`, bảo đảm `.env` đang trỏ database riêng, rồi chạy:

```powershell
npm run setup:local -- catalog ../config/local.json
npm run setup:local -- account --username quanly1 --email ql1@example.test --role MANAGER
npm run setup:local -- account --username daotao1 --email dt1@example.test --role STAFF --unit KHOA1 --campus CS1
npm run setup:local -- account --username giangvien1 --email gv1@example.test --role LECTURER --unit KHOA1
npm run setup:local -- account --username sinhvien1 --email sv1@example.test --role STUDENT
```

Mỗi lệnh `account` yêu cầu nhập và xác nhận mật khẩu riêng, tối thiểu 10 ký tự, và terminal sẽ ẩn các ký tự đang gõ. Đăng nhập bằng các tài khoản vừa tạo. Nhân viên và quản lý cần hai tài khoản khác nhau để gửi và duyệt lịch.

Với Docker, tại thư mục gốc tạo hoặc mở `.env` và thêm `FM05_SESSION_SECRET` với chuỗi riêng dài ít nhất 32 ký tự. Nếu đang chạy demo trên cổng 8081, thêm `FM05_WEB_PORT=8082`. Chạy đúng cặp tệp Compose sau để dùng database và volume riêng:

```powershell
docker compose -f compose.share.yaml -f compose.private.yaml up -d --build --wait
docker compose -f compose.share.yaml -f compose.private.yaml cp ./config/local.json app:/tmp/huce-local.json
docker compose -f compose.share.yaml -f compose.private.yaml exec app npm run setup:local -- catalog /tmp/huce-local.json
docker compose -f compose.share.yaml -f compose.private.yaml exec app npm run setup:local -- account --username quanly1 --email ql1@example.test --role MANAGER
docker compose -f compose.share.yaml -f compose.private.yaml exec app npm run setup:local -- account --username daotao1 --email dt1@example.test --role STAFF --unit KHOA1 --campus CS1
docker compose -f compose.share.yaml -f compose.private.yaml exec app npm run setup:local -- account --username giangvien1 --email gv1@example.test --role LECTURER --unit KHOA1
docker compose -f compose.share.yaml -f compose.private.yaml exec app npm run setup:local -- account --username sinhvien1 --email sv1@example.test --role STUDENT
```

**Để có lịch của sinh viên:** nhân viên tạo phòng, khung tiết, lớp học phần và phân công. Sau khi tạo mã lớp, thêm vào mảng `enrollments` trong `config/local.json` một dòng như `{"username":"sinhvien1","semesterCode":"2026-2027.1","sectionCode":"CSDL.01"}`, rồi nạp lại bằng lệnh `catalog` tương ứng (với Docker, sao chép tệp vào container lần nữa). Dùng mã lớp thực tế. Khi lịch được quản lý công bố, sinh viên đã được gắn lớp mới thấy lịch cá nhân. Nạp lại tệp sẽ thêm mã mới và giữ bản ghi đã có. Cả lượt nạp bị hủy nếu có mã tham chiếu sai.

## Tài khoản mẫu

Mật khẩu dùng chung của database demo là `Fm05Demo@2026`. Đây là dữ liệu minh họa của dự án. Hãy dùng tài khoản tự tạo với dữ liệu riêng.

| Tên đăng nhập | Vai trò |
| --- | --- |
| `daotao`, `daotao2` | Nhân viên đào tạo khoa CNTT, Kinh tế |
| `quanly` | Quản lý |
| `giangvien`, `giangvien2` | Giảng viên |
| `sinhvien` | Sinh viên đã gắn hai lớp mẫu |

Bốn nút hiện khi `DEMO=true`. Dữ liệu demo khởi đầu với các lớp nháp. Để thấy lịch cá nhân, dùng vai trò Nhân viên đào tạo xếp lịch, gửi duyệt và dùng vai trò Quản lý công bố.

## Xóa bốn nút đăng nhập nhanh

Bản dữ liệu riêng (`DEMO=false`) tự ẩn nhóm nút và chỉ cho đăng nhập bằng tài khoản riêng. Nếu muốn bỏ hẳn mã hiển thị bốn nút, làm theo các bước A–C. Bước D dành cho trường hợp muốn gỡ thêm API đăng nhập nhanh khỏi mã backend.

### A. Mở đúng tệp và xóa nhóm nút

1. Trong VS Code, mở Explorer, chọn **frontend → src → [main.jsx](frontend/src/main.jsx)**. Hoặc nhấn `Ctrl+P`, gõ `frontend/src/main.jsx` và Enter.
2. Nhấn `Ctrl+F`, tìm `const demoRoles`. Chọn và xóa nguyên đoạn dưới đây, từ `const demoRoles` tới dấu `]`. Dòng `function Login({ onLogin })` nằm ngay bên dưới.

```jsx
const demoRoles = [
  ['STAFF','Nhân viên đào tạo','layers'], ['MANAGER','Quản lý','check'],
  ['LECTURER','Giảng viên','book'], ['STUDENT','Sinh viên','user']
]
```

3. Tìm `className="demo-accounts"` trong hàm `Login`. Xóa cả đoạn JSX dưới đây, từ dấu `{` trước `mode?.demo` đến hết `</section>}`. Dòng `</form>` ngay phía trên thuộc biểu mẫu đăng nhập bằng mật khẩu.

```jsx
{mode?.demo === true && <section className="demo-accounts" aria-label="Tài khoản dùng thử">
  <h2>Tài khoản dùng thử</h2>
  <div className="demo-grid">{demoRoles.map(([role,label,icon]) => <button key={role} type="button" className="quiet demo-choice" disabled={!!pending} onClick={()=>signIn('/auth/demo-login',{role},role)}><Icon name={icon}/><span>{pending===role?'Đang vào…':label}</span><Icon name="arrow"/></button>)}</div>
</section>}
```

### B. Bỏ mã đọc chế độ dùng thử trên trang đăng nhập

Trong cùng hàm `Login`, xóa hai dòng sau:

```jsx
  const [mode,setMode] = useState(null)
  const [modeError,setModeError] = useState(null)
```

Xóa cả khối `useEffect` gọi `/auth/mode` ngay dưới `const lock = useRef(false)`:

```jsx
  useEffect(() => {
    let active = true
    api('/auth/mode').then(value => { if (active) setMode(value) })
      .catch(error => { if (active) setModeError(error) })
    return () => { active = false }
  }, [])
```

Đổi dòng `<ErrorBox error={error || modeError} />` thành `<ErrorBox error={error} />`. Giữ dòng `const lock = useRef(false)`, hai hàm `submit` và `signIn`, cùng khối `<form …>…</form>`. Những màn hình khác trong tệp vẫn dùng import `useEffect`.

### C. Xóa CSS của nhóm nút và chạy lại

Mở **frontend → src → [style.css](frontend/src/style.css)**. Tìm `.demo-accounts`, rồi xóa sáu quy tắc sau (từ selector đến dấu `}` của từng quy tắc):

```css
.demo-accounts { border-top: 1px solid var(--line); margin-top: 26px; padding-top: 18px; }
.demo-accounts h2 { font-size: .9rem; }
.demo-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 8px; margin-top: 13px; }
.demo-choice.quiet { display: flex; align-items: center; gap: 7px; padding: 9px; min-height: 49px; text-align: left; border-radius: 12px; font-size: .76rem; }
.demo-choice svg { flex: 0 0 17px; color: #416694; }
.demo-choice svg:last-child { margin-left: auto; width: 14px; }
```

Lưu cả hai tệp bằng `Ctrl+S`. Nếu đang chạy Vite, tải lại trang. Nếu Express đang phục vụ bản build, chạy `npm test` và `npm run build` trong `frontend/`, rồi tải lại trang. Nếu dùng Docker, chạy lại lệnh `up -d --build --wait` của đúng chế độ và truy cập cổng đã cấu hình.

Mở trang đăng nhập trong cửa sổ ẩn danh. Kiểm tra tiêu đề **Tài khoản dùng thử** và cả bốn nút đã biến mất. Thử đăng nhập bằng tên và mật khẩu của mỗi vai trò, mở một trang nghiệp vụ, rồi đăng xuất. Có thể thử bước này trên bản demo bằng tên và mật khẩu ở bảng trên.

### D. Tùy chọn: gỡ luôn API đăng nhập nhanh

Mở **backend → src → modules → [auth.js](backend/src/modules/auth.js)**. Tìm và xóa dòng dưới đây, nằm ngay phía trên `export function authRoutes`:

```js
const demoAccounts = Object.freeze({ STAFF:'daotao', MANAGER:'quanly', LECTURER:'giangvien', STUDENT:'sinhvien' })
```

Trong `authRoutes`, tìm và xóa nguyên route dưới đây, từ `api.post('/auth/demo-login'` đến dấu `})`. Khối `api.use(async (req,res,next) => { … })` ngay bên dưới xác thực phiên cho các trang nghiệp vụ.

```js
  api.post('/auth/demo-login', async (req,res) => {
    if (!demo) throw new HttpError(403,'DEMO_DISABLED','Chế độ dùng thử không được bật.')
    const role = req.body?.role
    if (typeof role !== 'string' || !Object.hasOwn(demoAccounts,role)) throw new HttpError(400,'VALIDATION_ERROR','Vai trò dùng thử không hợp lệ.')
    await login(req,res,demoAccounts[role],'Fm05Demo@2026',role)
  })
```

Giữ route `/auth/login`, `/auth/logout`, `/auth/mode`, `/csrf`, `/me` và các migration. Việc xóa các đoạn mã trên không cần chạy lệnh xóa dữ liệu, xóa volume hay đổi chế độ database. Tài khoản demo đã nạp vẫn dùng được bằng cách nhập mật khẩu trên database demo.

Sau khi lưu, khởi động lại backend hoặc build lại Docker, rồi kiểm tra dưới đây từ thư mục `backend/`. Thay 8080 bằng cổng ứng dụng, ví dụ 8081 khi dùng Docker.

```powershell
$env:FM05_TEST_URL = 'http://127.0.0.1:8080'
npm test
# Nếu máy chủ chạy demo
npm run test:auth -- --without-demo-endpoint
# Nếu máy chủ chạy dữ liệu riêng
npm run test:auth -- private --without-demo-endpoint
```

Script kiểm tra đăng nhập thường và trạng thái endpoint đã gỡ. Trên database demo mới, có thể thử lại toàn bộ luồng nghiệp vụ bằng `npm run test:workflows -- --disposable-demo --without-demo-endpoint`. Đối với bản riêng, xem mục [Kiểm tra đăng nhập sau khi cài đặt](#kiểm-tra-đăng-nhập-sau-khi-cài-đặt) để truyền thêm một tài khoản tự tạo vào bài thử.

## Nhập, xuất và chuyển dữ liệu

- Nhân viên đào tạo mở **Lớp học phần → Nhập lớp nháp** để tải mẫu CSV hoặc nhập tệp CSV/JSON. Chọn học kỳ đích có cùng mã với tệp. Mã học phần phải có sẵn và thuộc khoa của người nhập. Mỗi tệp nhận 1–200 dòng, ngày dùng `YYYY-MM-DD`, CSV lưu UTF-8 và hỗ trợ dấu phẩy hoặc chấm phẩy. Tất cả các dòng được kiểm tra và lưu trong một giao dịch. Mã lớp trùng hoặc dòng sai sẽ hủy cả lượt nhập.
- **Xuất lớp nháp CSV** và **Xuất lớp nháp JSON** lấy các lớp nháp trên trang đang xem. Các tệp này dùng mã học kỳ và mã học phần thay cho ID của một máy. Chúng mang thông tin tạo lớp nháp. Phân công giảng viên, nhóm, buổi học và lịch sử duyệt nằm ngoài tệp này.
- Ứng dụng hoặc phân hệ khác có thể tạo JSON với cấu trúc `{"format":"fm05-section-drafts","version":1,"rows":[...]}`. Tên trường trong `rows` theo hàng tiêu đề của mẫu CSV. Sau khi xác thực bằng tài khoản nhân viên, API `POST /api/course-sections/import` nhận `{semesterId, rows}` và áp dụng cùng quy tắc nhập.
- Thời khóa biểu công bố tiếp tục xuất CSV và iCalendar `.ics`. Để chuyển nguyên cơ sở dữ liệu **giữa hai PostgreSQL**, dùng sao lưu PostgreSQL và phục hồi vào database trống riêng, rồi trỏ backend đến database đó. Bản sao toàn bộ chứa dữ liệu tài khoản và lịch sử, chỉ chia sẻ với người được phép.

Backend trong gói này dùng Node.js và PostgreSQL cho cả hai cách khởi động. Tệp trao đổi dữ liệu có thể được tạo từ Spring Boot hoặc MongoDB theo cấu trúc trên. Vận hành FM05 trực tiếp trên Spring Boot hoặc MongoDB cần triển khai thêm backend tương thích.

### Chuyển toàn bộ dữ liệu giữa hai bản Docker

Ví dụ sau dành cho bản demo. Để chuyển bản riêng, thay mọi tiền tố `docker compose -f compose.share.yaml` bằng `docker compose -f compose.share.yaml -f compose.private.yaml` ở cả máy nguồn và máy nhận, cấu hình secret riêng, và giữ `DEMO=false`. Không chuyển đổi chế độ trên bản sao đã phục hồi.

Trên máy nguồn, chạy trong thư mục `Huce` khi cơ sở dữ liệu đang chạy:

```powershell
docker compose -f compose.share.yaml exec -T postgres pg_dump -U fm05 -d fm05 -Fc --exclude-table=fm05_web_sessions -f /tmp/fm05.dump
docker compose -f compose.share.yaml cp postgres:/tmp/fm05.dump ./fm05.dump
```

Nếu đang chạy với tên database khác, thay `-d fm05` bằng tên đó. Trên máy nhận, khởi động bản chia sẻ trước rồi đặt `fm05.dump` vào thư mục dự án. Tạo cơ sở dữ liệu mới và phục hồi:

```powershell
docker compose -f compose.share.yaml cp ./fm05.dump postgres:/tmp/fm05.dump
docker compose -f compose.share.yaml exec -T postgres createdb -U fm05 fm05_import
docker compose -f compose.share.yaml exec -T postgres pg_restore -U fm05 -d fm05_import --no-owner --single-transaction --exit-on-error /tmp/fm05.dump
```

Sau khi phục hồi thành công, đặt `FM05_DB_NAME=fm05_import` vào `.env` ở thư mục gốc và chạy lại bản chia sẻ. Nếu `fm05_import` đã tồn tại, dùng một tên mới trong cả lệnh tạo, lệnh phục hồi và cấu hình. Sau khi chuyển máy, đăng nhập lại bằng tài khoản từ dữ liệu đã phục hồi.

## Cấu trúc mã nguồn

| Đường dẫn | Nội dung |
| --- | --- |
| `frontend/index.html` | Điểm vào của Vite |
| `frontend/src/main.jsx` | Đăng nhập, bố cục và điều hướng theo vai trò |
| `frontend/src/navigation.js` | Menu theo vai trò, kiểm tra đích điều hướng |
| `backend/scripts/check-auth.mjs` | Kiểm thử HTTP đăng nhập và phân quyền trên máy chủ đang chạy |
| `frontend/src/Sections.jsx` | Quản lý lớp, phân công và chọn lớp đối tượng |
| `frontend/src/Timetable.jsx` | Xếp thủ công, lịch công bố, điều chỉnh và đề nghị đổi lịch |
| `frontend/src/Workflow.jsx` | Xếp tự động, duyệt lịch và khai báo thời gian bận |
| `frontend/src/Catalog.jsx` | Phòng học, khung tiết và ngày không học |
| `frontend/src/ui.jsx`, `style.css` | Thành phần giao diện dùng chung và CSS |
| `frontend/src/api.js` | Gọi API, quản lý mã biểu mẫu và xuất CSV |
| `backend/src/app.js`, `server.js` | Cấu hình Express và khởi động máy chủ |
| `backend/src/modules/` | API theo nhóm nghiệp vụ, gồm xác thực, lớp học phần, kiểm tra lịch, lần chạy, duyệt, danh mục và buổi học |
| `backend/src/workers/schedule.worker.js` | Giải thuật di truyền chạy trong worker thread |
| `backend/src/db/` | Pool PostgreSQL, giao dịch, chạy migration và khởi tạo mẫu |
| `backend/src/middleware/` | Kiểm tra quyền, xử lý tham số và lỗi HTTP |
| `backend/database/migrations/V2__fm05.sql` | 13 bảng FM05 theo tài liệu, chuyển sang PostgreSQL |
| `backend/database/seeds/` | Dữ liệu danh mục cho chế độ demo |

Khi xếp thủ công, React gửi thông tin lớp, phòng, thứ và tiết qua API. Backend kiểm tra lớp trùng ngày, phòng, giảng viên, nhóm đối tượng, giờ bận và các buổi đã công bố trước khi ghi. Khi xếp tự động, worker lọc cả các buổi thực tế đã công bố và ngày nghỉ, giữ lịch đã xếp rồi tạo phương án cho số buổi còn thiếu. Lúc áp dụng, backend kiểm tra lại lịch dưới giao dịch. Khi công bố, lịch được sinh từ bản đã duyệt, bỏ qua ngày nghỉ và ghi nhận thao tác chống lặp.

Lịch nháp giữ chỗ theo mẫu định kỳ. Sau công bố, buổi thực tế giữ chỗ theo ngày và giờ hiện hành. Nếu hủy hoặc chuyển một buổi, khung cũ có thể dùng lại khi phòng, giảng viên và lớp đối tượng đều rảnh. Các tuần khác tiếp tục giữ chỗ theo các buổi đã công bố. Khi trùng nhóm, phản hồi kiểm tra lịch kèm mã lớp hành chính liên quan.

Trong **Xếp lịch thủ công**, kéo mã lớp vào lưới hoặc chọn lớp rồi bấm dấu `+` ở ô cần xếp. Khi đang xem theo phòng, biểu mẫu dùng sẵn phòng đã chọn trong phạm vi cơ sở được cấp. Trường hợp có nhiều phòng phù hợp, chọn phòng để hệ thống kiểm tra tự động. Ô xung đột chuyển đỏ và biểu mẫu hiển thị quy tắc, mã lớp, phòng và khung tiết liên quan khi có dữ liệu. Chọn một khung gợi ý để kiểm tra lại. Lịch chỉ được ghi khi bấm **Lưu lịch** và vượt qua kiểm tra trong giao dịch.

Trong **Duyệt thời khóa biểu**, nhân viên thấy các lớp nháp thiếu giảng viên chính, thiếu số buổi hoặc có lịch xung đột. Bảng hiển thị số buổi đã xếp, số buổi yêu cầu và các khung đang vướng. **Kiểm tra lại** cập nhật trạng thái theo dữ liệu mới. API `GET /api/timetable-versions/readiness?semesterId=...` phục vụ bước này. Khi gửi duyệt, backend kiểm tra lại toàn học kỳ trong giao dịch trước khi tạo hồ sơ và lưu phương án.

## Kiểm tra

Đã build frontend, khởi động Node.js với PostgreSQL 16.9 mới và kiểm tra phân quyền, ghi trùng phòng đồng thời, kiểm tra trùng giảng viên và nhóm, điều chỉnh buổi học, thông báo và công bố chống lặp. Các màn hình React được kiểm tra bằng Chromium, gồm giao diện desktop và 320/390 px.

Đã kiểm tra trọn luồng giao diện sau khi build và chạy cùng Express, gồm tạo lớp, phân công, chạy rồi áp dụng phương án tự động, gửi duyệt, công bố và xem lịch. Đã khởi động trên bản sao database từ Spring Boot để đối chiếu số lớp, số buổi học và khả năng dùng lại mật khẩu đã lưu. Bài thử giải thuật dùng dữ liệu mẫu của FM05.

Luồng xếp thủ công được kiểm tra trên Chromium với PostgreSQL. Các ca gồm kéo thả trùng một buổi bắt đầu từ tiết liền trước, đổi phòng trên màn hình 390 px, đổi ô khi kết quả kiểm tra cũ còn đang tải, phát sinh lịch trùng trước lúc lưu và lưu thành công sau khi chọn gợi ý.

## Giao diện

Điều hướng được nhóm theo vai trò và dùng tên nghiệp vụ của FM05. Màn hình lịch công bố có lưới tuần với chiều cao mỗi khối theo số tiết, danh sách theo ngày và bảng cả học kỳ. Điện thoại mở danh sách theo ngày theo mặc định. Chọn một buổi học để xem phòng, giờ, giảng viên và các thay đổi liên quan.

Ô **Đi đến ngày** mở tuần chứa ngày được chọn trong học kỳ. Có thể dùng phím Tab để đặt tiêu điểm vào lưới hoặc bảng cần cuộn.

Trong **Xếp lịch tự động**, mở **Lần chạy** để xem theo từng phương án. **Dùng tham số lần này** điền lại tham số đã lưu, gồm hạt giống, vào biểu mẫu. Khi xem phương án, có thể tìm mã lớp, tên học phần hoặc phòng, bật lọc buổi có vi phạm và chuyển giữa lưới theo tiết với danh sách. Điện thoại mặc định mở danh sách. Nút **Tải lại lần chạy** xuất hiện khi kết nối xem tiến độ gặp lỗi. Nếu học kỳ đã có lượt đang chờ hoặc chạy, giao diện báo trạng thái trong học kỳ theo API. Nhân viên chỉ được bỏ lần chạy do mình tạo.

Bảng màu xanh Royal và trắng bám theo nhận diện HUCE. Giao diện dùng Be Vietnam Pro và bố cục riêng của FM05. [Nhận diện HUCE](https://huce.edu.vn/he-thong-nhan-dien) · [Màu biểu tượng HUCE](https://alumni.huce.edu.vn/en/ban-co-biet-huy-hieu-truong-dai-hoc-xay-dung-hn).

Trang đăng nhập dùng ảnh khuôn viên HUCE và biểu mẫu tên đăng nhập, mật khẩu. Các tài khoản thuộc bản cài của dự án. Canopy Flow UI sử dụng sidebar liên tục với vùng nội dung, menu pill, góc nối ở mục active, thu gọn thành icon và menu mở/đóng trên điện thoại cho cả bốn vai trò.

| Nguồn tham khảo | Áp dụng trong FM05 |
| --- | --- |
| [CalCentral, UC Berkeley](https://sis.berkeley.edu/sites/default/files/how_to_assign_advisors_to_students_0.pdf) | Trang **Tổng quan** của sinh viên kết hợp buổi học tiếp theo, các buổi sắp tới và thông báo mới. |
| [Axess, Stanford](https://studentservices.stanford.edu/my-academics/enroll-classes/how-do-i-enroll/view-edit-planned-classes) | Cùng dữ liệu lịch có chế độ lưới tuần và chế độ danh sách. |
| [My.Calendar, Harvard](https://my.harvard.edu/) | Nút **Tải lịch .ics** trên lịch công bố. Tệp lấy đúng tuần hoặc học kỳ và bộ lọc đang chọn, theo dữ liệu mà tài khoản có quyền xem. |

Trên điện thoại, menu thu gọn của sinh viên chứa Tổng quan, Thời khóa biểu và Thông báo. Trong Tổng quan, lịch sắp tới lấy các buổi đã công bố trong 14 ngày của học kỳ và dùng giờ Việt Nam. Có thể mở chi tiết từng buổi. Tệp `.ics` bỏ các buổi đã hủy, giữ buổi thay thế và có thể nhập vào ứng dụng lịch hỗ trợ iCalendar. [Đặc tả iCalendar](https://www.rfc-editor.org/rfc/inline-errata/rfc5545.html).

Chạy `npm test` trong `frontend/` để kiểm tra xuất lịch, gồm tiếng Việt, giờ Việt Nam, buổi đã hủy và buổi thay thế.

Đã kiểm tra trực tiếp giao diện trên Chromium ở độ rộng 1440, 390 và 320 px. Các ca kiểm tra gồm đăng nhập, chuyển nhóm chức năng, lịch theo tuần và theo ngày, chi tiết buổi học, đóng hộp thoại bằng bàn phím và xuất CSV.

## Đối chiếu chức năng FM05

| Vai trò | Chức năng và luồng bàn giao |
| --- | --- |
| Nhân viên đào tạo | Tạo, sửa, hủy lớp, gán giảng viên và nhóm trong khoa. Xếp lịch, gửi duyệt, quản lý danh mục theo cơ sở và xử lý đề nghị đổi buổi. |
| Quản lý | Xem lịch toàn trường, chạy và áp dụng xếp tự động, xem lưới và thống kê phòng của phương án, trả lại hoặc công bố, xem thống kê lịch thực tế. |
| Giảng viên và trợ giảng | Xem lịch các lớp được phân công, khai báo giờ bận, gửi đề nghị nghỉ hoặc học bù, nhận kết quả xử lý. |
| Sinh viên | Xem và xuất lịch các lớp đã đăng ký, nhận thông báo khi công bố hoặc đổi lịch. |

Đăng nhập, kiểm tra xung đột và thông báo là các chức năng dùng chung trong phạm vi quyền của từng vai trò. Công bố từ một quyết định đang chờ chạy trong cùng giao dịch. Khi có xung đột phát sinh, quyết định và các buổi tạo mới đều được hoàn tác. Mã lớp, tên học phần và mã phòng được ghi vào hồ sơ mới khi gửi duyệt để bản xem trước dùng đúng thông tin mà quản lý nhận được. Buổi học bù và đổi phòng đi qua ngoại lệ của buổi gốc để giữ lịch sử.

| Mã trong đặc tả | Màn hình hoặc điểm kiểm tra |
| --- | --- |
| UC00 | Đăng nhập bằng tài khoản mẫu của bốn vai trò. Xác thực hệ thống trường thuộc FM15. |
| UC01–UC02 | Lớp học phần, lớp hành chính đối tượng và phân công giảng viên/trợ giảng. |
| UC03–UC04, UC11 | Xếp thủ công và tự động, kiểm tra xung đột khi xem trước và khi ghi. |
| UC05–UC06 | Kiểm tra điều kiện, gửi duyệt, xem phương án, trả lại, duyệt và công bố. |
| UC07–UC08 | Nghỉ, bù, đổi phòng, danh mục phòng, khung tiết và ngày không học. |
| UC09–UC10 | Xem tuần hoặc học kỳ, xuất CSV, thống kê sử dụng phòng và giảng viên. |
| UC12–UC14 | Giảng viên khai báo giờ bận hoặc đề nghị đổi lịch. Nhân viên xử lý, hệ thống gửi thông báo cho người liên quan. |

Đề nghị cho buổi chưa gán phòng được chuyển đến nhân viên khoa phụ trách. Ngày nghỉ toàn trường kiểm tra cả các buổi này trước khi lưu. Màn hình thông báo hỗ trợ đánh dấu từng mục hoặc đánh dấu tất cả của tài khoản đang đăng nhập, kể cả các mục ở trang khác.

Chạy kiểm thử đơn vị sau khi cài các gói backend:

```bash
cd backend
npm test
```

Đã kiểm tra thực tế trên PostgreSQL 16.9 và Chromium. Các ca hồi quy gồm xếp trùng phòng đồng thời, xung đột giảng viên và trợ giảng, quyền theo khoa, phiên bản cũ khi cập nhật, không được tự duyệt, sửa lịch sau khi công bố, công bố chống lặp, và luồng đề nghị từ giảng viên đến nhân viên rồi cập nhật lịch và thông báo cho sinh viên.

Bài TC03 chạy với worker thực tế cho chi phí **3000,83**, **0,83** và **3,83**, đúng với ba trường hợp ở mục 6.4. Kiểm thử UC05 xác minh nhiều lớp bị chặn cùng lúc, màn hình 390 px, xung đột phát sinh giữa lúc kiểm tra và gửi, và hồ sơ chỉ được tạo sau khi điều kiện hợp lệ. Buổi học có sẵn chưa gán phòng vẫn được tính khi kiểm tra trùng giảng viên.

TC01–TC02 được đối chiếu trên PostgreSQL theo nền tảng của bản triển khai. Migration tạo đủ 13 bảng FM05. Các phép thử T1–T8 kiểm tra phân công chính và trợ giảng, lần chạy và phiên bản chờ duyệt duy nhất, thời gian buổi học, buổi thay thế bắt buộc, chồng tiết và sinh buổi theo lịch có ngày nghỉ.

Ở TC04, lỗi trùng mã được hiển thị tại trường **Mã lớp** và biểu mẫu giữ các trường khác. Đã thử nhập lại mã, sửa và lưu thành công trên Chromium ở màn hình 390 px.

Bộ thử hiệu năng giả lập gồm **30 lớp, 60 buổi mỗi tuần, 10 giảng viên, 6 nhóm hành chính và 5 phòng giảng đường phù hợp**. Chạy các seed 1–10 với tham số mặc định. Cả 10 lần đạt 0 vi phạm cứng, từ 4,62 đến 32,23 giây tính từ lúc gửi yêu cầu đến lúc có kết quả. Đây là kết quả trên dữ liệu kiểm thử.

Bản FM05 chạy độc lập dùng tài khoản, học kỳ và đăng ký học phần mẫu. Quản lý tài khoản và đăng ký học phần thuộc các phân hệ khác. Kết nối với các phân hệ ngoài FM05 cần môi trường tích hợp của cổng trường.

## HUCEChat

Mở **HUCEChat** sau khi đăng nhập. Câu gợi ý theo vai trò và trang đang mở. Kết quả tra trực tiếp cơ sở dữ liệu, ghi thời điểm và có liên kết đến trang liên quan. Có thể chọn **Lịch sử** để tìm lại theo tiêu đề hoặc tạo cuộc trò chuyện mới.

| Vai trò | Nội dung trong HUCEChat |
| --- | --- |
| Nhân viên đào tạo | Lớp học phần và lớp nháp trong khoa, hồ sơ duyệt, đề nghị đổi lịch. Xem trước lớp nháp, lần xếp tự động hoặc phương án cần áp dụng. |
| Quản lý | Lớp học phần, hồ sơ duyệt, thống kê phòng và giờ dạy. Xem trước lần xếp tự động hoặc phương án cần áp dụng. |
| Giảng viên | Lịch giảng đã công bố trong phạm vi phân công, buổi kế tiếp, thời gian bận và đề nghị đổi lịch của mình. |
| Sinh viên | Lịch đã công bố của lớp mình đang học và buổi kế tiếp. |
| Cả bốn vai trò | Xem thông báo cá nhân, đề xuất đánh dấu thông báo đã đọc và hỏi cách thao tác. |

Ví dụ: “Lịch học thứ năm tuần sau”, “Buổi dạy tiếp theo”, “Tra lớp học phần mã CSDL.01”, “Hồ sơ chờ duyệt”, “Tạo lớp học phần”. Sau câu hỏi về lịch, có thể hỏi tiếp “Còn ngày mai?”. Chọn học kỳ trước khi tra. Khi ở trang **Xếp lịch tự động**, chatbot lấy tham số đang nhập và có thể gợi ý lần chạy đang xem. Ở trang khác, nhập mã lần chạy rõ ràng, chẳng hạn “Áp dụng phương án #12”.

Thao tác ghi có bản xem trước và hạn 15 phút. Chỉ nút **Xác nhận** thực hiện thay đổi. Server kiểm tra lại danh tính, quyền và trạng thái, gọi cùng nghiệp vụ với trang thao tác tay và lưu kết quả để xác nhận lặp không ghi lần nữa. Có thể hủy đề xuất trước khi xác nhận. Quản lý vẫn duyệt và công bố lịch tại trang nghiệp vụ. Lịch sử lưu trong PostgreSQL theo tài khoản và phạm vi, hiển thị tối đa 20 cuộc gần nhất và 60 lượt gần nhất mỗi cuộc.

Các câu hỏi thông dụng và đề xuất trên hoạt động khi cài local. Để phân loại thêm những cách diễn đạt khác, điền `GEMINI_API_KEY` và `GEMINI_MODEL` trong `.env` ở backend và khởi động lại. Chọn model có hỗ trợ [đầu ra JSON có cấu trúc](https://ai.google.dev/gemini-api/docs/structured-output). Gemini nhận câu hỏi cần phân loại và các thao tác được cung cấp cho vai trò hiện tại. Dữ liệu trả lời được truy vấn và định dạng tại máy chủ của ứng dụng. Tránh nhập thông tin cá nhân trong câu hỏi gửi mô hình.

### Chạy kiểm thử liên vai trò

Từ `backend/`, chạy lệnh dưới đây với một database demo mới, tạo riêng để kiểm thử. Phép thử tạo lớp, xếp và công bố lịch, xử lý đổi lịch và đánh dấu thông báo.

```powershell
$env:FM05_TEST_URL = 'http://127.0.0.1:8080'
npm run test:workflows -- --disposable-demo
```

## API buổi học cụ thể

| Thao tác | Đường dẫn |
| --- | --- |
| Tạo buổi học bù từ buổi đã công bố | `POST /api/class-sessions` với `sourceSessionId`, `date`, `roomId`, `periodFrom`, `reason` |
| Đổi phòng hoặc dời buổi đã công bố | `PATCH /api/class-sessions/:id` với `action` là `ROOM_CHANGE` hoặc `RESCHEDULE` |
| Ghi hủy buổi hoặc xử lý qua luồng ngoại lệ | `POST /api/class-sessions/:id/exceptions` |

Các thao tác tạo buổi thay thế và đổi lịch đều dùng cùng quy tắc ngoại lệ, giữ tham chiếu tới buổi gốc và thông báo cho người liên quan.

## Thực nghiệm giải thuật

Khi toàn bộ lịch dùng cùng cơ sở và khoảng hiệu lực, worker đếm lượt chiếm mỗi ô thứ–tiết theo phòng, giảng viên và lớp hành chính như mục 6.3.3. Mỗi lượt vượt quá lượt đầu tăng một điểm cứng, kể cả khi ô đã có lịch cố định. Ví dụ ba buổi cùng chiếm một ô tính hai lượt vượt. Với nhiều cơ sở hoặc khoảng ngày khác nhau, worker kiểm tra các cặp theo phạm vi hiệu lực và giờ thực để nhận ra trường hợp cùng số tiết nhưng khác giờ hoặc ngược lại.

Chạy thực nghiệm từ `backend/`. Lệnh đầu so sánh ba phương pháp, thay `small` bằng `medium` hoặc `large` để dùng hai bộ còn lại. Lệnh thứ hai thử 18 cấu hình gồm ba kích thước quần thể, ba xác suất đột biến và bật/tắt đột biến sửa lỗi trên bộ nhỏ.

```bash
npm run experiment -- --size=small --trials=10 --population=50 --generations=20 --budgetMs=3500
npm run experiment -- --size=small --sweep --trials=10 --generations=12 --budgetMs=3500
```

Tùy chọn `--json` xuất số liệu từng seed và thống kê. `--budgetMs` đặt thời gian tính của worker thực nghiệm. Thời gian trong bảng đo từ lúc khởi tạo worker tới khi nhận kết quả, gồm chi phí truyền dữ liệu. Phép thử trên dùng giới hạn 20 thế hệ và 3.500 ms trong worker. Trình chạy chọn phương án ngẫu nhiên tốt nhất từ tối đa 1.000 lượt lấy mẫu trong giới hạn đó. Ngưỡng chạy của ứng dụng là 60 giây.

| Bộ thử (lớp/buổi/phòng) | Phương pháp | H = 0 | Chi phí mềm TB ± độ lệch chuẩn | Thế hệ TB | Thời gian TB (s) |
| --- | --- | --- | --- | --- | --- |
| Nhỏ (20/35/8) | Tham lam | 10/10 | 37,20 ± 4,42 | 0,0 | 0,11 |
| Nhỏ (20/35/8) | Ngẫu nhiên | 0/10 | 95,08 ± 6,99 | 19,0 | 0,22 |
| Nhỏ (20/35/8) | GA | 10/10 | 26,70 ± 1,31 | 20,0 | 0,34 |
| Vừa (100/180/25) | Tham lam | 10/10 | 183,89 ± 10,48 | 0,0 | 0,34 |
| Vừa (100/180/25) | Ngẫu nhiên | 0/10 | 471,37 ± 18,01 | 19,0 | 1,06 |
| Vừa (100/180/25) | GA | 10/10 | 168,38 ± 4,55 | 20,0 | 2,06 |
| Lớn (300/550/40) | Tham lam | 10/10 | 661,03 ± 20,86 | 0,0 | 1,32 |
| Lớn (300/550/40) | Ngẫu nhiên | 0/10 | 1.445,78 ± 41,38 | 18,5 | 4,56 |
| Lớn (300/550/40) | GA | 10/10 | 624,71 ± 7,80 | 1,0 | 3,74 |

Kết quả của 270 lượt chạy giả lập nằm trong `backend/scripts/experiment-results.json`. Ở phép thử trên bộ nhỏ, bật sửa lỗi cho chi phí mềm trung bình thấp hơn ở 8/9 cặp cấu hình, ngang bằng ở một cặp và mất thêm thời gian. Cả 18 cấu hình đều có 10/10 lượt đạt H = 0 trên dữ liệu này.

Phép đo qua API dùng PostgreSQL với 100 lớp, 180 buổi, 25 phòng, 40 giảng viên, 20 lớp hành chính, quần thể 100 và tối đa 60 thế hệ. Với seed 1–10, cả 10 lượt hoàn thành trong **17,30–21,24 giây**, xếp đủ 180 buổi và đều có H = 0. Trong lúc chạy, 447 lượt gọi API đọc thông tin tài khoản đều có phản hồi, lâu nhất 367,5 ms. Tệp kết quả cũng lưu từng lượt đo qua API.

## Phân trang và kiểm soát các thao tác ghi

Danh sách lớp học phần, buổi học cả học kỳ, đề nghị đổi lịch và thông báo lấy dữ liệu theo trang. Xuất CSV dùng toàn bộ điều kiện lọc đang chọn. Lưới tuần đọc trọn tuần tương ứng.

Các API danh sách sau hỗ trợ `page` và `pageSize`. Khi truyền một trong hai tham số, kết quả có cấu trúc `{items, total, page, pageSize}`. Mặc định là trang 1 với 25 bản ghi, giới hạn 100 bản ghi mỗi trang. Các lời gọi kiểu cũ vẫn nhận mảng.

- `/api/course-sections`, `/api/schedule-patterns` và `/api/schedule`
- `/api/timetable-versions` và `/api/schedule-runs`
- `/api/session-change-requests` và `/api/notifications`

Các thao tác ghi dùng giao dịch, như tạo lớp, sửa lịch và công bố, hỗ trợ header `Idempotency-Key`. Gửi lại cùng tài khoản, thao tác và nội dung nhận lại kết quả đã lưu trong 24 giờ. Dùng lại mã cho nội dung khác bị từ chối. Mã và kết quả thành công được ghi trong cùng giao dịch với nghiệp vụ. Khi mất kết nối, trình duyệt giữ mã của lần gửi còn chưa biết kết quả để dùng lại khi gửi cùng nội dung trong tab.

API trả `X-Request-Id` trong phản hồi. Phản hồi lỗi cũng có `request_id`, được hiển thị trong giao diện để tra cứu. Khi lớp học phần hoặc lịch định kỳ đã được người khác sửa, biểu mẫu giữ phần đang nhập và mở phần so sánh với dữ liệu đang lưu. Người dùng có thể xác nhận dùng phiên bản mới rồi bấm lưu lại.

Đã kiểm tra phân trang qua API và Chromium, gồm tổng số, trang vượt phạm vi, giới hạn truy cập theo khoa, vượt 25 dòng và xuất CSV cả học kỳ. Cũng đã thử gửi đồng thời hai yêu cầu tạo lớp với cùng mã chống lặp, sửa cùng bản ghi từ hai lượt thao tác và lưu lại sau khi đối chiếu phiên bản.


## Kiểm tra đăng nhập sau khi cài đặt

Chạy `npm ci` trong từng thư mục frontend và backend. Sau đó, tại frontend chạy `npm test` và `npm run build`; tại backend chạy `npm test`. Khởi động backend với database demo riêng rồi mở terminal khác tại backend:

```powershell
npm run test:auth
```

Nếu chạy bằng Docker ở cổng 8081, trước khi chạy kiểm thử trên máy chủ đặt `$env:FM05_TEST_URL = 'http://127.0.0.1:8081'` (PowerShell). Kiểm thử tự động đăng nhập nhanh và đăng nhập thường theo bốn vai trò, kiểm tra API giới hạn quyền, CSRF, mật khẩu sai và đăng xuất. Không ghi dữ liệu nghiệp vụ.

Với bản riêng mới, chạy `npm run test:auth -- private` (trỏ `FM05_TEST_URL` đến đúng cổng). Kiểm thử xác nhận API demo bị chặn và bốn tài khoản mẫu không tồn tại. Nếu đã tạo một tài khoản riêng, có thể đặt `FM05_TEST_USERNAME`, `FM05_TEST_PASSWORD` và `FM05_TEST_ROLE` trong môi trường kiểm thử để kiểm tra thêm đăng nhập của tài khoản đó; không ghi mật khẩu vào mã nguồn. Không đặt tên/mật khẩu giống tài khoản mẫu khi chạy ca kiểm tra bản trắng.

Bảng `fm05_runtime_mode` là metadata kỹ thuật xác định chế độ của database, không thuộc nghiệp vụ FM05. Để thay chế độ, tạo database mới; không xóa dữ liệu cũ. Các bước kiểm tra đã thực hiện cho gói này và giới hạn môi trường được ghi trong `KIEM_THU.md`.
