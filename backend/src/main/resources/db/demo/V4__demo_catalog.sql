INSERT INTO organizational_units(code,name) VALUES ('CNTT','Công nghệ thông tin'),('KT','Kinh tế');
INSERT INTO academic_years(code) VALUES ('2026-2027');
INSERT INTO semesters(year_id,code,start_date,end_date)
SELECT id,'2026-2027.1','2026-08-31','2026-12-26' FROM academic_years;
INSERT INTO subjects(code,name,managing_unit_id,credits)
SELECT v.code,v.name,u.id,3 FROM (VALUES ('CSDL','Cơ sở dữ liệu','CNTT'),('KTPM','Kỹ thuật phần mềm','CNTT'),
  ('MANG','Mạng máy tính','CNTT'),('KTVM','Kinh tế vi mô','KT')) v(code,name,unit) JOIN organizational_units u ON u.code=v.unit;
INSERT INTO curriculum_versions(code) VALUES ('CNTT-2026'),('KT-2026');
INSERT INTO academic_groups(code,curriculum_id,capacity)
SELECT left(code,strpos(code,'-')-1)||'-K26',id,55 FROM curriculum_versions;
INSERT INTO campuses(code,name) VALUES ('CS1','Cơ sở chính');
INSERT INTO teaching_rooms(campus_id,code,name,capacity,type)
SELECT c.id,v.code,v.name,v.capacity,v.type FROM campuses c CROSS JOIN (VALUES
    ('A101','Giảng đường A101',60,'LECTURE'),('A203','Giảng đường A203',80,'LECTURE'),
    ('M301','Phòng máy M301',45,'COMPUTER_LAB'),('M302','Phòng máy M302',55,'COMPUTER_LAB'),
    ('TN01','Phòng thí nghiệm TN01',40,'LAB')) v(code,name,capacity,type);
INSERT INTO period_definitions(campus_id,period_no,start_time,end_time,shift,effective_from)
SELECT c.id,v.no,v.start_at::time,v.end_at::time,CASE WHEN v.no<=6 THEN 'MORNING' ELSE 'AFTERNOON' END,'2025-08-01'
FROM campuses c CROSS JOIN (VALUES
 (1,'07:00','07:50'),(2,'07:55','08:45'),(3,'08:50','09:40'),(4,'09:50','10:40'),
 (5,'10:45','11:35'),(6,'11:40','12:30'),(7,'12:45','13:35'),(8,'13:40','14:30'),
 (9,'14:35','15:25'),(10,'15:35','16:25'),(11,'16:30','17:20'),(12,'17:25','18:15')) v(no,start_at,end_at);
