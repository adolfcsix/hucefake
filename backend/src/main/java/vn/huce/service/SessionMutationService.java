package vn.huce.service;

import vn.huce.Accounts;
import vn.huce.exception.ApiFailure;
import vn.huce.repository.SessionChangeRepository;
import vn.huce.repository.SectionRepository;
import vn.huce.repository.AuditRepository;

import static vn.huce.validation.Inputs.*;
import static vn.huce.service.SchedulingService.*;
import java.util.*;
import java.time.*;
import org.springframework.stereotype.Service;

@Service
public class SessionMutationService {
    private final SessionChangeRepository repository;
    private final SectionRepository sectionRepository;
    private final AuditRepository audit;
    private final SchedulingService scheduling;
    private final NotificationService notifications;
    private final AvailabilityService availabilityService;
    SessionMutationService(SessionChangeRepository repository, SectionRepository sectionRepository,
                           AuditRepository audit, SchedulingService scheduling, NotificationService notifications,
                           AvailabilityService availabilityService) {
        this.repository = repository;
        this.sectionRepository = sectionRepository;
        this.audit = audit;
        this.scheduling = scheduling;
        this.notifications = notifications;
        this.availabilityService = availabilityService;
    }
    private void forbidden(){throw new ApiFailure(403,"FORBIDDEN","Bạn không có quyền trong phạm vi này.");}
    private ApiFailure conflict(Object details){return new ApiFailure(409,"SCHEDULE_CONFLICT","Lịch dự kiến có xung đột.",details);}
    private Map<String,Object> section(long id){return sectionRepository.findSection(id);}
    private void campus(Accounts.Actor a,Object campus){if(campus!=null)scheduling.requireCampus(a,id(campus));}
    private Map<String,Object> room(Context ctx,long id){
        var room=ctx.rooms().get(id);if(room==null)throw new ApiFailure(404,"NOT_FOUND","Không tìm thấy dữ liệu.");return room;
    }
    private boolean unchanged(Slot slot,Span time,Map<String,Object> original){
        return Objects.equals(nullableId(original.get("room_id")),slot.room())&&time.start().equals(LocalDateTime.parse(s(original,"start_at")));
    }
    private List<Map<String,Object>> alternatives(Accounts.Actor a,Context ctx,Slot slot,Map<String,Object> section,Map<String,Object> original,String action,long exclude){
        var allowed=scheduling.campuses(a);var result=new ArrayList<Map<String,Object>>();
        int first="ROOM_CHANGE".equals(action)?slot.first():1,last="ROOM_CHANGE".equals(action)?slot.first():15;
        for(int p=first;p<=last;p++)for(var r:ctx.rooms().values()){
            long campus=n(r,"campus_id"),roomId=n(r,"id");
            if(!allowed.contains(campus)||!scheduling.fits(r,section)||scheduling.holiday(ctx,campus,slot.from())||("ROOM_CHANGE".equals(action)&&Objects.equals(nullableId(original.get("room_id")),roomId)))continue;
            var proposed=slot.move(roomId,slot.day(),p);var when=scheduling.interval(ctx,proposed,slot.from());
            if(when==null||scheduling.busy(ctx,proposed,"BUSY")||unchanged(proposed,when,original))continue;
            if(scheduling.actualConflicts(ctx,proposed,when,exclude).isEmpty())result.add(Map.of("roomId",roomId,"roomCode",r.get("code"),"dayOfWeek",slot.day(),"periodFrom",p));
            if(result.size()==5)return result;
        }
        return result;
    }
    public Object change(Accounts.Actor a,long id,Map<String,Object> f,Long requestId){
        role(a,"STAFF");
        var original=repository.findSession(id);
        var section=section(n(original,"section_id"));unit(a,section.get("managing_unit_id"));
        if(repository.hasException(id))throw new ApiFailure(409,"DUPLICATE_REQUEST","Buổi học đã có ngoại lệ.");
        if(!"PLANNED".equals(original.get("status"))||!"OPEN".equals(section.get("status")))throw state("Chỉ điều chỉnh buổi học đang lên lịch của lớp đã mở.");
        campus(a,original.get("campus_id"));
        var ctx=scheduling.context();
        String action=choice(f.get("action"),"CANCEL","RESCHEDULE","ROOM_CHANGE"),reason=text(f.get("reason"),500);
        var originalDate=LocalDateTime.parse(s(original,"start_at")).toLocalDate();Long replacement=null;
        if(!"CANCEL".equals(action)){
            var room=room(ctx,id(f.get("roomId")));long roomId=n(room,"id"),campus=n(room,"campus_id");scheduling.requireCampus(a,campus);
            boolean changeRoom="ROOM_CHANGE".equals(action);
            var day=changeRoom?originalDate:LocalDate.parse(date(f.get("date")));
            var sem=repository.semesterDates(n(section,"semester_id"));
            if(day.isBefore(SchedulingService.day(sem.get("start_date")))||day.isAfter(SchedulingService.day(sem.get("end_date"))))throw invalid("Ngày học bù phải nằm trong học kỳ.");
            if(scheduling.holiday(ctx,campus,day))throw invalid("Ngày đã chọn là ngày không học tại cơ sở.");
            int period=changeRoom?(int)n(original,"period_from"):(int)number(f.get("periodFrom"),1,15);
            if(!scheduling.fits(room,section))throw invalid("Phòng thay thế phải đủ chỗ và phù hợp học phần.");
            if(changeRoom&&Objects.equals(nullableId(original.get("room_id")),roomId))throw invalid("Cần chọn phòng khác.");
            var slot=new Slot(0,n(section,"id"),nullableId(original.get("staff_id")),roomId,day.getDayOfWeek().getValue(),period,(int)n(original,"period_count"),day,day);
            var time=scheduling.interval(ctx,slot,day);
            if(time==null||scheduling.busy(ctx,slot,"BUSY"))throw invalid("Tiết thay thế qua ca hoặc rơi vào giờ bận.");
            if(unchanged(slot,time,original))throw invalid("Giờ hoặc phòng thay thế phải khác buổi ban đầu.");
            var issues=scheduling.actualConflicts(ctx,slot,time,id);
            if(!issues.isEmpty())throw conflict(Map.of("issues",issues,"suggestions",alternatives(a,ctx,slot,section,original,action,id)));
            replacement=repository.insertReplacement(slot.section(),slot.staff(),slot.room(),slot.first(),slot.count(),time.start(),time.end(),original.get("timetable_version_id"),id);
        }
        var result=repository.insertException(id,originalDate,action,replacement,reason,a.id());
        repository.cancelSession(id);
        audit.record(a,action,"session_exceptions",n(result,"id"));
        if(requestId==null){
            var closed=repository.closePendingRequests(id,action);
            for(var request:closed){
                audit.record(a,s(request,"status"),"session_change_requests",n(request,"id"));
                notifications.notifyStaffProfile(n(request,"staff_id"),s(request,"decision_reason"));
            }
        }
        notifications.notifySection(n(section,"id"),"Lớp "+section.get("code")+" thay đổi lịch ngày "+originalDate+". "+reason);
        var out=new LinkedHashMap<String,Object>();out.put("id",result.get("id"));out.put("replacementId",replacement);return out;
    }
    public List<Map<String, Object>> availability(Accounts.Actor actor, long semesterId) {
        return availabilityService.find(actor, semesterId);
    }

