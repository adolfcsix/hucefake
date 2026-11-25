import assert from 'node:assert/strict'
import test from 'node:test'
import { busy, holiday, interval, occurrences, pair, problems } from '../src/modules/scheduling.js'

const room = (campus_id, code) => ({ campus_id, code, status:'ACTIVE', capacity:50, type:'LECTURE' })
const section = (id, staff_id, assistant_ids, group_ids) => ({
  id, staff_id, assistant_ids, group_ids, code:'C'+id, semester_id:1,
  start_date:'2026-09-07',end_date:'2026-10-04',capacity:40,required_room_type:'LECTURE',
})
const period = (campus_id, period_no, start_time, end_time, shift='MORNING') => ({
  campus_id, period_no, start_time, end_time, shift, effective_from:'2026-09-01',effective_to:null,
})
const slot = (section, staff, room, first=1, opts={}) => ({
  id:section, section, staff, room, first, count:2, day:1, from:'2026-09-07',to:'2026-10-04',...opts,
})
function fixture() {
  return {
    rooms:{10:room(1,'A101'),11:room(1,'A102'),12:room(2,'B101')},
    sections:{1:section(1,1,[2],[9]),2:section(2,2,[],[8]),3:section(3,3,[],[9])},
    periods:[
      period(1,1,'07:00:00','07:50:00'),period(1,2,'07:55:00','08:45:00'),
      period(1,3,'08:50:00','09:40:00'),period(1,4,'09:45:00','10:35:00'),
      period(1,5,'13:00:00','13:50:00','AFTERNOON'),
      period(2,3,'07:00:00','07:50:00'),period(2,4,'07:55:00','08:45:00'),
    ],
    availability:[], holidays:[], patterns:[],
  }
}

test('trợ giảng tham gia kiểm tra trùng cả khi là giảng viên chính của lớp khác', () => {
  const ctx=fixture()
  assert.deepEqual(pair(ctx,slot(1,1,10),slot(2,2,11)).map(issue=>issue.rule),['BR05'])
  assert.deepEqual(pair(ctx,slot(1,1,10),slot(2,2,11,3)),[])
})

test('so giờ thực giữa hai cơ sở có cấu hình tiết khác nhau', () => {
  const ctx=fixture()
  assert.deepEqual(pair(ctx,slot(1,1,10),slot(2,2,12,3)).map(issue=>issue.rule),['BR05'])
})

test('xét nhóm đối tượng, ngày lặp và khoảng hiệu lực', () => {
  const ctx=fixture()
  ctx.sections[3].group_codes=['CNTT.01']
  const issues=pair(ctx,slot(1,1,10),slot(3,3,10))
  assert.deepEqual(issues.map(issue=>issue.rule),['BR04','BR06'])
  assert.deepEqual(issues[1].groupCodes,['CNTT.01'])
  assert.deepEqual(pair(ctx,slot(1,1,10,1,{to:'2026-09-08'}),
    slot(3,3,10,1,{from:'2026-09-09',to:'2026-09-20'})),[])
})

test('hai mẫu lịch chỉ trùng vào ngày nghỉ thì không chiếm cùng khung giảng', () => {
  const ctx=fixture()
  ctx.holidays.push({campus_id:1,start_date:'2026-09-07',end_date:'2026-09-07'})
  const a=slot(1,1,10,1,{to:'2026-09-13'})
  const b=slot(2,2,12,3,{to:'2026-09-13'})
  assert.deepEqual(pair(ctx,a,b),[])
  assert.deepEqual(pair(ctx,{...a,to:'2026-09-20'},{...b,to:'2026-09-20'}).map(issue=>issue.rule),['BR05'])
})

test('giờ BUSY của trợ giảng chặn lịch, còn AVOID chỉ được tính điểm mềm', () => {
  const ctx=fixture()
  const shift=slot(1,1,10)
  const availability={staff_id:2,semester_id:1,day_of_week:1,period_from:2,period_to:3}
  ctx.availability.push({...availability,kind:'AVOID'})
  assert.equal(busy(ctx,shift,'AVOID'),1)
  assert.deepEqual(problems(ctx,shift),[])
  ctx.availability.push({...availability,kind:'BUSY'})
  assert.deepEqual(problems(ctx,shift).map(issue=>issue.rule),['BR08'])
})

test('khung tiết qua ca bị từ chối, ngày nghỉ dùng đúng phạm vi cơ sở', () => {
  const ctx=fixture()
  assert.equal(interval(ctx,slot(1,1,10,4),'2026-09-07'),null)
  const dates=[...occurrences(slot(1,1,10))]
  assert.deepEqual(dates,['2026-09-07','2026-09-14','2026-09-21','2026-09-28'])
  ctx.holidays.push({campus_id:1,start_date:'2026-09-14',end_date:'2026-09-14'})
  assert.deepEqual(dates.filter(day=>!holiday(ctx,1,day)),['2026-09-07','2026-09-21','2026-09-28'])
  assert.equal(holiday(ctx,2,'2026-09-14'),false)
})
