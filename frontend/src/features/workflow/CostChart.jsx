export function CostChart({ progress }) {
  const costs = progress.map(p => 1000 * p.hard_violations + Number(p.soft_penalty))
  const max = Math.max(1, ...costs)
  const total = Math.max(1, progress.at(-1).generation)
  const points = progress.map((p,i) => (6 + 308 * p.generation / total) + ',' + (75 - 67 * costs[i] / max)).join(' ')
  return <figure className="cost-chart"><figcaption>Chi phí tốt nhất theo thế hệ</figcaption>
    <svg viewBox="0 0 320 80" role="img" aria-label={'Chi phí từ ' + costs[0] + ' xuống ' + costs.at(-1)}>
      <path d="M 6 5 V 75 H 314" fill="none" stroke="currentColor" opacity=".22" />
      <polyline points={points} fill="none" stroke="currentColor" strokeWidth="2.5" />
      {progress.length === 1 && <circle cx={6} cy={75-67*costs[0]/max} r="3" fill="currentColor" />}
    </svg>
    <small>Thế hệ 0 <span>{total === 1 && progress.length === 1 ? '' : 'Thế hệ ' + total}</span></small>
  </figure>
}
