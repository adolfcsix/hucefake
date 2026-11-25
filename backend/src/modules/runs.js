import { Worker } from 'node:worker_threads'
import { randomInt } from 'node:crypto'
import { setImmediate } from 'node:timers/promises'
import { all, first, audit, pool } from '../db/pool.js'
import { transaction } from '../db/transaction.js'
import { list } from '../db/list.js'
import { route, write } from '../middleware/route.js'
import { HttpError, conflict, duplicate, forbidden, integer, invalid, need, stale, state, unit } from '../middleware/errors.js'
import { actualPattern, campuses, context, fits, holiday, insertPattern, interval, makeSlot, occurrences, problems, requireCampus, semesterLocked, sessionStaffSql, slotStaff } from './scheduling.js'
import { internal } from './sections.js'

const managerJob=`EXISTS (SELECT 1 FROM users u JOIN roles r ON r.role_id=u.role_id WHERE u.id=run.requested_by AND r.code='MANAGER')
  AND NOT EXISTS (SELECT 1 FROM schedule_run_items i JOIN course_sections c ON c.id=i.section_id JOIN subjects s ON s.id=c.subject_id
    WHERE i.run_id=run.id AND s.managing_unit_id IS DISTINCT FROM $4)`
export async function getRun(db,actor,id) {
  const run=need(await first(db,'SELECT * FROM schedule_runs WHERE id=$1',[id]))
  if (actor.role==='STAFF' && run.requested_by!==actor.id) {
    if (!['COMPLETED','APPLIED'].includes(run.status) ||
        !(await first(db,`SELECT 1 FROM schedule_runs run WHERE run.id=$1 AND ${managerJob.replace('$4','$2')}`,[id,actor.unitId]))) throw forbidden()
  }
  return run
}

const targets=(ctx,actor,sem)=>Object.values(ctx.sections).filter(s=>s.semester_id===sem && s.status==='DRAFT' &&
  (actor.role==='MANAGER' || s.managing_unit_id===actor.unitId))
  .filter(s=>ctx.patterns.filter(p=>p.section===s.id).length<s.sessions_per_week)

async function input(db,actor,sem) {
  const ctx=await context(db)
  const allowed=await campuses(db,actor)
  const genes=[], issues=[]
  const sections=targets(ctx,actor,sem)
  if (!sections.length) throw state('Không có buổi học nháp cần xếp trong phạm vi đã chọn.')
  const from=sections.reduce((day,s)=>s.start_date<day ? s.start_date : day,sections[0].start_date)
  const to=sections.reduce((day,s)=>s.end_date>day ? s.end_date : day,sections[0].end_date)
  const actual=await all(db,`SELECT x.section_id,x.room_id,x.start_at,x.end_at,(${sessionStaffSql}) AS staff_ids,
    ARRAY(SELECT g.group_id FROM section_target_groups g WHERE g.section_id=x.section_id) AS group_ids
    FROM class_sessions x WHERE x.start_at<$2::date+1 AND x.end_at>=$1::date AND x.status<>'CANCELLED'`,[from,to])
  const actualByDate=new Map()
  for (const row of actual) {
    const day=row.start_at.slice(0,10)
    if (!actualByDate.has(day)) actualByDate.set(day,[])
    actualByDate.get(day).push(row)
  }
  const intervals=new Map()
  function lessonTime(slot,day) {
    const key=[ctx.rooms[slot.room].campus_id,slot.first,slot.count,day].join(':')
    if (!intervals.has(key)) intervals.set(key,interval(ctx,slot,day))
    return intervals.get(key)
  }
  for (const section of sections) {
    await setImmediate()
    if (!section.staff_id) {
      issues.push({rule:'BR02',sectionCode:section.code,message:'Chưa có giảng viên chính.'})
      continue
    }
    const domain=[]
    for (const room of Object.values(ctx.rooms)) if (allowed.has(room.campus_id) && fits(room,section)) {
      for (let day=1;day<=6;day++) for (let period=1;period<=16-section.periods_per_session;period++) {
        const slot=makeSlot(section,room.id,day,period)
        const dates=[...occurrences(slot)]
        const teaching=dates.filter(d=>!holiday(ctx,room.campus_id,d))
        if (!teaching.length || dates.some(d=>!lessonTime(slot,d))) continue
        const free=teaching.every(d=>{
          const when=lessonTime(slot,d)
          return !(actualByDate.get(d)||[]).some(row=>when.start<row.end_at && row.start_at<when.end &&
            (row.room_id===slot.room || row.staff_ids.some(id=>slotStaff(ctx,slot).includes(id)) || row.section_id===slot.section ||
              row.group_ids.some(id=>section.group_ids.includes(id))))
        })
        if (free) domain.push(slot)
      }
    }
    if (!domain.length) {
      issues.push({rule:'BR07',sectionCode:section.code,message:'Không có phòng và khung tiết phù hợp.'})
      continue
    }
    for (let i=ctx.patterns.filter(p=>p.section===section.id).length;i<section.sessions_per_week;i++) genes.push({domain})
  }
  if (issues.length) throw new HttpError(400,'VALIDATION_ERROR','Dữ liệu chưa đủ để xếp lịch tự động.',{issues})
  return {ctx,genes,fixed:ctx.patterns}
}
export function settings(p={}) {
  const rate=(v,fallback)=>{
    if (v==null) return fallback
    if (typeof v!=='number' || !Number.isFinite(v) || v<0 || v>1) throw invalid('Xác suất phải nằm trong khoảng 0 đến 1.')
    return v
  }
  return {population:integer(p.population??100,8,200),generations:integer(p.generations??500,1,1000),
    crossover:rate(p.crossover,.9),mutation:rate(p.mutation,.02),seed:integer(p.seed??randomInt(0,2147483648),-2147483648,2147483647)}
}
export async function enqueueRun(db,actor,semesterId,parameters) {
  if (!internal.includes(actor.role)) throw forbidden()
  const sem=integer(semesterId)
  await semesterLocked(db,sem)
  if(await first(db,"SELECT 1 FROM schedule_runs WHERE semester_id=$1 AND status IN ('QUEUED','RUNNING')",[sem]))
    throw duplicate('Học kỳ đang có một lần chạy xếp lịch.')
  const params=settings(parameters??{})
  await input(db,actor,sem)
  const result=await first(db,'INSERT INTO schedule_runs(semester_id,requested_by,parameters) VALUES ($1,$2,$3) RETURNING id',[sem,actor.id,params])
  await audit(db,actor,'QUEUE','schedule_runs',result.id)
  return result
}

