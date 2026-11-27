import { useEffect, useRef, useState } from 'react'
import { api } from '../../services/api'
import { ErrorBox } from '../../components/common/ErrorBox'
import { useFetch } from '../../hooks/useFetch'
import { topics, pageTopics } from './chatPresentation'
import { ChatIcon } from './ChatIcon'
import { ChatHistory } from './ChatHistory'
import { ChatExchange } from './ChatExchange'

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
      {view==='history' ? <ChatHistory {...{query,setQuery,searching,history,busy,resume}} /> : <>
        <div ref={log} className="chat-log" aria-label="Nội dung trò chuyện" role="log">
          {!exchanges.length && <div className="chat-suggestions"><p>Bạn muốn tra cứu gì?</p>
            {suggestions.map(value=><button className="quiet" type="button" key={value} disabled={busy}
              onClick={()=>{setMessage(value);composer.current?.focus()}}>{value}</button>)}
          </div>}
          {exchanges.map((exchange,index)=><ChatExchange key={exchange.id} exchange={exchange}
            last={index===exchanges.length-1} {...{busy,now,send,decide,openSource,close}} />)}
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
