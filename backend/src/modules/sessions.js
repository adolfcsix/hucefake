import { all, first, audit, pool } from '../db/pool.js'
import { list } from '../db/list.js'
import { route, write } from '../middleware/route.js'
import { choice, conflict, date, duplicate, forbidden, integer, invalid, need, state, text, unit } from '../middleware/errors.js'
import { actualConflicts, busy, campuses, context, findSection, fits, holiday, insertSession, interval,
  notifySection, requireCampus, semesterLocked, sessionStaffSql, slotStaff, weekday } from './scheduling.js'
import { internal } from './sections.js'

const staff=['STAFF'], lecturer=['LECTURER']
const sessionQuery=`
  SELECT x.id,x.section_id,x.staff_id,x.room_id,x.period_from,x.period_count,x.status,x.source_session_id,
    to_char(x.start_at,'YYYY-MM-DD"T"HH24:MI:SS') AS start_at,to_char(x.end_at,'YYYY-MM-DD"T"HH24:MI:SS') AS end_at,
    c.code AS section_code,q.name AS subject_name,t.code AS room_code,sp.code AS staff_code,
    q.managing_unit_id,t.campus_id,c.status AS section_status,e.reason AS exception_reason
  FROM class_sessions x JOIN course_sections c ON c.id=x.section_id JOIN subjects q ON q.id=c.subject_id
  JOIN staff_profiles sp ON sp.id=x.staff_id LEFT JOIN teaching_rooms t ON t.id=x.room_id
  LEFT JOIN session_exceptions e ON e.session_id=x.id WHERE c.semester_id=$1`

export function scheduleQuery(req) {
  const view=choice(req.query.view || 'personal','personal','all','room','staff','group','section')
  const actor=req.actor
  if (!internal.includes(actor.role) && !['personal','section'].includes(view)) throw forbidden()
  const from=req.query.from ? date(req.query.from) : null
  const to=req.query.to ? date(req.query.to) : null
  if (from && to && from>to) throw invalid('Khoảng ngày không hợp lệ.')
  const args=[integer(req.query.semesterId),from,to]
  let sql=sessionQuery+' AND ($2::date IS NULL OR x.start_at >= $2::date) AND ($3::date IS NULL OR x.start_at < $3::date+1)'
  if (actor.role==='STUDENT') {
    args.push(actor.id)
    sql+=" AND EXISTS(SELECT 1 FROM section_enrollments en WHERE en.section_id=c.id AND en.student_user_id=$4 AND en.status='ENROLLED')"
  } else if (actor.role==='LECTURER') {
    args.push(actor.staffId)
    sql+=` AND $4=ANY(${sessionStaffSql})`
  }
  const column={room:'x.room_id',section:'x.section_id'}[view]
  if (column || view==='group' || view==='staff') {
    args.push(integer(req.query.id))
    sql+=column ? ' AND '+column+'=$'+args.length : view==='staff' ? ` AND $${args.length}=ANY(${sessionStaffSql})` :
      ' AND EXISTS(SELECT 1 FROM section_target_groups g WHERE g.section_id=c.id AND g.group_id=$'+args.length+')'
  }
  return {sql,args}
}