export async function applyRun(db,actor,id) {
  if (!internal.includes(actor.role)) throw forbidden()
  const run=await getRun(db,actor,id)
  if (run.status==='APPLIED') return {status:'APPLIED'}
  if (run.status!=='COMPLETED' || run.hard_violations!==0) throw state('Chỉ áp dụng được phương án hoàn tất với 0 vi phạm cứng.')
  await semesterLocked(db,run.semester_id)
  const ctx=await context(db), expected=targets(ctx,actor,run.semester_id)
  const plan=[]
  for (const r of await all(db,'SELECT * FROM schedule_run_items WHERE run_id=$1 ORDER BY id',[run.id])) {
    const section=ctx.sections[r.section_id]
    if (!section) throw stale({runId:run.id})
    unit(actor,section.managing_unit_id)
    if (!expected.includes(section) || section.staff_id!==r.staff_id || section.periods_per_session!==r.period_count) throw stale({runId:run.id})
    const room=need(ctx.rooms[r.room_id])
    await requireCampus(db,actor,room.campus_id)
    const slot=makeSlot(section,room.id,r.day_of_week,r.period_from)
    let issues=problems(ctx,slot,[...ctx.patterns,...plan])
    if (!issues.length) issues=await actualPattern(db,ctx,slot)
    if (issues.length) throw conflict({issues})
    plan.push(slot)
  }
  for (const s of expected) if ([...ctx.patterns,...plan].filter(p=>p.section===s.id).length!==s.sessions_per_week)
    throw stale({runId:run.id})
  for (const slot of plan) await insertPattern(db,slot,'GENETIC',run.id,actor)
  await db.query("UPDATE schedule_runs SET status='APPLIED' WHERE id=$1",[run.id])
  await audit(db,actor,'APPLY','schedule_runs',run.id)
  return {status:'APPLIED',patterns:plan.length}
}

export function runRoutes(api) {
  api.get('/schedule-runs/active',route(internal,async req=>{
    const semesterId=integer(req.query.semesterId)
    const run=await first(pool,"SELECT 1 FROM schedule_runs WHERE semester_id=$1 AND status IN ('QUEUED','RUNNING')",[semesterId])
    return {active:!!run}
  }))
  api.get('/schedule-runs',route(internal,req=>list(pool,req.query,`SELECT id,status,generations_run,hard_violations,soft_penalty,fitness,message,requested_by,created_at
    FROM schedule_runs run WHERE semester_id=$1 AND ($2 OR requested_by=$3 OR (status IN ('COMPLETED','APPLIED') AND ${managerJob}))
    `,[integer(req.query.semesterId),req.actor.role==='MANAGER',req.actor.id,req.actor.unitId],'id DESC',30)))
  api.get('/schedule-runs/:id',route(internal,async req=>{
    const run=await getRun(pool,req.actor,req.recordId)
    run.progress=await all(pool,'SELECT generation,hard_violations,soft_penalty FROM schedule_run_progress WHERE run_id=$1 ORDER BY generation',[run.id])
    return run
  }))
  api.get('/schedule-runs/:id/items',route(internal,async req=>{
    const run=await getRun(pool,req.actor,req.recordId)
    return all(pool,`SELECT i.*,c.code AS section_code,t.code AS room_code,s.name AS subject_name
      FROM schedule_run_items i JOIN course_sections c ON c.id=i.section_id JOIN subjects s ON s.id=c.subject_id
      JOIN teaching_rooms t ON t.id=i.room_id WHERE run_id=$1 ORDER BY day_of_week,period_from,i.id`,[run.id])
  }))
  api.post('/schedule-runs',write(internal,(req,db)=>enqueueRun(db,req.actor,req.body.semesterId,req.body.parameters),202))
  api.post('/schedule-runs/:id/apply',write(internal,(req,db)=>applyRun(db,req.actor,req.recordId)))
  api.post('/schedule-runs/:id/discard',write(internal,async (req,db)=>{
    const run=await getRun(db,req.actor,req.recordId)
    if (req.actor.role==='STAFF' && run.requested_by!==req.actor.id) throw forbidden()
    if (!['QUEUED','COMPLETED','FAILED'].includes(run.status)) throw state('Lần chạy không thể bỏ ở trạng thái hiện tại.')
    await db.query("UPDATE schedule_runs SET status='DISCARDED' WHERE id=$1",[run.id])
    await audit(db,req.actor,'DISCARD','schedule_runs',run.id)
  },204))
}

