import { date, invalid } from '../middleware/errors.js'
import { addDays, weekday } from './scheduling.js'

const normalization = value => value.toLowerCase().normalize('NFD').replace(/\p{M}/gu,'').replace(/đ/g,'d')
const rules = [
  ['READ_NOTICES', /danh dau.*(?:da doc|thong bao)|doc het.*thong bao/],
  ['NOTICES', /thong bao|tin moi/],
  ['APPLY', /ap dung.*(?:phuong an|lan chay|de xuat)/],
  ['GENERATE', /(?:xep|tao|chay).*lich.*(?:tu dong|con thieu)|chay xep lich/],
  ['DRAFT', /(?:tao|them|lap|mo).*(?:lop nhap|lop hoc phan)/],
  ['REQUESTS', /(?:de nghi|yeu cau).*(?:doi lich|doi buoi)|xin doi lich/],
  ['APPROVALS', /duyet.*(?:lich|ho so)|cong bo.*(?:lich|phuong an)|phe duyet|ho so cho duyet/],
  ['AVAILABILITY', /thoi gian ban|gio ban|lich ban/],
  ['STATS', /thong ke|su dung phong|bao nhieu buoi.*phong/],
  ['NEXT', /(?:buoi|tiet|mon).*(?:tiep theo|sap toi|ke tiep|gan nhat)|(?:tiep theo|sap toi).*(?:buoi|tiet|mon)/],
  ['SCHEDULE', /thoi khoa bieu|lich hoc|lich day|lich giang|lich cua toi|lich (?:hom nay|ngay mai|tuan nay|tuan sau|thu )|co hoc khong|co (?:mon|tiet|buoi hoc)|hoc gi|day gi/],
  ['SECTIONS', /lop hoc phan|lop nhap|danh sach lop|tra lop/],
]

const common=['NEXT','SCHEDULE','NOTICES','READ_NOTICES','GUIDE']
const permissions={
  STAFF:[...common,'SECTIONS','APPROVALS','REQUESTS','GENERATE','APPLY','DRAFT'],
  MANAGER:[...common,'SECTIONS','APPROVALS','STATS','GENERATE','APPLY'],
  LECTURER:[...common,'REQUESTS','AVAILABILITY'],
  STUDENT:common,
}
export const allowedIntents = role => permissions[role] || []

const scheduleActions=['SCHEDULE','NEXT']
export function recognize(message, previous) {
  const query=normalization(message)
  if (/^(?:xin )?chao[!. ]*$|^huong dan$|^ban (?:co the|lam duoc)|^tro giup/.test(query)) return 'GUIDE'
  const match=rules.find(([, pattern])=>pattern.test(query))?.[0]
  if (/^(?:lam sao|cach |huong dan)/.test(query) && ['READ_NOTICES','APPLY','GENERATE','DRAFT','APPROVALS','REQUESTS','AVAILABILITY'].includes(match))
    return 'GUIDE'
  if (match) return match
  if (scheduleActions.includes(previous) && /^(?:con |the con )?(?:hom nay|hom qua|ngay mai|tuan nay|tuan sau|tuan truoc|thu (?:hai|ba|tu|nam|sau|bay|[2-7])|chu nhat|ngay \d|\d{4}-\d{2}-\d{2})/.test(query))
    return 'SCHEDULE'
  return null
}

export function scheduleWindow(message, action, now, semester) {
  const query=normalization(message)
  const explicit=[...query.matchAll(/\b(\d{4}-\d{2}-\d{2}|\d{1,2}\/\d{1,2}(?:\/\d{4})?)\b/g)].map(([value])=>{
    if (value.includes('-')) return date(value)
    const [day,month,year=now.slice(0,4)]=value.split('/')
    return date(year+'-'+month.padStart(2,'0')+'-'+day.padStart(2,'0'))
  })
  if (explicit.length>2) throw invalid('Chọn một ngày hoặc một khoảng gồm hai ngày.')
  if (explicit.length) {
    const [from,to=from]=explicit
    if (to<from || to>addDays(from,30)) throw invalid('Khoảng tra cứu cần theo thứ tự và tối đa 31 ngày.')
    return {from,to}
  }
  if (/ngay mai/.test(query)) {
    const tomorrow=addDays(now,1)
    return {from:tomorrow,to:tomorrow}
  }
  if (/hom qua/.test(query)) {
    const yesterday=addDays(now,-1)
    return {from:yesterday,to:yesterday}
  }
  if (/hom nay/.test(query)) return {from:now,to:now}
  const delta=/tuan sau/.test(query) ? 7 : /tuan truoc/.test(query) ? -7 : 0
  const start=addDays(now,1-weekday(now)+delta)
  const namedDay=query.match(/\bthu (hai|ba|tu|nam|sau|bay|[2-7])\b/)?.[1]
  const offset={hai:0,ba:1,tu:2,nam:3,sau:4,bay:5}[namedDay] ?? (namedDay ? Number(namedDay)-2 : /chu nhat/.test(query)?6:null)
  if (offset!==null) {
    const day=addDays(start,offset)
    return {from:day,to:day}
  }
  if (action==='NEXT' && !/tuan /.test(query)) return {from:now,to:semester.end_date}
  return {from:start,to:addDays(start,6)}
}