async function changeSession(db,actor,id,f,requestId=null) {
  const original=need(await first(db,'SELECT * FROM class_sessions WHERE id=$1',[id]))
  const section=await findSection(db,original.section_id)
  unit(actor,section.managing_unit_id)
  if (await first(db,'SELECT 1 FROM session_exceptions WHERE session_id=$1',[id])) throw duplicate('Buổi học đã có ngoại lệ.')
  if (original.status!=='PLANNED' || section.status!=='OPEN') throw state('Chỉ điều chỉnh buổi học đang lên lịch của lớp đã mở.')
  const ctx=await context(db)
  if (original.room_id) await requireCampus(db,actor,ctx.rooms[original.room_id].campus_id)
  const action=choice(f.action,'CANCEL','RESCHEDULE','ROOM_CHANGE'), reason=text(f.reason,500)
  const originalDate=original.start_at.slice(0,10)
  let replacement=null
  if (action!=='CANCEL') {
    const room=need(ctx.rooms[integer(f.roomId)])
    await requireCampus(db,actor,room.campus_id)
    const day=action==='ROOM_CHANGE' ? originalDate : date(f.date)
    const semester=need(await first(db,'SELECT start_date,end_date FROM semesters WHERE id=$1',[section.semester_id]))
    if (day<semester.start_date || day>semester.end_date) throw invalid('Ngày học bù phải nằm trong học kỳ.')
    if (holiday(ctx,room.campus_id,day)) throw invalid('Ngày đã chọn là ngày không học tại cơ sở.')
    const period=action==='ROOM_CHANGE' ? original.period_from : integer(f.periodFrom,1,15)
    if (!fits(room,section)) throw invalid('Phòng thay thế phải đủ chỗ và phù hợp học phần.')
    if (action==='ROOM_CHANGE' && room.id===original.room_id) throw invalid('Cần chọn phòng khác.')
    const slot={section:section.id,staff:original.staff_id,room:room.id,day:weekday(day),
      first:period,count:original.period_count,from:day,to:day}
    const time=interval(ctx,slot,day)
    if (!time || busy(ctx,slot,'BUSY')) throw invalid('Tiết thay thế qua ca hoặc rơi vào giờ bận.')
    if (room.id===original.room_id && time.start===original.start_at) throw invalid('Giờ hoặc phòng thay thế phải khác buổi ban đầu.')
    const issues=await actualConflicts(db,ctx,slot,time,id)
    if (issues.length) {
      const allowed=await campuses(db,actor)
      const suggestions=[]
      const periods=action==='ROOM_CHANGE' ? [period] : Array.from({length:15},(_,i)=>i+1)
      for (const alternative of periods) {
        for (const r of Object.values(ctx.rooms)) {
          if (!allowed.has(r.campus_id) || !fits(r,section) || holiday(ctx,r.campus_id,day) || action==='ROOM_CHANGE' && r.id===original.room_id) continue
          const proposed={...slot,room:r.id,first:alternative}, when=interval(ctx,proposed,day)
          if (!when || busy(ctx,proposed,'BUSY') || r.id===original.room_id && when.start===original.start_at) continue
          if (!(await actualConflicts(db,ctx,proposed,when,id)).length)
            suggestions.push({roomId:r.id,roomCode:r.code,dayOfWeek:slot.day,periodFrom:alternative})
          if (suggestions.length===5) break
        }
        if (suggestions.length===5) break
      }
      throw conflict({issues,suggestions})
    }
    replacement=(await insertSession(db,slot,time,original.timetable_version_id,null,id)).id
  }
  const result=await first(db,`INSERT INTO session_exceptions(session_id,occurrence_date,action,replacement_id,reason,created_by)
      VALUES ($1,$2,$3,$4,$5,$6) RETURNING id`,[id,originalDate,action,replacement,reason,actor.id])
  await db.query("UPDATE class_sessions SET status='CANCELLED',version=version+1 WHERE id=$1",[id])
  await audit(db,actor,action,'session_exceptions',result.id)
  if (requestId === null) {
    const closed=await all(db,`UPDATE session_change_requests SET
      status=CASE WHEN action=$2 THEN 'APPROVED' ELSE 'RETURNED' END,
      decision_reason=CASE WHEN action=$2 THEN 'Đề nghị đã được xử lý bằng điều chỉnh trực tiếp. Xem lại thời khóa biểu.'
        ELSE 'Buổi học có điều chỉnh trực tiếp khác với đề nghị. Xem lại thời khóa biểu.' END
      WHERE session_id=$1 AND status='PENDING' RETURNING id,staff_id,status,decision_reason`,[id,action])
    for (const request of closed) {
      await audit(db,actor,request.status,'session_change_requests',request.id)
      await db.query('INSERT INTO notifications(user_id,message) SELECT user_id,$2 FROM staff_profiles WHERE id=$1',
        [request.staff_id,request.decision_reason])
    }
  }
  await notifySection(db,section.id,'Lớp '+section.code+' thay đổi lịch ngày '+originalDate+'. '+reason)
  return {id:result.id,replacementId:replacement}
}

async function setAvailability(req,db) {
  const sem=integer(req.body.semesterId)
  const actor=req.actor
  if (!actor.staffId) throw forbidden()
  await semesterLocked(db,sem)
  need(await first(db,'SELECT id FROM semesters WHERE id=$1',[sem]))
  if (!Array.isArray(req.body.entries) || req.body.entries.length>100) throw invalid('Tối đa 100 khoảng thời gian.')
  await db.query('DELETE FROM staff_availability WHERE staff_id=$1 AND semester_id=$2',[actor.staffId,sem])
  for (const entry of req.body.entries) {
    if (!entry || typeof entry!=='object') throw invalid('Khoảng thời gian không hợp lệ.')
    const first=integer(entry.periodFrom,1,15)
    await db.query(`INSERT INTO staff_availability(staff_id,semester_id,day_of_week,period_from,period_to,kind,reason)
      VALUES ($1,$2,$3,$4,$5,$6,$7)`,[actor.staffId,sem,integer(entry.dayOfWeek,1,7),first,integer(entry.periodTo,first,15),
      choice(entry.kind,'BUSY','AVOID'),entry.reason ? text(entry.reason,500) : null])
  }
  const ctx=await context(db)
  for (const slot of ctx.patterns) if (slotStaff(ctx,slot).includes(actor.staffId) && ctx.sections[slot.section].semester_id===sem && busy(ctx,slot,'BUSY'))
    throw conflict({issues:[{rule:'BR08',message:'Đã có lịch giảng trong khoảng giờ bận.',sectionCode:ctx.sections[slot.section].code}]})
  const actual=await all(db,`SELECT DISTINCT x.id,c.code AS section_code,to_char(x.start_at,'YYYY-MM-DD HH24:MI') AS start_at
      FROM class_sessions x JOIN course_sections c ON c.id=x.section_id
      JOIN staff_availability a ON a.staff_id=$1 AND a.semester_id=c.semester_id AND a.kind='BUSY'
        AND a.day_of_week=EXTRACT(ISODOW FROM x.start_at) AND a.period_from<=x.period_from+x.period_count-1 AND a.period_to>=x.period_from
      WHERE $1=ANY(${sessionStaffSql}) AND c.semester_id=$2 AND x.status='PLANNED' AND x.start_at>=CURRENT_DATE`,[actor.staffId,sem])
  if (actual.length) throw conflict({issues:actual})
  await audit(db,actor,'SET_AVAILABILITY','semesters',sem)
}

