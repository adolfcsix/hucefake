import { all, first } from '../db/pool.js'
import { conflict, forbidden, need, state } from '../middleware/errors.js'

export const sectionSql = `
  SELECT c.*,s.managing_unit_id,s.name AS subject_name,s.code AS subject_code,a.staff_id,sp.code AS staff_code,
    (SELECT count(*) FROM section_schedule_patterns p WHERE p.section_id=c.id) AS scheduled,
    ARRAY(SELECT g.group_id FROM section_target_groups g WHERE g.section_id=c.id ORDER BY g.group_id) AS group_ids,
    ARRAY(SELECT ag.code FROM section_target_groups g JOIN academic_groups ag ON ag.id=g.group_id
      WHERE g.section_id=c.id ORDER BY g.group_id) AS group_codes,
    ARRAY(SELECT aa.staff_id FROM section_assignments aa WHERE aa.section_id=c.id
      AND aa.role='ASSISTANT' AND aa.valid_to IS NULL ORDER BY aa.staff_id) AS assistant_ids
  FROM course_sections c JOIN subjects s ON s.id=c.subject_id
  LEFT JOIN section_assignments a ON a.section_id=c.id AND a.role='MAIN' AND a.valid_to IS NULL
  LEFT JOIN staff_profiles sp ON sp.id=a.staff_id`
export const patternSql = `SELECT p.*,c.code AS section_code,s.name AS subject_name,t.code AS room_code,sp.code AS staff_code,
  c.status AS section_status,c.subject_id,s.managing_unit_id,
  ARRAY(SELECT g.group_id FROM section_target_groups g WHERE g.section_id=c.id ORDER BY g.group_id) AS group_ids
  FROM section_schedule_patterns p JOIN course_sections c ON c.id=p.section_id
  JOIN subjects s ON s.id=c.subject_id JOIN teaching_rooms t ON t.id=p.room_id JOIN staff_profiles sp ON sp.id=p.staff_id`
export const semesterLocked = async (db, id) => {
  if (await first(db,"SELECT 1 FROM timetable_versions WHERE semester_id=$1 AND status='SUBMITTED'",[id]))
    throw state('Học kỳ đang chờ duyệt. Hãy xử lý hồ sơ trước khi chỉnh lịch.')
}
export async function campuses(db, actor) {
  const data = actor.role === 'STAFF' ?
    await all(db, 'SELECT campus_id AS id FROM staff_campuses JOIN campuses c ON c.id=campus_id WHERE staff_id=$1 AND c.status=\'ACTIVE\'', [actor.staffId]) :
    await all(db, "SELECT id FROM campuses WHERE status='ACTIVE'")
  return new Set(data.map(r => r.id))
}
export async function requireCampus(db,actor,campus) {
  if (!(await campuses(db,actor)).has(campus)) throw forbidden()
}
export const today = () => new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Ho_Chi_Minh'}).format(new Date())
export const weekday = value => new Date(value + 'T00:00:00Z').getUTCDay() || 7
export function addDays(value, days) {
  const day = new Date(value + 'T00:00:00Z')
  day.setUTCDate(day.getUTCDate()+days)
  return day.toISOString().slice(0,10)
}
export const firstDay = (value, day) => addDays(value,(day-weekday(value)+7)%7)
export function* occurrences(slot) {
  for (let day=firstDay(slot.from,slot.day); day<=slot.to; day=addDays(day,7)) yield day
}
export const asSlot = row => ({ id:row.id, section:row.section_id, staff:row.staff_id, room:row.room_id,
  day:row.day_of_week, first:row.period_from, count:row.period_count, from:row.effective_from, to:row.effective_to, version:row.version })
export const makeSlot = (section, room, day, period, from=section.start_date, to=section.end_date, prior=null) =>
  ({ id:prior?.id || 0, section:section.id, staff:section.staff_id, room, day, first:period, count:section.periods_per_session, from,to, version:prior?.version || 0 })
