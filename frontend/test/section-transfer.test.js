import assert from 'node:assert/strict'
import test from 'node:test'
import { decodeDraftFile, encodeDraftCsv, encodeDraftJson } from '../src/features/sections/SectionTransfer.js'

const draft={semesterCode:'2026-2027.1',subjectCode:'KTPM',code:'=Mã "lớp",01',capacity:40,
  sessionsPerWeek:1,periodsPerSession:2,requiredRoomType:'LECTURE',startDate:'2026-09-01',endDate:'2026-12-01'}

test('CSV bảo toàn Unicode, giá trị có dấu phẩy và bảo vệ công thức',()=>{
  const csv=encodeDraftCsv([draft])
  assert.ok(csv.includes('"\'=Mã ""lớp"",01"'))
  assert.equal(decodeDraftFile('LOP.CSV',csv)[0].code,draft.code)
  const simple=encodeDraftCsv([{...draft,code:'KTPM.01'}]).replaceAll(',',';')
  assert.equal(decodeDraftFile('LOP.csv',simple)[0].code,'KTPM.01')
})

test('JSON có phiên bản và từ chối cấu trúc CSV lỗi',()=>{
  assert.deepEqual(decodeDraftFile('LOP.json',encodeDraftJson([draft])),[draft])
  assert.throws(()=>decodeDraftFile('LOP.json','{"format":"fm05-section-drafts","version":2}'),/phiên bản/)
  const header=encodeDraftCsv([]).trimEnd()
  assert.throws(()=>decodeDraftFile('LOP.csv',header+'\r\n"chưa đóng'),/ngoặc kép/)
})