export async function statistics(db, query) {
  const semester=integer(query.semesterId)
  const rooms=await all(db,`SELECT r.id,r.code,ca.code AS campus_code,count(*) AS sessions,sum(x.period_count) AS periods
      FROM class_sessions x JOIN course_sections c ON c.id=x.section_id
      LEFT JOIN teaching_rooms r ON r.id=x.room_id LEFT JOIN campuses ca ON ca.id=r.campus_id
      WHERE c.semester_id=$1 AND x.status<>'CANCELLED' GROUP BY r.id,r.code,ca.code ORDER BY sessions DESC,r.code,ca.code`,[semester])
  const lecturers=await all(db,`SELECT s.id,s.code,count(*) AS sessions,sum(x.period_count) AS periods
      FROM class_sessions x JOIN course_sections c ON c.id=x.section_id JOIN staff_profiles s ON s.id=ANY(${sessionStaffSql})
      WHERE c.semester_id=$1 AND x.status<>'CANCELLED' GROUP BY s.id,s.code ORDER BY sessions DESC,s.code`,[semester])
  return {rooms,lecturers}
}

export function changeRequests(db, actor, query) {
  return list(db,query,`SELECT r.*,c.code AS section_code,s.name AS subject_name,
    to_char(x.start_at,'YYYY-MM-DD HH24:MI') AS session_time,to_char(x.end_at,'YYYY-MM-DD HH24:MI') AS end_at,
    x.period_from, x.room_id, room.code AS room_code
    FROM session_change_requests r JOIN class_sessions x ON x.id=r.session_id JOIN course_sections c ON c.id=x.section_id
    JOIN subjects s ON s.id=c.subject_id LEFT JOIN teaching_rooms room ON room.id=x.room_id
    WHERE c.semester_id=$1 AND ${actor.role==='STAFF' ?
      's.managing_unit_id=$2 AND (room.campus_id IS NULL OR EXISTS (SELECT 1 FROM staff_campuses sc JOIN campuses ca ON ca.id=sc.campus_id WHERE sc.campus_id=room.campus_id AND sc.staff_id=$3 AND ca.status=\'ACTIVE\'))'
      : 'r.staff_id=$2'}`,
    [integer(query.semesterId),...(actor.role==='STAFF' ? [actor.unitId,actor.staffId] : [actor.staffId])],'id DESC')
}

export async function readNotices(db, actor, lastId = null) {
  const result=await db.query(`UPDATE notifications SET read_at=now() WHERE user_id=$1
    AND read_at IS NULL AND ($2::bigint IS NULL OR id<=$2)`,[actor.id,lastId])
  return {marked:result.rowCount}
}

export const availabilityList = (db, actor, semesterId) => all(db,
  'SELECT * FROM staff_availability WHERE staff_id=$1 AND semester_id=$2 ORDER BY day_of_week,period_from,id',
  [actor.staffId,integer(semesterId)])

