import assert from 'node:assert/strict'
import test from 'node:test'
import { once } from '../src/middleware/idempotency.js'

function database() {
  const records = new Map()
  return {
    async query(sql, params) {
      if (sql.startsWith('SELECT')) return { rows:[records.get(params.slice(0,2).join(':'))].filter(Boolean) }
      if (sql.startsWith('INSERT')) {
        const [actor,key,fingerprint,response] = params
        records.set(actor+':'+key,{ fingerprint,response:JSON.parse(response) })
      }
      return { rows:[] }
    },
  }
}
const request=(body, options={})=>({
  method:'POST',baseUrl:'/api',path:'/course-sections',query:{},
  actor:{id:options.actor??1,role:'STAFF',unitId:3,staffId:4},
  get:()=>options.key??'sample-key',
  body,
})

test('cùng nghiệp vụ và dữ liệu trả kết quả cũ dù đổi thứ tự thuộc tính',async()=>{
  const db=database()
  let calls=0
  const write=async()=>({id:++calls})
  const first=await once(db,request({capacity:35,code:'A'}),write)
  assert.deepEqual(await once(db,request({code:'A',capacity:35}),write),first)
  assert.equal(calls,1)
})

test('từ chối dùng lại mã cho dữ liệu khác và tách mã theo tài khoản',async()=>{
  const db=database()
  const write=async()=>({id:1})
  await once(db,request({code:'A'}),write)
  await assert.rejects(once(db,request({code:'B'}),write),{code:'DUPLICATE_REQUEST'})
  assert.deepEqual(await once(db,request({code:'B'},{actor:2}),async()=>({id:2})),{id:2})
  await assert.rejects(once(db,request({},{key:'bad key'}),write),{code:'VALIDATION_ERROR'})
})

test('giữ kết quả thành công không có nội dung, cho phép sửa lỗi rồi gửi lại',async()=>{
  const db=database()
  const failed=request({code:'X'})
  await assert.rejects(once(db,failed,async()=>{throw new Error('invalid')}),/invalid/)
  const corrected=request({code:'Y'})
  let calls=0
  const write=async()=>{calls++}
  await once(db,corrected,write)
  await once(db,corrected,write)
  assert.equal(calls,1)
})
