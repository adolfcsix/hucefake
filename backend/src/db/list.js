import { all } from './pool.js'
import { integer } from '../middleware/errors.js'

// Sort and SQL fragments are fixed by the server. Query values stay parameterized.
export async function list(db,query,sql,args,order,legacyLimit=null) {
  if (query.page===undefined && query.pageSize===undefined) {
    return all(db,sql+' ORDER BY '+order+(legacyLimit ? ' LIMIT '+legacyLimit : ''),args)
  }
  const page=integer(query.page??1,1,100000)
  const pageSize=integer(query.pageSize??25,1,100)
  const rows=await all(db,`WITH matched AS (${sql}),
    page_rows AS (SELECT * FROM matched ORDER BY ${order} LIMIT $${args.length+1} OFFSET $${args.length+2})
    SELECT page_rows.*, totals.total AS fm05_total FROM (SELECT count(*) AS total FROM matched) totals
    LEFT JOIN page_rows ON TRUE ORDER BY ${order}`,[...args,pageSize,(page-1)*pageSize])
  const total=rows[0].fm05_total
  const items=rows.filter(row=>row.id!==null).map(({ fm05_total, ...row })=>row)
  return {items,total,page,pageSize}
}
