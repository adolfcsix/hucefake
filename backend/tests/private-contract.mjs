import assert from 'node:assert/strict'
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
