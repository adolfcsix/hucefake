import { useState } from 'react'
import { api } from '../../services/api'
import { usePagedQuery } from '../../hooks/usePagedQuery'
import { Pagination } from '../../components/common/Pagination'
import { ErrorBox } from '../../components/common/ErrorBox'
import { Panel } from '../../components/common/Panel'
import { Table } from '../../components/common/Table'
import { formatDate } from '../../utils/dates'

export default function Notifications({ revision, reload }) {
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