const last = slot => slot.first+slot.count-1
const intersects = (a,b) => a.some(id => b.includes(id))
export const fits = (room, section) => room?.status === 'ACTIVE' && room.capacity >= section.capacity && room.type === section.required_room_type

export async function context(db) {
  const sections=await all(db,sectionSql+" WHERE c.status IN ('OPEN','DRAFT') ORDER BY c.id")
  const rooms=await all(db,`SELECT r.id,r.campus_id,r.code,r.capacity,r.type,
    CASE WHEN c.status='ACTIVE' THEN r.status ELSE 'INACTIVE' END AS status FROM teaching_rooms r JOIN campuses c ON c.id=r.campus_id ORDER BY r.id`)
  return {
    sections:Object.fromEntries(sections.map(r => [r.id,r])), rooms:Object.fromEntries(rooms.map(r => [r.id,r])),
    periods:await all(db,"SELECT * FROM period_definitions WHERE status='ACTIVE' ORDER BY effective_from DESC"),
    availability:await all(db,'SELECT * FROM staff_availability ORDER BY id'),
    holidays:await all(db,'SELECT * FROM non_teaching_days'),
    // Published schedules reserve resources through class_sessions, including exceptions.
    patterns:(await all(db,"SELECT p.* FROM section_schedule_patterns p JOIN course_sections c ON c.id=p.section_id WHERE c.status='DRAFT' ORDER BY p.id")).map(asSlot),
  }
}

export function interval(ctx, slot, date) {
  const room=ctx.rooms[slot.room]
  if (!room || slot.first<1 || slot.count<1 || slot.count>6 || last(slot)>15) return null
  let firstTime, previous
  for (let period=slot.first; period<=last(slot); period++) {
    const p=ctx.periods.find(row => row.campus_id===room.campus_id && row.period_no===period &&
      row.effective_from<=date && (!row.effective_to || row.effective_to>=date))
    if (!p || previous && (previous.shift!==p.shift || previous.end_time>p.start_time)) return null
    firstTime ??= p.start_time
    previous=p
  }
  return { start:date+' '+firstTime, end:date+' '+previous.end_time,shift:previous.shift }
}

const staffBySlot = new WeakMap()
export function slotStaff(ctx,slot) {
  let members=staffBySlot.get(slot)
  if (!members) {
    members=[slot.staff,...(ctx.sections[slot.section]?.assistant_ids || [])]
    staffBySlot.set(slot,members)
  }
  return members
}
const startsBySlot = new WeakMap()
function startOf(slot) {
  let start=startsBySlot.get(slot)
  if (!start) {
    start=firstDay(slot.from,slot.day)
    startsBySlot.set(slot,start)
  }
  return start
}
export const sessionStaffSql = `ARRAY[x.staff_id] || ARRAY(SELECT a.staff_id FROM section_assignments a
  WHERE a.section_id=x.section_id AND a.role='ASSISTANT' AND a.valid_to IS NULL)`

export function busy(ctx,slot,kind) {
  const semester=ctx.sections[slot.section]?.semester_id
  const staff=slotStaff(ctx,slot)
  let count=0
  for (let period=slot.first;period<=last(slot);period++) if (ctx.availability.some(a => staff.includes(a.staff_id) &&
      a.semester_id===semester && a.day_of_week===slot.day && a.kind===kind && a.period_from<=period && a.period_to>=period)) count++
  return count
}

