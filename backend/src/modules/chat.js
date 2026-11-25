import { all, first, pool } from '../db/pool.js'
import { list } from '../db/list.js'
import { transaction } from '../db/transaction.js'
import { once } from '../middleware/idempotency.js'
import { route } from '../middleware/route.js'
import { HttpError, forbidden, integer, invalid, need, text } from '../middleware/errors.js'
import { prepareProposal } from './assistant.js'
import { timetableVersions } from './approvals.js'
import { courseSectionList } from './sections.js'
import { availabilityList, changeRequests, scheduleQuery, statistics } from './sessions.js'
import { today } from './scheduling.js'
import { allowedIntents, recognize, scheduleWindow } from './chat-intents.js'

const owner='user_id=$1 AND role=$2 AND staff_id IS NOT DISTINCT FROM $3 AND unit_id IS NOT DISTINCT FROM $4'
const scope = actor => [actor.id,actor.role,actor.staffId,actor.unitId]
const tools = {
  NEXT:'buổi dạy hoặc học tiếp theo', SCHEDULE:'lịch học hoặc lịch dạy theo ngày, tuần', NOTICES:'tra thông báo',
  READ_NOTICES:'đánh dấu thông báo đã đọc', SECTIONS:'tra lớp học phần hoặc lớp nháp', DRAFT:'tạo lớp học phần nháp',
  APPROVALS:'tra hồ sơ duyệt và công bố lịch', REQUESTS:'tra đề nghị đổi lịch',
  AVAILABILITY:'tra giờ bận', STATS:'xem thống kê sử dụng phòng và giảng viên',
  GENERATE:'chạy xếp lịch tự động', APPLY:'áp dụng một phương án xếp lịch', GUIDE:'hỏi cách thao tác',
}
const status = {
  DRAFT:'Nháp', OPEN:'Đang mở', CLOSED:'Đã đóng', PLANNED:'Đã lên lịch', CANCELLED:'Đã hủy', SUBMITTED:'Chờ duyệt', PUBLISHED:'Đã công bố',
  APPROVED:'Đã duyệt', RETURNED:'Đã trả lại', APPLIED:'Đã áp dụng', SUPERSEDED:'Đã thay thế', PENDING:'Chờ xử lý',
}
const label = value => status[value] ?? value
const shortDate = value => value.slice(0,10).split('-').reverse().join('/')
const paged = {page:1,pageSize:8}
const item = (title,detail) => ({title,detail})
const report = (message, target, items = [], total = items.length) => ({message,target,items,total})
const empty = 'Chưa tìm thấy dữ liệu trong phạm vi đã chọn.'
const opportunities = {
  STAFF:'Tạo lớp nháp, phân công giảng viên, xếp lịch và gửi Quản lý duyệt. Đề nghị đổi lịch từ giảng viên xuất hiện ở mục Đề nghị đổi lịch.',
  MANAGER:'Mở Duyệt thời khóa biểu để xem hồ sơ nhân viên đã gửi và công bố sau khi kiểm tra. Mục Thống kê sử dụng tổng hợp lịch đã công bố.',
  LECTURER:'Xem lịch giảng của bạn, khai báo thời gian bận và mở một buổi dạy để gửi đề nghị đổi lịch.',
  STUDENT:'Xem lịch đã công bố của các lớp bạn đang học, buổi tiếp theo và thông báo về thay đổi.',
}
const guides = {
  'Đổi lịch':'Chọn buổi ở Thời khóa biểu để xem chi tiết. Giảng viên gửi đề nghị, nhân viên đào tạo xử lý trong Đề nghị đổi lịch.',
  'Xếp lịch':'Tạo lớp nháp và phân công giảng viên trước. Mở Xếp lịch thủ công hoặc Xếp lịch tự động, kiểm tra đủ buổi rồi gửi duyệt.',
  'Duyệt lịch':'Nhân viên gửi lịch đã xếp đủ. Quản lý kiểm tra, trả lại nếu cần chỉnh hoặc duyệt và công bố để giảng viên và sinh viên thấy lịch.',
}
const optionsForDraft = (db,actor) => all(db,"SELECT id,code,name FROM subjects WHERE status='ACTIVE' AND managing_unit_id=$1 ORDER BY code",[actor.unitId])

