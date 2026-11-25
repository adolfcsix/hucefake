import { useEffect, useRef, useState } from 'react'
import { api } from './api'
import { ErrorBox, Field, Select, roomTypes, useFetch } from './ui'

const topics = {
  STAFF:['Tra lớp nháp','Tạo lớp học phần','Chạy xếp lịch tự động','Hồ sơ chờ duyệt','Đề nghị đổi lịch'],
  MANAGER:['Hồ sơ chờ duyệt','Thống kê sử dụng phòng','Tra lớp học phần','Chạy xếp lịch tự động'],
  LECTURER:['Lịch giảng tuần này','Buổi dạy tiếp theo','Thời gian bận','Đề nghị đổi lịch'],
  STUDENT:['Buổi học tiếp theo','Lịch học ngày mai','Thời khóa biểu tuần này'],
}
const pageTopics = {
  schedule:'Thời khóa biểu tuần này', sections:'Tra lớp học phần', automatic:'Chạy xếp lịch tự động',
  approvals:'Hồ sơ chờ duyệt', requests:'Đề nghị đổi lịch', availability:'Thời gian bận',
  statistics:'Thống kê sử dụng phòng', notifications:'Thông báo của tôi',
}
const proposalNames = {
  GENERATE:'Chạy xếp lịch tự động', APPLY:'Áp dụng phương án', DRAFT:'Tạo lớp học phần nháp',
  READ_NOTICES:'Đánh dấu thông báo đã đọc',
}
const displayTime = new Intl.DateTimeFormat('vi-VN',{dateStyle:'short',timeStyle:'short',timeZone:'Asia/Ho_Chi_Minh'})

function ChatIcon() {
  return <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.7" aria-hidden="true">
    <path strokeLinejoin="round" d="M20 4H4a1 1 0 0 0-1 1v11a1 1 0 0 0 1 1h3v4l5-4h8a1 1 0 0 0 1-1V5a1 1 0 0 0-1-1Z" />
    <path strokeLinecap="round" d="M7 9h10m-10 4h6" />
  </svg>
}

function DraftFields({ draft, busy, onSubmit }) {
  const [form, set] = useState({subjectId:'',code:'',capacity:'40',sessionsPerWeek:'1',periodsPerSession:'2',
    requiredRoomType:'LECTURE',startDate:draft.from,endDate:draft.to})
  const update = (key,value) => set(previous=>({...previous,[key]:value}))
  const numbers=[['capacity','Sĩ số',10000],['sessionsPerWeek','Buổi / tuần',6],['periodsPerSession','Tiết / buổi',6]]
  if (!draft.subjects.length) return <p>Khoa của bạn chưa có học phần đang hoạt động.</p>
  return <form className="chat-draft" onSubmit={event=>{
    event.preventDefault()
    onSubmit({...form,subjectId:Number(form.subjectId),
      ...Object.fromEntries(numbers.map(([key])=>[key,Number(form[key])]))},draft.semesterId)
  }}>
    <Field label="Học phần"><Select value={form.subjectId} set={value=>update('subjectId',value)} required
      options={draft.subjects.map(row=>[row.id,row.code+' · '+row.name])} placeholder="Chọn học phần" disabled={busy} /></Field>
    <Field label="Mã lớp"><input value={form.code} onChange={event=>update('code',event.target.value)} maxLength={40} required disabled={busy}/></Field>
    {numbers.map(([key,title,max])=><Field key={key} label={title}><input type="number" min="1" max={max} required disabled={busy}
      value={form[key]} onChange={event=>update(key,event.target.value)} /></Field>)}
    <Field label="Loại phòng"><Select value={form.requiredRoomType} set={value=>update('requiredRoomType',value)} options={Object.entries(roomTypes)} disabled={busy}/></Field>
    {[['startDate','Từ ngày'],['endDate','Đến ngày']].map(([key,title])=><Field key={key} label={title}>
      <input type="date" value={form[key]} min={draft.from} max={draft.to} onChange={event=>update(key,event.target.value)} required disabled={busy}/>
    </Field>)}
    <button type="submit" disabled={busy}>Xem trước lớp nháp</button>
  </form>
}

function proposalInfo({action,preview}) {
  if (action==='GENERATE') {
    const p=preview.parameters
    return [['Quần thể',p.population],['Số thế hệ',p.generations],['Lai ghép',p.crossover],
      ['Đột biến',p.mutation],['Hạt giống',p.seed]]
  }
  if (action==='APPLY') return [['Lần chạy','#'+preview.runId],['Số buổi',preview.count],['Vi phạm cứng',preview.hardViolations]]
  if (action==='DRAFT') return [['Mã lớp',preview.code],['Học phần',preview.subject],['Sĩ số',preview.capacity],
    ['Buổi / tuần',preview.sessionsPerWeek],['Tiết / buổi',preview.periodsPerSession],
    ['Loại phòng',roomTypes[preview.requiredRoomType]],
    ['Thời gian',preview.startDate+' → '+preview.endDate]]
  return [['Thông báo chưa đọc',preview.count]]
}

