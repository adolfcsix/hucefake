import { useState } from 'react'
import { Pagination } from '../../components/common/Pagination'
import { usePagedQuery } from '../../hooks/usePagedQuery'
import { statuses } from '../../constants/domain'
import { ErrorBox } from '../../components/common/ErrorBox'
import { Panel } from '../../components/common/Panel'
import { Table } from '../../components/common/Table'
import { Tag } from '../../components/common/Tag'
import { SessionEditor } from './SessionEditor'

export function ChangeRequests({ user, catalog, semester, revision, reload }) {
  const [edit, setEdit] = useState(null)
  const data = usePagedQuery('/session-change-requests?semesterId=' + semester.id,revision)
  return <Panel title="Đề nghị nghỉ hoặc học bù">
    <ErrorBox error={data.error} />
    <Table columns={[
      ['Lớp học phần','section_code'], ['Buổi học','session_time'], ['Đề nghị',r => statuses[r.action]],
      ['Lý do','reason'], ['Trạng thái',r => <><Tag value={r.status} />{r.decision_reason && <small>{r.decision_reason}</small>}</>],
      ...(user.role === 'STAFF' ? [['Thao tác',r => r.status === 'PENDING' && <div className="button-row">
        <button className="quiet" onClick={() => setEdit({ row:r })}>Xử lý</button>
        <button className="quiet" onClick={() => setEdit({ row:r, returning:true })}>Trả lại</button>
      </div>]] : [])
    ]} rows={data.data?.items || []} />
    <Pagination query={data} itemName="đề nghị" />
    {edit && <SessionEditor {...edit} request user={user} catalog={catalog} semester={semester} close={() => setEdit(null)} reload={reload} />}
  </Panel>
}
