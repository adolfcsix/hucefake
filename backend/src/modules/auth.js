import { randomBytes, timingSafeEqual } from 'node:crypto'
import bcrypt from 'bcryptjs'
import { first, pool } from '../db/pool.js'
import { HttpError } from '../middleware/errors.js'

const dummy = bcrypt.hashSync('invalid-credentials', 10)
const token = () => randomBytes(32).toString('hex')
const sessionCall = (req, method) => new Promise((resolve, reject) => req.session[method](e => e ? reject(e) : resolve()))
const lookup = username => first(pool, `
  SELECT u.id,u.username,u.password_hash,u.status,r.code AS role,s.id AS "staffId",s.unit_id AS "unitId"
  FROM users u JOIN roles r ON r.role_id=u.role_id
  LEFT JOIN staff_profiles s ON s.user_id=u.id AND s.status='ACTIVE' WHERE u.username=$1`, [username])
const safeActor = ({ id, username, role, staffId, unitId }) => ({ id, username, role, staffId, unitId })

const demoAccounts = Object.freeze({ STAFF:'daotao', MANAGER:'quanly', LECTURER:'giangvien', STUDENT:'sinhvien' })

export function authRoutes(api, { demo = false } = {}) {
  api.get('/auth/mode', (req,res) => res.set('Cache-Control','no-store').json({demo}))
  api.get('/csrf', (req, res) => {
    req.session.csrf ??= token()
    res.set('Cache-Control','no-store').json({ header: 'X-CSRF-Token', token: req.session.csrf })
  })
  api.use((req, res, next) => {
    if (['GET', 'HEAD', 'OPTIONS'].includes(req.method)) return next()
    const sent = Buffer.from(req.get('X-CSRF-Token') || '')
    const expected = Buffer.from(req.session.csrf || '')
    if (!expected.length || sent.length !== expected.length || !timingSafeEqual(sent, expected)) throw new HttpError(403,'CSRF_INVALID','Phiên biểu mẫu đã hết hạn. Vui lòng thử lại.')
    next()
  })
  async function login(req, res, username, password, expectedRole) {
    const user = typeof username === 'string' && username.length <= 200 ? await lookup(username) : null
    const matched = typeof password === 'string' && Buffer.byteLength(password) <= 72 &&
      await bcrypt.compare(password, (user?.password_hash || dummy).replace(/^\$2y\$/, '$2b$'))
    if (!matched || user?.status !== 'ACTIVE' || (expectedRole && user.role !== expectedRole)) throw new HttpError(401,'INVALID_CREDENTIALS','Tên đăng nhập hoặc mật khẩu không đúng.')
    const csrf = req.session.csrf
    await sessionCall(req,'regenerate')
    req.session.csrf = csrf
    req.session.username = user.username
    await sessionCall(req,'save')
    res.set('Cache-Control','no-store').json(safeActor(user))
  }
  api.post('/auth/login', async (req,res) => {
    const {username,password} = req.body || {}
    await login(req,res,username,password)
  })
  api.post('/auth/demo-login', async (req,res) => {
    if (!demo) throw new HttpError(403,'DEMO_DISABLED','Chế độ dùng thử không được bật.')
    const role = req.body?.role
    if (typeof role !== 'string' || !Object.hasOwn(demoAccounts,role)) throw new HttpError(400,'VALIDATION_ERROR','Vai trò dùng thử không hợp lệ.')
    await login(req,res,demoAccounts[role],'Fm05Demo@2026',role)
  })
  api.use(async (req,res,next) => {
    const user = req.session.username && await lookup(req.session.username)
    if (user?.status !== 'ACTIVE') throw new HttpError(401,'UNAUTHORIZED','Vui lòng đăng nhập.')
    req.actor = safeActor(user)
    res.set('Cache-Control','no-store')
    next()
  })
  api.get('/me', (req,res) => res.json(req.actor))
  api.post('/auth/logout', async (req,res) => {
    await sessionCall(req,'destroy')
    res.clearCookie('fm05.sid').sendStatus(204)
  })
}
