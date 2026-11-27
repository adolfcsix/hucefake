package vn.huce.repository;

import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Repository;

@Repository
public class RuntimeModeRepository {
    private final JdbcTemplate db;

    public RuntimeModeRepository(JdbcTemplate db) {
        this.db = db;
    }

    public void lock() {
        db.execute("LOCK TABLE fm05_runtime_mode IN EXCLUSIVE MODE");
    }

    public List<Boolean> savedMode() {
        return db.queryForList("SELECT demo FROM fm05_runtime_mode WHERE singleton=true", Boolean.class);
    }

    public boolean hasDemoSeed() {
        return exists("SELECT EXISTS(SELECT 1 FROM work_items WHERE kind='FM05_DEMO_SEED')");
    }

    public boolean hasExistingData() {
        return exists("""
                SELECT EXISTS(SELECT 1 FROM users) OR EXISTS(SELECT 1 FROM organizational_units)
                    OR EXISTS(SELECT 1 FROM campuses) OR EXISTS(SELECT 1 FROM semesters)
                """);
    }

    public void loadDemoCatalog() {
        db.execute((ConnectionCallback<Void>) connection -> {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/demo/V4__demo_catalog.sql"));
            return null;
        });
    }

    public void seedDemoAccountsAndSchedules(String hash) {
        List<DemoAccount> accounts = List.of(
                new DemoAccount("daotao", "STAFF"), new DemoAccount("quanly", "MANAGER"),
                new DemoAccount("giangvien", "LECTURER"), new DemoAccount("giangvien2", "LECTURER"),
                new DemoAccount("sinhvien", "STUDENT"), new DemoAccount("daotao2", "STAFF"));
        for (DemoAccount account : accounts) {
            db.update("""
                    INSERT INTO users(role_id,username,email,password_hash)
                    SELECT role_id,?,?,? FROM roles WHERE code=?
                    """, account.username(), account.username() + "@example.test", hash, account.role());
        }
        db.update("""
          INSERT INTO staff_profiles(user_id,code,unit_id,position)
          SELECT u.id,upper(u.username),o.id,r.name FROM users u JOIN roles r ON r.role_id=u.role_id
          JOIN organizational_units o ON o.code=CASE WHEN u.username='daotao2' THEN 'KT' ELSE 'CNTT' END
          WHERE r.code<>'STUDENT'
          """);
        db.update("""
          INSERT INTO staff_campuses(staff_id,campus_id)
          SELECT s.id,c.id FROM staff_profiles s CROSS JOIN campuses c
          WHERE s.code IN ('DAOTAO','DAOTAO2') AND c.code='CS1'
          """);
        long actor=db.queryForObject("SELECT id FROM users WHERE username='daotao'",Long.class);
        db.update("INSERT INTO work_items(kind,created_by,status) VALUES ('FM05_DEMO_SEED',?,'APPLIED')",actor);
        db.update("INSERT INTO non_teaching_days(start_date,end_date,name,created_by) VALUES ('2026-09-02','2026-09-02','Nghỉ Quốc khánh',?)",actor);
        db.update("""
          INSERT INTO course_sections(code,subject_id,semester_id,capacity,sessions_per_week,periods_per_session,required_room_type,start_date,end_date,created_by)
          SELECT v.code,s.id,h.id,v.capacity,v.sessions,2,v.room,h.start_date,h.end_date,?
          FROM (VALUES ('CSDL.01','CSDL',40,2,'COMPUTER_LAB'),('KTPM.01','KTPM',55,2,'LECTURE'),('MANG.01','MANG',35,1,'LECTURE')) v(code,subject,capacity,sessions,room)
          JOIN subjects s ON s.code=v.subject JOIN semesters h ON h.code='2026-2027.1'
          """,actor);
        db.update("""
          INSERT INTO section_assignments(section_id,staff_id,role,valid_from,assigned_by)
          SELECT c.id,g.id,'MAIN',c.start_date,? FROM course_sections c JOIN staff_profiles g
          ON g.code=CASE WHEN c.code='MANG.01' THEN 'GIANGVIEN2' ELSE 'GIANGVIEN' END
          WHERE c.created_by=? AND c.code IN ('CSDL.01','KTPM.01','MANG.01')
          """,actor,actor);
        db.update("""
          INSERT INTO section_target_groups(section_id,group_id)
          SELECT c.id,g.id FROM course_sections c JOIN academic_groups g ON g.code='CNTT-K26'
          WHERE c.created_by=? AND c.code IN ('CSDL.01','KTPM.01','MANG.01')
          """,actor);
        db.update("""
          INSERT INTO section_enrollments(section_id,student_user_id)
          SELECT c.id,u.id FROM course_sections c JOIN users u ON u.username='sinhvien'
          WHERE c.created_by=? AND c.code IN ('CSDL.01','KTPM.01')
          """,actor);
    }

    public void saveMode(boolean demo) {
        db.update("INSERT INTO fm05_runtime_mode(demo) VALUES (?)", demo);
    }

    private boolean exists(String sql) {
        return Boolean.TRUE.equals(db.queryForObject(sql, Boolean.class));
    }

    private record DemoAccount(String username, String role) {
    }
}
