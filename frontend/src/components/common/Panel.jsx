export function Panel({ title, children, action }) {
  return <section className="panel">{(title || action) && <div className="panel-heading"><h2>{title}</h2>{action}</div>}{children}</section>
}
