import assert from 'node:assert/strict'
const base=process.env.HUCE_TEST_URL
assert.ok(base&&['localhost','127.0.0.1'].includes(new URL(base).hostname))
assert.equal((await fetch(base+'/api/auth/mode').then(r=>r.json())).demo,false)
async function account(role){
 let cookie='',csrf=''
 async function call(path,method='GET',body,expected=200){
  const headers={Cookie:cookie,Accept:'application/json'}
  if(method!=='GET')Object.assign(headers,{'X-CSRF-Token':csrf,'Content-Type':'application/json'})
  const response=await fetch(base+'/api'+path,{method,headers,body:body===undefined?undefined:JSON.stringify(body)})
  cookie=response.headers.getSetCookie()[0]?.split(';')[0]??cookie
  const data=response.status===204?null:await response.json()
  assert.equal(response.status,expected,path+' '+JSON.stringify(data))
  return data
 }
 csrf=(await call('/csrf')).token
 await call('/auth/demo-login','POST',{role},403)
 const u=await call('/auth/login','POST',{username:'private_'+role.toLowerCase(),password:process.env.FM05_SETUP_PASSWORD})
 assert.equal(u.role,role)
 const catalog=await call('/catalog')
 assert.equal(catalog.semesters.length,1)
 assert.equal(catalog.semesters[0].code,'PRIVATE.1')
 return {call,catalog}
}
const staff=await account('STAFF'),manager=await account('MANAGER'),lecturer=await account('LECTURER'),student=await account('STUDENT')
const sem=staff.catalog.semesters[0],term='?semesterId='+sem.id
const rows=await staff.call('/course-sections'+term)
assert.equal(rows.length,1)
assert.equal(rows[0].code,'PRIVATE.CLASS')
assert.equal(rows[0].capacity,30)
assert.equal((await manager.call('/course-sections'+term))[0].id,rows[0].id)
const created=await staff.call('/course-sections','POST',{semesterId:sem.id,subjectId:staff.catalog.subjects[0].id,code:'PRIVATE.AFTER.RESTORE',capacity:32,sessionsPerWeek:1,periodsPerSession:2,requiredRoomType:'LECTURE',startDate:sem.start_date,endDate:sem.end_date},201)
assert.notEqual(created.id,rows[0].id)
assert.ok((await manager.call('/course-sections'+term)).some(r=>r.id===created.id))
assert.deepEqual(await lecturer.call('/schedule'+term),[])
assert.deepEqual(await student.call('/schedule'+term),[])