function proposalResult(result) {
  if (result.action==='GENERATE') return 'Đã đưa lần chạy #'+result.id+' vào hàng đợi.'
  if (result.action==='APPLY') return 'Phương án #'+result.id+' đã áp dụng.'
  if (result.action==='DRAFT') return 'Đã tạo lớp nháp '+result.code+'.'
  return 'Đã đánh dấu '+result.marked+' thông báo.'
}

function Proposal({ proposal, now, busy, decide }) {
  const outcome=proposal.outcome || 'PENDING'
  const pending=outcome==='PENDING' && Date.parse(proposal.expires_at)>now
  return <section className="chat-proposal">
    <h3>{proposalNames[proposal.action]}</h3>
    {proposal.semester && <p>Học kỳ {proposal.semester}</p>}
    <dl>{proposalInfo(proposal).map(([key,value])=><div key={key}><dt>{key}</dt><dd>{value}</dd></div>)}</dl>
    {pending ? <>
      <small>Hạn xác nhận {displayTime.format(new Date(proposal.expires_at))}</small>
      <div className="button-row">
        <button type="button" disabled={busy} onClick={()=>decide(proposal.id,'confirm')}>Xác nhận</button>
        <button type="button" className="quiet" disabled={busy} onClick={()=>decide(proposal.id,'cancel')}>Hủy đề xuất</button>
      </div>
    </> : <p role="status">{outcome==='CANCELLED'?'Đã hủy đề xuất.':outcome==='CONFIRMED' && proposal.result ?
      proposalResult(proposal.result) : 'Đề xuất đã hết hạn.'}</p>}
  </section>
}

