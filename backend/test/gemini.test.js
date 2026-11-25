import assert from 'node:assert/strict'
import test from 'node:test'
import { geminiAdvisor } from '../src/integrations/gemini.js'

test('Gemini nhận nội dung yêu cầu và trả về đúng thao tác cho phép',async()=>{
  const advisor=geminiAdvisor({key:'test-key',model:'gemini-test',request:async (url,options)=>{
    assert.equal(url,'https://generativelanguage.googleapis.com/v1beta/models/gemini-test:generateContent')
    assert.equal(options.headers['x-goog-api-key'],'test-key')
    assert.equal(options.redirect,'error')
    const body=JSON.parse(options.body)
    assert.deepEqual(body.contents,[{role:'user',parts:[{text:'Áp dụng phương án'}]}])
    assert.deepEqual(body.generationConfig.responseJsonSchema.properties.action.enum,['GENERATE','APPLY','UNRELATED'])
    return {ok:true,json:async()=>({candidates:[{content:{parts:[{text:'{"action":"APPLY","runId":123}'}]}}]})}
  }})
  assert.equal(await advisor('Áp dụng phương án'),'APPLY')
})

test('dữ liệu bất hợp lệ và lỗi nhà cung cấp trả về lỗi của trợ lý',async()=>{
  for (const request of [async()=>({ok:false}),async()=>({ok:true,json:async()=>({candidates:[]})})]) {
    const advisor=geminiAdvisor({key:'test-key',model:'gemini-test',request})
    await assert.rejects(()=>advisor('Xếp lịch'),{code:'ASSISTANT_UNAVAILABLE',status:502})
  }
  assert.equal(geminiAdvisor({key:'',model:'gemini-test'}),null)
})

test('HUCEChat giới hạn cách phân loại theo vai trò và bỏ phản hồi ngoài danh sách',async()=>{
  const actions=['NEXT','SCHEDULE','UNRELATED']
  const advisor=geminiAdvisor({key:'test-key',model:'gemini-test',request:async(_url,options)=>{
    const body=JSON.parse(options.body)
    assert.deepEqual(body.generationConfig.responseJsonSchema.properties.action.enum,actions)
    assert.equal(body.systemInstruction.parts[0].text,'Chỉ phân loại lịch cá nhân.')
    return {ok:true,json:async()=>({candidates:[{content:{parts:[{text:'{"action":"DRAFT"}'}]}}]})}
  }})
  await assert.rejects(()=>advisor('Tạo lớp',{actions,instruction:'Chỉ phân loại lịch cá nhân.'}),{code:'ASSISTANT_UNAVAILABLE'})
})
