import pg from 'pg'

for (const type of [1082, 1114]) pg.types.setTypeParser(type, value => value)
pg.types.setTypeParser(20, Number)
pg.types.setTypeParser(1016, value => value === '{}' ? [] : value.slice(1, -1).split(',').map(Number))

export const pool = new pg.Pool({
  connectionString: process.env.DATABASE_URL || 'postgresql://fm05:fm05_local@127.0.0.1:5432/fm05',
  options: '-c timezone=Asia/Ho_Chi_Minh',
})
export const all = async (db, sql, args = []) => (await db.query(sql, args)).rows
export const first = async (db, sql, args = []) => (await all(db, sql, args))[0]
export const audit = (db, actor, action, type, id) => db.query(
  'INSERT INTO audit_events(actor_id,action,entity_type,entity_id) VALUES ($1,$2,$3,$4)', [actor.id, action, type, id])
