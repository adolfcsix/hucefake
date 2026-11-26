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
assert.equal(before.length,3,'Fresh demo must have original three draft sections')
assert.ok(before.every(x=>x.status==='DRAFT' && x.staff_id && x.group_ids.length===1))
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

const group=catalog.groups[0], teacher=catalog.lecturers.find(x=>x.code==='GIANGVIEN'), assistant=catalog.lecturers.find(x=>x.code==='GIANGVIEN2')
const lab=catalog.rooms.find(x=>x.code==='M301'), lab2=catalog.rooms.find(x=>x.code==='M302'), lectureRoom=catalog.rooms.find(x=>x.code==='A101')
const a=(await staff.call('/course-sections','POST',{...form,code:'QA.SLOT.A',sessionsPerWeek:2},201)).id
const b=(await staff.call('/course-sections','POST',{...form,code:'QA.SLOT.B'},201)).id
const assignments='/course-sections/'+a+'/assignments'
const groups='/course-sections/'+a+'/target-groups'
await manager.call(assignments,'PUT',{staffId:teacher.id},403)
await staff.call(assignments,'PUT',{staffId:teacher.id,assistantIds:[teacher.id]},400)
await staff.call(assignments,'PUT',{staffId:teacher.id,assistantIds:[assistant.id]},204)
await staff.call(groups,'PUT',{groupIds:[group.id,group.id]},204)
await staff.call('/course-sections/'+b+'/assignments','PUT',{staffId:teacher.id},204)
await staff.call('/course-sections/'+b+'/target-groups','PUT',{groupIds:[group.id]},204)
const slot={sectionId:a,roomId:lab.id,dayOfWeek:1,periodFrom:1}
const check=async f=>staff.call('/schedule/conflicts/check','POST',f)
assert.equal((await check(slot)).valid,true)
assert.ok((await check({...slot,roomId:lectureRoom.id})).issues.some(x=>x.rule==='BR07'))
assert.ok((await check({...slot,periodFrom:6})).issues.some(x=>x.rule==='BR03'))
await other.call('/schedule/conflicts/check','POST',slot,403)
const p=(await staff.call('/schedule-patterns','POST',slot,201)).id
const clash=await check({...slot,sectionId:b})
for(const rule of ['BR04','BR05','BR06'])assert.ok(clash.issues.some(x=>x.rule===rule),rule)
assert.ok(clash.suggestions.length>0)
await staff.call('/schedule-patterns','POST',{...slot,sectionId:b},409)
assert.ok((await check({...slot,roomId:lab2.id,periodFrom:3})).issues.some(x=>x.rule==='BR09'))
await staff.call('/schedule-patterns/'+p,'PATCH',{...slot,periodFrom:2,version:0})
await staff.call('/schedule-patterns/'+p,'PATCH',{...slot,periodFrom:3,version:0},409)
const current=(await manager.call('/schedule-patterns'+term)).find(x=>x.id===p)
assert.equal(current.period_from,2);assert.equal(current.version,1)
const room={campusId:lab.campus_id,code:lab.code,name:lab.name,capacity:1,type:lab.type,status:lab.status}
await staff.call('/catalog/rooms/'+lab.id,'PUT',room,409)
assert.equal((await staff.call('/catalog')).rooms.find(x=>x.id===lab.id).capacity,lab.capacity)
const newRoom={campusId:lab.campus_id,code:'QA.SPRING.ROOM',name:'Phòng kiểm thử',capacity:40,type:'LECTURE',status:'ACTIVE'}
await manager.call('/catalog/rooms','POST',newRoom,403)
const rid=(await staff.call('/catalog/rooms','POST',newRoom,201)).id
await staff.call('/catalog/rooms/'+rid,'PUT',{...newRoom,capacity:50})
assert.equal((await manager.call('/catalog')).rooms.find(x=>x.id===rid).capacity,50)
const holiday={campusId:lab.campus_id,startDate:'2027-01-08',endDate:'2027-01-08',name:'Ngày nghỉ kiểm thử'}
const hid=(await staff.call('/catalog/holidays','POST',holiday,201)).id
await staff.call('/catalog/holidays/'+hid,'PUT',{...holiday,name:'Ngày nghỉ cập nhật'})
assert.equal((await staff.call('/catalog')).holidays.find(x=>x.id===hid).name,'Ngày nghỉ cập nhật')
const period={campusId:lab.campus_id,periodNo:13,shift:'EVENING',startTime:'18:30',endTime:'19:20',effectiveFrom:'2027-01-01'}
await staff.call('/catalog/periods','POST',{...period,startTime:'18:00'},400)
const pid=(await staff.call('/catalog/periods','POST',period,201)).id
assert.equal((await manager.call('/catalog')).periods.find(x=>x.id===pid).start_time,'18:30')
await staff.call('/schedule-patterns/'+p+'?version=0','DELETE',undefined,409)
await staff.call('/schedule-patterns/'+p+'?version=1','DELETE',undefined,204)
assert.ok(!(await staff.call('/schedule-patterns'+term)).some(x=>x.id===p))
console.log('PASS: assignment, target groups, room/teacher/group conflicts, scheduling, catalog mutation, rollback and cross-role refresh')
