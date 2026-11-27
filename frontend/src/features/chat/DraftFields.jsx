import { useState } from 'react'
import { Field, Select } from '../../components/forms/FormFields'
import { roomTypes } from '../../constants/domain'

export function DraftFields({ draft, busy, onSubmit }) {
  const [form, set] = useState({subjectId:'',code:'',capacity:'40',sessionsPerWeek:'1',periodsPerSession:'2',
    requiredRoomType:'LECTURE',startDate:draft.from,endDate:draft.to})
  const update = (key,value) => set(previous=>({...previous,[key]:value}))
  const numbers=[['capacity','Sĩ số',10000],['sessionsPerWeek','Buổi / tuần',6],['periodsPerSession','Tiết / buổi',6]]
  if (!draft.subjects.length) return <p>Khoa của bạn chưa có học phần đang hoạt động.</p>
  return <form className="chat-draft" onSubmit={event=>{
    event.preventDefault()
    onSubmit({...form,subjectId:Number(form.subjectId),
      ...Object.fromEntries(numbers.map(([key])=>[key,Number(form[key])]))},draft.semesterId)
  }}>
    <Field label="Học phần"><Select value={form.subjectId} set={value=>update('subjectId',value)} required
      options={draft.subjects.map(row=>[row.id,row.code+' · '+row.name])} placeholder="Chọn học phần" disabled={busy} /></Field>
    <Field label="Mã lớp"><input value={form.code} onChange={event=>update('code',event.target.value)} maxLength={40} required disabled={busy}/></Field>
    {numbers.map(([key,title,max])=><Field key={key} label={title}><input type="number" min="1" max={max} required disabled={busy}
      value={form[key]} onChange={event=>update(key,event.target.value)} /></Field>)}
    <Field label="Loại phòng"><Select value={form.requiredRoomType} set={value=>update('requiredRoomType',value)} options={Object.entries(roomTypes)} disabled={busy}/></Field>
    {[['startDate','Từ ngày'],['endDate','Đến ngày']].map(([key,title])=><Field key={key} label={title}>
      <input type="date" value={form[key]} min={draft.from} max={draft.to} onChange={event=>update(key,event.target.value)} required disabled={busy}/>
    </Field>)}
    <button type="submit" disabled={busy}>Xem trước lớp nháp</button>
  </form>
}
