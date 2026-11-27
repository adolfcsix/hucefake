export function Table({ columns, rows, empty = 'Chưa có dữ liệu.' }) {
  return rows.length ? <div className="table-wrap" tabIndex={0} role="region" aria-label="Bảng dữ liệu"><table><thead><tr>{columns.map(([label]) => <th scope="col" key={label}>{label}</th>)}</tr></thead>
    <tbody>{rows.map(row => <tr key={row.id}>{columns.map(([label, render]) =>
      <td key={label}>{typeof render === 'function' ? render(row) : row[render] ?? '—'}</td>)}</tr>)}</tbody></table></div>
    : <div className="empty">{empty}</div>
}
