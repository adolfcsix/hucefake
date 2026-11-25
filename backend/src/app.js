import express from 'express'
import { randomUUID } from 'node:crypto'
import session from 'express-session'
import connectPgSimple from 'connect-pg-simple'
import { existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { pool } from './db/pool.js'
import { errors, missing } from './middleware/errors.js'
import { authRoutes } from './modules/auth.js'
import { sectionRoutes } from './modules/sections.js'
import { catalogRoutes } from './modules/catalog.js'
import { approvalRoutes } from './modules/approvals.js'
import { sessionRoutes } from './modules/sessions.js'
import { runRoutes } from './modules/runs.js'
import { assistantRoutes } from './modules/assistant.js'
import { chatRoutes } from './modules/chat.js'
import { geminiAdvisor } from './integrations/gemini.js'

export function createApp(secret,{advisor=geminiAdvisor(),demo=process.env.DEMO==='true'}={}) {
  const app=express()
  app.disable('x-powered-by')
  app.use((req,res,next)=>{
    req.requestId=randomUUID()
    res.set('X-Request-Id',req.requestId)
    res.set('X-Content-Type-Options','nosniff')
    res.set('Referrer-Policy','same-origin')
    next()
  })
  app.get('/health', async (req,res)=>{
    await pool.query('SELECT 1')
    res.json({status:'ok'})
  })

  const api=express.Router()
  const PostgresStore=connectPgSimple(session)
  api.use(express.json({limit:'128kb'}))
  api.use((req,res,next)=>{
    req.body ??= {}
    next()
  })
  api.use(session({
    store:new PostgresStore({pool,tableName:'fm05_web_sessions',createTableIfMissing:true}),
    name:'fm05.sid', secret, saveUninitialized:false, resave:false,
    cookie:{httpOnly:true,sameSite:'lax',maxAge:8*60*60*1000,secure:process.env.COOKIE_SECURE==='true'},
  }))
  authRoutes(api,{demo})
  sectionRoutes(api)
  catalogRoutes(api)
  approvalRoutes(api)
  sessionRoutes(api)
  runRoutes(api)
  assistantRoutes(api,advisor)
  chatRoutes(api,advisor)
  api.use(()=>{ throw missing() })
  app.use('/api',api)

  const dist=fileURLToPath(new URL('../../frontend/dist/',import.meta.url))
  if(existsSync(dist)) app.use(express.static(dist))
  else app.get('/', (req,res)=>res.type('text').send('API cổng học vụ HUCE đang chạy. Mở frontend qua Vite hoặc build frontend để phục vụ cùng Express.'))
  app.use(errors)
  return app
}
