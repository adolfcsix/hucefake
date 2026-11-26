import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { readFileSync,writeFileSync,mkdirSync } from 'node:fs'
import { performance } from 'node:perf_hooks'
const base=process.env.HUCE_TEST_URL
assert.equal(process.env.HUCE_BENCH_CI,'true','CI-only benchmark')
assert.ok(base&&['127.0.0.1','localhost'].includes(new URL(base).hostname))
assert.ok(process.env.PG_CONTAINER)
function sql(statement){
 return execFileSync('docker',['exec','-i',process.env.PG_CONTAINER,'psql','-U','fm05','-d','huce_spring','-Atq','-v','ON_ERROR_STOP=1'],{input:statement,encoding:'utf8'}).trim()
}
assert.equal(sql("SELECT demo FROM fm05_runtime_mode"),'t')
sql(readFileSync(new URL('./ga-benchmark-fixture.sql',import.meta.url),'utf8'))
let cookie='',csrf=''
async function call(path,method='GET',body,status=200){
 const headers={Cookie:cookie,Accept:'application/json'}
 if(method!=='GET')Object.assign(headers,{'Content-Type':'application/json','X-CSRF-Token':csrf})
 const r=await fetch(base+'/api'+path,{method,headers,body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(90000)})
 cookie=r.headers.getSetCookie()[0]?.split(';')[0]??cookie
 const data=r.status===204?null:await r.json()
 assert.equal(r.status,status,path+' '+JSON.stringify(data))
 return data
}
csrf=(await call('/csrf')).token
await call('/auth/demo-login','POST',{role:'STAFF'})
const sem=(await call('/catalog')).semesters.find(s=>s.code==='QA-BENCH.1')
assert.ok(sem)
const term='?semesterId='+sem.id
assert.equal((await call('/course-sections'+term)).length,48)
const results=[]
for(const seed of [2026,73,911]){
 const params={population:24,generations:40,crossover:.9,mutation:.08,seed}
 const start=performance.now()
 const queued=await call('/schedule-runs','POST',{semesterId:sem.id,parameters:params},202)
 const enqueueMs=performance.now()-start
 let run
 while(performance.now()-start<150000){
  run=await call('/schedule-runs/'+queued.id)
  if(['COMPLETED','FAILED'].includes(run.status))break
  await new Promise(r=>setTimeout(r,500))
 }
 assert.equal(run?.status,'COMPLETED',JSON.stringify(run))
 assert.equal(run.hard_violations,0,JSON.stringify(run))
 const items=await call('/schedule-runs/'+queued.id+'/items')
 assert.equal(items.length,96)
 const result={seed,runId:queued.id,classes:48,slots:96,enqueueMs:Math.round(enqueueMs),totalMs:Math.round(performance.now()-start),generation:run.generations_run,hardViolations:run.hard_violations,softPenalty:Number(run.soft_penalty),fitness:Number(run.fitness)}
 results.push(result)
 console.log('GA BENCHMARK '+JSON.stringify(result))
 if(seed!==911)await call('/schedule-runs/'+queued.id+'/discard','POST',{},204)
 else{
  const applyStart=performance.now()
  const applied=await call('/schedule-runs/'+queued.id+'/apply','POST',{})
  result.applyMs=Math.round(performance.now()-applyStart)
  assert.equal(applied.patterns,96)
  assert.equal((await call('/course-sections'+term)).filter(x=>x.scheduled===2).length,48)
 }
}
const violations=sql(`
WITH p AS (
 SELECT p.* FROM section_schedule_patterns p JOIN course_sections c ON c.id=p.section_id JOIN semesters h ON h.id=c.semester_id WHERE h.code='QA-BENCH.1'
)
SELECT
 (SELECT count(*) FROM p a JOIN p b ON a.id<b.id AND a.day_of_week=b.day_of_week
 WHERE a.section_id=b.section_id OR
   (a.period_from<b.period_from+b.period_count AND b.period_from<a.period_from+a.period_count AND
    (a.room_id=b.room_id OR a.staff_id=b.staff_id OR EXISTS(
     SELECT 1 FROM section_target_groups g JOIN section_target_groups j USING(group_id) WHERE g.section_id=a.section_id AND j.section_id=b.section_id))))
 + (SELECT count(*) FROM p JOIN course_sections c ON c.id=p.section_id JOIN staff_availability a ON a.staff_id=p.staff_id AND a.semester_id=c.semester_id AND a.day_of_week=p.day_of_week WHERE a.kind='BUSY' AND a.period_from<=p.period_from+p.period_count-1 AND a.period_to>=p.period_from)
 + (SELECT count(*) FROM p JOIN course_sections c ON c.id=p.section_id JOIN teaching_rooms r ON r.id=p.room_id WHERE r.capacity<c.capacity OR r.type<>c.required_room_type OR r.status<>'ACTIVE');
`)
assert.equal(violations,'0','Independent SQL check')
mkdirSync(new URL('../target/',import.meta.url),{recursive:true})
writeFileSync(new URL('../target/ga-benchmark.json',import.meta.url),JSON.stringify({fixture:'48 classes / 96 slots / 16 lecturers / 12 groups / 8 added rooms',results,independentViolations:Number(violations)},null,2))
console.log('PASS: GA benchmark 3 seeds, 96 slots per run, independent SQL validation')
