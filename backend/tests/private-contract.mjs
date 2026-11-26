import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
const base=process.env.HUCE_TEST_URL
async function account(role){
  let cookie='',csrf=''
  async function call(path,method='GET',body,expected=200){
    const headers={Cookie:cookie}
    if(method!=='GET')Object.assign(headers,{'X-CSRF-Token':csrf,'Content-Type':'application/json'})
    const r=await fetch(base+'/api'+path,{method,headers,body:body===undefined?undefined:JSON.stringify(body)})
    cookie=r.headers.getSetCookie()[0]?.split(';')[0]??cookie
    const data=r.status===204?null:await r.json()
    assert.equal(r.status,expected,path+' '+JSON.stringify(data))
    return data
  }
  assert.equal((await call('/auth/mode')).demo,false)
  csrf=(await call('/csrf')).token
  await call('/auth/demo-login','POST',{role},403)
  const u=await call('/auth/login','POST',{username:'private_'+role.toLowerCase(),password:process.env.FM05_SETUP_PASSWORD})
  assert.equal(u.role,role)
  const catalog=await call('/catalog')
  assert.equal(catalog.semesters.length,1)
  assert.equal(catalog.semesters[0].code,'PRIVATE.1')
  return {call,catalog,u}
}
const staff=await account('STAFF'),manager=await account('MANAGER'),lecturer=await account('LECTURER'),student=await account('STUDENT')
const {semesters,subjects,campuses}=staff.catalog
assert.equal(subjects.length,1);assert.equal(campuses.length,1)
const sem=semesters[0],subject=subjects[0]
const term='?semesterId='+sem.id
assert.deepEqual(await staff.call('/course-sections'+term),[])
const created=await staff.call('/course-sections','POST',{semesterId:sem.id,subjectId:subject.id,code:'PRIVATE.CLASS',capacity:30,sessionsPerWeek:1,periodsPerSession:2,requiredRoomType:'LECTURE',startDate:sem.start_date,endDate:sem.end_date},201)
assert.equal((await manager.call('/course-sections'+term))[0].id,created.id)
assert.deepEqual(await student.call('/schedule'+term),[])
assert.deepEqual(await lecturer.call('/schedule'+term),[])

// CI-only profile revocation on the isolated private database.
assert.ok(process.env.PG_CONTAINER)
const setProfile=status=>execFileSync('docker',['exec',process.env.PG_CONTAINER,'psql','-U','fm05','-d','huce_private_ci','-v','ON_ERROR_STOP=1','-c',"UPDATE staff_profiles SET status='"+status+"' WHERE user_id=(SELECT id FROM users WHERE username='private_staff')"])
setProfile('INACTIVE')
try{
  await staff.call('/me','GET',undefined,401)
  await staff.call('/auth/login','POST',{username:'private_staff',password:process.env.FM05_SETUP_PASSWORD},401)
  await staff.call('/course-sections'+term,'GET',undefined,401)
}finally{setProfile('ACTIVE')}
await staff.call('/me','GET',undefined,401)
await staff.call('/auth/login','POST',{username:'private_staff',password:process.env.FM05_SETUP_PASSWORD})
await staff.call('/me')
const unauth=await fetch(base+'/api/me')
assert.equal(unauth.status,401)
const noCsrf=await fetch(base+'/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'private_staff',password:process.env.FM05_SETUP_PASSWORD})})
assert.equal(noCsrf.status,403)
assert.equal((await noCsrf.json()).code,'CSRF_INVALID')
const csrfRes=await fetch(base+'/api/csrf')
const cookie=csrfRes.headers.getSetCookie()[0].split(';')[0]
const token=(await csrfRes.json()).token
const headers={Cookie:cookie,'X-CSRF-Token':token,'Content-Type':'application/json'}
const bad=JSON.stringify({username:'private_staff',password:'invalid-password'})
for(let i=0;i<10;i++){
 const response=await fetch(base+'/api/auth/login',{method:'POST',headers,body:bad})
 assert.equal(response.status,401)
}
const blocked=await fetch(base+'/api/auth/login',{method:'POST',headers,body:bad})
assert.equal(blocked.status,429)
assert.equal((await blocked.json()).code,'RATE_LIMIT')
console.log('PASS: profile revocation, CSRF enforcement and login throttling on private database')
