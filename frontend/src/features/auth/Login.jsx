import { useEffect, useState, useRef } from 'react'
import { api } from '../../services/api'
import campusPhoto from '../../assets/huce-campus.webp'
import { Icon } from '../../components/common/Icon'
import { Brand } from '../../components/common/Brand'
import { ErrorBox } from '../../components/common/ErrorBox'
import { Field } from '../../components/forms/FormFields'

const demoRoles = [
  ['STAFF','Nhân viên đào tạo','layers'], ['MANAGER','Quản lý','check'],
  ['LECTURER','Giảng viên','book'], ['STUDENT','Sinh viên','user']
]
export default function Login({ onLogin }) {
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
