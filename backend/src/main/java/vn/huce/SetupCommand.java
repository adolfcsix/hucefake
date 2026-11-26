package vn.huce;

import static vn.huce.Inputs.*;
import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

/** Local operator CLI; never exposed over HTTP. */
final class SetupCommand {
    static int run(String[] args){
        try{
            if("true".equalsIgnoreCase(System.getenv("DEMO")))throw invalid("Đặt DEMO=false và chọn database riêng trước khi thiết lập bản trắng.");
            if(args.length<2)throw invalid("Dùng setup catalog <tệp.json> hoặc setup account --username <tên> --email <email> --role <vai trò>.");
            String url=System.getenv("DB_URL");
            if(url==null||!url.startsWith("jdbc:postgresql:"))throw invalid("Cần đặt DB_URL JDBC PostgreSQL của database riêng.");
            var source=new DriverManagerDataSource(url,env("DB_USER","fm05"),env("DB_PASSWORD","fm05_local"));
            Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
            var store=new SqlStore(new JdbcTemplate(source));
            var bcrypt=new BCryptPasswordEncoder();
            var tx=new TransactionTemplate(new DataSourceTransactionManager(source));
            if("catalog".equals(args[1])&&args.length==3){
                String content=Files.readString(Path.of(args[2]),StandardCharsets.UTF_8).replaceFirst("^\uFEFF","");
                var data=new ObjectMapper().readValue(content,new TypeReference<Map<String,Object>>(){});
                if(!"fm05-local".equals(data.get("format"))||number(data.get("version"),1,1)!=1)throw invalid("Tệp danh mục cần định dạng fm05-local phiên bản 1.");
                tx.executeWithoutResult(status->{guard(store,bcrypt);catalog(store,data);});
                System.out.println("Đã nạp dữ liệu nền. Các mã đã tồn tại được giữ nguyên.");
            }else if("account".equals(args[1])){
                var options=options(args);String password=password();
                tx.executeWithoutResult(status->{guard(store,bcrypt);account(store,bcrypt,options,password);});
                System.out.println("Đã tạo tài khoản "+options.get("username")+".");
            }else throw invalid("Lệnh setup không hợp lệ.");
            return 0;
        }catch(ApiFailure e){System.err.println(e.getMessage());return 1;}
        catch(org.springframework.dao.DataIntegrityViolationException e){System.err.println("Dữ liệu trùng hoặc vi phạm ràng buộc. Không có thay đổi một phần.");return 1;}
        catch(Exception e){System.err.println("Không thể thiết lập dữ liệu. Kiểm tra cấu hình kết nối, chế độ database và tệp đầu vào ("+e.getClass().getSimpleName()+").");return 1;}
    }
    static String env(String key,String fallback){String value=System.getenv(key);return value==null?fallback:value;}
    static void guard(SqlStore db,BCryptPasswordEncoder bcrypt){
        db.db.execute("SELECT pg_advisory_xact_lock(5060505)");
        new RuntimeMode(db.db,bcrypt,false).run(null);
    }
    static String code(Object x){return text(x,80).toUpperCase(Locale.ROOT);}
    static List<Map<?,?>> list(Map<String,Object> data,String key){
        Object raw=data.getOrDefault(key,List.of());
        if(!(raw instanceof List<?> values))throw invalid(key+" phải là danh sách.");
        var rows=new ArrayList<Map<?,?>>();for(Object item:values){if(!(item instanceof Map<?,?> row))throw invalid(key+" có dòng không hợp lệ.");rows.add(row);}return rows;
    }
    static long reference(SqlStore db,String sql,String label,Object... args){
        var row=db.one(sql,args);if(row==null)throw invalid("Không tìm thấy "+label+".");return n(row,"id");
    }
    static void catalog(SqlStore db,Map<String,Object> data){
        for(var row:list(data,"units"))db.db.update("INSERT INTO organizational_units(code,name) VALUES (?,?) ON CONFLICT(code) DO NOTHING",code(row.get("code")),text(row.get("name"),150));
        for(var row:list(data,"campuses"))db.db.update("INSERT INTO campuses(code,name) VALUES (?,?) ON CONFLICT(code) DO NOTHING",code(row.get("code")),text(row.get("name"),150));
        for(var row:list(data,"semesters")){
            String year=code(row.get("yearCode")),from=date(row.get("startDate")),to=date(row.get("endDate"));
            if(from.compareTo(to)>0)throw invalid("Ngày kết thúc học kỳ phải từ ngày bắt đầu trở đi.");
            db.db.update("INSERT INTO academic_years(code) VALUES (?) ON CONFLICT(code) DO NOTHING",year);
            db.db.update("INSERT INTO semesters(year_id,code,start_date,end_date) SELECT id,?,?,? FROM academic_years WHERE code=? ON CONFLICT(code) DO NOTHING",code(row.get("code")),Date.valueOf(from),Date.valueOf(to),year);
        }
        for(var row:list(data,"subjects")){
            long unit=reference(db,"SELECT id FROM organizational_units WHERE code=?","khoa "+row.get("unitCode"),code(row.get("unitCode")));
            db.db.update("INSERT INTO subjects(code,name,managing_unit_id,credits) VALUES (?,?,?,?) ON CONFLICT(code) DO NOTHING",code(row.get("code")),text(row.get("name"),150),unit,number(row.get("credits"),1,30));
        }
        for(var row:list(data,"groups")){
            String curriculum=code(row.get("curriculumCode"));
            db.db.update("INSERT INTO curriculum_versions(code) VALUES (?) ON CONFLICT(code) DO NOTHING",curriculum);
            db.db.update("INSERT INTO academic_groups(code,curriculum_id,capacity) SELECT ?,id,? FROM curriculum_versions WHERE code=? ON CONFLICT(code) DO NOTHING",code(row.get("code")),number(row.get("capacity"),1,10000),curriculum);
        }
        for(var row:list(data,"enrollments")){
            long student=reference(db,"SELECT u.id FROM users u JOIN roles r ON r.role_id=u.role_id WHERE u.username=? AND r.code='STUDENT' AND u.status='ACTIVE'","sinh viên "+row.get("username"),text(row.get("username"),100));
            long section=reference(db,"SELECT c.id FROM course_sections c JOIN semesters s ON s.id=c.semester_id WHERE c.code=? AND s.code=? AND c.status<>'CANCELLED'","lớp học phần "+row.get("sectionCode"),code(row.get("sectionCode")),code(row.get("semesterCode")));
            db.db.update("INSERT INTO section_enrollments(section_id,student_user_id) VALUES (?,?) ON CONFLICT(section_id,student_user_id) DO NOTHING",section,student);
        }
    }
    static Map<String,String> options(String[] args){
        var out=new LinkedHashMap<String,String>();
        if((args.length-2)%2!=0)throw invalid("Thiếu giá trị tùy chọn.");
        for(int i=2;i<args.length;i+=2){
            String key=args[i];if(!Set.of("--username","--email","--role","--unit","--campus","--code").contains(key)||out.putIfAbsent(key.substring(2),args[i+1])!=null)throw invalid("Tùy chọn không hợp lệ hoặc bị lặp.");
        }
        return out;
    }
    static String password(){
        String password=System.getenv("FM05_SETUP_PASSWORD");
        if(password==null){
            var console=System.console();if(console==null)throw invalid("Mở terminal tương tác hoặc đặt FM05_SETUP_PASSWORD cho phiên chạy.");
            char[] first=console.readPassword("Mật khẩu tài khoản mới (ẩn): "),second=console.readPassword("Nhập lại mật khẩu: ");
            try{if(first==null||second==null||!Arrays.equals(first,second))throw invalid("Hai mật khẩu chưa khớp.");password=new String(first);}
            finally{if(first!=null)Arrays.fill(first,'\0');if(second!=null)Arrays.fill(second,'\0');}
        }
        if(password.codePointCount(0,password.length())<10||password.getBytes(StandardCharsets.UTF_8).length>72)throw invalid("Mật khẩu cần ít nhất 10 ký tự và tối đa 72 byte.");
        return password;
    }
    static void account(SqlStore db,BCryptPasswordEncoder bcrypt,Map<String,String> opts,String password){
        String username=text(opts.get("username"),100),email=text(opts.get("email"),200),role=choice(opts.get("role"),"MANAGER","STAFF","LECTURER","STUDENT");
        if(!email.matches("[^\\s@]+@[^\\s@]+"))throw invalid("Email không hợp lệ.");
        boolean profile=Set.of("STAFF","LECTURER").contains(role);
        Long unit=profile?reference(db,"SELECT id FROM organizational_units WHERE code=?","khoa "+opts.get("unit"),code(opts.get("unit"))):null;
        Long campus="STAFF".equals(role)?reference(db,"SELECT id FROM campuses WHERE code=? AND status='ACTIVE'","cơ sở "+opts.get("campus"),code(opts.get("campus"))):null;
        long user=n(db.need("INSERT INTO users(role_id,username,email,password_hash) SELECT role_id,?,?,? FROM roles WHERE code=? RETURNING id",username,email,bcrypt.encode(password),role),"id");
        if(profile){
            long staff=n(db.need("INSERT INTO staff_profiles(user_id,code,unit_id) VALUES (?,?,?) RETURNING id",user,code(opts.getOrDefault("code",username)),unit),"id");
            if(campus!=null)db.db.update("INSERT INTO staff_campuses(staff_id,campus_id) VALUES (?,?)",staff,campus);
        }
    }
}
