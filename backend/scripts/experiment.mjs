import { parseArgs } from 'node:util'
import { Worker } from 'node:worker_threads'
import { performance } from 'node:perf_hooks'
import { settings } from '../src/modules/runs.js'
import { makeSlot } from '../src/modules/scheduling.js'

const sizes={
  small:{classes:20,sessions:35,rooms:8},
  medium:{classes:100,sessions:180,rooms:25},
  large:{classes:300,sessions:550,rooms:40},
}
const methods=['greedy','random','ga']
const {values}=parseArgs({options:{
  size:{type:'string',default:'small'}, methods:{type:'string',default:methods.join(',')},
  trials:{type:'string',default:'10'}, population:{type:'string',default:'100'},
  generations:{type:'string',default:'60'}, mutation:{type:'string',default:'0.02'},
  repair:{type:'string',default:'on'}, budgetMs:{type:'string',default:'60000'},
  json:{type:'boolean',default:false}, sweep:{type:'boolean',default:false},
}})
const size=sizes[values.size], chosen=values.methods.split(','), trials=Number(values.trials)
const budgetMs=Number(values.budgetMs)
if (!size || !chosen.length || chosen.some(method=>!methods.includes(method)) ||
    !Number.isInteger(trials) || trials<1 || trials>10 ||
    !['on','off'].includes(values.repair) || !Number.isInteger(budgetMs) || budgetMs<200 || budgetMs>60_000)
  throw new Error('Tham số thử nghiệm không hợp lệ.')
const parameters=settings({population:Number(values.population),generations:Number(values.generations),mutation:Number(values.mutation),seed:1})

function prepare(size) {
  const ctx={sections:{},rooms:{},periods:[],patterns:[],holidays:[],availability:[]}
  for (let no=1;no<=12;no++) {
    const minutes=(no-1)*55+420
    const clock=min=>String(Math.floor(min/60)).padStart(2,'0')+':'+String(min%60).padStart(2,'0')+':00'
    ctx.periods.push({campus_id:1,period_no:no,start_time:clock(minutes),end_time:clock(minutes+50),
      shift:no<=6?'MORNING':'AFTERNOON',effective_from:'2026-01-01',effective_to:null})
  }
  for (let id=1;id<=size.rooms;id++) {
    ctx.rooms[id]={id,campus_id:1,code:'P'+id,status:'ACTIVE',type:'LECTURE',capacity:[40,50,60][id%3]}
  }
  const genes=[]
  const lecturerCount=Math.ceil(size.classes/3)
  for (let id=1;id<=size.classes;id++) {
    const staffId=(id-1)%lecturerCount+1
    const section={id,code:'LH'+id,staff_id:staffId,assistant_ids:[],semester_id:1,
      group_ids:[Math.ceil(id/5)],capacity:[35,40,45][id%3], required_room_type:'LECTURE',
      periods_per_session:2,start_date:'2026-08-31',end_date:'2026-12-19'}
    ctx.sections[id]=section
    const domain=[]
    for (const room of Object.values(ctx.rooms)) if (room.capacity>=section.capacity)
      for (let day=1;day<=6;day++) for (const period of [1,2,3,4,5,7,8,9,10,11]) domain.push(makeSlot(section,room.id,day,period))
    const count=id<=size.sessions-size.classes ? 2 : 1
    for (let i=0;i<count;i++) genes.push({domain})
  }
  for (let id=1;id<=lecturerCount;id++) ctx.availability.push({staff_id:id,semester_id:1,day_of_week:id%6+1,kind:'BUSY',period_from:1,period_to:2})
  return {ctx,genes,fixed:[]}
}
function run(data,configuration,seed) {
  return new Promise((resolve,reject)=>{
    const started=performance.now()
    const {method,repair,population,mutation}=configuration
    const worker=new Worker(new URL('../src/workers/schedule.worker.js',import.meta.url),
      {workerData:{...data,method,repair,budgetMs,settings:{...parameters,population,mutation,seed}}})
    worker.on('message',value=>{
      if (value.type==='complete') resolve({method,seed,hard:value.hard,soft:value.soft,
        seconds:Number(((performance.now()-started)/1000).toFixed(2)),generation:value.generation,timedOut:value.timedOut})
    })
    worker.on('error',reject)
    worker.on('exit',code=>{if(code) reject(new Error('Worker exited with code '+code))})
  })
}

const input=prepare(size)
const configurations=values.sweep ? [50,100,200].flatMap(population => [.01,.02,.05].flatMap(mutation =>
  [true,false].map(repair=>({method:'ga',population,mutation,repair})))) :
  chosen.map(method=>({method,population:parameters.population,mutation:parameters.mutation,repair:values.repair==='on'}))
const results=[]
for (const configuration of configurations) {
  const rows=[]
  for (let seed=1;seed<=trials;seed++) rows.push(await run(input,configuration,seed))
  results.push({...configuration,rows})
}
const summary=results.map(({rows,...configuration})=>{
  const mean=key=>rows.reduce((sum,row)=>sum+row[key],0)/rows.length
  const soft=mean('soft')
  return {...configuration,feasible:rows.filter(row=>!row.hard).length+'/'+rows.length,
    withinBudget:rows.filter(row=>!row.timedOut&&!row.hard).length+'/'+rows.length,
    averageSoft:soft.toFixed(2),softStdDev:Math.sqrt(rows.reduce((sum,row)=>sum+(row.soft-soft)**2,0)/rows.length).toFixed(2),
    averageSeconds:mean('seconds').toFixed(2),averageGenerations:mean('generation').toFixed(1)}
})
if (values.json) console.log(JSON.stringify({size:values.size,trials,budgetMs,generations:parameters.generations,summary,results},null,2))
else {
  for (const {rows,...configuration} of results) {console.log(configuration);console.table(rows)}
  console.table(summary)
}