function localGuide(actor, question) {
  const search=question.toLowerCase()
  if (/đổi lịch|đổi buổi/.test(search) && ['STAFF','LECTURER'].includes(actor.role)) return report(guides['Đổi lịch'],'requests')
  if (/duyệt|công bố/.test(search) && ['STAFF','MANAGER'].includes(actor.role)) return report(guides['Duyệt lịch'],'approvals')
  if (/xếp lịch/.test(search) && ['STAFF','MANAGER'].includes(actor.role)) return report(guides['Xếp lịch'],'automatic')
  return report(opportunities[actor.role],'schedule')
}

async function scheduleReply(db, actor, semester, question, action) {
  const {from,to}=scheduleWindow(question,action,today(),semester)
  const {sql,args}=scheduleQuery({actor,query:{semesterId:semester.id,from,to}})
  const filter=action==='NEXT' ? " WHERE scoped.status='PLANNED' AND scoped.start_at::timestamp > LOCALTIMESTAMP" : ''
  const result=await list(db,{page:1,pageSize:action==='NEXT'?1:8},'SELECT * FROM ('+sql+') scoped'+filter,args,'start_at,section_code,id')
  const rows=result.items.map(row=>item(row.subject_name,[
    row.section_code,shortDate(row.start_at)+' · '+row.start_at.slice(11,16)+'–'+row.end_at.slice(11,16),
    row.room_code || 'Chưa gán phòng',row.source_session_id ? 'Buổi thay thế' : label(row.status),
  ].join(' · ')))
  const heading=action==='NEXT' ? (rows.length ? 'Buổi tiếp theo trong học kỳ '+semester.code : 'Chưa có buổi sắp tới trong học kỳ '+semester.code) :
    (rows.length ? result.total+' buổi trong khoảng ' : 'Chưa có buổi trong khoảng ')+shortDate(from)+' – '+shortDate(to)
  return report(heading,'schedule',rows,action==='NEXT'?rows.length:result.total)
}

