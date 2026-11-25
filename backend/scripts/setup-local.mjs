import { readFile } from 'node:fs/promises'
import { stdin, stdout } from 'node:process'
import { createInterface } from 'node:readline/promises'
import { Writable } from 'node:stream'
import { parseArgs } from 'node:util'
import bcrypt from 'bcryptjs'
import { migrate } from '../src/db/migrate.js'
import { first, pool } from '../src/db/pool.js'
import { transaction } from '../src/db/transaction.js'
import { choice, date, integer, text } from '../src/middleware/errors.js'

const code = value => text(value,80).toUpperCase()

function list(source,key) {
  if (source[key] === undefined) return []
  if (!Array.isArray(source[key])) throw new Error(key+' phải là một danh sách.')
  return source[key]
}

async function idFor(db,sql,parameters,label) {
  const row = await first(db,sql,parameters)
  if (!row) throw new Error('Không tìm thấy '+label+'.')
  return row.id
}

async function importCatalog(db,source) {
  const units=list(source,'units'), campuses=list(source,'campuses'), semesters=list(source,'semesters')
  const subjects=list(source,'subjects'), groups=list(source,'groups'), enrollments=list(source,'enrollments')
  for (const row of units) await db.query(
    'INSERT INTO organizational_units(code,name) VALUES ($1,$2) ON CONFLICT(code) DO NOTHING',
    [code(row.code),text(row.name,150)])
  for (const row of campuses) await db.query(
    'INSERT INTO campuses(code,name) VALUES ($1,$2) ON CONFLICT(code) DO NOTHING',
    [code(row.code),text(row.name,150)])
  for (const row of semesters) {
    const year=code(row.yearCode)
    const start=date(row.startDate), end=date(row.endDate)
    if (start>end) throw new Error('Ngày kết thúc học kỳ phải từ ngày bắt đầu trở đi.')
    await db.query('INSERT INTO academic_years(code) VALUES ($1) ON CONFLICT(code) DO NOTHING',[year])
    await db.query(`INSERT INTO semesters(year_id,code,start_date,end_date)
      SELECT id,$2,$3,$4 FROM academic_years WHERE code=$1 ON CONFLICT(code) DO NOTHING`,
    [year,code(row.code),start,end])
  }
  for (const row of subjects) {
    const unit=await idFor(db,'SELECT id FROM organizational_units WHERE code=$1',[code(row.unitCode)],'khoa '+row.unitCode)
    await db.query(`INSERT INTO subjects(code,name,managing_unit_id,credits) VALUES ($1,$2,$3,$4)
      ON CONFLICT(code) DO NOTHING`,[code(row.code),text(row.name,150),unit,integer(row.credits,1,30)])
  }
  for (const row of groups) {
    const curriculum=code(row.curriculumCode)
    await db.query('INSERT INTO curriculum_versions(code) VALUES ($1) ON CONFLICT(code) DO NOTHING',[curriculum])
    await db.query(`INSERT INTO academic_groups(code,curriculum_id,capacity) SELECT $2,id,$3 FROM curriculum_versions
      WHERE code=$1 ON CONFLICT(code) DO NOTHING`,[curriculum,code(row.code),integer(row.capacity,1,10000)])
  }
  for (const row of enrollments) {
    const student=await idFor(db,`SELECT u.id FROM users u JOIN roles r ON r.role_id=u.role_id
      WHERE u.username=$1 AND r.code='STUDENT' AND u.status='ACTIVE'`,[text(row.username,100)],'sinh viên '+row.username)
    const section=await idFor(db,`SELECT c.id FROM course_sections c JOIN semesters s ON s.id=c.semester_id
      WHERE c.code=$1 AND s.code=$2 AND c.status<>'CANCELLED'`,[code(row.sectionCode),code(row.semesterCode)],'lớp học phần '+row.sectionCode)
    await db.query(`INSERT INTO section_enrollments(section_id,student_user_id) VALUES ($1,$2)
      ON CONFLICT(section_id,student_user_id) DO NOTHING`,[section,student])
  }
}

