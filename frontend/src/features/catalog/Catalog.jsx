import { useState } from 'react'
import { formatDate } from '../../utils/dates'
import { Panel } from '../../components/common/Panel'
import { roomTypes } from '../../constants/domain'
import { Table } from '../../components/common/Table'
import { Tag } from '../../components/common/Tag'
import { shifts } from './catalogFields'
import { CatalogEditor } from './CatalogEditor'

export default function Catalog({ catalog, reload }) {
  const [tab, setTab] = useState('rooms')
  const [dialog, setDialog] = useState(null)
  const campuses = Object.fromEntries(catalog.campuses.map(r => [r.id,r.code]))
  const labels = { rooms:'Phòng học', periods:'Khung tiết', holidays:'Ngày không học' }
  const rows = catalog[tab].filter(r => r.campus_id === null || campuses[r.campus_id])
  const columns = {
    rooms:[['Mã phòng','code'],['Tên phòng','name'],['Sức chứa','capacity'],['Loại phòng',r => roomTypes[r.type]],['Trạng thái',r => <Tag value={r.status} />]],
    periods:[['Tiết','period_no'],['Ca',r => shifts[r.shift]],['Bắt đầu','start_time'],['Kết thúc','end_time'],['Từ ngày',r => formatDate(r.effective_from)],['Đến ngày',r => formatDate(r.effective_to)]],
    holidays:[['Tên','name'],['Bắt đầu',r => formatDate(r.start_date)],['Kết thúc',r => formatDate(r.end_date)]]
  }
  return <>
    <Panel title={labels[tab]} action={<button onClick={() => setDialog({})}>{tab==='periods' ? 'Thêm khung hiệu lực' : 'Thêm ' + labels[tab].toLowerCase()}</button>}>
      <div className="segmented">{Object.entries(labels).map(([key,label]) => <button key={key} type="button" className={tab===key ? 'selected' : 'quiet'} onClick={() => setTab(key)}>{label}</button>)}</div>
      <Table rows={rows} columns={[['Cơ sở',r => campuses[r.campus_id] || 'Toàn trường'], ...columns[tab], ['Thao tác',r =>
        <button className="quiet" onClick={() => setDialog({ row:r })}>{tab==='periods' ? 'Tạo khung kế tiếp' : 'Sửa'}</button>]]} />
    </Panel>
    {dialog && <CatalogEditor tab={tab} row={dialog.row} catalog={catalog} close={() => setDialog(null)} reload={reload} />}
  </>
}
