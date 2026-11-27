import { useEffect, useRef, useState } from 'react'
import { api, checkScheduleConflicts } from '../../services/api'
import { formatDate } from '../../utils/dates'
import { builder, codes, numeric, values } from '../../utils/forms'
import { days, dayOptions } from '../../constants/domain'
import { Editor } from '../../components/forms/Editor'
import { ErrorBox } from '../../components/common/ErrorBox'
import { Field, Select } from '../../components/forms/FormFields'
import { same } from './timetableFilters'

function eligibleRooms(catalog, section) {
  return catalog.rooms.filter(room => section && room.status === 'ACTIVE' && room.type === section.required_room_type &&
    room.capacity >= section.capacity && catalog.campuses.some(c => c.id === room.campus_id))
}

function defaultRoom(catalog, section, selected) {
  if (selected) return selected.id
  const rooms = eligibleRooms(catalog, section)
  return rooms.length === 1 ? rooms[0].id : ''
}

function conflictCell(slot, verdict, sectionCode, roomCode) {
  const issue = verdict.issues?.[0]
  if (!issue) return null
  const otherSection = issue.sectionCode || issue.section_code
  return { day: slot.dayOfWeek, period: slot.periodFrom, sectionCode, roomCode,
    otherSection: otherSection !== sectionCode ? otherSection : null,
    message: issue.message || 'Trùng với lịch đã công bố.' }
}