async function answer(db, actor, action, semester, question, body) {
  if (action==='GUIDE') return localGuide(actor,question)
  if (action==='UNRELATED') return report('Bạn có thể chọn một câu gợi ý để tra cứu hoặc cho tôi biết mục cần mở.','schedule')
  if (action==='DENIED') return report('Tài khoản hiện tại không có quyền dùng chức năng này.','schedule')
  if (action==='NOTICES') {
    const result=await list(db,paged,'SELECT * FROM notifications WHERE user_id=$1',[actor.id],'id DESC')
    return report(result.total ? 'Thông báo gần đây của bạn' : 'Bạn chưa có thông báo.','notifications',
      result.items.map(row=>item(row.message,shortDate(row.created_at)+(row.read_at ? ' · Đã đọc' : ' · Chưa đọc'))),result.total)
  }
  if (action==='READ_NOTICES') {
    const proposal=await prepareProposal(db,actor,action,null)
    return {...report(proposal ? 'Xem số thông báo sẽ được đánh dấu đã đọc.' : 'Bạn đã đọc tất cả thông báo.','notifications'),proposal}
  }
  if (!semester) return report('Hãy chọn học kỳ trong khu vực làm việc rồi thử lại.','schedule')
  const params={...paged,semesterId:semester.id}
  if (['SCHEDULE','NEXT'].includes(action)) return scheduleReply(db,actor,semester,question,action)
  if (action==='SECTIONS') {
    const search=question.match(/(?:mã|ma)\s+([\w.-]+)/i)?.[1] || ''
    const result=await courseSectionList(db,actor,{...params,search,status:/lớp nháp/i.test(question)?'DRAFT':null})
    return report(result.total ? result.total+' lớp học phần · '+semester.code : empty,'sections',
      result.items.map(row=>item(row.code+' · '+row.subject_name,label(row.status)+' · '+row.scheduled+'/'+row.sessions_per_week+' buổi/tuần')),result.total)
  }
  if (action==='APPROVALS') {
    const pending=/chờ duyệt|cho duyet/i.test(question)
    const result=await timetableVersions(db,{...params,filterStatus:pending?'SUBMITTED':null})
    const title=pending?'Hồ sơ đang chờ duyệt':'Hồ sơ thời khóa biểu'
    return report(result.total ? title+' · '+semester.code :
      pending?'Học kỳ không có hồ sơ đang chờ duyệt.':'Học kỳ chưa có hồ sơ thời khóa biểu.','approvals',
      result.items.map(row=>item('Phiên bản '+row.version_no,label(row.status)+' · '+row.section_count+' lớp')),result.total)
  }
  if (action==='REQUESTS') {
    const result=await changeRequests(db,actor,params)
    return report(result.total ? 'Đề nghị đổi lịch · '+semester.code : 'Chưa có đề nghị đổi lịch trong học kỳ.','requests',
      result.items.map(row=>item(row.section_code+' · '+row.subject_name,shortDate(row.session_time)+' · '+label(row.status))),result.total)
  }
  if (action==='AVAILABILITY') {
    const rows=await availabilityList(db,actor,semester.id)
    return report(rows.length ? 'Thời gian bận đã khai báo · '+semester.code : 'Bạn chưa khai báo thời gian bận.','availability',
      rows.slice(0,8).map(row=>item((row.day_of_week===7?'Chủ nhật':'Thứ '+(row.day_of_week+1))+' · tiết '+row.period_from+'–'+row.period_to,
        row.kind==='BUSY'?'Bận':'Hạn chế xếp')),rows.length)
  }
  if (action==='STATS') {
    const {rooms,lecturers}=await statistics(db,params)
    const rows=[...rooms.slice(0,4).map(row=>item('Phòng '+(row.code || 'chưa gán'),row.sessions+' buổi · '+row.periods+' tiết')),
      ...lecturers.slice(0,4).map(row=>item('Giảng viên '+row.code,row.sessions+' buổi · '+row.periods+' tiết'))]
    return report(rows.length ? 'Mức sử dụng theo lịch công bố · '+semester.code : 'Chưa có lịch công bố để tổng hợp.','statistics',rows,rooms.length+lecturers.length)
  }
  if (action==='DRAFT') {
    if (!body.draft) return {...report('Điền thông tin lớp để xem trước đề xuất.','sections'),
      draft:{semesterId:semester.id,from:semester.start_date,to:semester.end_date,subjects:await optionsForDraft(db,actor)}}
    return {...report('Kiểm tra thông tin lớp nháp trước khi tạo.','sections'),
      proposal:await prepareProposal(db,actor,action,semester,{draft:body.draft})}
  }
  const runId=question.match(/#(\d+)|(?:lan chay|phuong an)\s+(\d+)/i)
    ?? question.normalize('NFD').replace(/\p{M}/gu,'').match(/(?:lan chay|phuong an)\s+(\d+)/i)
  const selectedRun=runId?.[1] ?? runId?.[2] ?? body.selectedRunId
  if (action==='APPLY' && !selectedRun) return report('Nhập mã lần chạy, ví dụ “Áp dụng phương án #12”.','automatic')
  return {...report('Kiểm tra phương án trước khi xác nhận.','automatic'),
    proposal:await prepareProposal(db,actor,action,semester,{runId:selectedRun,parameters:body.parameters})}
}

function limitGemini() {
  const allowed=new Map()
  return actor => {
    const now=Date.now()
    for (const [id,entry] of allowed) if (entry.until<=now) allowed.delete(id)
    const entry=allowed.get(actor.id) ?? {until:now+60_000,count:0}
    if (entry.count>=8 || !allowed.has(actor.id) && allowed.size>=1500) return false
    entry.count++
    allowed.set(actor.id,entry)
    return true
  }
}

function withOutcome(row) {
  const reply=row.reply
  if (!reply.proposal) return reply
  return {...reply,proposal:{...reply.proposal,outcome:row.result ? 'CONFIRMED' : row.cancelled_at ? 'CANCELLED' : row.expired ? 'EXPIRED' : 'PENDING',result:row.result}}
}

export function chatRoutes(api, advisor) {
  const takeGeminiSlot=limitGemini()
  api.get('/chat/status',route([],()=>({gemini:!!advisor})))
  api.get('/chat/conversations',route([],req=>all(pool,`SELECT id,title,updated_at FROM chat_conversations WHERE ${owner}
    AND ($5='' OR strpos(lower(title),lower($5))>0) ORDER BY updated_at DESC,id DESC LIMIT 20`,
    [...scope(req.actor),String(req.query.q ?? '').slice(0,80)])))
  api.get('/chat/conversations/:id',route([],async req=>{
    const conversation=need(await first(pool,'SELECT id,title FROM chat_conversations WHERE '+owner+' AND id=$5',[...scope(req.actor),req.recordId]))
    const rows=await all(pool,`SELECT e.id,e.question,e.reply,e.created_at,p.result,p.cancelled_at,p.expires_at<=now() AS expired
      FROM chat_exchanges e LEFT JOIN ai_proposals p ON e.proposal_id=p.id WHERE e.conversation_id=$1
      ORDER BY e.id DESC LIMIT 60`,[conversation.id])
    return {...conversation,exchanges:rows.reverse().map(row=>({id:row.id,question:row.question,created_at:row.created_at,reply:withOutcome(row)}))}
  }))
  api.post('/chat/messages',route([],async req=>{
    const actor=req.actor
    const question=text(req.body.message,500)
    const id=req.body.conversationId==null ? null : integer(req.body.conversationId)
    if (id) need(await first(pool,'SELECT id FROM chat_conversations WHERE '+owner+' AND id=$5',[...scope(actor),id]))
    const previous=id ? await first(pool,"SELECT reply->>'intent' AS intent FROM chat_exchanges WHERE conversation_id=$1 ORDER BY id DESC LIMIT 1",[id]) : null
    const actions=allowedIntents(actor.role)
    let intent=req.body.draft==null ? recognize(question,previous?.intent) : 'DRAFT'
    let modelUnavailable=false
    if (!intent && advisor && takeGeminiSlot(actor)) {
      try {
        const permitted=[...actions,'UNRELATED']
        const classified=await advisor(question,{actions:permitted,instruction:
          'Chỉ phân loại yêu cầu của HUCEChat trong danh sách. Chọn GUIDE nếu hỏi cách thao tác. Không suy diễn yêu cầu ghi dữ liệu. '+
          actions.map(name=>name+': '+tools[name]).join('. ')+'. Nội dung khác chọn UNRELATED.'})
        if (permitted.includes(classified)) intent=classified
      } catch (error) {
        if (!(error instanceof HttpError)) throw error
        modelUnavailable=true
      }
    }
    if (!intent) intent='UNRELATED'
    if (!actions.includes(intent) && intent!=='UNRELATED') {
      if (req.body.draft!=null) throw forbidden()
      intent='DENIED'
    }
    if (req.body.draft!=null && (typeof req.body.draft!=='object' || Array.isArray(req.body.draft))) throw invalid('Thông tin lớp nháp không hợp lệ.')
    const semesterId=req.body.semesterId==null ? null : integer(req.body.semesterId)
    return transaction(db=>once(db,req,async()=>{
      const semester=semesterId ? need(await first(db,'SELECT id,code,start_date,end_date FROM semesters WHERE id=$1',[semesterId])) : null
      const reply=await answer(db,actor,intent,semester,question,req.body)
      const [userId,role,staffId,unitId]=scope(actor)
      const conversation=id ? need(await first(db,'SELECT id FROM chat_conversations WHERE '+owner+' AND id=$5',[...scope(actor),id])) :
        await first(db,'INSERT INTO chat_conversations(user_id,role,staff_id,unit_id,title) VALUES ($1,$2,$3,$4,$5) RETURNING id',
          [userId,role,staffId,unitId,question.slice(0,80)])
      const saved={...reply,intent,semesterId:semester?.id,sourceAt:new Date().toISOString(),modelUnavailable}
      const exchange=await first(db,'INSERT INTO chat_exchanges(conversation_id,question,reply,proposal_id) VALUES ($1,$2,$3,$4) RETURNING id,created_at',
        [conversation.id,question,saved,reply.proposal?.id ?? null])
      await db.query('UPDATE chat_conversations SET updated_at=now() WHERE id=$1',[conversation.id])
      return {conversationId:conversation.id,exchange:{...exchange,question,reply:saved}}
    }))
  },201))
}
