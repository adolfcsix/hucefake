export function Pagination({ query, itemName }) {
  const { data,total,page,lastPage,move } = query
  if (!total || lastPage === 1) return null
  return <nav className="table-pagination" aria-label={'Phân trang ' + itemName}>
    <p aria-live="polite">Trang {page}/{lastPage} · {total} {itemName}</p>
    <div className="button-row">
      <button type="button" className="quiet" disabled={!data || page<=1} onClick={()=>move(page-1)}>Trang trước</button>
      <button type="button" className="quiet" disabled={!data || page>=lastPage} onClick={()=>move(page+1)}>Trang sau</button>
    </div>
  </nav>
}
