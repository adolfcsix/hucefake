import assert from 'node:assert/strict'
import test from 'node:test'
import { Worker } from 'node:worker_threads'

function evaluate(ctx,genes,fixed=[]) {
  const workerData = { ctx, genes, fixed, method:'greedy',
    settings:{ seed:42, population:2, generations:1 } }
  return new Promise((resolve,reject) => {
    const worker = new Worker(new URL('../src/workers/schedule.worker.js',import.meta.url),{workerData})
    worker.on('message',value => { if (value.type === 'complete') resolve(value) })
    worker.on('error',reject)
    worker.on('exit',() => reject(new Error('Worker đã dừng trước khi trả điểm.')))
  })
}

function evaluateExample({ moved, consecutive }) {
  const ctx = { sections:{}, rooms:{}, periods:[], availability:[], patterns:[], holidays:[] }
  for (let period = 1; period <= 6; period++) {
    ctx.periods.push({ campus_id:1, period_no:period, start_time:'0'+(period+1)+':00:00',
      end_time:'0'+(period+1)+':50:00', shift:'MORNING', effective_from:'2026-01-01', effective_to:null })
  }
  for (const [id,capacity] of [[1,40],[2,60],[3,60]]) {
    ctx.rooms[id] = { id, campus_id:1, capacity, status:'ACTIVE', type:'LECTURE' }
  }
  for (const [id,capacity] of [[1,60],[2,55],[3,40],[4,40]]) {
    ctx.sections[id] = { id, staff_id:id, capacity, assistant_ids:[], group_ids:[], semester_id:1 }
  }
  const genes = [
    [1,1,1,2], [1,3,1,2], [2,1,1,moved ? 3 : 2],
    [3,1,4,1], [3,consecutive ? 2 : 3,4,1], [4,2,1,1],
  ].map(([section,day,first,room]) => ({
    domain:[{ id:0, section, staff:section, room, day, first, count:3, from:'2026-09-07', to:'2026-10-04' }]
  }))
  return evaluate(ctx,genes)
}

test('TC03: worker tính đúng chi phí theo ví dụ mục 6.4', async () => {
  const before = await evaluateExample({moved:false,consecutive:false})
  const after = await evaluateExample({moved:true,consecutive:false})
  const consecutive = await evaluateExample({moved:true,consecutive:true})
  for (const [result,hard,cost] of [[before,3,3000.83],[after,0,0.83],[consecutive,0,3.83]]) {
    assert.equal(result.hard,hard)
    assert.equal(Number((1000*result.hard+result.soft).toFixed(2)),cost)
  }
})

test('tính H1–H3 theo số lượt vượt sức chứa của ô tiết, kể cả với lịch đã xếp', async () => {
  const ctx={ rooms:{1:{id:1,campus_id:1,capacity:45}}, sections:{},
    periods:[1,2].map(period_no=>({campus_id:1,period_no,shift:'MORNING',start_time:'0'+(period_no+6)+':00',
      end_time:'0'+(period_no+6)+':50',effective_from:'2026-01-01',effective_to:null})),
    availability:[], patterns:[], holidays:[] }
  const slots=[1,2,3].map(section=>{
    ctx.sections[section]={id:section,staff_id:5,assistant_ids:[],group_ids:[9],capacity:45,semester_id:1}
    return {id:section,section,staff:5,room:1,day:1,first:1,count:2,from:'2026-09-07',to:'2026-10-04'}
  })
  const [planned,...newSlots]=slots
  const result=await evaluate(ctx,newSlots.map(slot=>({domain:[slot]})),[planned])
  assert.equal(result.hard,12) // (3 lượt - 1) × 2 tiết × 3 nhóm ràng buộc
  assert.equal(result.soft,0)

  const later={...slots[2],from:'2026-10-05',to:'2026-11-01'}
  const split=await evaluate(ctx,[{domain:[later]}],[planned])
  assert.equal(split.hard,0)
})
