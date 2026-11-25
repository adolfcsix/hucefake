import { all, first, audit, pool } from '../db/pool.js'
import { list } from '../db/list.js'
import { transaction } from '../db/transaction.js'
import { route, write } from '../middleware/route.js'
import { HttpError, choice, conflict, date, duplicate, forbidden, integer, invalid, need, state, text, unit, version } from '../middleware/errors.js'
import { checkSlot, context, findSection, insertPattern, makeSlot, patternSql, requireCampus, sectionSql, semesterLocked, verify } from './scheduling.js'

export const internal = ['STAFF','MANAGER']
export const roomTypes = ['LECTURE','COMPUTER_LAB','LAB','STUDIO']
const staff = ['STAFF']

async function editable(db, actor, id) {
  const section = await findSection(db,id)
  unit(actor,section.managing_unit_id)
  if (section.status !== 'DRAFT') throw state('Chỉ chỉnh lịch định kỳ của lớp nháp.')
  await semesterLocked(db,section.semester_id)
  return section
}

async function sectionInput(db, actor, f) {
  const sem=need(await first(db,'SELECT * FROM semesters WHERE id=$1',[integer(f.semesterId)]))
  const subject=need(await first(db,'SELECT * FROM subjects WHERE id=$1',[integer(f.subjectId)]))
  unit(actor,subject.managing_unit_id)
  return sectionValues(sem,subject,f)
}

function sectionValues(sem,subject,f) {
  if (sem.status!=='ACTIVE' || subject.status!=='ACTIVE') throw invalid('Học kỳ và học phần phải đang hoạt động.')
  const from=date(f.startDate), to=date(f.endDate)
  if (from>to || from<sem.start_date || to>sem.end_date) throw invalid('Khoảng ngày phải nằm trong học kỳ.')
  return [sem.id,subject.id,text(f.code,40).toUpperCase(),integer(f.capacity,1,10000),
    integer(f.sessionsPerWeek,1,6),integer(f.periodsPerSession,1,6),choice(f.requiredRoomType,...roomTypes),from,to]
}

async function createDraft(db,actor,values) {
  const result=await first(db,`INSERT INTO course_sections(semester_id,subject_id,code,capacity,sessions_per_week,periods_per_session,
    required_room_type,start_date,end_date,created_by) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10) RETURNING id`,[...values,actor.id])
  await audit(db,actor,'CREATE','course_sections',result.id)
  return result
}

async function importDrafts(req,db) {
  const {semesterId,rows}=req.body
  if (!Array.isArray(rows) || rows.length<1 || rows.length>200) throw invalid('Mỗi tệp cần có từ 1 đến 200 lớp.')
  const semester=need(await first(db,'SELECT * FROM semesters WHERE id=$1',[integer(semesterId)]))
  await semesterLocked(db,semester.id)
  const subjects=new Map((await all(db,"SELECT * FROM subjects WHERE managing_unit_id=$1 AND status='ACTIVE'",[req.actor.unitId]))
    .map(subject=>[subject.code,subject]))
  const used=new Set((await all(db,'SELECT code FROM course_sections WHERE semester_id=$1',[semester.id])).map(row=>row.code))
  const batch=rows.map((row,index)=>{
    try {
      if (!row || typeof row!=='object' || Array.isArray(row)) throw invalid('Dòng dữ liệu không hợp lệ.')
      if (row.semesterCode!==semester.code) throw invalid('Mã học kỳ trong tệp khác học kỳ đang chọn.')
      const code=text(row.subjectCode,40)
      const subject=subjects.get(code) || subjects.get(code.toUpperCase())
      if (!subject) throw invalid('Mã học phần chưa có hoặc nằm ngoài phạm vi khoa.')
      const values=sectionValues(semester,subject,row)
      if (used.has(values[2])) throw duplicate('Mã lớp đã có trong học kỳ hoặc xuất hiện nhiều lần trong tệp.')
      used.add(values[2])
      return values
    } catch (error) {
      if (!(error instanceof HttpError)) throw error
      throw new HttpError(error.status,error.code,'Dòng dữ liệu '+(index+1)+' · '+error.message)
    }
  })
  for (const values of batch) await createDraft(db,req.actor,values)
  return { imported:batch.length }
}

function idSet(value) {
  if (value==null) return []
  if (!Array.isArray(value) || value.length>200) throw invalid('Danh sách lựa chọn không hợp lệ.')
  return [...new Set(value.map(id => integer(id)))]
}

async function recheck(db,actor,section) {
  const ctx=await context(db)
  for (const slot of ctx.patterns.filter(p => p.section===section)) await verify(db,ctx,slot,actor)
}

