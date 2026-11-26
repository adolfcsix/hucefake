import { useEffect, useState, useRef } from 'react'
import { createRoot } from 'react-dom/client'
import { api } from './api'
import campusPhoto from './assets/huce-campus.webp'
import { navigation, resolvePage } from './navigation'
import { Pagination, usePagedQuery } from './Pagination'
import Catalog from './Catalog'
import HuceChat from './HuceChat'
import Sections from './Sections'
import StudentHome from './StudentHome'
import { ChangeRequests, Planning, Published } from './Timetable'
import { Approvals, Automatic, Availability } from './Workflow'
import { ErrorBox, Field, formatDate, Panel, roleNames, Select, Table, useFetch } from './ui'
import '@fontsource/be-vietnam-pro/latin-400.css'
import '@fontsource/be-vietnam-pro/vietnamese-400.css'
import '@fontsource/be-vietnam-pro/latin-500.css'
import '@fontsource/be-vietnam-pro/vietnamese-500.css'
import '@fontsource/be-vietnam-pro/latin-600.css'
import '@fontsource/be-vietnam-pro/vietnamese-600.css'
import '@fontsource/be-vietnam-pro/latin-700.css'
import '@fontsource/be-vietnam-pro/vietnamese-700.css'
import './style.css'

const destinations = {
  overview: ['Tổng quan', 'home', StudentHome],
  schedule: ['Thời khóa biểu', 'calendar', Published],
  sections: ['Lớp học phần', 'layers', Sections],
  planning: ['Xếp lịch thủ công', 'grid', Planning],
  automatic: ['Xếp lịch tự động', 'spark', Automatic],
  approvals: ['Duyệt thời khóa biểu', 'check', Approvals],
  catalog: ['Danh mục lịch học', 'folder', Catalog],
  availability: ['Thời gian bận', 'clock', Availability],
  requests: ['Đề nghị đổi lịch', 'repeat', ChangeRequests],
  statistics: ['Thống kê sử dụng', 'chart', Statistics],
  notifications: ['Thông báo', 'bell', Notifications]
}
const icons = {
  home:'m3 10 9-7 9 7v10h-6v-6H9v6H3z',
  calendar:'M4 7h16v13H4z M4 11h16 M8 4v5m8-5v5 M8 15h2m4 0h2',
  layers:'m3 8 9-5 9 5-9 5z M3 12l9 5 9-5M3 16l9 5 9-5',
  grid:'M4 4h16v16H4zM4 9h16M9 9v11m6-11v11',
  spark:'M12 2 9 9 2 12l7 3 3 7 3-7 7-3-7-3z',
  check:'M5 3h14v18H5z m3 9 3 3 5-6',
  folder:'M3 6h7l2 3h9v11H3z',
  clock:'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zm0 4v5l3 2',
  repeat:'M4 8h15m-4-4 4 4-4 4m5 4H5m4-4-4 4 4 4',
  chart:'M4 3v17h17 M8 16v-4m4 4V6m4 10v-7m4 7V5',
  bell:'M5 17h14l-2-3V9a5 5 0 0 0-10 0v5zm5 3h4',
  exit:'M11 5H5v14h6m2-7h8m-4-4 4 4-4 4',
  menu:'M4 6h16M4 12h16M4 18h16',
  close:'M6 6l12 12M18 6 6 18',
  collapse:'M4 4h16v16H4z M10 4v16 m6-11-3 3 3 3',
  expand:'M4 4h16v16H4z M10 4v16 m3-11 3 3-3 3',
  arrow:'M5 12h14m-6-6 6 6-6 6',
  user:'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zm-8 8v-2a8 8 0 0 1 16 0v2',
  book:'M12 6c-3-2-6-2-9-1v14c3-1 6-1 9 1 3-2 6-2 9-1V5c-3-1-6-1-9 1Zm0 0v14'
}
function Icon({ name }) {
  return <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={icons[name]} /></svg>
}
function Brand() {
  return <div className="brand">
    <span className="brand-mark"><Icon name="book" /></span>
    <div className="brand-copy"><strong>HUCE</strong></div>
  </div>
}