async function askPassword() {
  if (!stdin.isTTY) throw new Error('Mở terminal tương tác để nhập mật khẩu hoặc cung cấp FM05_SETUP_PASSWORD cho phiên chạy này.')
  let muted=false
  const output=new Writable({
    write(chunk,encoding,done) { if (!muted) stdout.write(chunk); done() }
  })
  const rl=createInterface({input:stdin,output,terminal:true})
  try {
    stdout.write('Mật khẩu tài khoản mới (ẩn): ')
    muted=true
    const password=await rl.question('')
    stdout.write('\nNhập lại mật khẩu: ')
    const confirmation=await rl.question('')
    stdout.write('\n')
    if (password!==confirmation) throw new Error('Hai mật khẩu chưa khớp.')
    return password
  } finally { rl.close() }
}

async function addAccount(options) {
  const username=text(options.username,100), email=text(options.email,200)
  const role=choice(options.role,'MANAGER','STAFF','LECTURER','STUDENT')
  const hasProfile=role==='STAFF' || role==='LECTURER'
  const unit=hasProfile ? code(options.unit) : null
  const campus=role==='STAFF' ? code(options.campus) : null
  const password=process.env.FM05_SETUP_PASSWORD ?? await askPassword()
  if (password.length<10 || Buffer.byteLength(password)>72) throw new Error('Mật khẩu cần ít nhất 10 ký tự và tối đa 72 byte.')
  const hash=await bcrypt.hash(password,10)
  await transaction(async db=>{
    const user=await first(db,`INSERT INTO users(role_id,username,email,password_hash)
      SELECT role_id,$2,$3,$4 FROM roles WHERE code=$1 RETURNING id`,[role,username,email,hash])
    if (!hasProfile) return
    const unitId=await idFor(db,'SELECT id FROM organizational_units WHERE code=$1',[unit],'khoa '+unit)
    const profile=await first(db,`INSERT INTO staff_profiles(user_id,code,unit_id) VALUES ($1,$2,$3) RETURNING id`,
      [user.id,code(options.code ?? username),unitId])
    if (campus) {
      const campusId=await idFor(db,"SELECT id FROM campuses WHERE code=$1 AND status='ACTIVE'",[campus],'cơ sở '+campus)
      await db.query('INSERT INTO staff_campuses(staff_id,campus_id) VALUES ($1,$2)',[profile.id,campusId])
    }
  })
  console.log('Đã tạo tài khoản '+username+' ('+role+').')
}

try {
  if (process.env.DEMO==='true') throw new Error('Đặt DEMO=false và chọn database riêng trước khi thiết lập bản trắng.')
  const [action,...args]=process.argv.slice(2)
  if (action==='catalog' && args.length===1) {
    const data=JSON.parse((await readFile(args[0],'utf8')).replace(/^\uFEFF/,''))
    if (data?.format!=='fm05-local' || data.version!==1) throw new Error('Tệp danh mục cần định dạng fm05-local phiên bản 1.')
    await migrate(false)
    await transaction(db=>importCatalog(db,data))
    console.log('Đã nạp dữ liệu nền. Các mã đã tồn tại được giữ nguyên.')
  } else if (action==='account') {
    const {values}=parseArgs({args,options:Object.fromEntries(['username','email','role','unit','campus','code']
      .map(key=>[key,{type:'string'}]))})
    await migrate(false)
    await addAccount(values)
  } else {
    throw new Error('Dùng setup:local -- catalog <tệp.json> hoặc setup:local -- account --username <tên> --email <email> --role <vai trò>.')
  }
} catch (error) {
  console.error(error.code==='23505' ? 'Tên tài khoản, email hoặc mã hồ sơ đã tồn tại.' : error.message)
  process.exitCode=1
} finally {
  await pool.end()
}
