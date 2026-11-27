import { useEffect, useState } from 'react'
import { api } from '../services/api'
import { ErrorBox } from '../components/common/ErrorBox'
import Login from '../features/auth/Login'
import Workspace from './Workspace'

export default function App() {
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
