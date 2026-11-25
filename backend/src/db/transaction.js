import { pool } from './pool.js'

export async function transaction(work, { readOnly = false } = {}) {
  const client = await pool.connect()
  try {
    await client.query(readOnly ? 'BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY' : 'BEGIN')
    if (!readOnly) await client.query('SELECT pg_advisory_xact_lock(5060505)')
    const result = await work(client)
    await client.query('COMMIT')
    return result
  } catch (error) {
    await client.query('ROLLBACK')
    throw error
  } finally {
    client.release()
  }
}