export function pair(ctx, a, b) {
  if (a.day!==b.day) return []
  const from=a.from>b.from ? a.from : b.from
  const to=a.to<b.to ? a.to : b.to
  if (startOf(a)>to || startOf(b)>to) return []
  const other=ctx.sections[b.section]
  const issues=[]
  const sameSection=a.section===b.section
  const sameRoom=a.room===b.room, sameStaff=intersects(slotStaff(ctx,a),slotStaff(ctx,b))
  const groups=ctx.sections[a.section].group_ids
  const sameGroup=intersects(groups,other.group_ids)
  if (!sameSection && !sameRoom && !sameStaff && !sameGroup) return issues
  const aCampus=ctx.rooms[a.room]?.campus_id, bCampus=ctx.rooms[b.room]?.campus_id
  if (ctx.holidays?.length) {
    let day=firstDay(from,a.day)
    while (day<=to && (holiday(ctx,aCampus,day) || holiday(ctx,bCampus,day))) day=addDays(day,7)
    if (day>to) return issues
  }
  const issue=(rule,message) => ({rule,message,patternId:b.id,sectionCode:other.code,
    roomCode:ctx.rooms[b.room]?.code,dayOfWeek:b.day,periodFrom:b.first,periodCount:b.count})
  if (sameSection) issues.push(issue('BR09','Lớp đã có buổi khác trong ngày.'))
  if (!sameRoom && !sameStaff && !sameGroup) return issues
  let overlap=a.first<=last(b) && b.first<=last(a)
  if (aCampus!==bCampus) {
    overlap=false
    for (let day=firstDay(from,a.day);day<=to;day=addDays(day,7)) {
      if (ctx.holidays?.length && (holiday(ctx,aCampus,day) || holiday(ctx,bCampus,day))) continue
      const x=interval(ctx,a,day), y=interval(ctx,b,day)
      if (x && y && x.start<y.end && y.start<x.end) { overlap=true; break }
    }
  }
  if (overlap) {
    if (sameRoom) issues.push(issue('BR04','Phòng đã được xếp cho lớp khác.'))
    if (sameStaff) issues.push(issue('BR05','Giảng viên đã có lịch dạy.'))
    if (sameGroup) issues.push({...issue('BR06','Lớp hành chính đối tượng đã có lịch.'),
      groupCodes:other.group_codes?.filter((_,index)=>groups.includes(other.group_ids[index])) || []})
  }
  return issues
}

export function problems(ctx,slot,against=ctx.patterns) {
  const s=ctx.sections[slot.section]
  const issue=(rule,message) => ({rule,message,sectionCode:s?.code})
  if (!s || !slot.staff || slot.staff!==s.staff_id) return [issue('BR02','Lớp chưa có giảng viên chính hợp lệ.')]
  if (!fits(ctx.rooms[slot.room],s)) return [issue('BR07','Phòng phải hoạt động, đúng loại và đủ sức chứa.')]
  if (slot.from<s.start_date || slot.to>s.end_date || slot.from>slot.to ||
      firstDay(slot.from,slot.day)>slot.to || [...occurrences(slot)].some(d => !interval(ctx,slot,d)))
    return [issue('BR03','Tiết học phải nằm trọn trong một ca và trong khoảng ngày của lớp.')]
  const out=busy(ctx,slot,'BUSY') ? [issue('BR08','Giảng viên đã khai báo bận.')] : []
  for (const other of against) if (!slot.id || slot.id!==other.id) out.push(...pair(ctx,slot,other))
  return out
}
export const holiday = (ctx, campus, date) =>
  ctx.holidays.some(r => (!r.campus_id || r.campus_id===campus) && r.start_date<=date && r.end_date>=date)

