import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { setTimeout as delay } from 'node:timers/promises'

const origin=process.env.FM05_TEST_URL
if (!origin || process.argv[2]!=='--disposable-demo') {
  throw new Error('Indicate a fresh disposable demo with FM05_TEST_URL and --disposable-demo.')
}
const base=new URL(origin)
assert.ok(['127.0.0.1','localhost','[::1]'].includes(base.hostname))
const route=path=>new URL('/api'+path,base)
const mode=await fetch(route('/auth/mode')).then(response=>response.json())
assert.equal(mode.demo,true)
const demoUsernames={STAFF:'daotao',MANAGER:'quanly',LECTURER:'giangvien',STUDENT:'sinhvien'}
const manualLogin=process.argv.includes('--without-demo-endpoint')

async function account(role,username) {
  let cookie='', csrf=''
  async function send(path,method='GET',body,key=randomUUID()) {
    const headers={Cookie:cookie}
    if (method!=='GET') Object.assign(headers,{'X-CSRF-Token':csrf,'Idempotency-Key':key,'Content-Type':'application/json'})
    const response=await fetch(route(path),{method,headers,body:body===undefined?undefined:JSON.stringify(body)})
    cookie=response.headers.getSetCookie()[0]?.split(';')[0] ?? cookie
    const data=response.status===204 ? null : await response.json()
    return {status:response.status,data}
  }
  async function call(path,method='GET',body,expected=200,key) {
    const result=await send(path,method,body,key)
    assert.equal(result.status,expected,path+' '+JSON.stringify(result.data))
    return result.data
  }
  csrf=(await call('/csrf')).token
  const name=username ?? (manualLogin ? demoUsernames[role] : null)
  const user=await call(name ? '/auth/login' : '/auth/demo-login','POST',
    name ? {username:name,password:'Fm05Demo@2026'} : {role})
  assert.equal(user.role,role)
  return {call,send,user}
}
const staff=await account('STAFF')
const otherStaff=await account('STAFF','daotao2')
const manager=await account('MANAGER')
const lecturer=await account('LECTURER')
const otherLecturer=await account('LECTURER','giangvien2')
const student=await account('STUDENT')
const catalog=await staff.call('/catalog')
const semester=catalog.semesters[0], semesterId=semester.id
const term='?semesterId='+semesterId
const chat=(client,message,extra={},key)=>client.call('/chat/messages','POST',{message,semesterId,...extra},201,key)
const proposal=async (client,message,extra)=> (await chat(client,message,extra)).exchange.reply.proposal
const handle=(client,id,action='confirm',expected=200)=>client.call('/chat/proposals/'+id+'/'+action,'POST',{},expected)
const noticeRows=client=>client.call('/notifications?'+new URLSearchParams({page:1,pageSize:100})).then(({items})=>items)
const sectionRows=client=>client.call('/course-sections'+term)
const sessionRows=(client,view='personal')=>client.call('/schedule'+term+'&view='+view)
const sample=await sectionRows(staff)
assert.equal(sample.length,3,'Start with a fresh demo database')
assert.ok(sample.every(row=>row.status==='DRAFT'))
assert.equal((await sessionRows(student)).length,0)
const readiness=await staff.call('/timetable-versions/readiness'+term)
assert.equal(readiness.ready,false)
assert.equal(readiness.blockedSections.length,3)

const campusId=catalog.campuses[0].id
const room={campusId,code:'QA01',name:'Phòng kiểm thử',capacity:30,type:'LECTURE',status:'INACTIVE'}
await manager.call('/catalog/rooms','POST',room,403)
const roomId=(await staff.call('/catalog/rooms','POST',room,201)).id
await staff.call('/catalog/rooms/'+roomId,'PUT',{...room,capacity:42})
const holiday={campusId,startDate:'2027-01-08',endDate:'2027-01-08',name:'Ngày nghỉ kiểm thử'}
const holidayId=(await staff.call('/catalog/holidays','POST',holiday,201)).id
await staff.call('/catalog/holidays/'+holidayId,'PUT',{...holiday,name:'Ngày nghỉ đã cập nhật'})
const latePeriod={campusId,periodNo:13,shift:'EVENING',startTime:'18:30',endTime:'19:20',effectiveFrom:'2027-01-01'}
await staff.call('/catalog/periods','POST',{...latePeriod,startTime:'18:00'},400)
const periodId=(await staff.call('/catalog/periods','POST',latePeriod,201)).id
const updated=await staff.call('/catalog')
assert.equal(updated.rooms.find(row=>row.id===roomId).capacity,42)
assert.equal(updated.holidays.find(row=>row.id===holidayId).name,'Ngày nghỉ đã cập nhật')
assert.equal(updated.periods.find(row=>row.id===periodId).start_time,'18:30')

const draftStart=await chat(staff,'Tạo lớp học phần')
assert.ok(draftStart.exchange.reply.draft.subjects.some(row=>row.code==='CSDL'))
assert.ok(draftStart.exchange.reply.draft.subjects.every(row=>row.code!=='KTVM'))
const form={subjectId:catalog.subjects.find(row=>row.code==='CSDL').id,code:'CSDL.QA',capacity:30,sessionsPerWeek:1,
  periodsPerSession:2,requiredRoomType:'COMPUTER_LAB',startDate:semester.start_date,endDate:semester.end_date}