export function sessionRoutes(api) {
  api.get('/schedule',route([],req=>{
    const {sql,args}=scheduleQuery(req)
    return list(pool,req.query,sql,args,'start_at,section_code,id')
  }))
  api.get('/schedule/sections',route([],req=>{
    const {sql,args}=scheduleQuery(req)
    return all(pool,`SELECT DISTINCT section_id AS id,section_code AS code,subject_name AS name FROM (${sql}) AS scoped ORDER BY code,id`,args)
  }))
  api.get('/schedule/statistics',route(['MANAGER'],req => statistics(pool,req.query)))
  api.post('/class-sessions',write(staff,(req,db)=>
    changeSession(db,req.actor,integer(req.body.sourceSessionId),{...req.body,action:'RESCHEDULE'}),201))
  api.patch('/class-sessions/:id',write(staff,(req,db)=>{
    choice(req.body.action,'RESCHEDULE','ROOM_CHANGE')
    return changeSession(db,req.actor,req.recordId,req.body)
  }))
  api.post('/class-sessions/:id/exceptions',write(staff,(req,db)=>changeSession(db,req.actor,req.recordId,req.body)))
  api.get('/me/availability',route(lecturer,req=>availabilityList(pool,req.actor,req.query.semesterId)))
  api.put('/me/availability',write(lecturer,setAvailability,204))
  api.get('/session-change-requests',route(['STAFF','LECTURER'],req => changeRequests(pool,req.actor,req.query)))
  api.post('/class-sessions/:id/requests',write(lecturer,async (req,db)=>{
    const session=need(await first(db,`SELECT x.*,(${sessionStaffSql}) AS staff_ids FROM class_sessions x WHERE x.id=$1`,[req.recordId]))
    if (!session.staff_ids.includes(req.actor.staffId)) throw forbidden()
    const section=await findSection(db,session.section_id)
    if (session.status!=='PLANNED' || section.status!=='OPEN') throw state('Buổi học không còn ở trạng thái đã lên lịch.')
    const result=await first(db,'INSERT INTO session_change_requests(session_id,staff_id,action,reason) VALUES ($1,$2,$3,$4) RETURNING id',
      [session.id,req.actor.staffId,choice(req.body.action,'CANCEL','RESCHEDULE'),text(req.body.reason,500)])
    await audit(db,req.actor,'REQUEST_CHANGE','class_sessions',session.id)
    await db.query(`INSERT INTO notifications(user_id,message)
      SELECT p.user_id,$1 FROM staff_profiles p JOIN users u ON u.id=p.user_id JOIN roles r ON r.role_id=u.role_id
      WHERE p.unit_id=$2 AND p.status='ACTIVE' AND u.status='ACTIVE' AND r.code='STAFF' AND ($3::bigint IS NULL OR EXISTS (
        SELECT 1 FROM teaching_rooms t JOIN staff_campuses sc ON sc.campus_id=t.campus_id
        JOIN campuses c ON c.id=t.campus_id WHERE t.id=$3 AND sc.staff_id=p.id AND c.status='ACTIVE'))`,
      ['Có đề nghị đổi lịch cho lớp '+section.code+'. Mở Đề nghị đổi lịch để xử lý.',section.managing_unit_id,session.room_id])
    return result
  },201))
  api.post('/session-change-requests/:id/decide',write(staff,async (req,db)=>{
    const record=need(await first(db,`SELECT r.*,s.managing_unit_id,room.campus_id FROM session_change_requests r JOIN class_sessions x ON x.id=r.session_id
      JOIN course_sections c ON c.id=x.section_id JOIN subjects s ON s.id=c.subject_id LEFT JOIN teaching_rooms room ON room.id=x.room_id WHERE r.id=$1`,[req.recordId]))
    unit(req.actor,record.managing_unit_id)
    if (record.campus_id) await requireCampus(db,req.actor,record.campus_id)
    if (record.status!=='PENDING') throw state('Đề nghị đã được xử lý.')
    const reason=text(req.body.reason,500)
    const status=req.body.action==='RETURNED' ? 'RETURNED' : 'APPROVED'
    if (status==='APPROVED') {
      if (record.action!==req.body.action) throw invalid('Loại điều chỉnh phải khớp với đề nghị.')
      await changeSession(db,req.actor,record.session_id,req.body,record.id)
    }
    await db.query('UPDATE session_change_requests SET status=$1,decision_reason=$2 WHERE id=$3',[status,reason,record.id])
    await db.query('INSERT INTO notifications(user_id,message) SELECT user_id,$1 FROM staff_profiles WHERE id=$2',
      [(status==='APPROVED' ? 'Đề nghị đổi lịch đã được xử lý. ' : 'Đề nghị đổi lịch đã được trả lại. ')+reason,record.staff_id])
    await audit(db,req.actor,status,'session_change_requests',record.id)
  },204))
  api.get('/notifications',route([],req=>list(pool,req.query,'SELECT * FROM notifications WHERE user_id=$1',[req.actor.id],'id DESC',100)))
  api.post('/notifications/read-all',write([], (req,db) => readNotices(db,req.actor)))
  api.post('/notifications/:id/read',write([],async (req,db)=>{
    await db.query('UPDATE notifications SET read_at=COALESCE(read_at,now()) WHERE id=$1 AND user_id=$2',[req.recordId,req.actor.id])
  },204))
}