export function actualConflicts(db,ctx,slot,time,exclude=0) {
  const sharedStaff = `(${sessionStaffSql}) && $2::bigint[]`
  return all(db,`SELECT x.id,c.code AS section_code,r.code AS "roomCode",x.period_from AS "periodFrom",
    x.period_count AS "periodCount",to_char(x.start_at,'YYYY-MM-DD"T"HH24:MI:SS') AS start_at,
    ARRAY(SELECT g.code FROM section_target_groups a JOIN section_target_groups b USING(group_id)
      JOIN academic_groups g ON g.id=a.group_id WHERE a.section_id=x.section_id AND b.section_id=$6 ORDER BY g.code) AS "groupCodes",
    CASE WHEN x.room_id=$1 THEN 'BR04' WHEN ${sharedStaff} THEN 'BR05' ELSE 'BR06' END AS rule
    FROM class_sessions x JOIN course_sections c ON c.id=x.section_id
    LEFT JOIN teaching_rooms r ON r.id=x.room_id
    WHERE x.id<>$3 AND x.status<>'CANCELLED' AND x.start_at<$4 AND x.end_at>$5
      AND (x.room_id=$1 OR ${sharedStaff} OR x.section_id=$6 OR EXISTS (
       SELECT 1 FROM section_target_groups a JOIN section_target_groups b USING(group_id)
       WHERE a.section_id=x.section_id AND b.section_id=$6)) ORDER BY x.id`,
    [slot.room,slotStaff(ctx,slot),exclude,time.end,time.start,slot.section])
}
export async function actualPattern(db,ctx,slot) {
  const campus=ctx.rooms[slot.room].campus_id
  let teaching=false
  for (const date of occurrences(slot)) {
    if (holiday(ctx,campus,date)) continue
    teaching=true
    const issues=await actualConflicts(db,ctx,slot,interval(ctx,slot,date))
    if (issues.length) return issues
  }
  if (!teaching) return [{rule:'BR14',message:'Lịch định kỳ không có ngày học vì toàn bộ ngày đều nghỉ.'}]
  return []
}
export async function checkSlot(db,ctx,slot,actor,suggest=false) {
  let issues=problems(ctx,slot)
  if (!issues.length) issues=await actualPattern(db,ctx,slot)
  const suggestions=[]
  if (suggest && issues.length && slot.staff) {
    const allowed=await campuses(db,actor)
    for (const room of Object.values(ctx.rooms)) {
      if (!allowed.has(room.campus_id) || !fits(room,ctx.sections[slot.section])) continue
      for (let day=1;day<=7 && suggestions.length<5;day++) for (let period=1;period<=15 && suggestions.length<5;period++) {
        const next={...slot,room:room.id,day,first:period}
        if (!problems(ctx,next).length && !(await actualPattern(db,ctx,next)).length)
          suggestions.push({roomId:room.id,roomCode:room.code,dayOfWeek:day,periodFrom:period})
      }
      if (suggestions.length>=5) break
    }
  }
  return {valid:issues.length===0,issues,suggestions}
}
export async function verify(db,ctx,slot,actor) {
  const result=await checkSlot(db,ctx,slot,actor)
  if (!result.valid) throw conflict(result)
}
export const insertPattern = (db,slot,source,run,actor) => first(db,`INSERT INTO section_schedule_patterns
  (section_id,staff_id,room_id,day_of_week,period_from,period_count,effective_from,effective_to,source,run_id,created_by)
  VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11) RETURNING id`,
  [slot.section,slot.staff,slot.room,slot.day,slot.first,slot.count,slot.from,slot.to,source,run,actor.id])
export const insertSession = (db,slot,time,version,pattern,source) => first(db,`INSERT INTO class_sessions
  (section_id,staff_id,room_id,period_from,period_count,start_at,end_at,timetable_version_id,pattern_id,source_session_id)
  VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10) RETURNING id`,
  [slot.section,slot.staff,slot.room,slot.first,slot.count,time.start,time.end,version,pattern,source])

export function notifySection(db,section,message) {
  return db.query(`INSERT INTO notifications(user_id,message) SELECT recipient,$2 FROM (
    SELECT p.user_id AS recipient FROM section_assignments a JOIN staff_profiles p ON p.id=a.staff_id WHERE a.section_id=$1 AND a.valid_to IS NULL
    UNION SELECT student_user_id FROM section_enrollments WHERE section_id=$1 AND status='ENROLLED'
  ) audience`,[section,message])
}
export const findSection = async (db,id) => need(await first(db,sectionSql+' WHERE c.id=$1',[id]))
