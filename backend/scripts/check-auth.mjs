// Run against a running FM05 instance. No dependency beyond Node.js 22+.
import assert from 'node:assert/strict'
const base=(process.env.FM05_TEST_URL||'http://127.0.0.1:8080').replace(/\/$/,'')
const demo=!process.argv.includes('private')
const quickLogin=!process.argv.includes('--without-demo-endpoint')
const accounts={STAFF:'daotao',MANAGER:'quanly',LECTURER:'giangvien',STUDENT:'sinhvien'}
function client(){
  let cookie='', csrf=''
  return async(path,method='GET',body)=>{
    const headers={Accept:'application/json',Cookie:cookie}
    if(method!=='GET'){ headers['X-CSRF-Token']=csrf; headers['Content-Type']='application/json' }
    const res=await fetch(base+'/api'+path,{method,headers,body:body===undefined?undefined:JSON.stringify(body)})
    const setCookie=res.headers.get('set-cookie'); if(setCookie) cookie=setCookie.split(';')[0]
    const data=res.status===204?null:await res.json()
    if(path==='/csrf') csrf=data.token
    return {status:res.status,data}
  }
}
const anonymous=client()
assert.deepEqual(await anonymous('/auth/mode'),{status:200,data:{demo}})
assert.equal((await anonymous('/me')).status,401)
assert.equal((await anonymous('/auth/demo-login','POST',{role:'STAFF'})).status,403)
await anonymous('/csrf')
if(demo){
  assert.equal((await anonymous('/auth/demo-login','POST',{role:'ADMIN'})).status,quickLogin?400:401)
  for(const [role,username] of Object.entries(accounts)){
    const call=client(); await call('/csrf')
    const login=quickLogin ? await call('/auth/demo-login','POST',{role}) :
      await call('/auth/login','POST',{username,password:'Fm05Demo@2026'})
    assert.equal(login.status,200); assert.equal(login.data.role,role); assert.equal(login.data.username,username)
    assert.deepEqual(await call('/me'),login)
    const catalog=await call('/catalog'); assert.equal(catalog.status,200)
    const semester=catalog.data.semesters[0].id
    assert.equal((await call('/course-sections?semesterId='+semester)).status,['STAFF','MANAGER'].includes(role)?200:403)
    assert.equal((await call('/schedule/statistics?semesterId='+semester)).status,role==='MANAGER'?200:403)
    assert.equal((await call('/me/availability?semesterId='+semester)).status,role==='LECTURER'?200:403)
    assert.equal((await call('/auth/logout','POST')).status,204)
    assert.equal((await call('/me')).status,401)
    await call('/csrf')
    assert.equal((await call('/auth/login','POST',{username,password:'incorrect'})).status,401)
    const manual=await call('/auth/login','POST',{username,password:'Fm05Demo@2026'})
    assert.equal(manual.status,200); assert.equal(manual.data.role,role)
    assert.equal((await call('/auth/logout','POST')).status,204)
    console.log('PASS '+role+' · '+(quickLogin?'quick login, ':'')+'manual login, access control, logout')
  }
}else{
  for(const role of Object.keys(accounts)){
    const blocked=await anonymous('/auth/demo-login','POST',{role})
    assert.equal(blocked.status,quickLogin?403:401)
    assert.equal(blocked.data.code,quickLogin?'DEMO_DISABLED':'UNAUTHORIZED')
    // Fresh private database: sample accounts must not exist.
    assert.equal((await anonymous('/auth/login','POST',{username:accounts[role],password:'Fm05Demo@2026'})).status,401)
  }
  // Optional custom account for an initialized private database.
  if(process.env.FM05_TEST_USERNAME && process.env.FM05_TEST_PASSWORD){
    const result=await anonymous('/auth/login','POST',{username:process.env.FM05_TEST_USERNAME,password:process.env.FM05_TEST_PASSWORD})
    assert.equal(result.status,200); assert.equal(result.data.role,process.env.FM05_TEST_ROLE)
    assert.equal((await anonymous('/auth/logout','POST')).status,204)
  }
  console.log('PASS private mode · demo disabled, sample users rejected')
}
