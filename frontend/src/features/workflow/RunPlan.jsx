import { useState } from 'react'
import { days } from '../../constants/domain'
import { Field } from '../../components/forms/FormFields'
import { Panel } from '../../components/common/Panel'
import { Table } from '../../components/common/Table'
import { WeekGrid } from '../timetable/WeekGrid'

export function RunPlan({ rows }) {
  const [search,setSearch] = useState('')
  const [onlyIssues,setOnlyIssues] = useState(false)
  const [layout,setLayout] = useState(() => window.matchMedia('(max-width: 760px)').matches ? 'list' : 'grid')
  const query=search.trim().toLocaleLowerCase('vi')
  const matches=rows.filter(row => (!onlyIssues || row.violation_note) &&
    [row.section_code,row.subject_name,row.room_code].some(value => value?.toLocaleLowerCase('vi').includes(query)))
  const maxPeriod=rows.reduce((last,row) => Math.max(last,row.period_from + row.period_count - 1),12)
  return <Panel title="Phương án đề xuất" action={<div className="calendar-modes" role="group" aria-label="Cách xem phương án">
    <button type="button" aria-pressed={layout === 'grid'} onClick={() => setLayout('grid')}>Lưới theo tiết</button>
    <button type="button" aria-pressed={layout === 'list'} onClick={() => setLayout('list')}>Danh sách</button>
  </div>}>
    <div className="toolbar run-plan-tools">
      <Field label="Tìm lớp hoặc phòng"><input type="search" value={search} onChange={e => setSearch(e.target.value)} /></Field>
      {rows.some(row => row.violation_note) && <label className="inline-choice">
        <input type="checkbox" checked={onlyIssues} onChange={e => setOnlyIssues(e.target.checked)} /> Chỉ hiện buổi có vi phạm
      </label>}
      <small role="status">{matches.length}/{rows.length} buổi</small>
    </div>
    {layout === 'grid' && matches.length > 0 ? <WeekGrid rows={matches} maxPeriod={maxPeriod} /> :
    <Table rows={matches} empty="Không tìm thấy buổi phù hợp." columns={[
      ['Lớp học phần','section_code'],['Thứ',r => days[r.day_of_week - 1]],['Tiết','period_from'],
      ['Phòng','room_code'],['Vi phạm','violation_note']
    ]} />}
  </Panel>
}
