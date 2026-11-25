import { parentPort, workerData } from 'node:worker_threads'
import { busy, firstDay, interval, pair, slotStaff } from '../modules/scheduling.js'

const {ctx, genes, fixed, settings}=workerData
const budgetMs=workerData.budgetMs ?? 60_000
const deadline=Date.now()+budgetMs
const softDeadline=deadline-Math.min(15_000,Math.floor(budgetMs/4))
let seed=settings.seed >>> 0
function random() {
  seed=(seed+0x6D2B79F5)>>>0
  let value=Math.imul(seed^(seed>>>15),1|seed)
  value^=value+Math.imul(value^(value>>>7),61|value)
  return ((value^(value>>>14))>>>0)/4294967296
}
const pick = list => list[Math.floor(random()*list.length)]
const sample = i => pick(genes[i].domain)
const compare = (a,b) => a.hard-b.hard || a.soft-b.soft
const clash = (a,b) => a.day!==b.day ? 0 : pair(ctx,a,b).reduce((score,issue) => score+(issue.rule==='BR09' ? 1 :
  Math.max(1,Math.min(a.first+a.count,b.first+b.count)-Math.max(a.first,b.first))),0)
const intrinsic=new Map()
const occupancy = sharedCalendarOccupancy()

function sharedCalendarOccupancy() {
  const reference=genes[0]?.domain[0]
  if (!reference) return null
  const campus=ctx.rooms[reference.room].campus_id
  const matches=slot=>ctx.rooms[slot.room]?.campus_id===campus && slot.from===reference.from && slot.to===reference.to
  const domains=new Set(genes.map(gene=>gene.domain))
  if (!fixed.every(matches) || [...domains].some(domain=>!domain.every(matches))) return null

  let next=0
  const roomIds=new Map(), staffIds=new Map(), groupIds=new Map()
  const resource=(indices,id)=>{
    if (!indices.has(id)) indices.set(id,next++)
    return indices.get(id)
  }
  for (const id of Object.keys(ctx.rooms)) resource(roomIds,Number(id))
  const sections=new Map()
  for (const section of Object.values(ctx.sections)) {
    sections.set(section.id,sections.size)
    for (const staff of [section.staff_id,...(section.assistant_ids || [])]) resource(staffIds,staff)
    for (const group of section.group_ids) resource(groupIds,group)
  }
  for (const slot of fixed) resource(staffIds,slot.staff)
  for (const domain of domains) for (const slot of domain) resource(staffIds,slot.staff)
  const counts=new Uint32Array(next*7*15)
  const sameDay=new Uint32Array(sections.size*7)
  const offsets=new WeakMap()
  function add(slot) {
    if (!offsets.has(slot)) {
      const day=slot.day-1, period=slot.first-1
      const resources=[roomIds.get(slot.room),...slotStaff(ctx,slot).map(id=>staffIds.get(id)),
        ...ctx.sections[slot.section].group_ids.map(id=>groupIds.get(id))]
      offsets.set(slot,{ section:sections.get(slot.section)*7+day,
        starts:resources.map(index=>(index*7+day)*15+period) })
    }
    const entry=offsets.get(slot)
    let hard=sameDay[entry.section]++
    for (const start of entry.starts)
      for (let period=start;period<start+slot.count;period++) if (counts[period]++) hard++
    return hard
  }
  for (const slot of fixed) add(slot)
  const fixedCounts=counts.slice(), fixedDays=sameDay.slice()
  return {add, reset() { counts.set(fixedCounts); sameDay.set(fixedDays) }}
}

function base(slot) {
  if (intrinsic.has(slot)) return intrinsic.get(slot)
  const room=ctx.rooms[slot.room], section=ctx.sections[slot.section]
  const result={
    busy:busy(ctx,slot,'BUSY'),
    fixedHard:fixed.reduce((sum,other)=>sum+clash(slot,other),0),
    soft:5*busy(ctx,slot,'AVOID')+10*(room.capacity-section.capacity)/room.capacity,
    staff:slotStaff(ctx,slot),
    shift:interval(ctx,slot,firstDay(slot.from,slot.day)).shift
  }
  intrinsic.set(slot,result)
  return result
}

function score(slots) {
  let hard=0, soft=0
  const staffDaily=new Map(), groupPeriods=new Map(), sectionDays=new Map()
  const byDay=occupancy ? null : Array.from({length:8},()=>[])
  occupancy?.reset()
  for (let i=0;i<slots.length;i++) {
    const slot=slots[i]
    if (!slot) continue
    const own=base(slot)
    hard+=own.busy
    soft+=own.soft
    for (const staff of own.staff) {
      const staffKey=staff+':'+slot.day
      staffDaily.set(staffKey,(staffDaily.get(staffKey)||0)+slot.count)
    }
    for (const group of ctx.sections[slot.section].group_ids) {
      const key=[group,slot.day,own.shift,slot.from,slot.to].join(':')
      if (!groupPeriods.has(key)) groupPeriods.set(key,new Set())
      for (let p=slot.first;p<slot.first+slot.count;p++) groupPeriods.get(key).add(p)
    }
    if (occupancy) hard+=occupancy.add(slot)
    else {
      hard+=own.fixedHard
      for (const other of byDay[slot.day]) hard+=clash(slot,other)
      byDay[slot.day].push(slot)
    }
    if (!sectionDays.has(slot.section)) sectionDays.set(slot.section,new Uint16Array(9))
    const counts=sectionDays.get(slot.section)
    soft+=3*(counts[slot.day-1]+counts[slot.day+1])
    counts[slot.day]++
  }
  for (const count of staffDaily.values()) soft+=4*Math.max(0,count-8)
  for (const periods of groupPeriods.values()) soft+=2*(Math.max(...periods)-Math.min(...periods)+1-periods.size)
  return {slots,hard,soft}
}

