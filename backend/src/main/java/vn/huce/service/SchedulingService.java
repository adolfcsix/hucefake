package vn.huce.service;

import vn.huce.Accounts;
import vn.huce.exception.ApiFailure;
import vn.huce.repository.SqlStore;
import vn.huce.repository.SectionQueries;

import static vn.huce.validation.Inputs.*;
import java.util.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.sql.Timestamp;
import org.springframework.stereotype.Service;

@Service
public class SchedulingService {
    private final SqlStore db;
    SchedulingService(SqlStore db){this.db=db;}
    public record Slot(long id,long section,Long staff,long room,int day,int first,int count,LocalDate from,LocalDate to){
        public int last(){return first+count-1;}
        Slot move(long r,int d,int p){return new Slot(id,section,staff,r,d,p,count,from,to);}
    }
    public record Span(LocalDateTime start,LocalDateTime end){}
    public record Context(Map<Long,Map<String,Object>> sections,Map<Long,Map<String,Object>> rooms,List<Map<String,Object>> periods,List<Map<String,Object>> availability,List<Map<String,Object>> holidays,List<Slot> patterns){}
    public record Result(boolean valid,List<Map<String,Object>> issues,List<Map<String,Object>> suggestions){}
    public static LocalDate day(Object value){return LocalDate.parse(value.toString());}
    public static Long nullableId(Object value){return value==null?null:((Number)value).longValue();}
    public static Set<Long> ids(Object value){
        var out=new LinkedHashSet<Long>();
        if(value instanceof Object[] array)for(Object v:array)out.add(nullableId(v));
        else if(value instanceof Collection<?> list)for(Object v:list)out.add(nullableId(v));
        return out;
    }
    public static Map<Long,Map<String,Object>> index(List<Map<String,Object>> rows){
        var out=new LinkedHashMap<Long,Map<String,Object>>();for(var r:rows)out.put(n(r,"id"),r);return out;
    }
    public static LocalDate firstDay(LocalDate from,int day){return from.plusDays(Math.floorMod(day-from.getDayOfWeek().getValue(),7));}
    public static List<LocalDate> occurrences(Slot slot){
        var out=new ArrayList<LocalDate>();for(var d=firstDay(slot.from,slot.day);!d.isAfter(slot.to);d=d.plusWeeks(1))out.add(d);return out;
    }
    public static Slot slot(Map<String,Object> r){return new Slot(n(r,"id"),n(r,"section_id"),nullableId(r.get("staff_id")),n(r,"room_id"),(int)n(r,"day_of_week"),(int)n(r,"period_from"),(int)n(r,"period_count"),day(r.get("effective_from")),day(r.get("effective_to")));}
    public Context context(){
        return new Context(index(db.rows(SectionQueries.SECTION_SQL+" WHERE c.status IN ('OPEN','DRAFT') ORDER BY c.id")),
          index(db.rows("""
            SELECT r.id,r.campus_id,r.code,r.capacity,r.type,CASE WHEN c.status='ACTIVE' THEN r.status ELSE 'INACTIVE' END AS status
            FROM teaching_rooms r JOIN campuses c ON c.id=r.campus_id ORDER BY r.id
            """)),db.rows("SELECT * FROM period_definitions WHERE status='ACTIVE' ORDER BY effective_from DESC"),
          db.rows("SELECT * FROM staff_availability ORDER BY id"),db.rows("SELECT * FROM non_teaching_days"),
          db.rows("SELECT p.* FROM section_schedule_patterns p JOIN course_sections c ON c.id=p.section_id WHERE c.status='DRAFT' ORDER BY p.id").stream().map(SchedulingService::slot).toList());
    }
    public Set<Long> campuses(Accounts.Actor a){
        var rows="STAFF".equals(a.role())?db.rows("SELECT campus_id AS id FROM staff_campuses JOIN campuses c ON c.id=campus_id WHERE staff_id=? AND c.status='ACTIVE'",a.staffId()):db.rows("SELECT id FROM campuses WHERE status='ACTIVE'");
        var out=new LinkedHashSet<Long>();for(var r:rows)out.add(n(r,"id"));return out;
    }
    public void requireCampus(Accounts.Actor a,long campus){if(!campuses(a).contains(campus))throw new ApiFailure(403,"FORBIDDEN","Bạn không có quyền trong phạm vi này.");}
    public Slot candidate(Accounts.Actor a,Context ctx,Map<String,Object> f,Long priorId,Map<String,Object> section){
        Slot prior=priorId==null?null:ctx.patterns.stream().filter(p->p.id==priorId).findFirst().orElseThrow(()->new ApiFailure(404,"NOT_FOUND","Không tìm thấy dữ liệu."));
        if(prior!=null&&prior.section!=n(section,"id"))throw invalid("Không thể chuyển lịch sang lớp khác.");
        long roomId=id(f.get("roomId"));var room=ctx.rooms.get(roomId);
        if(room==null)throw new ApiFailure(404,"NOT_FOUND","Không tìm thấy dữ liệu.");
        requireCampus(a,n(room,"campus_id"));
        if(prior!=null)requireCampus(a,n(ctx.rooms.get(prior.room),"campus_id"));
        var from=f.get("effectiveFrom")==null||"".equals(f.get("effectiveFrom"))?day(section.get("start_date")):day(date(f.get("effectiveFrom")));
        var to=f.get("effectiveTo")==null||"".equals(f.get("effectiveTo"))?day(section.get("end_date")):day(date(f.get("effectiveTo")));
        return new Slot(prior==null?0:prior.id,n(section,"id"),nullableId(section.get("staff_id")),roomId,(int)number(f.get("dayOfWeek"),1,7),(int)number(f.get("periodFrom"),1,15),(int)n(section,"periods_per_session"),from,to);
    }
    public boolean fits(Map<String,Object> room,Map<String,Object> section){return room!=null&&section!=null&&"ACTIVE".equals(room.get("status"))&&n(room,"capacity")>=n(section,"capacity")&&Objects.equals(room.get("type"),section.get("required_room_type"));}
    public Span interval(Context ctx,Slot slot,LocalDate date){
        var room=ctx.rooms.get(slot.room);if(room==null||slot.first<1||slot.count<1||slot.count>6||slot.last()>15)return null;
        LocalTime start=null,end=null;String shift=null;
        for(int i=slot.first;i<=slot.last();i++){
            Map<String,Object> selected=null;
            for(var p:ctx.periods)if(n(p,"campus_id")==n(room,"campus_id")&&n(p,"period_no")==i&&!day(p.get("effective_from")).isAfter(date)&&(p.get("effective_to")==null||!day(p.get("effective_to")).isBefore(date))){selected=p;break;}
            if(selected==null)return null;
            var st=LocalTime.parse(s(selected,"start_time"));var en=LocalTime.parse(s(selected,"end_time"));
            if(end!=null&&(!Objects.equals(shift,selected.get("shift"))||end.isAfter(st)))return null;
            if(start==null)start=st;end=en;shift=s(selected,"shift");
        }
        return new Span(date.atTime(start),date.atTime(end));
    }
    public boolean holiday(Context ctx,long campus,LocalDate date){
        return ctx.holidays.stream().anyMatch(r->(r.get("campus_id")==null||n(r,"campus_id")==campus)&&!day(r.get("start_date")).isAfter(date)&&!day(r.get("end_date")).isBefore(date));
    }
    public Set<Long> staff(Context ctx,Slot slot){
        var set=ids(ctx.sections.get(slot.section).get("assistant_ids"));if(slot.staff!=null)set.add(slot.staff);return set;
    }
    public Map<String,Object> issue(String rule,String message,Map<String,Object> section){
        var out=new LinkedHashMap<String,Object>();out.put("rule",rule);out.put("message",message);if(section!=null)out.put("sectionCode",section.get("code"));return out;
    }
    public Map<String,Object> pairIssue(Context ctx,Slot b,String rule,String message){
        var out=issue(rule,message,ctx.sections.get(b.section));out.put("patternId",b.id);out.put("roomCode",ctx.rooms.get(b.room).get("code"));out.put("dayOfWeek",b.day);out.put("periodFrom",b.first);out.put("periodCount",b.count);return out;
    }
    public List<Map<String,Object>> pair(Context ctx,Slot a,Slot b){
        var out=new ArrayList<Map<String,Object>>();
        if(a.day!=b.day)return out;
        LocalDate from=a.from.isAfter(b.from)?a.from:b.from,to=a.to.isBefore(b.to)?a.to:b.to;
        boolean sameSection=a.section==b.section,sameRoom=a.room==b.room,sameStaff=!Collections.disjoint(staff(ctx,a),staff(ctx,b));
        var groups=ids(ctx.sections.get(a.section).get("group_ids"));var other=ctx.sections.get(b.section);
        boolean sameGroup=!Collections.disjoint(groups,ids(other.get("group_ids")));
        if(!sameSection&&!sameRoom&&!sameStaff&&!sameGroup)return out;
        long ca=n(ctx.rooms.get(a.room),"campus_id"),cb=n(ctx.rooms.get(b.room),"campus_id");
        boolean teaches=false,overlap=false;
        for(var d=firstDay(from,a.day);!d.isAfter(to);d=d.plusWeeks(1)){
            if(holiday(ctx,ca,d)||holiday(ctx,cb,d))continue;
            teaches=true;
            if(ca==cb){overlap=a.first<=b.last()&&b.first<=a.last();break;}
            var x=interval(ctx,a,d);var y=interval(ctx,b,d);
            if(x!=null&&y!=null&&x.start.isBefore(y.end)&&y.start.isBefore(x.end)){overlap=true;break;}
        }
        if(!teaches)return out;
        if(sameSection)out.add(pairIssue(ctx,b,"BR09","Lớp đã có buổi khác trong ngày."));
        if(overlap){
            if(sameRoom)out.add(pairIssue(ctx,b,"BR04","Phòng đã được xếp cho lớp khác."));
            if(sameStaff)out.add(pairIssue(ctx,b,"BR05","Giảng viên đã có lịch dạy."));
            if(sameGroup){
                var issue=pairIssue(ctx,b,"BR06","Lớp hành chính đối tượng đã có lịch.");
                var ids=(Object[])other.get("group_ids");var codes=(Object[])other.get("group_codes");var shared=new ArrayList<Object>();
                for(int i=0;i<ids.length;i++)if(groups.contains(nullableId(ids[i])))shared.add(codes[i]);
                issue.put("groupCodes",shared);out.add(issue);
            }
        }return out;
    }
    public List<Map<String,Object>> problems(Context ctx,Slot slot){
        var section=ctx.sections.get(slot.section);
        if(section==null||slot.staff==null||!Objects.equals(slot.staff,nullableId(section.get("staff_id"))))return List.of(issue("BR02","Lớp chưa có giảng viên chính hợp lệ.",section));
        if(!fits(ctx.rooms.get(slot.room),section))return List.of(issue("BR07","Phòng phải hoạt động, đúng loại và đủ sức chứa.",section));
        if(slot.from.isBefore(day(section.get("start_date")))||slot.to.isAfter(day(section.get("end_date")))||slot.from.isAfter(slot.to)||firstDay(slot.from,slot.day).isAfter(slot.to)||occurrences(slot).stream().anyMatch(d->interval(ctx,slot,d)==null))
            return List.of(issue("BR03","Tiết học phải nằm trọn trong một ca và trong khoảng ngày của lớp.",section));
        var out=new ArrayList<Map<String,Object>>();var staff=staff(ctx,slot);
        if(ctx.availability.stream().anyMatch(r->staff.contains(n(r,"staff_id"))&&n(r,"semester_id")==n(section,"semester_id")&&n(r,"day_of_week")==slot.day&&"BUSY".equals(r.get("kind"))&&n(r,"period_from")<=slot.last()&&n(r,"period_to")>=slot.first))
            out.add(issue("BR08","Giảng viên đã khai báo bận.",section));
        for(var other:ctx.patterns)if(slot.id==0||slot.id!=other.id)out.addAll(pair(ctx,slot,other));
        return out;
    }
    public boolean busy(Context ctx,Slot slot,String kind){
        var section=ctx.sections.get(slot.section);var members=staff(ctx,slot);
        return ctx.availability.stream().anyMatch(r->members.contains(n(r,"staff_id"))&&n(r,"semester_id")==n(section,"semester_id")&&n(r,"day_of_week")==slot.day&&kind.equals(r.get("kind"))&&n(r,"period_from")<=slot.last()&&n(r,"period_to")>=slot.first);
    }
    public List<Map<String,Object>> actualConflicts(Context ctx,Slot slot,Span span,long exclude){
        String staffArray="{"+String.join(",",staff(ctx,slot).stream().map(String::valueOf).toList())+"}";
        return db.rows("""
              SELECT x.id,c.code AS section_code,r.code AS "roomCode",x.period_from AS "periodFrom",x.period_count AS "periodCount",
                to_char(x.start_at,'YYYY-MM-DD"T"HH24:MI:SS') AS start_at,
                ARRAY(SELECT g.code FROM section_target_groups a JOIN section_target_groups b USING(group_id) JOIN academic_groups g ON g.id=a.group_id
                  WHERE a.section_id=x.section_id AND b.section_id=?) AS "groupCodes",
                CASE WHEN x.room_id=? THEN 'BR04'
                  WHEN (ARRAY[x.staff_id] || ARRAY(SELECT a.staff_id FROM section_assignments a WHERE a.section_id=x.section_id AND a.role='ASSISTANT' AND a.valid_to IS NULL)) && ?::bigint[] THEN 'BR05' ELSE 'BR06' END AS rule
              FROM class_sessions x JOIN course_sections c ON c.id=x.section_id LEFT JOIN teaching_rooms r ON r.id=x.room_id
              WHERE x.id<>? AND x.status<>'CANCELLED' AND x.start_at<? AND x.end_at>?
              AND (x.room_id=? OR (ARRAY[x.staff_id] || ARRAY(SELECT a.staff_id FROM section_assignments a WHERE a.section_id=x.section_id AND a.role='ASSISTANT' AND a.valid_to IS NULL)) && ?::bigint[]
                  OR x.section_id=? OR EXISTS(SELECT 1 FROM section_target_groups a JOIN section_target_groups b USING(group_id) WHERE a.section_id=x.section_id AND b.section_id=?))
              ORDER BY x.id
              """,slot.section,slot.room,staffArray,exclude,Timestamp.valueOf(span.end),Timestamp.valueOf(span.start),slot.room,staffArray,slot.section,slot.section);
    }
    public List<Map<String,Object>> actual(Context ctx,Slot slot){
        long campus=n(ctx.rooms.get(slot.room),"campus_id");boolean teaching=false;
        for(var date:occurrences(slot)){
            if(holiday(ctx,campus,date))continue;teaching=true;
            var rows=actualConflicts(ctx,slot,interval(ctx,slot,date),0);
            if(!rows.isEmpty())return rows;
        }
        return teaching?List.of():List.of(issue("BR14","Lịch định kỳ không có ngày học vì toàn bộ ngày đều nghỉ.",null));
    }
    public Result check(Context ctx,Slot slot,Accounts.Actor actor,boolean suggest){
        var issues=problems(ctx,slot);if(issues.isEmpty())issues=actual(ctx,slot);
        var suggestions=new ArrayList<Map<String,Object>>();
        if(suggest&&!issues.isEmpty()&&slot.staff!=null){
            var allowed=campuses(actor);
            outer:for(var room:ctx.rooms.values()){
                if(!allowed.contains(n(room,"campus_id"))||!fits(room,ctx.sections.get(slot.section)))continue;
                for(int d=1;d<=7;d++)for(int p=1;p<=15;p++){
                    var candidate=slot.move(n(room,"id"),d,p);
                    if(problems(ctx,candidate).isEmpty()&&actual(ctx,candidate).isEmpty()){
                        suggestions.add(Map.of("roomId",candidate.room,"roomCode",room.get("code"),"dayOfWeek",d,"periodFrom",p));
                        if(suggestions.size()==5)break outer;
                    }
                }
            }
        }
        return new Result(issues.isEmpty(),issues,suggestions);
    }
    public void verify(Context ctx,Slot slot,Accounts.Actor actor){var result=check(ctx,slot,actor,false);if(!result.valid)throw new ApiFailure(409,"SCHEDULE_CONFLICT","Lịch dự kiến có xung đột.",result);}
    public void recheck(long section,Accounts.Actor actor){var ctx=context();for(var slot:ctx.patterns)if(slot.section==section)verify(ctx,slot,actor);}
}
