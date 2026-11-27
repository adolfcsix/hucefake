import test from 'node:test'
import assert from 'node:assert/strict'

const json = (value, status=200) => new Response(JSON.stringify(value), { status, headers:{'Content-Type':'application/json'} })
const csrf = value => json({ header:'X-CSRF-Token', token:value })

test('CSRF retry preserves mutation idempotency key', async t => {
  const { api } = await import('../src/services/api.js?csrf-retry')
  const calls = []
  const replies = [csrf('old'), json({code:'CSRF_INVALID'},403), csrf('new'), json({id:1})]
  t.mock.method(globalThis,'fetch',async (path, options) => {
    calls.push({path,options})
    return replies.shift()
  })
  assert.deepEqual(await api('/course-sections','POST',{code:'A'}),{id:1})
  assert.deepEqual(calls.map(call=>call.path),['/api/csrf','/api/course-sections','/api/csrf','/api/course-sections'])
  assert.equal(calls[1].options.headers['X-CSRF-Token'],'old')
  assert.equal(calls[3].options.headers['X-CSRF-Token'],'new')
  assert.match(calls[1].options.headers['Idempotency-Key'],/^[a-f0-9]{32}$/)
  assert.equal(calls[1].options.headers['Idempotency-Key'],calls[3].options.headers['Idempotency-Key'])
})

test('server failure keeps mutation key for retry; success clears it', async t => {
  const { api } = await import('../src/services/api.js?server-retry')
  const keys = []
  const replies = [json({code:'TEMPORARY'},503),json({id:1}),json({id:2})]
  t.mock.method(globalThis,'fetch',async (path, options) => {
    if(path==='/api/csrf') return csrf('token')
    keys.push(options.headers['Idempotency-Key'])
    return replies.shift()
  })
  await assert.rejects(api('/course-sections','POST',{code:'A'}),{status:503})
  await api('/course-sections','POST',{code:'A'})
  await api('/course-sections','POST',{code:'A'})
  assert.equal(keys[0],keys[1])
  assert.notEqual(keys[1],keys[2])
})

test('client validation failure clears key for a fresh attempt', async t => {
  const { api } = await import('../src/services/api.js?client-error')
  const keys = []
  t.mock.method(globalThis,'fetch',async (path, options) => {
    if(path==='/api/csrf') return csrf('token')
    keys.push(options.headers['Idempotency-Key'])
    return keys.length===1 ? json({code:'VALIDATION_ERROR'},400) : json({id:1})
  })
  await assert.rejects(api('/course-sections','POST',{code:'A'}),{status:400,code:'VALIDATION_ERROR'})
  await api('/course-sections','POST',{code:'A'})
  assert.notEqual(keys[0],keys[1])
})

test('conflict preview uses CSRF without mutation idempotency key', async t => {
  const { checkScheduleConflicts } = await import('../src/services/api.js?preview')
  t.mock.method(globalThis,'fetch',async (path,options) => {
    if(path==='/api/csrf') return csrf('token')
    assert.equal(path,'/api/schedule/conflicts/check')
    assert.equal(options.method,'POST')
    assert.equal(options.headers['X-CSRF-Token'],'token')
    assert.equal(options.headers['Idempotency-Key'],undefined)
    return json({valid:true})
  })
  assert.deepEqual(await checkScheduleConflicts({sectionId:1}),{valid:true})
})

test('successful logout clears CSRF cache', async t => {
  const { api } = await import('../src/services/api.js?logout')
  let tokens=0
  t.mock.method(globalThis,'fetch',async (path,options) => {
    if(path==='/api/csrf') return csrf(String(++tokens))
    if(path==='/api/auth/logout') return new Response(null,{status:204})
    assert.equal(options.headers['X-CSRF-Token'],'2')
    return json({id:1})
  })
  await api('/auth/logout','POST')
  await api('/course-sections','POST',{code:'A'})
  assert.equal(tokens,2)
})