async function candidate(db,actor,ctx,f,id,section) {
  const previous=id==null ? null : need(ctx.patterns.find(p => p.id===id))
  if (previous && previous.section!==section.id) throw invalid('Không thể chuyển lịch sang lớp khác.')
  const room=need(ctx.rooms[integer(f.roomId)])
  await requireCampus(db,actor,room.campus_id)
  if (previous) await requireCampus(db,actor,ctx.rooms[previous.room].campus_id)
  return makeSlot(section,room.id,integer(f.dayOfWeek,1,7),integer(f.periodFrom,1,15),
    f.effectiveFrom ? date(f.effectiveFrom) : section.start_date,
    f.effectiveTo ? date(f.effectiveTo) : section.end_date,previous)
}

async function savePattern(req, db) {
  const f=req.body
  const section=await editable(db,req.actor,integer(f.sectionId))
  const ctx=await context(db)
  const slot=await candidate(db,req.actor,ctx,f,req.recordId,section)
  if (slot.id) version(f.version,need(await first(db,'SELECT * FROM section_schedule_patterns WHERE id=$1',[slot.id])))
  if (!slot.id && section.scheduled>=section.sessions_per_week) throw state('Lớp đã xếp đủ số buổi mỗi tuần.')
  const result=await checkSlot(db,ctx,slot,req.actor,true)
  if (!result.valid) throw conflict(result)
  const record=slot.id ? await first(db,`UPDATE section_schedule_patterns SET room_id=$1,day_of_week=$2,period_from=$3,
    effective_from=$4,effective_to=$5,source='MANUAL',run_id=NULL,version=version+1 WHERE id=$6 RETURNING id`,
    [slot.room,slot.day,slot.first,slot.from,slot.to,slot.id]) : await insertPattern(db,slot,'MANUAL',null,req.actor)
  await audit(db,req.actor,'SAVE','section_schedule_patterns',record.id)
  return record
}

export function courseSectionList(db, actor, query) {
  return list(db,query,sectionSql+`
    WHERE c.semester_id=$1 AND ($2::bigint IS NULL OR s.managing_unit_id=$2)
      AND ($3::text IS NULL OR c.status=$3) AND ($4::bigint IS NULL OR c.subject_id=$4)
      AND (c.code ILIKE $5 OR s.name ILIKE $5)`,
    [integer(query.semesterId),actor.role==='STAFF' ? actor.unitId ?? -1 : null,
      query.status || null,query.subjectId ? integer(query.subjectId) : null,'%'+String(query.search || '')+'%'],'code,id')
}

export async function previewDraft(db, actor, form) {
  if (actor.role!=='STAFF') throw forbidden()
  const values=await sectionInput(db,actor,form)
  await semesterLocked(db,values[0])
  if (await first(db,'SELECT 1 FROM course_sections WHERE semester_id=$1 AND code=$2',[values[0],values[2]]))
    throw duplicate('Mã lớp đã có trong học kỳ này.',{field:'code'})
  return values
}

export async function saveDraft(db, actor, form) {
  const values=await previewDraft(db,actor,form)
  return createDraft(db,actor,values)
}

