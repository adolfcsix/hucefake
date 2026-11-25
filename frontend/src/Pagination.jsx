import { useEffect, useState } from 'react'
import { api } from './api'
import { useFetch } from './ui'

export function usePagedQuery(path, revision, pageSize = 25) {
  const [selection, setSelection] = useState(null)
  const page = selection?.path === path ? selection.page : 1
  const url = path ? path + (path.includes('?') ? '&' : '?') + 'page=' + page + '&pageSize=' + pageSize : null
  const result = useFetch(async () => ({ url, path, payload:url ? await api(url) : null }), [url,revision])
  const data = result.data?.url === url ? result.data.payload : null
  const total = result.data?.path === path ? result.data.payload?.total ?? 0 : 0
  const lastPage = Math.max(1,Math.ceil(total / pageSize))
  useEffect(() => {
    if (data && page > lastPage) setSelection({ path, page:lastPage })
  }, [data,page,lastPage,path])
  return { data, total, error:result.error, page, lastPage, move:next=>setSelection({ path,page:next }) }
}

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
