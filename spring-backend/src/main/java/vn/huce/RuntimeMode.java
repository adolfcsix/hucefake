package vn.huce;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class RuntimeMode implements ApplicationRunner {
    private final JdbcTemplate db;
    private final BCryptPasswordEncoder bcrypt;
    private final boolean demo;
    RuntimeMode(JdbcTemplate db,BCryptPasswordEncoder bcrypt,@Value("${huce.demo}") boolean demo){
        this.db=db;this.bcrypt=bcrypt;this.demo=demo;
    }
    @Override @Transactional public void run(ApplicationArguments args){
        db.execute("LOCK TABLE fm05_runtime_mode IN EXCLUSIVE MODE");
        var saved=db.queryForList("SELECT demo FROM fm05_runtime_mode WHERE singleton=true",Boolean.class);
        if(!saved.isEmpty() && saved.getFirst()!=demo)throw new IllegalStateException("Database thuộc chế độ khác.");
        if(!demo && exists("SELECT EXISTS(SELECT 1 FROM work_items WHERE kind='FM05_DEMO_SEED')"))throw new IllegalStateException("Không dùng database demo cho dữ liệu riêng.");
        if(!saved.isEmpty())return;
        if(demo){
            if(exists("SELECT EXISTS(SELECT 1 FROM users) OR EXISTS(SELECT 1 FROM organizational_units) OR EXISTS(SELECT 1 FROM campuses) OR EXISTS(SELECT 1 FROM semesters)"))
              throw new IllegalStateException("Không bật demo trên database đã có dữ liệu.");
            db.execute((ConnectionCallback<Void>) con->{ScriptUtils.executeSqlScript(con,new ClassPathResource("db/demo/V4__demo_catalog.sql"));return null;});
            String hash=bcrypt.encode("Fm05Demo@2026");
            String[][] accounts={{"daotao","STAFF"},{"quanly","MANAGER"},{"giangvien","LECTURER"},{"giangvien2","LECTURER"},{"sinhvien","STUDENT"},{"daotao2","STAFF"}};
            for(var row:accounts)db.update("INSERT INTO users(role_id,username,email,password_hash) SELECT role_id,?,?,? FROM roles WHERE code=?",row[0],row[0]+"@example.test",hash,row[1]);
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
        }
        db.update("INSERT INTO fm05_runtime_mode(demo) VALUES (?)",demo);
    }
    private boolean exists(String sql){return Boolean.TRUE.equals(db.queryForObject(sql,Boolean.class));}
}
