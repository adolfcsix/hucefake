import test from 'node:test'
import assert from 'node:assert/strict'
import { navigation, resolvePage } from '../src/navigation.js'
const expected={STAFF:['sections','planning','automatic','approvals','schedule','requests','catalog','notifications'],MANAGER:['approvals','sections','automatic','schedule','statistics','notifications'],LECTURER:['schedule','availability','requests','notifications'],STUDENT:['overview','schedule','notifications']}
for(const [role,keys] of Object.entries(expected)) test('Menu và kiểm tra route '+role,()=>{
  assert.deepEqual(navigation[role].flatMap(([,pages])=>pages),keys)
  for(const page of keys) assert.equal(resolvePage(role,page),page)
  for(const page of [...new Set(Object.values(expected).flat()),'invalid','']) if(!keys.includes(page)) assert.equal(resolvePage(role,page),role==='STUDENT'?'schedule':keys[0])
})