const imported={...form,code:'CSDL.IMPORT',subjectCode:'CSDL',semesterCode:semester.code}
await staff.call('/course-sections/import','POST',{semesterId,rows:[imported,{...imported,subjectCode:'UNKNOWN'}]},400)
assert.equal((await sectionRows(staff)).length,3)
assert.equal((await staff.call('/course-sections/import','POST',{semesterId,rows:[imported]},201)).imported,1)
const importedRow=(await sectionRows(staff)).find(row=>row.code===imported.code)
await staff.call('/course-sections/'+importedRow.id+'/cancel?version='+importedRow.version,'POST',{},204)
assert.ok(!(await chat(manager,'Tạo lớp học phần')).exchange.reply.draft)
await student.call('/chat/messages','POST',{message:'Tạo lớp',semesterId,draft:form},403)
await student.call('/schedule-assistant/proposals/1/confirm','POST',{},403)
await otherStaff.call('/chat/messages','POST',{message:'Tạo lớp',semesterId,draft:form},403)
await student.call('/chat/conversations/'+draftStart.conversationId,'GET',undefined,404)
await student.call('/chat/messages','POST',{message:'Tiếp tục',semesterId,conversationId:draftStart.conversationId},404)
const rejected=await proposal(staff,'Xem trước lớp nháp',{draft:{...form,code:'CSDL.HUY'}})
await handle(manager,rejected.id,'confirm',403)
await handle(staff,rejected.id,'cancel')
await handle(staff,rejected.id,'confirm',409)
assert.ok(!(await sectionRows(staff)).some(row=>row.code==='CSDL.HUY'))

const key=randomUUID(), draftParams={draft:form,conversationId:draftStart.conversationId}
const preview=await chat(staff,'Xem trước lớp nháp',draftParams,key)
assert.deepEqual(await chat(staff,'Xem trước lớp nháp',draftParams,key),preview)
const draftId=preview.exchange.reply.proposal.id
assert.equal((await sectionRows(staff)).filter(row=>row.status==='DRAFT').length,3)
const [created,replay]=await Promise.all([handle(staff,draftId),handle(staff,draftId)])
assert.deepEqual(created,replay)
assert.equal((await sectionRows(staff)).filter(row=>row.code===form.code).length,1)
assert.equal((await chat(otherStaff,'Tra lớp học phần mã CSDL.QA')).exchange.reply.total,0)
const stored=await staff.call('/chat/conversations/'+draftStart.conversationId)
assert.equal(stored.exchanges.length,2)
assert.equal(stored.exchanges.at(-1).reply.proposal.outcome,'CONFIRMED')
assert.ok((await staff.call('/chat/conversations?q='+encodeURIComponent('lớp học phần'))).some(row=>row.id===stored.id))
await staff.call('/course-sections/'+created.id+'/assignments','PUT',{staffId:lecturer.user.staffId},204)
await lecturer.call('/me/availability','PUT',{semesterId,entries:[{dayOfWeek:7,periodFrom:14,periodTo:15,kind:'BUSY',reason:'Kiểm thử'}]},204)
assert.equal((await chat(lecturer,'Thời gian bận')).exchange.reply.total,1)
const lab=catalog.rooms.find(row=>row.code==='M301')
const slot={sectionId:created.id,roomId:lab.id,dayOfWeek:1,periodFrom:1}
assert.equal((await staff.call('/schedule/conflicts/check','POST',slot)).valid,true)
await staff.call('/schedule-patterns','POST',slot,201)
const wrongScope=await otherStaff.send('/schedule/conflicts/check','POST',slot)
assert.equal(wrongScope.status,403)
const staleForm=await proposal(staff,'Xem trước một lớp',{draft:{...form,code:'CSDL.TRUNG'}})
const repeated={...form,code:'CSDL.TRUNG',semesterId}
const competing=await staff.call('/course-sections','POST',repeated,201)
await handle(staff,staleForm.id,'confirm',409)
await staff.call('/course-sections/'+competing.id+'/cancel?version=0','POST',{},204)

const run=await handle(staff,(await proposal(staff,'Chạy xếp lịch tự động')).id)
let details
for (let i=0;i<80;i++) {
  details=await staff.call('/schedule-runs/'+run.id)
  if (details.status==='COMPLETED' || details.status==='FAILED') break
  await delay(400)
}
assert.equal(details.status,'COMPLETED',JSON.stringify(details))
assert.equal(details.hard_violations,0)
const apply=await proposal(staff,'Áp dụng phương án #'+run.id)
assert.equal((await handle(staff,apply.id)).status,'APPLIED')
assert.equal((await staff.call('/timetable-versions/readiness'+term)).ready,true)

