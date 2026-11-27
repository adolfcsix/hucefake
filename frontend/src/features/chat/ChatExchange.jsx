import { DraftFields } from './DraftFields'
import { Proposal } from './Proposal'
import { displayTime } from './chatPresentation'

export function ChatExchange({ exchange, last, busy, now, send, decide, openSource, close }) {
  const { id, question, reply } = exchange
  return <article className="chat-exchange" key={id}>
            <p className="chat-question">{question}</p>
            <div className="chat-answer">
              <p>{reply.message}</p>
              {!!reply.items?.length && <ul>{reply.items.map((row,index)=>
                <li key={index}><strong>{row.title}</strong><small>{row.detail}</small></li>)}</ul>}
              {reply.items?.length>0 && reply.total>reply.items.length && <small>Hiển thị {reply.items.length}/{reply.total} mục.</small>}
              {reply.modelUnavailable && <small>Gemini chưa phản hồi. Bạn có thể chọn câu gợi ý để tiếp tục tra cứu.</small>}
              {reply.draft && last && <DraftFields draft={reply.draft} busy={busy}
                onSubmit={(data,semesterId)=>send('Xem trước lớp '+data.code,data,semesterId)} />}
              {reply.proposal && <Proposal proposal={reply.proposal} now={now} busy={busy} decide={decide} />}
              <footer><a href={'#'+reply.target} onClick={()=>{openSource(reply.semesterId);close()}}>Mở trang liên quan</a>
                {reply.sourceAt && <time dateTime={reply.sourceAt}>{displayTime.format(new Date(reply.sourceAt))}</time>}
              </footer>
            </div>
          </article>
}
