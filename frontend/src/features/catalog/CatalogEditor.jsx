import { useState } from 'react'
import { api } from '../../services/api'
import { builder, codes } from '../../utils/forms'
import { Editor } from '../../components/forms/Editor'
import { Field, Options, Select } from '../../components/forms/FormFields'
import { vietnamToday } from '../../utils/dates'
import { roomTypes } from '../../constants/domain'
import { catalogPayload, roomStatuses, shifts } from './catalogFields'

export function CatalogEditor({ tab, row, catalog, close, reload }) {
  const [form, set] = useState({ campusId: row ? row.campus_id ?? '' : catalog.campuses[0]?.id ?? '',
    code:row?.code || '', name:row?.name || '', capacity:row?.capacity || 40, type:row?.type || 'LECTURE', status:row?.status || 'ACTIVE',
    periodNo:row?.period_no || 1, startTime:row?.start_time || '07:00', endTime:row?.end_time || '07:50', shift:row?.shift || 'MORNING',
    effectiveFrom:'', startDate:row?.start_date?.slice(0,10) || vietnamToday(), endDate:row?.end_date?.slice(0,10) || vietnamToday() })
  const field = builder(set)
  const isEdit = row && tab!=='periods'
  const input = (key,label,props={}) => <Field label={label}><input required value={form[key]} onChange={e => field(key)(e.target.value)} {...props} /></Field>
  const titles = { rooms:isEdit ? 'Sửa phòng học' : 'Thêm phòng học', periods:'Tạo khung tiết có ngày hiệu lực', holidays:isEdit ? 'Sửa ngày không học' : 'Thêm ngày không học' }
  return <Editor title={titles[tab]} onClose={close} onSubmit={async () => {
    const payload = catalogPayload(tab, form)
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
