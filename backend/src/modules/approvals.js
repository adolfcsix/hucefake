import { all, first, audit, pool } from '../db/pool.js'
import { list } from '../db/list.js'
import { transaction } from '../db/transaction.js'
import { route, write } from '../middleware/route.js'
import { choice, conflict, duplicate, forbidden, integer, need, state, text } from '../middleware/errors.js'
import { actualConflicts, checkSlot, context, holiday, insertSession, interval, notifySection, occurrences, problems, semesterLocked } from './scheduling.js'
import { internal } from './sections.js'

const isStatus = (row,...value) => {
  if (!value.includes(row.status)) throw state('Hồ sơ đang ở trạng thái '+row.status+'.')
}
const approval = async (db,id) => need(await first(db,`SELECT a.* FROM approval_requests a
  JOIN timetable_versions t ON t.approval_request_id=a.id WHERE a.id=$1`,[id]))

async function assessSubmission(db,actor,semesterId) {
  const ctx=await context(db)
  const drafts=Object.values(ctx.sections).filter(s=>s.semester_id===semesterId && s.status==='DRAFT')
  const eligible=drafts.some(s=>s.managing_unit_id===actor.unitId)
  const readiness={semesterId,draftCount:drafts.length,blockedSections:[],ready:false}
  if (!drafts.length || !eligible) return {readiness:{...readiness,eligible}}
  const bySection=new Map(drafts.map(s=>[s.id,[]]))
  for (const slot of ctx.patterns) bySection.get(slot.section)?.push(slot)
  const snapshot=[]
  for (const section of drafts) {
    const slots=bySection.get(section.id)
    const missing=[]
    if (!section.staff_id) missing.push({rule:'BR02',message:'Chưa có giảng viên chính.'})
    if (slots.length!==section.sessions_per_week) missing.push({rule:'BR10',message:'Số buổi đã xếp khác số buổi yêu cầu.'})
    const conflicts=[]
    for (const slot of slots) {
      const {issues}=await checkSlot(db,ctx,slot,actor)
      if (issues.length) conflicts.push({id:slot.id,dayOfWeek:slot.day,periodFrom:slot.first,roomCode:ctx.rooms[slot.room]?.code,issues})
      else snapshot.push({...slot,sectionCode:section.code,subjectName:section.subject_name,roomCode:ctx.rooms[slot.room].code})
    }
    if (missing.length || conflicts.length) readiness.blockedSections.push({
      id:section.id,code:section.code,subjectName:section.subject_name,editable:section.managing_unit_id===actor.unitId,
      scheduled:slots.length,required:section.sessions_per_week,missing,conflicts
    })
  }
  return {readiness:{...readiness,eligible,ready:!readiness.blockedSections.length},snapshot}
}

export function timetableVersions(db, query) {
  return list(db,query,`SELECT t.*,a.status AS decision_status,a.decision_reason,u.username AS created_by_name
    FROM timetable_versions t JOIN users u ON u.id=t.created_by LEFT JOIN approval_requests a ON a.id=t.approval_request_id
    WHERE t.semester_id=$1 AND ($2::text IS NULL OR t.status=$2)`,
    [integer(query.semesterId),query.filterStatus ?? null],'version_no DESC,id DESC')
}

export function approvalRoutes(api) {
  api.get('/timetable-versions',route(internal,req => timetableVersions(pool,req.query)))
  api.get('/approvals/:id',route(internal,req => approval(pool,req.recordId)))
  api.get('/timetable-versions/readiness',route(['STAFF'],req => transaction(async db => {
    const semesterId=integer(req.query.semesterId)
    need(await first(db,'SELECT id FROM semesters WHERE id=$1',[semesterId]))
    const pending=await first(db,"SELECT id FROM timetable_versions WHERE semester_id=$1 AND status='SUBMITTED'",[semesterId])
    if (pending) return {semesterId,pending:true,ready:false,blockedSections:[]}
    const {readiness}=await assessSubmission(db,req.actor,semesterId)
    return readiness
  },{readOnly:true})))
  api.post('/timetable-versions',write(['STAFF'],async (req,db) => {
    const sem=integer(req.body.semesterId)
    await semesterLocked(db,sem)
    const existing=await first(db,`SELECT id FROM timetable_versions WHERE semester_id=$1 AND created_by=$2 AND status='DRAFT'
      ORDER BY id DESC LIMIT 1`,[sem,req.actor.id])
    return existing ?? first(db,`INSERT INTO timetable_versions(semester_id,version_no,created_by)
      SELECT $1,COALESCE(max(version_no),0)+1,$2 FROM timetable_versions WHERE semester_id=$1 RETURNING id`,[sem,req.actor.id])
  },201))
  api.post('/timetable-versions/:id/submit',write(['STAFF'],async (req,db) => {
    const v=need(await first(db,'SELECT * FROM timetable_versions WHERE id=$1',[req.recordId]))
    if (v.created_by!==req.actor.id) throw forbidden()
    isStatus(v,'DRAFT')
    if (await first(db,"SELECT id FROM timetable_versions WHERE semester_id=$1 AND status='SUBMITTED'",[v.semester_id]))
      throw duplicate('Học kỳ đã có thời khóa biểu chờ duyệt.')
    const {readiness,snapshot}=await assessSubmission(db,req.actor,v.semester_id)
    if (!readiness.draftCount) throw state('Học kỳ chưa có lớp nháp để gửi duyệt.')
    if (!readiness.eligible) throw forbidden()
    if (readiness.blockedSections.some(s=>s.conflicts.length)) {
      const issues=readiness.blockedSections.flatMap(section=>section.conflicts.flatMap(slot=>
        slot.issues.map(issue=>({...issue,patternId:issue.patternId ?? slot.id,candidateSectionCode:section.code}))))
      throw conflict({...readiness,issues})
    }
    if (!readiness.ready) throw state('Cần hoàn tất phân công và xếp đủ các buổi trước khi gửi duyệt.',readiness)
    const work=await first(db,"INSERT INTO work_items(kind,created_by) VALUES ('TIMETABLE',$1) RETURNING id",[req.actor.id])
    const created=await first(db,'INSERT INTO approval_requests(work_item_id,requested_by,payload) VALUES ($1,$2,$3::jsonb) RETURNING id',
      [work.id,req.actor.id,JSON.stringify(snapshot)])
    await db.query("UPDATE timetable_versions SET status='SUBMITTED',section_count=$1,approval_request_id=$2 WHERE id=$3",[readiness.draftCount,created.id,v.id])
    await audit(db,req.actor,'SUBMIT','timetable_versions',v.id)
    await db.query(`INSERT INTO notifications(user_id,message) SELECT u.id,$1 FROM users u
      JOIN roles r ON r.role_id=u.role_id WHERE u.status='ACTIVE' AND r.code='MANAGER'`,
      ['Thời khóa biểu bản '+v.version_no+' đang chờ duyệt. Mở Duyệt thời khóa biểu để xem phương án.'])
    return {approvalId:created.id}
  }))
  api.post('/approvals/:id/decide',write(['MANAGER'],async (req,db) =>
    decide(db,req.actor,await approval(db,req.recordId),req.body)))
  api.post('/approvals/:id/apply',write(['MANAGER'],async (req,db) =>
    apply(db,req.actor,await approval(db,req.recordId))))
  api.post('/approvals/:id/publish',write(['MANAGER'],async (req,db) => {
    const a=await approval(db,req.recordId)
    if (a.status==='PENDING') {
      await decide(db,req.actor,a,{decision:'APPROVED'})
      a.status='APPROVED'
    }
    return apply(db,req.actor,a)
  }))
}

