import { all, first, audit, pool } from '../db/pool.js'
import { route, write } from '../middleware/route.js'
import { choice, conflict, date, forbidden, integer, invalid, need, text } from '../middleware/errors.js'
import { campuses, context, occurrences, requireCampus, today, verify } from './scheduling.js'
import { internal, roomTypes } from './sections.js'

const staff=['STAFF']
async function room(req,db) {
  const f=req.body, campus=integer(f.campusId)
  await requireCampus(db,req.actor,campus)
  const values=[campus,text(f.code,30).toUpperCase(),text(f.name,100),integer(f.capacity,1,10000),
    choice(f.type,...roomTypes),choice(f.status,'ACTIVE','MAINTENANCE','INACTIVE')]
  let result
  if (!req.recordId) result=await first(db,'INSERT INTO teaching_rooms(campus_id,code,name,capacity,type,status) VALUES ($1,$2,$3,$4,$5,$6) RETURNING id',values)
  else {
    const current=need(await first(db,'SELECT * FROM teaching_rooms WHERE id=$1',[req.recordId]))
    if (current.campus_id!==campus) throw invalid('Không thể chuyển cơ sở của phòng đã lưu.')
    const affected=await all(db,`SELECT x.id,c.code AS section_code,to_char(x.start_at,'YYYY-MM-DD HH24:MI') AS start_at
      FROM class_sessions x JOIN course_sections c ON c.id=x.section_id WHERE x.room_id=$1 AND x.status='PLANNED' AND x.start_at>=CURRENT_DATE
       AND ($2<>'ACTIVE' OR c.capacity>$3 OR c.required_room_type<>$4)`,[current.id,values[5],values[3],values[4]])
    const patterns=await all(db,`SELECT p.id,c.code AS section_code FROM section_schedule_patterns p JOIN course_sections c ON c.id=p.section_id
      WHERE p.room_id=$1 AND c.status='DRAFT' AND ($2<>'ACTIVE' OR c.capacity>$3 OR c.required_room_type<>$4)`,[current.id,values[5],values[3],values[4]])
    if (affected.length || patterns.length) throw conflict({issues:affected,patterns})
    result=await first(db,'UPDATE teaching_rooms SET code=$1,name=$2,capacity=$3,type=$4,status=$5 WHERE id=$6 RETURNING id',[...values.slice(1),current.id])
  }
  await audit(db,req.actor,'SAVE','teaching_rooms',result.id)
  return result
}
async function holidayScope(db,actor,campus) {
  if (campus!==null) return requireCampus(db,actor,campus)
  if ((await campuses(db,actor)).size !== (await first(db,"SELECT count(*) AS count FROM campuses WHERE status='ACTIVE'")).count) throw forbidden()
}
async function holiday(req,db) {
  const f=req.body
  const campus=f.campusId==null ? null : integer(f.campusId)
  await holidayScope(db,req.actor,campus)
  const from=date(f.startDate), to=date(f.endDate), name=text(f.name,150)
  if (from>to) throw invalid('Khoảng ngày không hợp lệ.')
  const issues=await all(db,`SELECT x.id,c.code AS section_code,to_char(x.start_at,'YYYY-MM-DD HH24:MI') AS start_at FROM class_sessions x
    JOIN course_sections c ON c.id=x.section_id LEFT JOIN teaching_rooms r ON r.id=x.room_id
    WHERE ($1::bigint IS NULL OR r.campus_id=$1) AND x.status='PLANNED' AND x.start_at>=$2::date AND x.start_at<$3::date+1`,[campus,from,to])
  if (issues.length) throw conflict({issues})
  const values=[campus,from,to,name]
  let result
  if (req.recordId) {
    const current=need(await first(db,'SELECT * FROM non_teaching_days WHERE id=$1',[req.recordId]))
    await holidayScope(db,req.actor,current.campus_id)
    result=await first(db,'UPDATE non_teaching_days SET campus_id=$1,start_date=$2,end_date=$3,name=$4 WHERE id=$5 RETURNING id',[...values,current.id])
  } else result=await first(db,'INSERT INTO non_teaching_days(campus_id,start_date,end_date,name,created_by) VALUES ($1,$2,$3,$4,$5) RETURNING id',[...values,req.actor.id])
  await audit(db,req.actor,'SAVE','non_teaching_days',result.id)
  return result
}
const time = x => {
  if (typeof x!=='string' || !/^([01]\d|2[0-3]):[0-5]\d(:[0-5]\d)?$/.test(x)) throw invalid('Giờ không hợp lệ.')
  return x.length===5 ? x+':00' : x
}
export function catalogRoutes(api) {
  api.get('/catalog',route([],async req => {
    const semesters=await all(pool,'SELECT * FROM semesters ORDER BY start_date DESC,id DESC')
    if (!internal.includes(req.actor.role)) return {semesters}
    const allowed=await campuses(pool,req.actor)
    return {semesters,
      campuses:(await all(pool,'SELECT * FROM campuses ORDER BY code')).filter(c=>allowed.has(c.id)),
      rooms:await all(pool,'SELECT * FROM teaching_rooms ORDER BY code'),
      lecturers:await all(pool,`SELECT s.id,s.code,u.username FROM staff_profiles s JOIN users u ON u.id=s.user_id JOIN roles r ON r.role_id=u.role_id
        WHERE s.status='ACTIVE' AND u.status='ACTIVE' AND r.code='LECTURER' ORDER BY s.code`),
      groups:await all(pool,'SELECT id,code,capacity FROM academic_groups ORDER BY code'),
      subjects:await all(pool,"SELECT id,code,name FROM subjects WHERE status='ACTIVE' AND ($1 OR managing_unit_id=$2) ORDER BY code",[req.actor.role==='MANAGER',req.actor.unitId]),
      periods:await all(pool,`SELECT id,campus_id,period_no,shift,effective_from,effective_to,to_char(start_time,'HH24:MI') AS start_time,
        to_char(end_time,'HH24:MI') AS end_time FROM period_definitions ORDER BY campus_id,period_no,effective_from DESC`),
      holidays:await all(pool,'SELECT * FROM non_teaching_days ORDER BY start_date DESC'),
    }
  }))
  api.post('/catalog/rooms',write(staff,room,201))
  api.put('/catalog/rooms/:id',write(staff,room))
  api.post('/catalog/holidays',write(staff,holiday,201))
  api.put('/catalog/holidays/:id',write(staff,holiday))
  api.post('/catalog/periods',write(staff,async (req,db) => {
    const f=req.body
    const campus=integer(f.campusId), period=integer(f.periodNo,1,15), from=date(f.effectiveFrom)
    const start=time(f.startTime), end=time(f.endTime), shift=choice(f.shift,'MORNING','AFTERNOON','EVENING')
    await requireCampus(db,req.actor,campus)
    if (start>=end) throw invalid('Giờ bắt đầu phải trước giờ kết thúc.')
    const current=await first(db,'SELECT * FROM period_definitions WHERE campus_id=$1 AND period_no=$2 ORDER BY effective_from DESC LIMIT 1',[campus,period])
    if (current && (from<=today() || from<=current.effective_from)) throw invalid('Khung tiết mới phải có ngày hiệu lực trong tương lai và sau khung hiện có.')
    const issues=await all(db,`SELECT x.id,c.code AS section_code FROM class_sessions x JOIN teaching_rooms t ON t.id=x.room_id
      JOIN course_sections c ON c.id=x.section_id WHERE t.campus_id=$1 AND x.status='PLANNED' AND x.start_at>=$2::date
      AND $3 BETWEEN x.period_from AND x.period_from+x.period_count-1`,[campus,from,period])
    if (issues.length) throw conflict({issues})
    if (await first(db,`SELECT 1 FROM period_definitions WHERE campus_id=$1 AND period_no<>$2 AND status='ACTIVE'
      AND (effective_to IS NULL OR effective_to>=$3::date) AND ((period_no<$2 AND end_time>$4::time) OR (period_no>$2 AND start_time<$5::time))`,
      [campus,period,from,start,end])) throw invalid('Giờ của tiết mới chồng lấn hoặc sai thứ tự.')
    if (current) await db.query('UPDATE period_definitions SET effective_to=$1::date-1 WHERE id=$2',[from,current.id])
    const result=await first(db,`INSERT INTO period_definitions(campus_id,period_no,start_time,end_time,shift,effective_from)
      VALUES ($1,$2,$3,$4,$5,$6) RETURNING id`,[campus,period,start,end,shift,from])
    const ctx=await context(db)
    for (const slot of ctx.patterns) {
      if (ctx.sections[slot.section].status==='DRAFT' && ctx.rooms[slot.room].campus_id===campus &&
          period>=slot.first && period<slot.first+slot.count && [...occurrences(slot)].some(d=>d>=from))
        await verify(db,ctx,slot,req.actor)
    }
    await audit(db,req.actor,'CREATE','period_definitions',result.id)
    return result
  },201))
}
