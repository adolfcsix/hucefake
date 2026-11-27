package vn.huce.service;

import java.sql.Date;
import java.util.*;
import org.springframework.stereotype.Service;
import vn.huce.Accounts;
import vn.huce.exception.ApiFailure;
import vn.huce.repository.SqlStore;
import static vn.huce.repository.SectionQueries.*;
import static vn.huce.validation.Inputs.*;

@Service
public class SectionService {
    private final SqlStore db;

    public SectionService(SqlStore db) {
        this.db = db;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Object sections(Accounts.Actor a, Map<String,String> q){
        role(a,"STAFF","MANAGER");
        String status=q.getOrDefault("status",""),subject=q.getOrDefault("subjectId","");
        Long unit="STAFF".equals(a.role())?(a.unitId()==null?-1L:a.unitId()):null;
        String search="%"+q.getOrDefault("search","")+"%";
        return db.paged(q,SECTION_SQL+"""
          WHERE c.semester_id=? AND (?::bigint IS NULL OR s.managing_unit_id=?)
          AND (?::text IS NULL OR c.status=?) AND (?::bigint IS NULL OR c.subject_id=?)
          AND (c.code ILIKE ? OR s.name ILIKE ?)
          """,Arrays.asList(id(q.get("semesterId")),unit,unit,status.isEmpty()?null:status,status.isEmpty()?null:status,subject.isEmpty()?null:id(subject),subject.isEmpty()?null:id(subject),search,search),"code,id");
    }
    @org.springframework.transaction.annotation.Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Object patterns(Accounts.Actor a, Map<String,String> q){
        role(a,"STAFF","MANAGER");
        return db.paged(q,PATTERN_SQL+" WHERE c.semester_id=?",List.of(id(q.get("semesterId"))),"day_of_week,period_from,id");
    }
    public void unlocked(long semester){if(db.exists("SELECT 1 FROM timetable_versions WHERE semester_id=? AND status='SUBMITTED'",semester))throw state("Học kỳ đang chờ duyệt. Hãy xử lý hồ sơ trước khi chỉnh lịch.");}
    public Map<String,Object> editable(Accounts.Actor a,long id){
        var row=db.need(SECTION_SQL+" WHERE c.id=?",id);
        unit(a,row.get("managing_unit_id"));
        if(!"DRAFT".equals(row.get("status")))throw state("Chỉ chỉnh lớp nháp.");
        unlocked(n(row,"semester_id"));return row;
    }
    public record Form(long sem,long subject,String code,long capacity,long count,long length,String type,String from,String to){}
    public Form values(Map<String,Object> sem,Map<String,Object> subject,Map<String,Object> f){
        if(!"ACTIVE".equals(sem.get("status"))||!"ACTIVE".equals(subject.get("status")))throw invalid("Học kỳ và học phần phải đang hoạt động.");
        String from=date(f.get("startDate")),to=date(f.get("endDate"));
        if(from.compareTo(to)>0||from.compareTo(s(sem,"start_date"))<0||to.compareTo(s(sem,"end_date"))>0)throw invalid("Khoảng ngày phải nằm trong học kỳ.");
        return new Form(n(sem,"id"),n(subject,"id"),text(f.get("code"),40).toUpperCase(Locale.ROOT),number(f.get("capacity"),1,10000),number(f.get("sessionsPerWeek"),1,6),number(f.get("periodsPerSession"),1,6),choice(f.get("requiredRoomType"),"LECTURE","COMPUTER_LAB","LAB","STUDIO"),from,to);
    }
    public Form form(Accounts.Actor a,Map<String,Object> f){
        var sem=db.need("SELECT * FROM semesters WHERE id=?",id(f.get("semesterId")));
        var subject=db.need("SELECT * FROM subjects WHERE id=?",id(f.get("subjectId")));
        unit(a,subject.get("managing_unit_id"));return values(sem,subject,f);
    }
    public Map<String,Object> previewDraft(Accounts.Actor a,Map<String,Object> input){
        role(a,"STAFF");var f=form(a,input);unlocked(f.sem);
        if(db.exists("SELECT 1 FROM course_sections WHERE semester_id=? AND code=?",f.sem,f.code))throw new ApiFailure(409,"DUPLICATE_REQUEST","Mã lớp đã có trong học kỳ này.",Map.of("field","code"));
        return Map.of("semesterId",f.sem,"subjectId",f.subject,"code",f.code,"capacity",f.capacity,"sessionsPerWeek",f.count,"periodsPerSession",f.length,"requiredRoomType",f.type,"startDate",f.from,"endDate",f.to);
    }
    public Object saveDraft(Accounts.Actor a,Map<String,Object> input){role(a,"STAFF");return create(a,form(a,input));}
    public Object create(Accounts.Actor a,Form f){
        unlocked(f.sem);
        if(db.exists("SELECT 1 FROM course_sections WHERE semester_id=? AND code=?",f.sem,f.code))throw new ApiFailure(409,"DUPLICATE_REQUEST","Mã lớp đã có trong học kỳ này.",Map.of("field","code"));
        var row=db.need("""
          INSERT INTO course_sections(semester_id,subject_id,code,capacity,sessions_per_week,periods_per_session,required_room_type,start_date,end_date,created_by)
          VALUES (?,?,?,?,?,?,?,?,?,?) RETURNING id
          """,f.sem,f.subject,f.code,f.capacity,f.count,f.length,f.type,Date.valueOf(f.from),Date.valueOf(f.to),a.id());
        db.audit(a,"CREATE","course_sections",n(row,"id"));return row;
    }
}