export function PatternEditor({ edit, catalog, drafts, close, reload, onConflict }) {
  const { item, section, readOnly } = edit
  const [form, set] = useState(() => ({ sectionId: section?.id ?? '', roomId: item?.room_id ?? defaultRoom(catalog, section, edit.room), dayOfWeek: item?.day_of_week ?? edit.day,
    periodFrom: item?.period_from ?? edit.period, effectiveFrom: item?.effective_from?.slice(0, 10) ?? section?.start_date?.slice(0, 10),
    effectiveTo: item?.effective_to?.slice(0, 10) ?? section?.end_date?.slice(0, 10), version: item?.version ?? null }))
  const [assessment, setAssessment] = useState(null)
  const [revision, recheck] = useState(0)
  const request = useRef(0)
  const [remove, setRemove] = useState(false)
  const [observed, setObserved] = useState(item)
  const field = builder(set)
  const payload = () => numeric({ ...form, patternId: item?.id ?? null }, ['sectionId','roomId','dayOfWeek','periodFrom'])
  const current = item ? section : drafts.find(s => same(s.id, form.sectionId))
  const rooms = eligibleRooms(catalog, current)
  const chosenRoom = catalog.rooms.find(room => same(room.id, form.roomId))
  const roomOptions = codes(rooms)
  if (chosenRoom && !rooms.includes(chosenRoom)) roomOptions.unshift([chosenRoom.id, chosenRoom.code + ' · Không đủ điều kiện'])
  const previewKey = !readOnly && !remove && form.sectionId && form.roomId && form.effectiveFrom && form.effectiveTo
    ? JSON.stringify(payload()) : null
  const result = assessment?.key === previewKey && assessment?.revision === revision ? assessment.result : null
  const checking = !!previewKey && !result
  const sectionCode = current?.code
  const roomCode = chosenRoom?.code
  useEffect(() => {
    const generation = ++request.current
    setAssessment(null)
    onConflict(null)
    if (!previewKey) return
    const slot = JSON.parse(previewKey)
    const timer = setTimeout(async () => {
      try {
        const verdict = await checkScheduleConflicts(slot)
        if (request.current !== generation) return
        setAssessment({ key: previewKey, revision, result: verdict })
        onConflict(conflictCell(slot, verdict, sectionCode, roomCode))
      } catch (error) {
        if (request.current === generation) setAssessment({ key: previewKey, revision, result: error })
      }
    }, 200)
    return () => { clearTimeout(timer); request.current++ }
  }, [previewKey, revision, sectionCode, roomCode, onConflict])
  function suggestion(s) {
    set(previous => ({ ...previous, roomId: s.roomId, dayOfWeek: s.dayOfWeek, periodFrom: s.periodFrom }))
  }
  async function save() {
    if (readOnly) return
    if (remove) await api('/schedule-patterns/' + item.id + '?version=' + form.version, 'DELETE')
    else {
      const slot = payload()
      const generation = request.current
      try { await api('/schedule-patterns' + (item ? '/' + item.id : ''), item ? 'PATCH' : 'POST', slot) }
      catch (error) {
        if (error.code === 'SCHEDULE_CONFLICT' && request.current === generation) {
          request.current++
          setAssessment({ key: previewKey, revision, result: { reportedBySave: true } })
          onConflict(conflictCell(slot, error.details, sectionCode, roomCode))
        }
        throw error
      }
    }
    onConflict(null)
    reload()
  }
  return <Editor title={readOnly ? 'Chi tiết lịch định kỳ' : remove ? 'Gỡ lịch định kỳ' : item ? 'Sửa lịch định kỳ' : 'Xếp buổi học'} onClose={close} onSubmit={save}
    onSuggestion={suggestion} scheduleKey={previewKey} submit={readOnly ? 'Xong' : remove ? 'Gỡ lịch' : 'Lưu lịch'}
    latestFields={latest => [['Thứ',days[latest.day_of_week-1]],['Tiết bắt đầu',latest.period_from],
      ['Phòng',catalog.rooms.find(room=>room.id===latest.room_id)?.code],['Từ ngày',formatDate(latest.effective_from)],
      ['Đến ngày',formatDate(latest.effective_to)]]}
    onUseVersion={latest => { setObserved(latest); field('version')(latest.version) }}
    extra={!readOnly && !remove && <>
      <button type="button" className="quiet" disabled={checking} onClick={event => {
        if (event.currentTarget.form.reportValidity()) recheck(value => value + 1)
      }}>Kiểm tra xung đột</button>
      {item && <button type="button" className="quiet danger" onClick={() => setRemove(true)}>Gỡ lịch</button>}
    </>}>
    {readOnly ? <p><strong>{item.section_code} · {item.subject_name}</strong><br />{days[item.day_of_week - 1]}, tiết {item.period_from} · {item.room_code} · {item.staff_code}</p>
      : remove ? <p>Gỡ buổi <strong>{item.section_code}</strong> vào {days[observed.day_of_week - 1]}, tiết {observed.period_from}?</p> : <>
        {checking && <p role="status" className="notice">Đang kiểm tra xung đột…</p>}
        {result?.valid ? <p role="status" className="notice success">Khung đã chọn không có xung đột.</p>
          : !result?.reportedBySave && <ErrorBox error={result} onSuggestion={suggestion} />}
        {!form.roomId && <p className="notice">Chọn phòng để kiểm tra khung giờ đã đặt.</p>}
        <div className="form-grid">
          <Field label="Lớp học phần"><Select value={form.sectionId} set={value => {
            const target = drafts.find(s => same(s.id, value))
            set(previous => ({ ...previous, sectionId: value, roomId: defaultRoom(catalog, target, edit.room),
              effectiveFrom: target.start_date.slice(0,10), effectiveTo: target.end_date.slice(0,10) }))
          }} options={codes(item ? [section] : drafts)} disabled={!!item} required /></Field>
          <Field label="Phòng"><Select value={form.roomId} set={field('roomId')} options={roomOptions} placeholder="Chọn phòng" required /></Field>
          <Field label="Thứ"><Select value={form.dayOfWeek} set={field('dayOfWeek')} options={dayOptions} /></Field>
          <Field label={'Tiết bắt đầu · ' + (current?.periods_per_session || '—') + ' tiết'}><Select value={form.periodFrom} set={field('periodFrom')} options={values(1,15).map(n => [n,n])} /></Field>
          <Field label="Từ ngày"><input type="date" value={form.effectiveFrom} onChange={e => field('effectiveFrom')(e.target.value)} required /></Field>
          <Field label="Đến ngày"><input type="date" value={form.effectiveTo} onChange={e => field('effectiveTo')(e.target.value)} required /></Field>
        </div>
      </>}
  </Editor>
}
