import assert from 'node:assert/strict'
import {randomUUID} from 'node:crypto'
const base=process.env.HUCE_TEST_URL
assert.ok(base && ['127.0.0.1','localhost'].includes(new URL(base).hostname),'Use a disposable local demo database')
async function account(role,username){
 let cookie='',csrf=''
 async function send(path,method='GET',body,key=randomUUID()){
  const headers={Cookie:cookie,Accept:'application/json'}
  if(method!=='GET')Object.assign(headers,{'X-CSRF-Token':csrf,'Idempotency-Key':key,'Content-Type':'application/json'})
  const r=await fetch(new URL('/api'+path,base),{method,headers,body:body===undefined?undefined:JSON.stringify(body)})
  cookie=r.headers.getSetCookie()[0]?.split(';')[0]??cookie
  const data=r.status===204?null:await r.json()
  return {status:r.status,data}
 }
 async function call(path,method='GET',body,status=200,key){
  const r=await send(path,method,body,key);assert.equal(r.status,status,path+' '+JSON.stringify(r.data));return r.data
 }
 csrf=(await call('/csrf')).token
 const user=await call(username?'/auth/login':'/auth/demo-login','POST',username?{username,password:'Fm05Demo@2026'}:{role})
 assert.equal(user.role,role)
 return {call,send,user}
}
const staff=await account('STAFF'),other=await account('STAFF','daotao2'),manager=await account('MANAGER'),lecturer=await account('LECTURER'),student=await account('STUDENT')
const catalog=await staff.call('/catalog')
const sem=catalog.semesters[0],subject=catalog.subjects.find(s=>s.code==='CSDL')
assert.match(sem.start_date,/^\d{4}-\d{2}-\d{2}$/)
const term='?semesterId='+sem.id
const list='/course-sections'+term
const form={semesterId:sem.id,subjectId:subject.id,code:'QA.SPRING',capacity:30,sessionsPerWeek:1,periodsPerSession:2,requiredRoomType:'COMPUTER_LAB',startDate:sem.start_date,endDate:sem.end_date}
const before=await staff.call(list)
await lecturer.call(list,'GET',undefined,403)
await student.call(list,'GET',undefined,403)
await manager.call('/course-sections','POST',form,403)
await other.call('/course-sections','POST',form,403)
await staff.call('/course-sections','POST',{...form,capacity:0},400)
const key=randomUUID()
const created=await staff.call('/course-sections','POST',form,201,key)
assert.deepEqual(await staff.call('/course-sections','POST',form,201,key),created)
await staff.call('/course-sections','POST',{...form,code:'QA.DIFFERENT'},409,key)
await staff.call('/course-sections','POST',form,409)
let rows=await staff.call(list)
assert.equal(rows.length,before.length+1)
let row=rows.find(x=>x.id===created.id)
assert.equal(row.status,'DRAFT')
assert.deepEqual(row.group_ids,[])
assert.deepEqual(row.assistant_ids,[])
assert.equal(row.scheduled,0)
assert.equal(row.subject_code,subject.code)
assert.equal(row.start_date,sem.start_date)
assert.ok((await manager.call(list)).some(x=>x.id===created.id))
assert.ok(!(await other.call(list)).some(x=>x.id===created.id))
const page=await staff.call(list+'&page=1&pageSize=1&search=QA.SPRING')
assert.equal(page.total,1);assert.equal(page.items[0].id,created.id)
assert.deepEqual((await staff.call(list+'&page=2&pageSize=1&search=QA.SPRING')).items,[])
await staff.call('/course-sections/'+created.id,'PATCH',{...form,capacity:35,version:row.version})
await staff.call('/course-sections/'+created.id,'PATCH',{...form,capacity:36,version:row.version},409)
row=(await staff.call(list)).find(x=>x.id===created.id)
assert.equal(row.capacity,35);assert.equal(row.version,1)
const imported={...form,code:'QA.IMPORT',semesterCode:sem.code,subjectCode:subject.code}
await staff.call('/course-sections/import','POST',{semesterId:sem.id,rows:[imported,{...imported,code:'QA.BAD',subjectCode:'UNKNOWN'}]},400)
assert.ok(!(await staff.call(list)).some(x=>x.code==='QA.IMPORT'))
assert.equal((await staff.call('/course-sections/import','POST',{semesterId:sem.id,rows:[imported]},201)).imported,1)
await staff.call('/course-sections/'+created.id+'/cancel?version=0','POST',{},409)
const cancelKey=randomUUID()
await staff.call('/course-sections/'+created.id+'/cancel?version=1','POST',{},204,cancelKey)
await staff.call('/course-sections/'+created.id+'/cancel?version=1','POST',{},204,cancelKey)
assert.equal((await staff.call(list)).find(x=>x.id===created.id).status,'CANCELLED')
assert.ok((await staff.call('/schedule-patterns'+term+'&page=1&pageSize=10')).items instanceof Array)
console.log('PASS: section CRUD, cross-role visibility, scope, pagination, validation, versioning, import rollback and idempotency')
