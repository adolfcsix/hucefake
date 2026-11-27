import { Field } from '../../components/forms/FormFields'
import { displayTime } from './chatPresentation'

export function ChatHistory({ query, setQuery, searching, history, busy, resume }) {
  return <div className="chat-log">
        <Field label="Tìm cuộc trò chuyện"><input type="search" value={query} maxLength={80} onChange={event=>setQuery(event.target.value)} /></Field>
        {searching && <p role="status">Đang tìm…</p>}
        {!searching && history.length===0 && <p>Chưa có cuộc trò chuyện phù hợp.</p>}
        <ul className="chat-history">{history.map(row=><li key={row.id}><button type="button" className="quiet" disabled={busy} onClick={()=>resume(row.id)}>
          <span>{row.title}</span><small>{displayTime.format(new Date(row.updated_at))}</small>
        </button></li>)}</ul>
</div>
}
