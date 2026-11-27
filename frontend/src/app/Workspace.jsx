import { useEffect, useState } from 'react'
import { api } from '../services/api'
import { navigation, resolvePage } from './navigation'
import { destinations } from './destinations'
import { Icon } from '../components/common/Icon'
import { Brand } from '../components/common/Brand'
import { ErrorBox } from '../components/common/ErrorBox'
import { Field, Select } from '../components/forms/FormFields'
import { formatDate } from '../utils/dates'
import { roleNames } from '../constants/domain'
import { useFetch } from '../hooks/useFetch'
import HuceChat from '../features/chat/HuceChat'

export default function Workspace({ user, onLogout }) {
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
