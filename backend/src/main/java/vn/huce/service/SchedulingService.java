package vn.huce.service;

import vn.huce.Accounts;
import vn.huce.exception.ApiFailure;
import vn.huce.repository.SchedulingRepository;

import static vn.huce.validation.Inputs.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class SchedulingService {
    private final SchedulingRepository repository;

    public SchedulingService(SchedulingRepository repository) {
        this.repository = repository;
    }
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
    public static LocalDate firstDay(LocalDate from, int dayOfWeek) {
        return ScheduleCalendar.firstDay(from, dayOfWeek);
    }

    public static List<LocalDate> occurrences(Slot slot) {
        return ScheduleCalendar.occurrences(slot);
    }

    public static Slot slot(Map<String,Object> r){return new Slot(n(r,"id"),n(r,"section_id"),nullableId(r.get("staff_id")),n(r,"room_id"),(int)n(r,"day_of_week"),(int)n(r,"period_from"),(int)n(r,"period_count"),day(r.get("effective_from")),day(r.get("effective_to")));}
    public Context context() {
        return new Context(index(repository.activeSections()), index(repository.rooms()),
                repository.activePeriods(), repository.availability(), repository.holidays(),
                repository.draftPatterns().stream().map(SchedulingService::slot).toList());
    }

    public Set<Long> campuses(Accounts.Actor actor) {
        return repository.allowedCampuses(actor);
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
    public boolean fits(Map<String, Object> room, Map<String, Object> section) {
        return ScheduleConstraints.fits(room, section);
    }
    public Span interval(Context context, Slot slot, LocalDate date) {
        return ScheduleCalendar.interval(context, slot, date);
    }

    public boolean holiday(Context context, long campusId, LocalDate date) {
        return ScheduleCalendar.holiday(context, campusId, date);
    }

    public Set<Long> staff(Context context, Slot slot) {
        return ScheduleConstraints.staff(context, slot);
    }

    public Map<String, Object> issue(String rule, String message, Map<String, Object> section) {
        return ScheduleConstraints.issue(rule, message, section);
    }

    public Map<String, Object> pairIssue(Context context, Slot other, String rule, String message) {
        return ScheduleConstraints.pairIssue(context, other, rule, message);
    }

    public List<Map<String, Object>> pair(Context context, Slot first, Slot second) {
        return ScheduleConstraints.pair(context, first, second);
    }

    public List<Map<String, Object>> problems(Context context, Slot slot) {
        return ScheduleConstraints.problems(context, slot);
    }

    public boolean busy(Context context, Slot slot, String kind) {
        return ScheduleConstraints.busy(context, slot, kind);
    }

    public List<Map<String, Object>> actualConflicts(Context context, Slot slot, Span span, long excludedSessionId) {
        return repository.actualConflicts(slot.section(), slot.room(), staff(context, slot),
                span.start(), span.end(), excludedSessionId);
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
