package vn.huce.service;

import java.util.*;
import java.time.*;
import java.sql.Date;
import java.sql.Time;
import org.springframework.stereotype.Service;
import vn.huce.Accounts;
import vn.huce.exception.ApiFailure;
import vn.huce.repository.SqlStore;
import static vn.huce.validation.Inputs.*;

@Service
public class CatalogMutationService {
    private final SqlStore db;
    private final SchedulingService scheduling;

    public CatalogMutationService(SqlStore db, SchedulingService scheduling) {
        this.db = db;
        this.scheduling = scheduling;
    }

    private ApiFailure conflict(Object details){return new ApiFailure(409,"SCHEDULE_CONFLICT","Lịch dự kiến có xung đột.",details);}
    public Object room(Accounts.Actor a,Map<String,Object> f,Long record){
        role(a, "STAFF");
        long campus=id(f.get("campusId"));scheduling.requireCampus(a,campus);
        String code=text(f.get("code"),30).toUpperCase(Locale.ROOT),name=text(f.get("name"),100);
        long capacity=number(f.get("capacity"),1,10000);
        String type=choice(f.get("type"),"LECTURE","COMPUTER_LAB","LAB","STUDIO"),status=choice(f.get("status"),"ACTIVE","MAINTENANCE","INACTIVE");
        Map<String,Object> result;
        if(record==null)result=db.need("INSERT INTO teaching_rooms(campus_id,code,name,capacity,type,status) VALUES (?,?,?,?,?,?) RETURNING id",campus,code,name,capacity,type,status);
        else{
            var current=db.need("SELECT * FROM teaching_rooms WHERE id=?",record);
            if(n(current,"campus_id")!=campus)throw invalid("Không thể chuyển cơ sở của phòng đã lưu.");
            var issues=db.rows("""
              SELECT x.id,c.code AS section_code,to_char(x.start_at,'YYYY-MM-DD HH24:MI') AS start_at
              FROM class_sessions x JOIN course_sections c ON c.id=x.section_id WHERE x.room_id=? AND x.status='PLANNED' AND x.start_at>=CURRENT_DATE
                AND (?<>'ACTIVE' OR c.capacity>? OR c.required_room_type<>?)
              """,record,status,capacity,type);
            var patterns=db.rows("""
              SELECT p.id,c.code AS section_code FROM section_schedule_patterns p JOIN course_sections c ON c.id=p.section_id
              WHERE p.room_id=? AND c.status='DRAFT' AND (?<>'ACTIVE' OR c.capacity>? OR c.required_room_type<>?)
              """,record,status,capacity,type);
            if(!issues.isEmpty()||!patterns.isEmpty())throw conflict(Map.of("issues",issues,"patterns",patterns));
            result=db.need("UPDATE teaching_rooms SET code=?,name=?,capacity=?,type=?,status=? WHERE id=? RETURNING id",code,name,capacity,type,status,record);
        }
        db.audit(a,"SAVE","teaching_rooms",n(result,"id"));return result;
    }
    private void holidayScope(Accounts.Actor a,Long campus){
        if(campus!=null){scheduling.requireCampus(a,campus);return;}
        if(scheduling.campuses(a).size()!=db.jdbc().queryForObject("SELECT count(*) FROM campuses WHERE status='ACTIVE'",Long.class))
            throw new ApiFailure(403,"FORBIDDEN","Bạn không có quyền trong phạm vi này.");
    }
    public Object holiday(Accounts.Actor a,Map<String,Object> f,Long record){
        role(a, "STAFF");
        Long campus=f.get("campusId")==null?null:id(f.get("campusId"));
        holidayScope(a,campus);
        String from=date(f.get("startDate")),to=date(f.get("endDate")),name=text(f.get("name"),150);
        if(from.compareTo(to)>0)throw invalid("Khoảng ngày không hợp lệ.");
        var issues=db.rows("""
          SELECT x.id,c.code AS section_code,to_char(x.start_at,'YYYY-MM-DD HH24:MI') AS start_at FROM class_sessions x
          JOIN course_sections c ON c.id=x.section_id LEFT JOIN teaching_rooms r ON r.id=x.room_id
          WHERE (?::bigint IS NULL OR r.campus_id=?) AND x.status='PLANNED' AND x.start_at>=?::date AND x.start_at<?::date+1
          """,campus,campus,Date.valueOf(from),Date.valueOf(to));
        if(!issues.isEmpty())throw conflict(Map.of("issues",issues));
        Map<String,Object> result;
        if(record!=null){
            var current=db.need("SELECT * FROM non_teaching_days WHERE id=?",record);holidayScope(a,SchedulingService.nullableId(current.get("campus_id")));
            result=db.need("UPDATE non_teaching_days SET campus_id=?,start_date=?,end_date=?,name=? WHERE id=? RETURNING id",campus,Date.valueOf(from),Date.valueOf(to),name,record);
        }else result=db.need("INSERT INTO non_teaching_days(campus_id,start_date,end_date,name,created_by) VALUES (?,?,?,?,?) RETURNING id",campus,Date.valueOf(from),Date.valueOf(to),name,a.id());
        db.audit(a,"SAVE","non_teaching_days",n(result,"id"));return result;
    }
    private LocalTime time(Object x){
        if(!(x instanceof String s)||!s.matches("([01][0-9]|2[0-3]):[0-5][0-9](:[0-5][0-9])?"))throw invalid("Giờ không hợp lệ.");
        return LocalTime.parse(s);
    }