export default function HuceChat({ role, semester, page, reload, scheduling, openSource }) {
  const [open,setOpen]=useState(false)
  const [view,setView]=useState('chat')
  const [query,setQuery]=useState('')
  const [history,setHistory]=useState([])
  const [searching,setSearching]=useState(false)
  const [conversation,setConversation]=useState(null)
  const [exchanges,setExchanges]=useState([])
  const [message,setMessage]=useState('')
  const [error,setError]=useState(null)
  const [busy,setBusy]=useState(false)
  const [now,setNow]=useState(Date.now)
  const log=useRef(null)
  const composer=useRef(null)
  const launcher=useRef(null)
  const operation=useRef(false)
  const config=useFetch(()=>api('/chat/status'),[])
  const selectedRun=scheduling?.runId ? 'Áp dụng phương án #'+scheduling.runId : null
  const suggestions=[...new Set([pageTopics[page],selectedRun,...topics[role],'Thông báo của tôi'])].filter(Boolean).slice(0,5)

  useEffect(()=>{
    if (!open) return
    composer.current?.focus()
    const timer=setInterval(()=>setNow(Date.now()),5000)
    return ()=>clearInterval(timer)
  },[open,view])
  useEffect(()=>{
    if (open && log.current) log.current.scrollTop=log.current.scrollHeight
  },[exchanges,open,view])
  useEffect(()=>{
    if (!open || view!=='history') return
    let active=true
    setSearching(true)
    const timer=setTimeout(() => api('/chat/conversations?q='+encodeURIComponent(query)).then(rows=>{
      if (active) setHistory(rows)
    }).catch(error=>{if (active) setError(error)}).finally(()=>{if (active) setSearching(false)}),200)
    return ()=>{active=false;clearTimeout(timer)}
  },[open,view,query])

  async function perform(work) {
    if (operation.current) return
    operation.current=true
    setBusy(true)
    setError(null)
    try { await work() }
    catch (error) { setError(error) }
    finally { operation.current=false;setBusy(false) }
  }
  function close() {
    setOpen(false)
    launcher.current?.focus()
  }
  function newConversation() {
    setConversation(null)
    setExchanges([])
    setMessage('')
    setError(null)
    setView('chat')
  }
  function resume(id) {
    return perform(async()=>{
      const data=await api('/chat/conversations/'+id)
      setConversation(data.id)
      setExchanges(data.exchanges)
      setMessage('')
      setView('chat')
    })
  }
  function send(value,draft,semesterId=semester?.id) {
    if (!value.trim()) return
    return perform(async()=>{
      const data=await api('/chat/messages','POST',{message:value,conversationId:conversation,semesterId,draft,
        parameters:scheduling?.parameters,selectedRunId:scheduling?.runId})
      setConversation(data.conversationId)
      setExchanges(previous=>[...previous,data.exchange])
      if (!draft) setMessage('')
      setNow(Date.now())
    })
  }
  function decide(id,action) {
    return perform(async()=>{
      const result=await api('/chat/proposals/'+id+'/'+action,'POST')
      const outcome=action==='confirm'?'CONFIRMED':'CANCELLED'
      setExchanges(previous=>previous.map(exchange=>exchange.reply.proposal?.id===id ?
        {...exchange,reply:{...exchange.reply,proposal:{...exchange.reply.proposal,outcome,result}}} : exchange))
      if (outcome==='CONFIRMED') reload()
    })
  }

  return <div className="huce-chat" onKeyDown={event=>{if (event.key==='Escape' && open) { event.stopPropagation(); close() }}}>
    {open && <section className="chat-panel" id="huce-chat-panel" aria-labelledby="huce-chat-title">
      <header className="chat-header">
        <div><h2 id="huce-chat-title"><ChatIcon />HUCEChat</h2>{semester && <small>Học kỳ {semester.code}</small>}</div>
        <button className="quiet icon-only" type="button" onClick={close} aria-label="Đóng HUCEChat">×</button>
      </header>
      <div className="chat-controls">
        <button type="button" className="quiet" disabled={busy} onClick={()=>{setError(null);setView(view==='chat'?'history':'chat')}}>
          {view==='chat'?'Lịch sử':'Trò chuyện'}
        </button>
        <button type="button" className="quiet" disabled={busy} onClick={newConversation}>Cuộc trò chuyện mới</button>
      </div>
      {view==='history' ? <div className="chat-log">
        <Field label="Tìm cuộc trò chuyện"><input type="search" value={query} maxLength={80} onChange={event=>setQuery(event.target.value)} /></Field>
        {searching && <p role="status">Đang tìm…</p>}
        {!searching && history.length===0 && <p>Chưa có cuộc trò chuyện phù hợp.</p>}
        <ul className="chat-history">{history.map(row=><li key={row.id}><button type="button" className="quiet" disabled={busy} onClick={()=>resume(row.id)}>
          <span>{row.title}</span><small>{displayTime.format(new Date(row.updated_at))}</small>
        </button></li>)}</ul>
      </div> : <>
        <div ref={log} className="chat-log" aria-label="Nội dung trò chuyện" role="log">
          {!exchanges.length && <div className="chat-suggestions"><p>Bạn muốn tra cứu gì?</p>
            {suggestions.map(value=><button className="quiet" type="button" key={value} disabled={busy}
              onClick={()=>{setMessage(value);composer.current?.focus()}}>{value}</button>)}
          </div>}
          {exchanges.map(({id,question,reply},index)=><article className="chat-exchange" key={id}>
            <p className="chat-question">{question}</p>
            <div className="chat-answer">
              <p>{reply.message}</p>
              {!!reply.items?.length && <ul>{reply.items.map((row,index)=>
                <li key={index}><strong>{row.title}</strong><small>{row.detail}</small></li>)}</ul>}
              {reply.items?.length>0 && reply.total>reply.items.length && <small>Hiển thị {reply.items.length}/{reply.total} mục.</small>}
              {reply.modelUnavailable && <small>Gemini chưa phản hồi. Bạn có thể chọn câu gợi ý để tiếp tục tra cứu.</small>}
              {reply.draft && index===exchanges.length-1 && <DraftFields draft={reply.draft} busy={busy}
                onSubmit={(data,semesterId)=>send('Xem trước lớp '+data.code,data,semesterId)} />}
              {reply.proposal && <Proposal proposal={reply.proposal} now={now} busy={busy} decide={decide} />}
              <footer><a href={'#'+reply.target} onClick={()=>{openSource(reply.semesterId);close()}}>Mở trang liên quan</a>
                {reply.sourceAt && <time dateTime={reply.sourceAt}>{displayTime.format(new Date(reply.sourceAt))}</time>}
              </footer>
            </div>
          </article>)}
          {busy && <p role="status">Đang xử lý…</p>}
        </div>
        <form className="chat-composer" onSubmit={event=>{event.preventDefault();send(message)}}>
          <label htmlFor="huce-chat-message" className="chat-visually-hidden">Câu hỏi cho HUCEChat</label>
          <textarea id="huce-chat-message" ref={composer} rows={2} maxLength={500} value={message}
            placeholder="Nhập câu hỏi…" onChange={event=>setMessage(event.target.value)} />
          <button type="submit" disabled={busy || !message.trim()}>Gửi</button>
          {config.data?.gemini && <small>Câu hỏi nâng cao có thể được gửi đến Gemini. Tránh nhập thông tin cá nhân.</small>}
        </form>
      </>}
      {error && <div className="chat-error"><ErrorBox error={error}/></div>}
    </section>}
    <button ref={launcher} type="button" className="chat-launcher" aria-expanded={open} aria-controls={open?'huce-chat-panel':undefined}
      onClick={()=>open?close():setOpen(true)}><ChatIcon /> HUCEChat</button>
  </div>
}
