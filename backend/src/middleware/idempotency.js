import { createHash } from 'node:crypto'
import { first } from '../db/pool.js'
import { duplicate, invalid } from './errors.js'

function ordered(value) {
  if (Array.isArray(value)) return value.map(ordered)
  if (!value || typeof value!=='object') return value
  return Object.fromEntries(Object.keys(value).sort().map(key=>[key,ordered(value[key])]))
}

export async function once(db,req,handler) {
  const key=req.get('Idempotency-Key')
  if (key===undefined) return handler(req,db)
  if (!/^[a-zA-Z0-9._:-]{1,128}$/.test(key)) throw invalid('Idempotency-Key không hợp lệ.')
  const actor=req.actor
  const fingerprint=createHash('sha256').update(JSON.stringify(ordered([
    req.method,req.baseUrl,req.path,actor.role,actor.unitId,actor.staffId,req.query,req.body,
  ]))).digest('hex')
  // Runs under the same advisory transaction lock as the business mutation.
  await db.query('DELETE FROM fm05_idempotency_keys WHERE expires_at<=now()')
  const prior=await first(db,'SELECT fingerprint,response FROM fm05_idempotency_keys WHERE actor_id=$1 AND key=$2',[actor.id,key])
  if (prior) {
    if (prior.fingerprint!==fingerprint) throw duplicate('Mã chống lặp đã được dùng cho một yêu cầu khác.')
    return prior.response
  }
  const result=await handler(req,db)
  await db.query('INSERT INTO fm05_idempotency_keys(actor_id,key,fingerprint,response) VALUES ($1,$2,$3,$4::jsonb)',
    [actor.id,key,fingerprint,JSON.stringify(result??null)])
  return result
}
