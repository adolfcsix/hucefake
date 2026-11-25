import bcrypt from 'bcryptjs'
import { first } from './pool.js'

export async function seedDemo(db) {
  const hash = await bcrypt.hash('Fm05Demo@2026', 10)
  const users = [['daotao','STAFF'], ['quanly','MANAGER'], ['giangvien','LECTURER'],
    ['giangvien2','LECTURER'], ['sinhvien','STUDENT'], ['daotao2','STAFF']]
  for (const [name, role] of users) {
    await db.query(`INSERT INTO users(role_id,username,email,password_hash)
                SELECT role_id,$1,$2,$3 FROM roles WHERE code=$4 ON CONFLICT (username) DO NOTHING`, [name,name+'@example.test',hash,role])
  }
  await db.query(`INSERT INTO staff_profiles(user_id,code,unit_id,position)
            SELECT u.id,upper(u.username),o.id,r.name FROM users u JOIN roles r ON r.role_id=u.role_id
            JOIN organizational_units o ON o.code=CASE WHEN u.username='daotao2' THEN 'KT' ELSE 'CNTT' END
            WHERE u.username IN ('daotao','daotao2','giangvien','giangvien2','quanly') ON CONFLICT (user_id) DO NOTHING`)
  await db.query(`INSERT INTO staff_campuses(staff_id,campus_id)
    SELECT s.id,c.id FROM staff_profiles s CROSS JOIN campuses c
    WHERE s.code IN ('DAOTAO','DAOTAO2') AND c.code='CS1' ON CONFLICT DO NOTHING`)
  if (await first(db, "SELECT 1 FROM work_items WHERE kind='FM05_DEMO_SEED'")) return
  const actor = (await first(db, "SELECT id FROM users WHERE username='daotao'")).id
  await db.query("INSERT INTO work_items(kind,created_by,status) VALUES ('FM05_DEMO_SEED',$1,'APPLIED')", [actor])
  await db.query(`INSERT INTO non_teaching_days(start_date,end_date,name,created_by)
    VALUES ('2026-09-02','2026-09-02','Nghỉ Quốc khánh',$1)`, [actor])
  await db.query(`INSERT INTO course_sections(code,subject_id,semester_id,capacity,sessions_per_week,periods_per_session,
                                        required_room_type,start_date,end_date,created_by)
            SELECT v.code,s.id,h.id,v.capacity,v.sessions,2,v.room,h.start_date,h.end_date,$1
            FROM (VALUES ('CSDL.01','CSDL',40,2,'COMPUTER_LAB'),('KTPM.01','KTPM',55,2,'LECTURE'),
                         ('MANG.01','MANG',35,1,'LECTURE')) v(code,subject,capacity,sessions,room)
            JOIN subjects s ON s.code=v.subject JOIN semesters h ON h.code='2026-2027.1'`, [actor])
  await db.query(`INSERT INTO section_assignments(section_id,staff_id,role,valid_from,assigned_by)
            SELECT c.id,g.id,'MAIN',c.start_date,$1 FROM course_sections c JOIN staff_profiles g
            ON g.code=CASE WHEN c.code='MANG.01' THEN 'GIANGVIEN2' ELSE 'GIANGVIEN' END
            WHERE c.created_by=$2 AND c.code IN ('CSDL.01','KTPM.01','MANG.01')`, [actor,actor])
  await db.query(`INSERT INTO section_target_groups(section_id,group_id)
            SELECT c.id,g.id FROM course_sections c JOIN academic_groups g ON g.code='CNTT-K26'
            WHERE c.created_by=$1 AND c.code IN ('CSDL.01','KTPM.01','MANG.01')`, [actor])
  await db.query(`INSERT INTO section_enrollments(section_id,student_user_id)
            SELECT c.id,u.id FROM course_sections c JOIN users u ON u.username='sinhvien'
            WHERE c.created_by=$1 AND c.code IN ('CSDL.01','KTPM.01')`, [actor])
}
