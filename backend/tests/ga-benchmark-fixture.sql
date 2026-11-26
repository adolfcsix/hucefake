-- Disposable CI-only fixture. Separate term; no changes to real data.
BEGIN;
INSERT INTO academic_years(code) VALUES ('QA-BENCH-2028');
INSERT INTO semesters(year_id,code,start_date,end_date)
SELECT id,'QA-BENCH.1','2028-09-04','2028-12-23' FROM academic_years WHERE code='QA-BENCH-2028';
INSERT INTO teaching_rooms(campus_id,code,name,capacity,type)
SELECT c.id,'QA-BENCH-R'||i,'Benchmark room '||i,40,'LECTURE'
FROM campuses c CROSS JOIN generate_series(1,8) i WHERE c.code='CS1';
INSERT INTO users(role_id,username,email,password_hash)
SELECT r.role_id,'qa_bench_teacher_'||i,'bench'||i||'@example.test',u.password_hash
FROM roles r CROSS JOIN generate_series(1,16) i CROSS JOIN users u
WHERE r.code='LECTURER' AND u.username='giangvien';
INSERT INTO staff_profiles(user_id,code,unit_id)
SELECT u.id,upper(u.username),o.id FROM users u CROSS JOIN organizational_units o
WHERE u.username LIKE 'qa_bench_teacher_%' AND o.code='CNTT';
INSERT INTO academic_groups(code,curriculum_id,capacity)
SELECT 'QA-BENCH-G'||i,c.id,40 FROM curriculum_versions c CROSS JOIN generate_series(1,12) i WHERE c.code='CNTT-2026';
INSERT INTO course_sections(semester_id,subject_id,code,capacity,sessions_per_week,periods_per_session,required_room_type,start_date,end_date,created_by)
SELECT h.id,s.id,'QA-BENCH-C'||i,40,2,2,'LECTURE',h.start_date,h.end_date,u.id
FROM semesters h CROSS JOIN subjects s CROSS JOIN users u CROSS JOIN generate_series(1,48) i
WHERE h.code='QA-BENCH.1' AND s.code='KTPM' AND u.username='daotao';
INSERT INTO section_assignments(section_id,staff_id,role,valid_from,assigned_by)
SELECT c.id,p.id,'MAIN',c.start_date,c.created_by FROM course_sections c
JOIN staff_profiles p ON p.code='QA_BENCH_TEACHER_'||(((substring(c.code from '[0-9]+$')::int-1)%16)+1)
WHERE c.code LIKE 'QA-BENCH-C%';
INSERT INTO section_target_groups(section_id,group_id)
SELECT c.id,g.id FROM course_sections c
JOIN academic_groups g ON g.code='QA-BENCH-G'||(((substring(c.code from '[0-9]+$')::int-1)%12)+1)
WHERE c.code LIKE 'QA-BENCH-C%';
INSERT INTO staff_availability(staff_id,semester_id,day_of_week,period_from,period_to,kind)
SELECT p.id,h.id,1,1,2,'BUSY' FROM staff_profiles p CROSS JOIN semesters h
WHERE p.code LIKE 'QA_BENCH_TEACHER_%' AND h.code='QA-BENCH.1';
COMMIT;