const version=await staff.call('/timetable-versions','POST',{semesterId},201)
const submitted=await staff.call('/timetable-versions/'+version.id+'/submit','POST',{})
assert.ok((await noticeRows(manager)).some(row=>row.message.includes('đang chờ duyệt')))
assert.match((await chat(manager,'Hồ sơ chờ duyệt')).exchange.reply.items[0].detail,/Chờ duyệt/)
await staff.call('/approvals/'+submitted.approvalId+'/publish','POST',{},403)
await staff.call('/course-sections','POST',{...form,code:'CSDL.KHOA',semesterId},409)
await manager.call('/approvals/'+submitted.approvalId+'/decide','POST',{decision:'RETURNED',reason:'Đối chiếu bản xếp'})
assert.ok((await noticeRows(staff)).some(row=>row.message.includes('được trả lại')))
const resubmission=await staff.call('/timetable-versions','POST',{semesterId},201)
const {approvalId}=await staff.call('/timetable-versions/'+resubmission.id+'/submit','POST',{})
assert.equal((await sessionRows(student)).length,0)
const published=await manager.call('/approvals/'+approvalId+'/publish','POST',{})
assert.equal(published.sections,4)
assert.equal((await chat(manager,'Hồ sơ chờ duyệt')).exchange.reply.total,0)
assert.match((await chat(manager,'Hồ sơ duyệt lịch')).exchange.reply.items[0].detail,/Đã công bố/)
assert.deepEqual(await manager.call('/approvals/'+approvalId+'/publish','POST',{}),published)
assert.ok((await chat(manager,'Thống kê sử dụng phòng')).exchange.reply.total>0)
const lessons=await sessionRows(student)
assert.ok(lessons.length>0 && lessons.every(row=>['CSDL.01','KTPM.01'].includes(row.section_code)))
assert.ok((await sessionRows(lecturer)).some(row=>row.section_code==='CSDL.QA'))
assert.ok((await sessionRows(otherLecturer)).every(row=>row.section_code==='MANG.01'))
assert.ok((await noticeRows(student)).some(row=>row.message.includes('Đã công bố lịch lớp')))
const classDay=lessons.find(row=>row.section_code==='CSDL.01' && row.status==='PLANNED')
const date=classDay.start_at.slice(0,10)
const conversation=await chat(student,'Lịch học '+date)
assert.ok(conversation.exchange.reply.items.some(row=>row.detail.includes('CSDL.01')))
const followup=await chat(student,date,{conversationId:conversation.conversationId})
assert.equal(followup.exchange.reply.intent,'SCHEDULE')
await student.call('/schedule'+term+'&view=all','GET',undefined,403)

const request=await lecturer.call('/class-sessions/'+classDay.id+'/requests','POST',{action:'RESCHEDULE',reason:'Đề nghị học bù'},201)
await otherLecturer.call('/class-sessions/'+classDay.id+'/requests','POST',{action:'RESCHEDULE',reason:'Không đúng phân công'},403)
assert.ok((await staff.call('/session-change-requests'+term)).some(row=>row.id===request.id))
assert.ok(!(await otherStaff.call('/session-change-requests'+term)).some(row=>row.id===request.id))
assert.ok((await chat(lecturer,'Đề nghị đổi lịch')).exchange.reply.items.some(row=>row.detail.includes('Chờ xử lý')))
const decision='/session-change-requests/'+request.id+'/decide'
const change={action:'RESCHEDULE',reason:'Học bù',roomId:lab.id,periodFrom:1}
await otherStaff.call(decision,'POST',{...change,date},403)
await staff.call(decision,'POST',{...change,date:'2026-09-02'},400)
await staff.call(decision,'POST',{...change,date:'2027-01-01'},400)
assert.equal((await staff.call('/session-change-requests'+term)).find(row=>row.id===request.id).status,'PENDING')
let replaced=false
for (const periodFrom of [1,3,5,7,9,11]) {
  const response=await staff.send(decision,'POST',{...change,date:'2026-09-16',periodFrom})
  if (response.status===409) continue
  assert.equal(response.status,204,JSON.stringify(response))
  replaced=true
  break
}
assert.equal(replaced,true)
const revised=await sessionRows(student)
assert.equal(revised.find(row=>row.id===classDay.id).status,'CANCELLED')
assert.ok(revised.some(row=>row.source_session_id===classDay.id))
assert.ok((await sessionRows(lecturer)).some(row=>row.source_session_id===classDay.id))
assert.ok((await noticeRows(student)).some(row=>row.message.includes('thay đổi lịch')))
assert.ok((await noticeRows(lecturer)).some(row=>row.message.includes('đề nghị') || row.message.includes('Đề nghị')))

const unread=await proposal(student,'Đánh dấu tất cả thông báo đã đọc')
assert.ok(unread.preview.count>0)
await handle(lecturer,unread.id,'confirm',403)
assert.equal((await handle(student,unread.id)).marked,unread.preview.count)
assert.ok((await noticeRows(student)).every(row=>row.read_at))
assert.ok((await noticeRows(lecturer)).some(row=>!row.read_at))
console.log('PASS: chat draft/cancel/idempotency/scope, staff prepare, manager publish, lecturer request, staff reschedule, student calendar/notifications, and chat history.')