function initial(greedy) {
  if (!greedy) return score(genes.map((_,i)=>sample(i)))
  const order=genes.map((_,i)=>i).sort((a,b)=>genes[a].domain.length-genes[b].domain.length)
  const slots=new Array(genes.length)
  const byDay=Array.from({length:8},()=>[])
  const bySection=new Map()
  for (const i of order) {
    const cost=slot=>{
      const own=base(slot)
      const hard=own.busy+own.fixedHard+byDay[slot.day].reduce((total,other)=>total+clash(slot,other),0)
      const soft=own.soft+3*(bySection.get(slot.section)||[]).filter(day=>Math.abs(day-slot.day)===1).length
      return {hard,soft}
    }
    let best=sample(i), bestCost=cost(best)
    for(let trial=0;trial<32 && Date.now()<deadline;trial++) {
      const candidate=sample(i), candidateCost=cost(candidate)
      if(compare(candidateCost,bestCost)<0) {best=candidate;bestCost=candidateCost}
      if (!bestCost.hard && !bestCost.soft) break
    }
    slots[i]=best
    byDay[best.day].push(best)
    if (!bySection.has(best.section)) bySection.set(best.section,[])
    bySection.get(best.section).push(best.day)
  }
  return score(slots)
}
function tournament(group) {
  return [pick(group),pick(group),pick(group)].sort(compare)[0]
}
function mutate(child) {
  if (workerData.repair===false || !child.hard || random()>=0.3) return child
  const slots=[...child.slots]
  const damaged=slots.map((s,i)=>i).filter(i=>base(slots[i]).busy || base(slots[i]).fixedHard ||
    slots.some((other,j)=>i!==j && clash(slots[i],other)))
  if (!damaged.length) return child
  const i=pick(damaged)
  let best=child
  for(let n=0;n<20 && Date.now()<deadline;n++) {
    slots[i]=sample(i)
    const attempt=score([...slots])
    if (compare(attempt,best)<0) best=attempt
  }
  return best
}
function breed(a,b) {
  const crossover=random()<settings.crossover
  const groups=new Map()
  const slots=a.slots.map((s,i)=>{
    if (!groups.has(s.section)) groups.set(s.section,random()<.5)
    if (random()<settings.mutation) return sample(i)
    return crossover && groups.get(s.section) ? b.slots[i] : s
  })
  return mutate(score(slots))
}

const method=workerData.method || 'ga'
let best, generation=0
if (method==='greedy') {
  best=initial(true)
} else if (method==='random') {
  best=initial(false)
  const attempts=settings.population*settings.generations
  for (let i=1;i<attempts && Date.now()<deadline && (best.hard || best.soft);i++) {
    const candidate=initial(false)
    if (compare(candidate,best)<0) best=candidate
    generation=Math.floor(i/settings.population)
  }
} else {
  let group=[]
  for (let i=0;i<settings.population && Date.now()<deadline;i++)
    group.push(initial(i<settings.population/5))
  if (!group.length) throw new Error('Không còn thời gian tạo quần thể.')
  group.sort(compare)
  best=group[0]
  let stagnant=0
  const emit=()=>parentPort.postMessage({type:'progress',generation,hard:best.hard,soft:best.soft})
  emit()
  while (generation<settings.generations && stagnant<100 && (best.hard || best.soft) && Date.now()<(best.hard ? deadline : softDeadline)) {
    const next=group.slice(0,2)
    while(next.length<settings.population && Date.now()<(best.hard ? deadline : softDeadline))
      next.push(breed(tournament(group),tournament(group)))
    next.sort(compare)
    group=next
    generation++
    if (compare(group[0],best)<0) {best=group[0];stagnant=0} else stagnant++
    if (generation%5===0) emit()
  }
  emit()
}
const notes=best.slots.map((s,i)=>{
  const issues=fixed.flatMap(other=>pair(ctx,s,other))
  for (let j=0;j<best.slots.length;j++) if(i!==j) issues.push(...pair(ctx,s,best.slots[j]))
  if (busy(ctx,s,'BUSY')) issues.push({message:'Giảng viên bận trong khung tiết.'})
  return [...new Set(issues.map(issue=>issue.message))].join(' ').slice(0,1000) || null
})
parentPort.postMessage({type:'complete',...best,generation,notes,timedOut:Date.now()>=deadline})
