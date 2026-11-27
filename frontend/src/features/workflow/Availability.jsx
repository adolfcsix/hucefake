import { useState } from 'react'
import { api } from '../../services/api'
import { numeric } from '../../utils/forms'
import { dayOptions } from '../../constants/domain'
import { ErrorBox } from '../../components/common/ErrorBox'
import { Field, Options, Select } from '../../components/forms/FormFields'
import { Panel } from '../../components/common/Panel'
import { useFetch } from '../../hooks/useFetch'

export function Availability({ semester, revision, reload }) {
  const state = useFetch(() => api('/me/availability?semesterId=' + semester.id), [semester.id, revision])
  if (state.error) return <ErrorBox error={state.error} />
  if (!state.data) return <Panel title="Thời gian bận"><p>Đang tải…</p></Panel>
  return <BusyList key={revision} items={state.data} semester={semester} reload={reload} />
}
function BusyList({ items, semester, reload }) {
  const [rows, setRows] = useState(items.map(r => ({ key:r.id,dayOfWeek:r.day_of_week,periodFrom:r.period_from,periodTo:r.period_to,kind:r.kind,reason:r.reason || '' })))
  const [error, setError] = useState(null)
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  function edit(key, field, value) { setRows(list => list.map(r => r.key === key ? { ...r, [field]:value } : r)) }
  async function save(event) {
    event.preventDefault()
    setError(null); setMessage(''); setBusy(true)
    try {
      await api('/me/availability','PUT',{ semesterId:semester.id, entries:rows.map(r => numeric(r,['dayOfWeek','periodFrom','periodTo'])) })
      setMessage('Đã lưu thời gian bận.')
    } catch (e) { setError(e) }
    finally { setBusy(false) }
  }
  return <Panel title="Khai báo thời gian bận">
    <p className="muted">Khoảng Bận chặn xếp lịch. Khoảng Hạn chế xếp được tính khi hệ thống chọn phương án tự động.</p>
    <ErrorBox error={error} />
    {message && <p className="notice success" role="status">{message}</p>}
    <form className="stack" onSubmit={save}>
      {rows.map(r => <div className="busy-row" key={r.key}>
        <Field label="Thứ"><Select value={r.dayOfWeek} set={v => edit(r.key,'dayOfWeek',v)} options={dayOptions} /></Field>
        {['periodFrom','periodTo'].map((k,i) => <Field label={i ? 'Đến tiết' : 'Từ tiết'} key={k}>
          <input type="number" min="1" max="15" required value={r[k]} onChange={e => edit(r.key,k,e.target.value)} /></Field>)}
        <Field label="Loại"><Options value={r.kind} set={v => edit(r.key,'kind',v)} values={{ BUSY:'Bận', AVOID:'Hạn chế xếp' }} /></Field>
        <Field label="Lý do"><input value={r.reason} maxLength={500} onChange={e => edit(r.key,'reason',e.target.value)} /></Field>
        <button type="button" className="quiet danger" onClick={() => setRows(list => list.filter(row => row.key !== r.key))}>Bỏ</button>
      </div>)}
      {!rows.length && <div className="empty">Chưa có khoảng thời gian bận.</div>}
      <div className="button-row"><button type="button" className="quiet" onClick={() => setRows(list => [...list, { key:crypto.randomUUID(),dayOfWeek:1,periodFrom:1,periodTo:2,kind:'BUSY',reason:'' }])}>Thêm khoảng</button>
        <button disabled={busy}>{busy ? 'Đang lưu…' : 'Lưu thời gian bận'}</button></div>
    </form>
  </Panel>
}