async function decide(db,actor,a,body) {
  if (a.requested_by===actor.id) throw forbidden()
  const decision=choice(body.decision,'APPROVED','RETURNED')
  isStatus(a,...(decision==='RETURNED' ? ['PENDING','APPROVED'] : ['PENDING']))
  const reason=decision==='RETURNED' ? text(body.reason,500) : null
  await db.query('UPDATE approval_requests SET status=$1,decided_by=$2,decided_at=now(),decision_reason=$3 WHERE id=$4',
    [decision,actor.id,reason,a.id])
  await db.query('UPDATE work_items SET status=$1 WHERE id=$2',[decision,a.work_item_id])
  if (decision==='RETURNED') {
    await db.query("UPDATE timetable_versions SET status='RETURNED' WHERE approval_request_id=$1",[a.id])
    await db.query('INSERT INTO notifications(user_id,message) VALUES ($1,$2)',[a.requested_by,'Thời khóa biểu được trả lại. Lý do: '+reason])
  }
  await audit(db,actor,decision,'approval_requests',a.id)
  return {status:decision}
}

async function apply(db,actor,a) {
  if (a.requested_by===actor.id) throw forbidden()
  const prior=await first(db,'SELECT result FROM execution_records WHERE approval_request_id=$1',[a.id])
  if (prior) return prior.result
  isStatus(a,'APPROVED')
  const v=need(await first(db,'SELECT * FROM timetable_versions WHERE approval_request_id=$1',[a.id]))
  isStatus(v,'SUBMITTED')
  const ctx=await context(db), sections=new Set()
  let total=0
  for (const slot of a.payload) {
    const issues=problems(ctx,slot)
    if (issues.length) throw conflict({issues})
    let count=0
    for (const day of occurrences(slot)) {
      if (holiday(ctx,ctx.rooms[slot.room].campus_id,day)) continue
      const time=interval(ctx,slot,day)
      const hits=await actualConflicts(db,ctx,slot,time)
      if (hits.length) throw conflict({issues:hits})
      await insertSession(db,slot,time,v.id,slot.id,null)
      count++
    }
    if (!count) throw state('Một lịch định kỳ không sinh được buổi học vì toàn bộ ngày đều nghỉ.')
    total+=count
    sections.add(slot.section)
  }
  for (const section of sections) {
    await db.query("UPDATE course_sections SET status='OPEN',version=version+1,updated_at=now() WHERE id=$1",[section])
    await notifySection(db,section,'Đã công bố lịch lớp '+ctx.sections[section].code+'.')
  }
  await db.query("UPDATE timetable_versions SET status='SUPERSEDED' WHERE semester_id=$1 AND status='PUBLISHED'",[v.semester_id])
  await db.query("UPDATE timetable_versions SET status='PUBLISHED',published_by=$1,published_at=now() WHERE id=$2",[actor.id,v.id])
  await db.query("UPDATE approval_requests SET status='APPLIED' WHERE id=$1",[a.id])
  await db.query("UPDATE work_items SET status='APPLIED' WHERE id=$1",[a.work_item_id])
  const result={versionId:v.id,sessions:total,sections:sections.size}
  await db.query('INSERT INTO execution_records(approval_request_id,idempotency_key,result) VALUES ($1,$2,$3)',[a.id,'fm05-publish:'+a.id,result])
  await audit(db,actor,'PUBLISH','timetable_versions',v.id)
  return result
}

