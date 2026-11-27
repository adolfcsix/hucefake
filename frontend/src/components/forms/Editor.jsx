import { useEffect, useId, useRef, useState } from 'react'
import { ErrorBox } from '../common/ErrorBox'

export function Editor({ title, onClose, onSubmit, children, submit = 'Lưu', extra, onSuggestion, latestFields, onUseVersion, scheduleKey, onFieldError }) {
  const ref = useRef(null)
  const headingId = useId()
  const [pending, setPending] = useState(false)
  const [failure, setFailure] = useState(null)
  const error = failure?.error?.code === 'SCHEDULE_CONFLICT' && failure.scheduleKey !== scheduleKey ? null : failure?.error
  const latest = error?.code === 'VERSION_CONFLICT' && Number.isInteger(error.details?.version) ? error.details : null
  useEffect(() => {
    const dialog = ref.current
    dialog.showModal()
    return () => dialog.close()
  }, [])
  async function save(event) {
    event.preventDefault()
    if (pending || latest) return
    setPending(true)
    setFailure(null)
    try { await onSubmit(); onClose() }
    catch (error) { if (!onFieldError?.(error)) setFailure({ error, scheduleKey }) }
    finally { setPending(false) }
  }
  return <dialog ref={ref} aria-labelledby={headingId} onCancel={event => { event.preventDefault(); if (!pending) onClose() }}>
    <form onSubmit={save}>
      <div className="dialog-heading"><h2 id={headingId}>{title}</h2><button type="button" className="quiet icon-only" aria-label="Đóng" onClick={onClose} disabled={pending}>×</button></div>
      <div className="dialog-content"><ErrorBox error={error} onSuggestion={onSuggestion} />
        {latest && <section className="version-resolution">
          <h3>Dữ liệu đang lưu · Phiên bản {latest.version}</h3>
          {latestFields && <dl>{latestFields(latest).map(([label,value]) =>
            <div key={label}><dt>{label}</dt><dd>{value ?? '—'}</dd></div>)}</dl>}
          <p>Kiểm tra dữ liệu mới trước khi lưu tiếp. Nội dung đang nhập vẫn nằm trong biểu mẫu.</p>
          {onUseVersion && <button type="button" className="quiet" onClick={() => { onUseVersion(latest); setFailure(null) }}>
            Dùng phiên bản mới và giữ nội dung đang nhập
          </button>}
        </section>}
        {children}</div>
      <div className="dialog-actions">{extra && <div className="dialog-extra">{extra}</div>}
        <button type="button" className="quiet" onClick={onClose} disabled={pending}>Đóng</button>
        <button type="submit" disabled={pending || !!latest}>{pending ? 'Đang lưu…' : submit}</button>
      </div>
    </form>
  </dialog>
}