    public void setAvailability(Accounts.Actor actor, Map<String, Object> input) {
        availabilityService.replace(actor, input);
    }

    public Object requests(Accounts.Actor a,Map<String,String> q){
        role(a,"STAFF","LECTURER");
        return repository.findRequests(q, id(q.get("semesterId")), "STAFF".equals(a.role()), a.unitId(), a.staffId());
    }
    public Object request(Accounts.Actor a,long id,Map<String,Object> f){
        role(a,"LECTURER");
        var session=repository.findSessionWithStaff(id);
        if(a.staffId()==null||!ids(session.get("staff_ids")).contains(a.staffId()))forbidden();
        var section=section(n(session,"section_id"));
        if(!"PLANNED".equals(session.get("status"))||!"OPEN".equals(section.get("status")))throw state("Buổi học không còn ở trạng thái đã lên lịch.");
        if(repository.hasPendingRequest(id))throw new ApiFailure(409,"DUPLICATE_REQUEST","Buổi học đã có đề nghị chờ xử lý.");
        var result=repository.insertRequest(id,a.staffId(),choice(f.get("action"),"CANCEL","RESCHEDULE"),text(f.get("reason"),500));
        audit.record(a,"REQUEST_CHANGE","class_sessions",id);
        notifications.notifyScopedStaff(section.get("managing_unit_id"),session.get("room_id"),"Có đề nghị đổi lịch cho lớp "+section.get("code")+". Mở Đề nghị đổi lịch để xử lý.");
        return result;
    }
    public void decide(Accounts.Actor a,long id,Map<String,Object> f){
        role(a,"STAFF");
        var record=repository.findRequest(id);
        unit(a,record.get("managing_unit_id"));campus(a,record.get("campus_id"));
        if(!"PENDING".equals(record.get("status")))throw state("Đề nghị đã được xử lý.");
        String reason=text(f.get("reason"),500),action=choice(f.get("action"),"RETURNED","CANCEL","RESCHEDULE");
        String status="RETURNED".equals(action)?"RETURNED":"APPROVED";
        if("APPROVED".equals(status)){
            if(!action.equals(record.get("action")))throw invalid("Loại điều chỉnh phải khớp với đề nghị.");
            change(a,n(record,"session_id"),f,id);
        }
        repository.decideRequest(id,status,reason);
        notifications.notifyStaffProfile(n(record,"staff_id"),("APPROVED".equals(status)?"Đề nghị đổi lịch đã được xử lý. ":"Đề nghị đổi lịch đã được trả lại. ")+reason);
        audit.record(a,status,"session_change_requests",id);
    }
}
