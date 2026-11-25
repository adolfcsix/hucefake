import { readFile, readdir } from 'node:fs/promises'
import { transaction } from './transaction.js'
import { all, first } from './pool.js'
import { seedDemo } from './seed.js'

export async function migrate(demo) {
  await transaction(async db => {
    await db.query('CREATE TABLE IF NOT EXISTS fm05_node_migrations (name text PRIMARY KEY, applied_at timestamptz NOT NULL DEFAULT now())')
    const usedFlyway = Boolean((await first(db, "SELECT to_regclass('flyway_schema_history') AS name")).name)
    const old = usedFlyway ? await all(db, 'SELECT version,script FROM flyway_schema_history WHERE success') : []
    async function applyDirectory(directory) {
      const root = new URL('../../database/' + directory + '/', import.meta.url)
      for (const name of (await readdir(root)).filter(file => file.endsWith('.sql')).sort()) {
        if (await first(db, 'SELECT 1 FROM fm05_node_migrations WHERE name=$1', [name])) continue
        if (!old.some(row => row.script === name)) await db.query(await readFile(new URL(name, root), 'utf8'))
        await db.query('INSERT INTO fm05_node_migrations(name) VALUES ($1)', [name])
      }
    }
    await applyDirectory('migrations')
    // Pin the mode to this database, including an empty private installation.
    await db.query(`CREATE TABLE IF NOT EXISTS fm05_runtime_mode (
      singleton boolean PRIMARY KEY DEFAULT true CHECK(singleton), demo boolean NOT NULL)`)
    await db.query('LOCK TABLE fm05_runtime_mode IN EXCLUSIVE MODE')
    const saved = await first(db, 'SELECT demo FROM fm05_runtime_mode WHERE singleton=true')
    const demoMarker = await first(db, "SELECT 1 FROM work_items WHERE kind='FM05_DEMO_SEED'")
    if ((saved && saved.demo !== demo) || (!demo && demoMarker)) {
      throw new Error('Database thuộc chế độ khác. Hãy tạo database riêng cho DEMO=true và DEMO=false.')
    }
    if (!saved && demo && !demoMarker) {
      const privateData = await first(db, `SELECT 1 WHERE EXISTS(SELECT 1 FROM users)
        OR EXISTS(SELECT 1 FROM organizational_units) OR EXISTS(SELECT 1 FROM campuses)
        OR EXISTS(SELECT 1 FROM semesters)`)
      if (privateData) throw new Error('Database đã có dữ liệu riêng. Không được bật DEMO=true trên database này.')
    }
    if (!saved) await db.query('INSERT INTO fm05_runtime_mode(demo) VALUES ($1)', [demo])
    if (demo) {
      await applyDirectory('seeds')
      await seedDemo(db)
    }
  })
}