function App() {
  const [user, setUser] = useState(undefined)
  const [startupError, setStartupError] = useState(null)
  const [attempt, retry] = useState(0)
  useEffect(() => {
    let active = true
    setStartupError(null)
    api('/me').then(value => { if (active) setUser(value) }).catch(error => {
      if (!active) return
      if (error.status === 401) setUser(null)
      else setStartupError(error)
    })
    return () => { active = false }
  }, [attempt])
  if (user === undefined) return <main className="centered">{startupError ?
    <div className="stack"><ErrorBox error={startupError} /><button onClick={() => retry(n => n+1)}>Thử lại kết nối</button></div> : 'Đang tải…'}</main>
  if (!user) return <Login onLogin={setUser} />
  return <Workspace key={user.id} user={user} onLogout={() => setUser(null)} />
}

const demoRoles = [
  ['STAFF','Nhân viên đào tạo','layers'], ['MANAGER','Quản lý','check'],
  ['LECTURER','Giảng viên','book'], ['STUDENT','Sinh viên','user']
]
function Login({ onLogin }) {
  const [username,setUsername] = useState('')
  const [password,setPassword] = useState('')
  const [showPassword,setShowPassword] = useState(false)
  const [error,setError] = useState(null)
  const [pending,setPending] = useState(null)
  const [mode,setMode] = useState(null)
  const [modeError,setModeError] = useState(null)
  const lock = useRef(false)
  useEffect(() => {
    let active = true
    api('/auth/mode').then(value => { if (active) setMode(value) })
      .catch(error => { if (active) setModeError(error) })
    return () => { active = false }
  }, [])
  async function signIn(path, body, kind) {
    if (lock.current) return
    lock.current = true; setPending(kind); setError(null)
    try { onLogin(await api(path,'POST',body)) }
    catch (error) { setError(error) }
    finally { lock.current = false; setPending(null) }
  }
  function submit(event) {
    event.preventDefault()
    signIn('/auth/login',{ username:username.trim(),password },'manual')
  }
  return <main className="login">
    <div className="login-layout">
      <div className="login-intro">
        <img src={campusPhoto} alt="" width="1600" height="900" fetchPriority="high" />
        <p>Chào mừng đến với HUCE</p>
      </div>
      <section className="login-form" aria-labelledby="login-title">
        <div className="login-inner">
          <Brand />
          <h1 id="login-title">Đăng nhập</h1>
          <form className="stack" onSubmit={submit}>
            <ErrorBox error={error || modeError} />
            <Field label="Tên đăng nhập"><input value={username} onChange={e => setUsername(e.target.value)} required autoComplete="username" disabled={!!pending} /></Field>
            <div className="field"><label htmlFor="login-password">Mật khẩu</label><div className="password-field"><input id="login-password" type={showPassword?'text':'password'} value={password} onChange={e => setPassword(e.target.value)} required autoComplete="current-password" disabled={!!pending} /><button type="button" className="quiet" aria-pressed={showPassword} onClick={() => setShowPassword(v=>!v)}>{showPassword?'Ẩn':'Hiện'}</button></div></div>
            <button className="login-submit" disabled={!!pending}>{pending==='manual' ? 'Đang đăng nhập…' : 'Đăng nhập'}<Icon name="arrow" /></button>
          </form>
          {mode?.demo === true && <section className="demo-accounts" aria-label="Tài khoản dùng thử">
            <h2>Tài khoản dùng thử</h2>
            <div className="demo-grid">{demoRoles.map(([role,label,icon]) => <button key={role} type="button" className="quiet demo-choice" disabled={!!pending} onClick={()=>signIn('/auth/demo-login',{role},role)}><Icon name={icon}/><span>{pending===role?'Đang vào…':label}</span><Icon name="arrow"/></button>)}</div>
          </section>}
        </div>
      </section>
    </div>
  </main>
}
function Workspace({ user, onLogout }) {
  const groups = navigation[user.role]
  const student = user.role === 'STUDENT'
  const [menuOpen, setMenuOpen] = useState(false)
  const [collapsed, setCollapsed] = useState(() => { try { return localStorage.getItem('fm05.sidebar.collapsed') === 'true' } catch { return false } })
  function toggleCollapsed() { setCollapsed(value => !value) }
  useEffect(() => { try { localStorage.setItem('fm05.sidebar.collapsed',String(collapsed)) } catch {} }, [collapsed])
  useEffect(() => { const close = e => { if (e.key === 'Escape') setMenuOpen(false) }; window.addEventListener('keydown',close); return () => window.removeEventListener('keydown',close) }, [])
  const [page, setPage] = useState(() => {
    return resolvePage(user.role, location.hash.slice(1))
  })
  const [semesterId, setSemester] = useState('')
  const [revision, setRevision] = useState(0)
  const [schedulingContext, setSchedulingContext] = useState(null)
  const [error, setError] = useState(null)
  const catalog = useFetch(() => api('/catalog'), [revision])
  const semesters = catalog.data?.semesters || []
  const semester = semesters.find(s => s.id === Number(semesterId)) || semesters[0]
  const [title, , Screen] = destinations[page]
  const reload = () => setRevision(x => x + 1)
  useEffect(() => {
    const change = () => {
      const target = location.hash.slice(1)
      setPage(resolvePage(user.role,target))
      setMenuOpen(false)
    }
    window.addEventListener('hashchange',change)
    return () => window.removeEventListener('hashchange',change)
  }, [user.role])
  async function logout() {
    try { await api('/auth/logout','POST'); location.hash=''; onLogout() }
    catch (error) { if (error.status===401) onLogout(); else setError(error) }
  }
  return <div className={'workspace cfui' + (student ? ' student-workspace' : '') + (collapsed ? ' is-collapsed' : '')}>
    <aside className="sidebar">
      <div className="sidebar-head"><Brand /><button type="button" className="collapse-toggle icon-only" aria-label={collapsed ? 'Mở rộng sidebar' : 'Thu gọn sidebar'} title={collapsed ? 'Mở rộng sidebar' : 'Thu gọn sidebar'} aria-expanded={!collapsed} aria-controls="workspace-navigation" onClick={toggleCollapsed}><Icon name={collapsed ? 'expand' : 'collapse'}/></button></div>
      <button type="button" className="menu-toggle icon-only" aria-label={menuOpen ? 'Đóng menu' : 'Mở menu'}
        aria-expanded={menuOpen} aria-controls="workspace-navigation" onClick={() => setMenuOpen(open => !open)}>
        <Icon name={menuOpen ? 'close' : 'menu'} />
      </button>
      <nav id="workspace-navigation" className={menuOpen ? 'is-open' : undefined} aria-label="Phân hệ học vụ">
        {groups.map(([label, keys]) => <div className="nav-group" key={label}>
          <p>{label}</p>
          <ul>{keys.map(key => <li key={key}>
            <a href={'#' + key} title={destinations[key][0]} aria-label={destinations[key][0]} aria-current={key === page ? 'page' : undefined} onClick={() => { setPage(key); setMenuOpen(false) }}>
              <Icon name={destinations[key][1]} /><span>{destinations[key][0]}</span>
            </a>
          </li>)}</ul>
        </div>)}
      </nav>
      <div className="sidebar-foot"><span className="sidebar-role">{roleNames[user.role]}</span><button type="button" className="sidebar-exit" aria-label="Đăng xuất" title="Đăng xuất" onClick={logout}><Icon name="exit"/><span>Đăng xuất</span></button></div>
    </aside>
    <main>
      <div className="masthead">
        <div className="masthead-section">{groups.find(([, keys]) => keys.includes(page))?.[0]}</div>
        <div className="identity"><div className="avatar" aria-hidden="true">{user.username.slice(0,1).toUpperCase()}</div>
          <div><strong>{user.username}</strong><small>{roleNames[user.role]}</small></div>
          <button type="button" className="quiet icon-only mobile-exit" aria-label="Đăng xuất" title="Đăng xuất" onClick={logout}><Icon name="exit" /></button>
        </div>
      </div>
      <header className="top"><h1>{title}</h1>
        {!!semesters.length && !['catalog','notifications'].includes(page) && <div className="term">
          <Field label="Học kỳ">
            <Select value={semester?.id} set={setSemester} options={semesters.map(s => [s.id,s.code])} />
          </Field>
          {semester && <small>{formatDate(semester.start_date)} – {formatDate(semester.end_date)}</small>}
        </div>}
      </header>
      <div className="content">
        <ErrorBox error={error || catalog.error} />
        {catalog.error && <button className="quiet" onClick={() => { if (catalog.error.status === 401) { location.hash = ''; onLogout() } else { setError(null); reload() } }}>{catalog.error.status === 401 ? 'Đăng nhập lại' : 'Thử tải lại dữ liệu'}</button>}
        {catalog.data && (semester || ['catalog','notifications'].includes(page)) ?
          <Screen key={page + ':' + semester?.id} {...{ user, catalog:catalog.data, semester, revision, reload, setSchedulingContext }} /> :
          !catalog.error && <div className="empty" role="status">{catalog.data ? <>
            <strong>Chưa có học kỳ.</strong>
            <p>{user.role === 'STAFF' || user.role === 'MANAGER' ?
              'Nạp học kỳ và danh mục bằng công cụ thiết lập Java theo README, sau đó tải lại dữ liệu.' :
              'Hãy liên hệ bộ phận đào tạo để được cập nhật học kỳ.'}</p>
            <button type="button" className="quiet" onClick={reload}>Tải lại dữ liệu</button>
          </> : 'Đang tải dữ liệu…'}</div>}
      </div>
    </main>
    <HuceChat role={user.role} semester={semester} page={page} reload={reload}
      scheduling={page==='automatic' && schedulingContext?.semesterId===semester?.id ? schedulingContext : null}
      openSource={id=>{if (semesters.some(term=>term.id===id)) setSemester(String(id))}} />
  </div>
}
function Statistics({ semester, revision }) {
  const data = useFetch(() => api('/schedule/statistics?semesterId=' + semester.id), [semester.id,revision])
  return <>
    <ErrorBox error={data.error} />
    <div className="split">
      {[['rooms','Sử dụng phòng'],['lecturers','Giờ dạy theo giảng viên']].map(([key,title]) =>
        <Panel title={title} key={key}><Table rows={(data.data?.[key] || []).map(r => ({ ...r,id:r.id ?? r.code ?? 'none' }))}
          columns={[[key==='rooms' ? 'Phòng' : 'Giảng viên','code'],...(key==='rooms' ? [['Cơ sở','campus_code']] : []),['Số buổi','sessions'],['Tổng tiết','periods']]} /></Panel>)}
    </div>
  </>
}
function Notifications({ revision, reload }) {
  const data = usePagedQuery('/notifications',revision)
  const [error,setError] = useState(null)
  const [message,setMessage] = useState('')
  const [busy,setBusy] = useState(false)
  async function mark(id) {
    try { await api('/notifications/' + id + '/read','POST'); reload() }
    catch (error) { setError(error) }
  }
  async function markAll() {
    setBusy(true)
    setError(null)
    setMessage('')
    try {
      const {marked} = await api('/notifications/read-all','POST')
      setMessage(marked ? 'Đã đánh dấu ' + marked + ' thông báo là đã đọc.' : 'Bạn đã đọc tất cả thông báo.')
      reload()
    } catch (error) { setError(error) }
    finally { setBusy(false) }
  }
  return <Panel title="Thông báo của bạn" action={!!data.data?.total &&
    <button className="quiet" disabled={busy} onClick={markAll}>Đánh dấu tất cả đã đọc</button>}>
    <ErrorBox error={error || data.error} />
    {message && <p className="notice success" role="status">{message}</p>}
    <Table rows={data.data?.items || []} empty="Chưa có thông báo." columns={[
      ['Ngày',r => formatDate(r.created_at)],['Nội dung','message'],
      ['Trạng thái',r => r.read_at ? 'Đã đọc' : <button className="quiet" disabled={busy} onClick={() => mark(r.id)}>Đánh dấu đã đọc</button>]
    ]} />
    <Pagination query={data} itemName="thông báo" />
  </Panel>
}

createRoot(document.getElementById('root')).render(<App />)
