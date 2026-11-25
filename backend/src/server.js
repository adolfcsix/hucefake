import { randomBytes } from 'node:crypto'
import { setTimeout as delay } from 'node:timers/promises'
import { createApp } from './app.js'
import { migrate } from './db/migrate.js'
import { pool } from './db/pool.js'
import { startRunWorker } from './modules/runs.js'

const demo=process.env.DEMO==='true'
const secret=process.env.SESSION_SECRET || (demo ? randomBytes(32).toString('hex') : '')
if (secret.length<32) throw new Error('SESSION_SECRET phải có ít nhất 32 ký tự hoặc bật DEMO=true khi chạy thử.')
for (let retry=0; ; retry++) {
  try {
    await migrate(demo)
    break
  } catch (error) {
    if (retry===10 || !['ECONNREFUSED','57P03'].includes(error.code)) throw error
    if (retry===0) console.log('Đang chờ PostgreSQL sẵn sàng…')
    await delay(500)
  }
}

const app=createApp(secret,{demo})
const port=Number(process.env.PORT || 8080)
const server=app.listen(port,process.env.HOST || '127.0.0.1',()=>{
  console.log('HUCE đang chạy tại http://localhost:'+port)
})
const stopWorker=startRunWorker()
for (const signal of ['SIGINT','SIGTERM']) process.once(signal,()=>{
  stopWorker()
  server.close(async ()=>{await pool.end()})
})
