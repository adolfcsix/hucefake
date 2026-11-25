import assert from 'node:assert/strict'
import test from 'node:test'
import { makeCalendar } from '../src/CalendarExport.js'

const lesson = {
  id:13, section_code:'CSDL.01', status:'PLANNED', room_code:'H1;301', staff_code:'GV01',
  subject_name:'Cơ sở dữ liệu, ' + 'Đồ án tiếng Việt '.repeat(12) + '\nBEGIN:VEVENT',
  start_at:'2026-11-26T07:00:00', end_at:'2026-11-26T08:45:00'
}
const now = Date.UTC(2026,10,24)

test('lịch dùng giờ Việt Nam, định danh ổn định và ngắt dòng theo số byte UTF-8', () => {
  const calendar=makeCalendar([lesson],now)
  const lines=calendar.split('\r\n')
  assert.ok(lines.every(line=>Buffer.byteLength(line)<=75))
  assert.equal(lines.filter(line=>line==='BEGIN:VEVENT').length,1)
  assert.ok(lines.includes('UID:fm05-session-13@campus.local'))
  assert.ok(lines.includes('DTSTART:20261126T000000Z'))
  assert.ok(lines.includes('DTEND:20261126T014500Z'))
  assert.ok(lines.includes('LOCATION:H1\\;301'))
  const unfolded=calendar.replace(/\r\n /g,'')
  assert.ok(unfolded.includes('SUMMARY:Cơ sở dữ liệu\\, ' + 'Đồ án tiếng Việt '.repeat(12) + '\\nBEGIN:VEVENT\r\n'))
})

test('bỏ buổi đã hủy và thời gian không hợp lệ, giữ buổi thay thế', () => {
  const cancelled={...lesson,status:'CANCELLED'}
  const invalid={...lesson,id:17,end_at:lesson.start_at}
  const replacement={...lesson,id:18,source_session_id:13}
  assert.equal(makeCalendar([cancelled,invalid],now),null)
  const calendar=makeCalendar([cancelled,invalid,replacement],now)
  assert.ok(calendar.includes('UID:fm05-session-18@campus.local'))
  assert.ok(calendar.includes('Buổi thay thế'))
  assert.equal(calendar.split('\r\n').filter(line=>line==='BEGIN:VEVENT').length,1)
})