    public Object createPeriod(Accounts.Actor a, Map<String,Object> f) {
        role(a, "STAFF");
        long campus=id(f.get("campusId")),period=number(f.get("periodNo"),1,15);
        var from=LocalDate.parse(date(f.get("effectiveFrom")));
        var start=time(f.get("startTime"));var end=time(f.get("endTime"));
        String shift=choice(f.get("shift"),"MORNING","AFTERNOON","EVENING");
        scheduling.requireCampus(a,campus);
        if(!start.isBefore(end))throw invalid("Giờ bắt đầu phải trước giờ kết thúc.");
        var current=db.one("SELECT * FROM period_definitions WHERE campus_id=? AND period_no=? ORDER BY effective_from DESC LIMIT 1",campus,period);
        if(current!=null&&(!from.isAfter(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")))||!from.isAfter(SchedulingService.day(current.get("effective_from")))))throw invalid("Khung tiết mới phải có ngày hiệu lực trong tương lai và sau khung hiện có.");
        var issues=db.rows("""
          SELECT x.id,c.code AS section_code FROM class_sessions x JOIN teaching_rooms t ON t.id=x.room_id
          JOIN course_sections c ON c.id=x.section_id WHERE t.campus_id=? AND x.status='PLANNED' AND x.start_at>=?::date
          AND ? BETWEEN x.period_from AND x.period_from+x.period_count-1
          """,campus,Date.valueOf(from),period);
        if(!issues.isEmpty())throw conflict(Map.of("issues",issues));
        if(db.exists("""
          SELECT 1 FROM period_definitions WHERE campus_id=? AND period_no<>? AND status='ACTIVE'
          AND (effective_to IS NULL OR effective_to>=?::date)
          AND ((period_no<? AND end_time>?::time) OR (period_no>? AND start_time<?::time))
          """,campus,period,Date.valueOf(from),period,Time.valueOf(start),period,Time.valueOf(end)))throw invalid("Giờ của tiết mới chồng lấn hoặc sai thứ tự.");
        if(current!=null)db.jdbc().update("UPDATE period_definitions SET effective_to=?::date-1 WHERE id=?",Date.valueOf(from),n(current,"id"));
        var result=db.need("INSERT INTO period_definitions(campus_id,period_no,start_time,end_time,shift,effective_from) VALUES (?,?,?,?,?,?) RETURNING id",campus,period,Time.valueOf(start),Time.valueOf(end),shift,Date.valueOf(from));
        var ctx=scheduling.context();
        for(var slot:ctx.patterns())if(n(ctx.rooms().get(slot.room()),"campus_id")==campus&&period>=slot.first()&&period<=slot.last()&&SchedulingService.occurrences(slot).stream().anyMatch(d->!d.isBefore(from)))scheduling.verify(ctx,slot,a);
        db.audit(a,"CREATE","period_definitions",n(result,"id"));return result;
    }
}