export function sectionRoutes(api) {
  api.get('/course-sections',route(internal,req => courseSectionList(pool,req.actor,req.query)))
  api.post('/course-sections',write(staff,(req,db) => saveDraft(db,req.actor,req.body),201))
  api.post('/course-sections/import',write(staff,importDrafts,201))
  api.patch('/course-sections/:id',write(staff,async (req,db) => {
    const section=await editable(db,req.actor,req.recordId)
    version(req.body.version,section)
    const [sem,subject,code,capacity,count,length,type,from,to]=await sectionInput(db,req.actor,req.body)
    if (section.semester_id!==sem) throw invalid('Không thể chuyển học kỳ của lớp.')
    if (section.scheduled>count || await first(db,`SELECT 1 FROM section_schedule_patterns p JOIN teaching_rooms r ON r.id=p.room_id
      WHERE p.section_id=$1 AND (p.period_count<>$2 OR p.effective_from<$3 OR p.effective_to>$4 OR r.capacity<$5 OR r.type<>$6)`,
      [section.id,length,from,to,capacity,type])) throw state('Hãy gỡ lịch định kỳ bị ảnh hưởng trước khi sửa lớp.')
    await db.query(`UPDATE course_sections SET subject_id=$1,code=$2,capacity=$3,sessions_per_week=$4,periods_per_session=$5,
      required_room_type=$6,start_date=$7,end_date=$8,version=version+1,updated_at=now() WHERE id=$9`,[subject,code,capacity,count,length,type,from,to,section.id])
    await audit(db,req.actor,'UPDATE','course_sections',section.id)
    return {id:section.id}
  }))
  api.post('/course-sections/:id/cancel',write(staff,async (req,db) => {
    const section=await editable(db,req.actor,req.recordId)
    version(req.query.version,section)
    await db.query('DELETE FROM section_schedule_patterns WHERE section_id=$1',[section.id])
    await db.query("UPDATE course_sections SET status='CANCELLED',version=version+1,updated_at=now() WHERE id=$1",[section.id])
    await audit(db,req.actor,'CANCEL','course_sections',section.id)
  },204))
  api.put('/course-sections/:id/assignments',write(staff,async (req,db) => {
    const section=await editable(db,req.actor,req.recordId)
    if (req.body.version != null) version(req.body.version,section)
    const main=integer(req.body.staffId), assistants=idSet(req.body.assistantIds)
    if (assistants.includes(main)) throw invalid('Giảng viên chính không thể đồng thời là trợ giảng.')
    const desired=new Map([[main,'MAIN'],...assistants.map(id => [id,'ASSISTANT'])])
    const lecturer=await all(db,`SELECT p.id FROM staff_profiles p JOIN users u ON u.id=p.user_id JOIN roles r ON r.role_id=u.role_id
      WHERE p.id=ANY($1::bigint[]) AND p.status='ACTIVE' AND u.status='ACTIVE' AND r.code='LECTURER'`,[[...desired.keys()]])
    if (lecturer.length!==desired.size) throw invalid('Tài khoản giảng viên phải đang hoạt động.')
    for (const row of await all(db,'SELECT * FROM section_assignments WHERE section_id=$1 AND valid_to IS NULL',[section.id])) {
      if (desired.get(row.staff_id)===row.role) desired.delete(row.staff_id)
      else await db.query('UPDATE section_assignments SET valid_to=GREATEST(valid_from,CURRENT_DATE) WHERE id=$1',[row.id])
    }
    for (const [id,role] of desired)
      await db.query('INSERT INTO section_assignments(section_id,staff_id,role,valid_from,assigned_by) VALUES ($1,$2,$3,$4,$5)',
        [section.id,id,role,section.start_date,req.actor.id])
    if (section.staff_id!==main) await db.query('UPDATE section_schedule_patterns SET staff_id=$1,version=version+1 WHERE section_id=$2',[main,section.id])
    await recheck(db,req.actor,section.id)
    await db.query('UPDATE course_sections SET version=version+1,updated_at=now() WHERE id=$1',[section.id])
    await audit(db,req.actor,'ASSIGN','course_sections',section.id)
  },204))
  api.put('/course-sections/:id/target-groups',write(staff,async (req,db) => {
    const section=await editable(db,req.actor,req.recordId)
    if (req.body.version != null) version(req.body.version,section)
    const ids=idSet(req.body.groupIds)
    await db.query('DELETE FROM section_target_groups WHERE section_id=$1',[section.id])
    await db.query('INSERT INTO section_target_groups(section_id,group_id) SELECT $1,id FROM unnest($2::bigint[]) id',[section.id,ids])
    await recheck(db,req.actor,section.id)
    await db.query('UPDATE course_sections SET version=version+1,updated_at=now() WHERE id=$1',[section.id])
    await audit(db,req.actor,'SET_TARGET_GROUPS','course_sections',section.id)
  },204))

  api.get('/schedule-patterns',route(internal,req => list(pool,req.query,patternSql+' WHERE c.semester_id=$1',
    [integer(req.query.semesterId)],'day_of_week,period_from,id')))
  api.post('/schedule/conflicts/check',route(internal,req => transaction(async db => {
    const section=await findSection(db,integer(req.body.sectionId))
    unit(req.actor,section.managing_unit_id)
    if (section.status!=='DRAFT') throw state('Chỉ kiểm tra phương án cho lớp nháp.')
    const ctx=await context(db)
    const slot=await candidate(db,req.actor,ctx,req.body,req.body.patternId ? integer(req.body.patternId) : null,section)
    return checkSlot(db,ctx,slot,req.actor,true)
  },{readOnly:true})))
  api.post('/schedule-patterns',write(staff,savePattern,201))
  api.patch('/schedule-patterns/:id',write(staff,savePattern))
  api.delete('/schedule-patterns/:id',write(staff,async (req,db) => {
    const pattern=need(await first(db,'SELECT p.*,r.campus_id FROM section_schedule_patterns p JOIN teaching_rooms r ON r.id=p.room_id WHERE p.id=$1',[req.recordId]))
    await editable(db,req.actor,pattern.section_id)
    await requireCampus(db,req.actor,pattern.campus_id)
    version(req.query.version,pattern)
    await db.query('DELETE FROM section_schedule_patterns WHERE id=$1',[pattern.id])
    await audit(db,req.actor,'DELETE','section_schedule_patterns',pattern.id)
  },204))
}
