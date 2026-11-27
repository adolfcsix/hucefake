import { useState } from 'react'
import { api } from '../../services/api'
import { builder, codes, numeric } from '../../utils/forms'
import { Editor } from '../../components/forms/Editor'
import { Field, Options, Select } from '../../components/forms/FormFields'
import { formatDate, vietnamToday } from '../../utils/dates'
import { Panel } from '../../components/common/Panel'
import { roomTypes } from '../../constants/domain'
import { Table } from '../../components/common/Table'
import { Tag } from '../../components/common/Tag'

const roomStatuses = { ACTIVE:'Hoạt động', MAINTENANCE:'Bảo trì', INACTIVE:'Ngừng sử dụng' }
const shifts = { MORNING:'Ca sáng', AFTERNOON:'Ca chiều', EVENING:'Ca tối' }

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
function CatalogEditor({ tab, row, catalog, close, reload }) {
  const [form, set] = useState({ campusId: row ? row.campus_id ?? '' : catalog.campuses[0]?.id ?? '',
    code:row?.code || '', name:row?.name || '', capacity:row?.capacity || 40, type:row?.type || 'LECTURE', status:row?.status || 'ACTIVE',
    periodNo:row?.period_no || 1, startTime:row?.start_time || '07:00', endTime:row?.end_time || '07:50', shift:row?.shift || 'MORNING',
    effectiveFrom:'', startDate:row?.start_date?.slice(0,10) || vietnamToday(), endDate:row?.end_date?.slice(0,10) || vietnamToday() })
  const field = builder(set)
  const isEdit = row && tab!=='periods'
  const input = (key,label,props={}) => <Field label={label}><input required value={form[key]} onChange={e => field(key)(e.target.value)} {...props} /></Field>
  const titles = { rooms:isEdit ? 'Sửa phòng học' : 'Thêm phòng học', periods:'Tạo khung tiết có ngày hiệu lực', holidays:isEdit ? 'Sửa ngày không học' : 'Thêm ngày không học' }
  return <Editor title={titles[tab]} onClose={close} onSubmit={async () => {
    const keys = {
      rooms:['campusId','code','name','capacity','type','status'], periods:['campusId','periodNo','startTime','endTime','shift','effectiveFrom'],
      holidays:['campusId','name','startDate','endDate']
    }
    const data = numeric(form,['campusId','capacity','periodNo'])
    const payload = Object.fromEntries(keys[tab].map(k => [k,data[k]]))
    await api('/catalog/' + tab + (isEdit ? '/' + row.id : ''),isEdit ? 'PUT' : 'POST',payload)
    reload()
  }}>
    <div className="form-grid">
      <Field label="Cơ sở"><Select value={form.campusId} set={field('campusId')} options={codes(catalog.campuses)}
        placeholder={tab==='holidays' ? 'Toàn trường' : 'Chọn cơ sở'} required={tab!=='holidays'} disabled={!!row && tab!=='holidays'} /></Field>
      {tab==='rooms' && <>
        {input('code','Mã phòng',{ maxLength:30 })}{input('name','Tên phòng',{ maxLength:100 })}
        {input('capacity','Sức chứa',{ type:'number',min:1 })}
        <Field label="Loại phòng"><Options value={form.type} set={field('type')} values={roomTypes} /></Field>
        <Field label="Trạng thái"><Options value={form.status} set={field('status')} values={roomStatuses} /></Field>
      </>}
      {tab==='periods' && <>
        {input('periodNo','Số tiết',{ type:'number',min:1,max:15,disabled:!!row })}
        {input('startTime','Giờ bắt đầu',{ type:'time' })}{input('endTime','Giờ kết thúc',{ type:'time' })}
        <Field label="Ca"><Options value={form.shift} set={field('shift')} values={shifts} /></Field>
        {input('effectiveFrom','Ngày hiệu lực',{ type:'date' })}
      </>}
      {tab==='holidays' && <>{input('name','Tên ngày không học',{ maxLength:150 })}
        {input('startDate','Từ ngày',{ type:'date' })}{input('endDate','Đến ngày',{ type:'date' })}</>}
    </div>
  </Editor>
}
