import { first, audit, pool } from '../db/pool.js'
import { route, write } from '../middleware/route.js'
import { HttpError, forbidden, integer, invalid, need, state, text } from '../middleware/errors.js'
import { applyRun, enqueueRun, getRun, settings } from './runs.js'
import { internal, previewDraft, saveDraft } from './sections.js'
import { readNotices } from './sessions.js'

export async function prepareProposal(db, actor, action, semester, input = {}) {
  let preview, runId=null, parameters=null
  if (['GENERATE','APPLY'].includes(action) && !internal.includes(actor.role)) throw forbidden()
  if (action==='GENERATE') {
    parameters=settings(input.parameters ?? {})
    preview={parameters}
  } else if (action==='APPLY') {
    const run=await getRun(db,actor,integer(input.runId))
    if (run.semester_id!==semester.id) throw forbidden()
    if (run.status!=='COMPLETED' || run.hard_violations!==0)
      throw state('Chọn phương án đã hoàn tất với 0 vi phạm cứng để tạo đề xuất áp dụng.')
    runId=run.id
    const {count}=await first(db,'SELECT count(*) AS count FROM schedule_run_items WHERE run_id=$1',[runId])
    preview={runId,count,hardViolations:run.hard_violations}
  } else if (action==='DRAFT') {
    const form={...input.draft,semesterId:semester.id}
    const [,subjectId,code,capacity,sessionsPerWeek,periodsPerSession,requiredRoomType,startDate,endDate]=await previewDraft(db,actor,form)
    parameters={semesterId:semester.id,subjectId,code,capacity,sessionsPerWeek,periodsPerSession,requiredRoomType,startDate,endDate}
    const subject=await first(db,'SELECT code,name FROM subjects WHERE id=$1',[subjectId])
    preview={...parameters,subject:subject.code+' · '+subject.name}
  } else if (action==='READ_NOTICES') {
    const {count,lastId}=await first(db,'SELECT count(*) AS count,max(id) AS "lastId" FROM notifications WHERE user_id=$1 AND read_at IS NULL',[actor.id])
    if (!count) return null
    parameters={lastId}
    preview={count}
  } else throw invalid('Thao tác không hợp lệ.')

  const proposal=await first(db,`INSERT INTO ai_proposals(requested_by,semester_id,action,run_id,parameters)
    VALUES ($1,$2,$3,$4,$5) RETURNING id,expires_at`,[actor.id,semester?.id ?? null,action,runId,parameters])
  return {...proposal,semester:semester?.code,action,preview}
}

async function ownedProposal(db, actor, id) {
  const pending=need(await first(db,'SELECT *,expires_at<=clock_timestamp() AS expired FROM ai_proposals WHERE id=$1 FOR UPDATE',[id]))
  if (pending.requested_by!==actor.id) throw forbidden()
  if (['GENERATE','APPLY'].includes(pending.action) && !internal.includes(actor.role)) throw forbidden()
  if (pending.action==='DRAFT' && actor.role!=='STAFF') throw forbidden()
  return pending
}

async function confirmProposal(req,db) {
  const pending=await ownedProposal(db,req.actor,req.recordId)
  if (pending.result) return pending.result
  if (pending.cancelled_at) throw state('Đề xuất đã hủy.')
  if (pending.expired) throw state('Đề xuất đã hết hạn. Hãy tạo đề xuất mới.')
  let payload
  if (pending.action==='GENERATE') {
    payload=await enqueueRun(db,req.actor,pending.semester_id,pending.parameters)
  } else if (pending.action==='APPLY') {
    const run=await getRun(db,req.actor,pending.run_id)
    if (run.semester_id!==pending.semester_id) throw forbidden()
    payload={...await applyRun(db,req.actor,pending.run_id),id:pending.run_id}
  } else if (pending.action==='DRAFT') {
    payload={...await saveDraft(db,req.actor,pending.parameters),code:pending.parameters.code}
  } else {
    payload=await readNotices(db,req.actor,pending.parameters.lastId)
  }
  const result={action:pending.action,...payload}
  await db.query('UPDATE ai_proposals SET result=$1,confirmed_at=now() WHERE id=$2',[result,pending.id])
  await audit(db,req.actor,'CONFIRM','ai_proposals',pending.id)
  return result
}

export function assistantRoutes(api, advisor) {
  api.get('/schedule-assistant',route(internal,()=>({enabled:!!advisor})))
  api.post('/schedule-assistant/proposals',route(internal,async req=>{
    if (!advisor) throw new HttpError(503,'ASSISTANT_DISABLED','Cần cấu hình Gemini trên backend để dùng chế độ này.')
    const semester=need(await first(pool,'SELECT id,code FROM semesters WHERE id=$1',[integer(req.body.semesterId)]))
    const action=await advisor(text(req.body.message,500))
    if (action==='UNRELATED') return {action,message:'Nhập yêu cầu chạy xếp lịch hoặc áp dụng phương án đã chọn.'}
    if (!['GENERATE','APPLY'].includes(action)) throw invalid('Đề xuất của trợ lý không hợp lệ.')
    return prepareProposal(pool,req.actor,action,semester,req.body)
  },201))
  api.post('/schedule-assistant/proposals/:id/confirm',write(internal,confirmProposal))
  api.post('/chat/proposals/:id/confirm',write([],confirmProposal))
  api.post('/chat/proposals/:id/cancel',write([],async (req,db)=>{
    const proposal=await ownedProposal(db,req.actor,req.recordId)
    if (proposal.result) throw state('Đề xuất đã thực hiện.')
    await db.query('UPDATE ai_proposals SET cancelled_at=COALESCE(cancelled_at,now()) WHERE id=$1',[proposal.id])
    return {status:'CANCELLED'}
  }))
}
