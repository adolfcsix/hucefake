import { integer, role } from './errors.js'
import { once } from './idempotency.js'
import { transaction } from '../db/transaction.js'

export function route(roles, handler, status = 200) {
  return async (req, res) => {
    if (roles.length) role(req,...roles)
    req.recordId = req.params.id === undefined ? null : integer(req.params.id)
    const result = await handler(req)
    if (status === 204) res.status(status).end()
    else res.status(status).json(result)
  }
}
export const write = (roles, handler, status = 200) => route(roles, req => transaction(db => once(db,req,handler)), status)