async function progress(db,id,update) {
  await db.query('UPDATE schedule_runs SET generations_run=$1 WHERE id=$2 AND status=\'RUNNING\'',[update.generation,id])
  await db.query(`INSERT INTO schedule_run_progress(run_id,generation,hard_violations,soft_penalty)
    VALUES ($1,$2,$3,$4) ON CONFLICT (run_id,generation) DO UPDATE SET hard_violations=EXCLUDED.hard_violations,soft_penalty=EXCLUDED.soft_penalty`,
    [id,update.generation,update.hard,update.soft])
}
function execute(run, data) {
  return new Promise((resolve,reject)=>{
    const worker=new Worker(new URL('../workers/schedule.worker.js',import.meta.url),{workerData:{...data,settings:run.parameters}})
    const timer=setTimeout(()=>{
      worker.terminate().catch(console.error)
      reject(state('Vượt giới hạn 60 giây.'))
    },65_000)
    let writes=Promise.resolve()
    const finish=(error,value)=>{
      clearTimeout(timer)
      if(error) reject(error)
      else resolve(value)
    }
    worker.on('message',message=>{
      if(message.type==='progress') writes=writes.then(()=>progress(pool,run.id,message)).catch(console.error)
      else if(message.type==='complete') writes.then(()=>finish(null,message))
    })
    worker.on('error',error=>finish(error))
    worker.on('exit',code=>{if(code) finish(new Error('Worker exited with code '+code))})
  })
}
export function startRunWorker() {
  let processing=false
  const timer=setInterval(async ()=>{
    if (processing) return
    processing=true
    let run
    try {
      run=await transaction(async db=>{
        await db.query(`UPDATE schedule_runs SET status='FAILED',finished_at=now(),message='Lần chạy bị gián đoạn.'
          WHERE status='RUNNING' AND started_at<now()-interval '10 minutes'`)
        return first(db,`UPDATE schedule_runs SET status='RUNNING',started_at=now() WHERE id=(
          SELECT id FROM schedule_runs WHERE status='QUEUED' ORDER BY id FOR UPDATE SKIP LOCKED LIMIT 1) RETURNING *`)
      })
      if (!run) return
      const data=await transaction(async db=>{
        const actor=need(await first(db,`SELECT u.id,r.code AS role,p.id AS "staffId",p.unit_id AS "unitId" FROM users u
          JOIN roles r ON r.role_id=u.role_id LEFT JOIN staff_profiles p ON p.user_id=u.id AND p.status='ACTIVE'
          WHERE u.id=$1 AND u.status='ACTIVE'`,[run.requested_by]))
        if(!internal.includes(actor.role)) throw forbidden()
        return input(db,actor,run.semester_id)
      })
      const result=await execute(run,data)
      if(result.timedOut) throw state('Vượt giới hạn 60 giây.')
      await transaction(async db=>{
        for (const [i,s] of result.slots.entries())
          await db.query(`INSERT INTO schedule_run_items(run_id,section_id,staff_id,room_id,day_of_week,period_from,period_count,violation_note)
            VALUES ($1,$2,$3,$4,$5,$6,$7,$8)`,[run.id,s.section,s.staff,s.room,s.day,s.first,s.count,result.notes[i]])
        await db.query(`UPDATE schedule_runs SET status='COMPLETED',hard_violations=$1,soft_penalty=$2,fitness=$3,finished_at=now()
          WHERE id=$4`,[result.hard,result.soft,1/(1+1000*result.hard+result.soft),run.id])
      })
    } catch (e) {
      if (!(e instanceof HttpError)) console.error(e)
      if (run) await pool.query("UPDATE schedule_runs SET status='FAILED',finished_at=now(),message=$1 WHERE id=$2",
        [e instanceof HttpError ? e.message : 'Không thể hoàn tất xếp lịch.',run.id]).catch(console.error)
    } finally {
      processing=false
    }
  },1000)
  timer.unref()
  return ()=>clearInterval(timer)
}
