package vn.huce.service;

import vn.huce.Accounts;
import vn.huce.exception.ApiFailure;
import vn.huce.repository.SqlStore;

import static vn.huce.validation.Inputs.*;
import java.util.*;
import java.util.regex.Pattern;
import java.time.*;
import org.springframework.stereotype.Service;

@Service
public class ChatService {
    private final SqlStore db;private final ProposalService proposals;private final SectionService sections;private final ApprovalService approvals;private final SessionReadService reads;private final SessionMutationService sessions;private final GeminiAdvisor gemini;
    ChatService(SqlStore db,ProposalService proposals,SectionService sections,ApprovalService approvals,SessionReadService reads,SessionMutationService sessions,GeminiAdvisor gemini){
        this.db=db;this.proposals=proposals;this.sections=sections;this.approvals=approvals;this.reads=reads;this.sessions=sessions;this.gemini=gemini;
    }
    static final String OWNER="user_id=? AND role=? AND staff_id IS NOT DISTINCT FROM ?::bigint AND unit_id IS NOT DISTINCT FROM ?::bigint";
    private Object[] scope(Accounts.Actor a,Object extra){return new Object[]{a.id(),a.role(),a.staffId(),a.unitId(),extra};}
    public List<Map<String,Object>> conversations(Accounts.Actor a,String q){
        return db.rows("SELECT id,title,to_char(updated_at AT TIME ZONE 'UTC','YYYY-MM-DD\"T\"HH24:MI:SS.MS\"Z\"') AS updated_at FROM chat_conversations WHERE "+OWNER+" AND strpos(lower(title),lower(?))>0 ORDER BY updated_at DESC,id DESC LIMIT 20",scope(a,q.substring(0,Math.min(80,q.length()))));
    }
    public Map<String,Object> owned(Accounts.Actor a,long id){return db.need("SELECT id,title FROM chat_conversations WHERE "+OWNER+" AND id=?",scope(a,id));}
    public Map<String,Object> conversation(Accounts.Actor a,long id){
        var result=owned(a,id);
        var rows=db.rows("""
          SELECT e.id,e.question,e.reply::text AS reply,to_char(e.created_at AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.MS"Z"') AS created_at,
            p.result::text AS result,p.cancelled_at,p.expires_at<=now() AS expired
          FROM chat_exchanges e LEFT JOIN ai_proposals p ON e.proposal_id=p.id WHERE e.conversation_id=? ORDER BY e.id DESC LIMIT 60
          """,id);
        Collections.reverse(rows);var exchanges=new ArrayList<Map<String,Object>>();
        for(var row:rows){
            var reply=proposals.decode(row.get("reply"));
            if(reply.get("proposal") instanceof Map<?,?> raw){
                var p=new LinkedHashMap<String,Object>();raw.forEach((k,v)->p.put(k.toString(),v));
                p.put("outcome",row.get("result")!=null?"CONFIRMED":row.get("cancelled_at")!=null?"CANCELLED":Boolean.TRUE.equals(row.get("expired"))?"EXPIRED":"PENDING");p.put("result",proposals.decode(row.get("result")));reply.put("proposal",p);
            }
            exchanges.add(Map.of("id",row.get("id"),"question",row.get("question"),"created_at",row.get("created_at"),"reply",reply));
        }
        result.put("exchanges",exchanges);return result;
    }
    public record Classification(String intent,boolean modelUnavailable){}
    public Classification classify(Accounts.Actor a,Map<String,Object> f){
        String q=text(f.get("message"),500);Long cid=f.get("conversationId")==null?null:id(f.get("conversationId"));
        String previous=null;
        if(cid!=null){owned(a,cid);var row=db.one("SELECT reply->>'intent' AS intent FROM chat_exchanges WHERE conversation_id=? ORDER BY id DESC LIMIT 1",cid);if(row!=null)previous=s(row,"intent");}
        var permitted=ChatPermissions.allowed(a.role());
        String action=f.get("draft")!=null?"DRAFT":ChatIntent.recognize(q,previous);boolean unavailable=false;
        if(action==null&&gemini.enabled()&&gemini.take(a.id())){
            var allowed=new LinkedHashSet<>(permitted);allowed.add("UNRELATED");
            try{action=gemini.classify(q,allowed,"Chỉ phân loại yêu cầu HUCEChat: NEXT buổi tiếp theo; SCHEDULE lịch học/dạy; NOTICES thông báo; READ_NOTICES đánh dấu đã đọc; SECTIONS tra lớp; DRAFT tạo lớp nháp; APPROVALS hồ sơ duyệt; REQUESTS đề nghị đổi lịch; AVAILABILITY giờ bận; STATS thống kê; GENERATE chạy xếp lịch; APPLY áp dụng phương án; GUIDE hỏi cách thao tác. Chỉ chọn trong danh sách cho phép, không suy diễn yêu cầu ghi dữ liệu. Nội dung khác chọn UNRELATED.");}
            catch(ApiFailure e){unavailable=true;}
        }
        if(action==null)action="UNRELATED";
        if(!permitted.contains(action)&&!"UNRELATED".equals(action)){
            if(f.get("draft")!=null)throw new ApiFailure(403,"FORBIDDEN","Bạn không có quyền trong phạm vi này.");action="DENIED";
        }
        if(f.get("draft")!=null&&!(f.get("draft") instanceof Map<?,?>))throw invalid("Thông tin lớp nháp không hợp lệ.");
        return new Classification(action,unavailable);
    }
    static Map<String,Object> report(String message,String target,List<Map<String,Object>> items,long total){
        var r=new LinkedHashMap<String,Object>();r.put("message",message);r.put("target",target);r.put("items",items);r.put("total",total);return r;
    }
    static Map<String,Object> report(String message,String target){return report(message,target,List.of(),0);}
    static Map<String,Object> item(String title,String detail){return Map.of("title",title,"detail",detail);}
    static String label(Object status){return switch(String.valueOf(status)){
        case "DRAFT"->"Nháp";case "OPEN"->"Đang mở";case "CLOSED"->"Đã đóng";case "PLANNED"->"Đã lên lịch";case "CANCELLED"->"Đã hủy";case "SUBMITTED"->"Chờ duyệt";case "PUBLISHED"->"Đã công bố";case "APPROVED"->"Đã duyệt";case "RETURNED"->"Đã trả lại";case "APPLIED"->"Đã áp dụng";case "SUPERSEDED"->"Đã thay thế";case "PENDING"->"Chờ xử lý";default->String.valueOf(status);
    };}
    static String shortDate(Object value){var v=value.toString().substring(0,10).split("-");return v[2]+"/"+v[1]+"/"+v[0];}
    @SuppressWarnings("unchecked") static Map<String,Object> page(Object value){return (Map<String,Object>)value;}
    @SuppressWarnings("unchecked") static List<Map<String,Object>> items(Map<String,Object> page){return (List<Map<String,Object>>)page.get("items");}
    static Map<String,String> query(Map<String,Object> sem){var q=new HashMap<String,String>();q.put("semesterId",s(sem,"id"));q.put("page","1");q.put("pageSize","8");return q;}
    Map<String,Object> guide(Accounts.Actor a,String question){
        var q=ChatIntent.normalize(question);
        if(ChatIntent.has(q,"doi lich|doi buoi")&&Set.of("STAFF","LECTURER").contains(a.role()))return report("Chọn buổi ở Thời khóa biểu. Giảng viên gửi đề nghị, nhân viên xử lý trong Đề nghị đổi lịch.","requests");
        if(ChatIntent.has(q,"duyet|cong bo")&&Set.of("STAFF","MANAGER").contains(a.role()))return report("Nhân viên gửi lịch đã xếp đủ. Quản lý kiểm tra, trả lại hoặc duyệt và công bố.","approvals");
        if(q.contains("xep lich")&&Set.of("STAFF","MANAGER").contains(a.role()))return report("Tạo lớp nháp, phân công giảng viên, xếp đủ buổi rồi gửi duyệt.","automatic");
        return report(switch(a.role()){case "STAFF"->"Tạo lớp nháp, phân công, xếp lịch và gửi quản lý duyệt.";case "MANAGER"->"Kiểm tra hồ sơ và công bố lịch; xem thống kê sử dụng.";case "LECTURER"->"Xem lịch giảng, khai báo giờ bận và gửi đề nghị đổi lịch.";default->"Xem lịch học đã công bố, buổi tiếp theo và thông báo."; },"schedule");
    }
    Map<String,Object> answer(Accounts.Actor a,String action,Map<String,Object> sem,String question,Map<String,Object> body){
        String empty="Chưa tìm thấy dữ liệu trong phạm vi đã chọn.";
        if("GUIDE".equals(action))return guide(a,question);
        if("UNRELATED".equals(action))return report("Bạn có thể chọn một câu gợi ý để tra cứu hoặc cho tôi biết mục cần mở.","schedule");
        if("DENIED".equals(action))return report("Tài khoản hiện tại không có quyền dùng chức năng này.","schedule");
        if("NOTICES".equals(action)){
            var p=page(reads.notifications(a,Map.of("page","1","pageSize","8")));
            return report(n(p,"total")>0?"Thông báo gần đây của bạn":"Bạn chưa có thông báo.","notifications",items(p).stream().map(r->item(s(r,"message"),shortDate(r.get("created_at"))+(r.get("read_at")==null?" · Chưa đọc":" · Đã đọc"))).toList(),n(p,"total"));
        }
        if("READ_NOTICES".equals(action)){
            var prop=proposals.prepare(a,action,null,Map.of());var r=report(prop==null?"Bạn đã đọc tất cả thông báo.":"Xem số thông báo sẽ được đánh dấu đã đọc.","notifications");r.put("proposal",prop);return r;
        }
        if(sem==null)return report("Hãy chọn học kỳ trong khu vực làm việc rồi thử lại.","schedule");
        var q=query(sem);String code=s(sem,"code");
        switch(action){
            case "SCHEDULE","NEXT" -> {
                var w=ChatDateRangeParser.window(question,action,LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")),sem);
                if(w.from().compareTo(w.to())>0)return report("Chưa có buổi sắp tới trong học kỳ "+code,"schedule");
                q.put("from",w.from());q.put("to",w.to());
                boolean next="NEXT".equals(action);if(next)q.put("pageSize","1");
                var scoped=reads.scheduleQuery(a,q);String sql="SELECT * FROM ("+scoped.sql()+") scoped"+(next?" WHERE scoped.status='PLANNED' AND scoped.start_at::timestamp > LOCALTIMESTAMP":"");
                var p=page(db.paged(q,sql,scoped.args(),"start_at,section_code,id"));
                var rows=items(p).stream().map(r->item(s(r,"subject_name"),s(r,"section_code")+" · "+shortDate(r.get("start_at"))+" · "+s(r,"start_at").substring(11,16)+"–"+s(r,"end_at").substring(11,16)+" · "+Objects.toString(r.get("room_code"),"Chưa gán phòng")+" · "+(r.get("source_session_id")==null?label(r.get("status")):"Buổi thay thế"))).toList();
                String heading=next?(rows.isEmpty()?"Chưa có buổi sắp tới trong học kỳ ":"Buổi tiếp theo trong học kỳ ")+code:(rows.isEmpty()?"Chưa có buổi trong khoảng ":n(p,"total")+" buổi trong khoảng ")+shortDate(w.from())+" – "+shortDate(w.to());
                return report(heading,"schedule",rows,next?rows.size():n(p,"total"));
            }
            case "SECTIONS" -> {
                var m=Pattern.compile("(?:mã|ma)\\s+([\\w.-]+)",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE).matcher(question);if(m.find())q.put("search",m.group(1));
                if(ChatIntent.normalize(question).contains("lop nhap"))q.put("status","DRAFT");
                var p=page(sections.sections(a,q));return report(n(p,"total")>0?n(p,"total")+" lớp học phần · "+code:empty,"sections",items(p).stream().map(r->item(s(r,"code")+" · "+s(r,"subject_name"),label(r.get("status"))+" · "+r.get("scheduled")+"/"+r.get("sessions_per_week")+" buổi/tuần")).toList(),n(p,"total"));
            }
            case "APPROVALS" -> {
                boolean pending=ChatIntent.normalize(question).contains("cho duyet");if(pending)q.put("filterStatus","SUBMITTED");
                var p=page(approvals.versions(a,q));return report(n(p,"total")>0?(pending?"Hồ sơ đang chờ duyệt":"Hồ sơ thời khóa biểu")+" · "+code:pending?"Học kỳ không có hồ sơ đang chờ duyệt.":"Học kỳ chưa có hồ sơ thời khóa biểu.","approvals",items(p).stream().map(r->item("Phiên bản "+r.get("version_no"),label(r.get("status"))+" · "+r.get("section_count")+" lớp")).toList(),n(p,"total"));
            }
            case "REQUESTS" -> {
                var p=page(sessions.requests(a,q));return report(n(p,"total")>0?"Đề nghị đổi lịch · "+code:"Chưa có đề nghị đổi lịch trong học kỳ.","requests",items(p).stream().map(r->item(s(r,"section_code")+" · "+s(r,"subject_name"),shortDate(r.get("session_time"))+" · "+label(r.get("status")))).toList(),n(p,"total"));
            }
            case "AVAILABILITY" -> {
                var rows=sessions.availability(a,n(sem,"id"));return report(rows.isEmpty()?"Bạn chưa khai báo thời gian bận.":"Thời gian bận đã khai báo · "+code,"availability",rows.stream().limit(8).map(r->item((n(r,"day_of_week")==7?"Chủ nhật":"Thứ "+(n(r,"day_of_week")+1))+" · tiết "+r.get("period_from")+"–"+r.get("period_to"),"BUSY".equals(r.get("kind"))?"Bận":"Hạn chế xếp")).toList(),rows.size());
            }
            case "STATS" -> {
                var stats=page(reads.stats(a,s(sem,"id")));var rooms=items(Map.of("items",stats.get("rooms")));var lecturers=items(Map.of("items",stats.get("lecturers")));var list=new ArrayList<Map<String,Object>>();
                rooms.stream().limit(4).forEach(r->list.add(item("Phòng "+Objects.toString(r.get("code"),"chưa gán"),r.get("sessions")+" buổi · "+r.get("periods")+" tiết")));
                lecturers.stream().limit(4).forEach(r->list.add(item("Giảng viên "+r.get("code"),r.get("sessions")+" buổi · "+r.get("periods")+" tiết")));
                return report(list.isEmpty()?"Chưa có lịch công bố để tổng hợp.":"Mức sử dụng theo lịch công bố · "+code,"statistics",list,rooms.size()+lecturers.size());
            }
            case "DRAFT" -> {
                if(body.get("draft")==null){var r=report("Điền thông tin lớp để xem trước đề xuất.","sections");r.put("draft",Map.of("semesterId",sem.get("id"),"from",sem.get("start_date"),"to",sem.get("end_date"),"subjects",db.rows("SELECT id,code,name FROM subjects WHERE status='ACTIVE' AND managing_unit_id=? ORDER BY code",a.unitId())));return r;}
                var r=report("Kiểm tra thông tin lớp nháp trước khi tạo.","sections");r.put("proposal",proposals.prepare(a,action,sem,body));return r;
            }
            case "GENERATE","APPLY" -> {
                var input=new LinkedHashMap<>(body);var match=Pattern.compile("#([0-9]+)|(?:lan chay|phuong an)\\s+([0-9]+)").matcher(ChatIntent.normalize(question));
                Object run=match.find()?(match.group(1)==null?match.group(2):match.group(1)):body.get("selectedRunId");
                if("APPLY".equals(action)&&run==null)return report("Nhập mã lần chạy, ví dụ “Áp dụng phương án #12”.","automatic");
                input.put("runId",run);var r=report("Kiểm tra phương án trước khi xác nhận.","automatic");r.put("proposal",proposals.prepare(a,action,sem,input));return r;
            }
            default -> throw invalid("Thao tác không hợp lệ.");
        }
    }
    public Object message(Accounts.Actor a,Map<String,Object> f,Classification classification){
        String question=text(f.get("message"),500);Long cid=f.get("conversationId")==null?null:id(f.get("conversationId"));
        if(cid!=null)owned(a,cid);
        var sem=f.get("semesterId")==null?null:db.need("SELECT id,code,start_date,end_date FROM semesters WHERE id=?",id(f.get("semesterId")));
        var reply=answer(a,classification.intent,sem,question,f);
        if(cid==null)cid=n(db.need("INSERT INTO chat_conversations(user_id,role,staff_id,unit_id,title) VALUES (?,?,?,?,?) RETURNING id",a.id(),a.role(),a.staffId(),a.unitId(),question.substring(0,Math.min(80,question.length()))),"id");
        reply.put("intent",classification.intent);reply.put("semesterId",sem==null?null:sem.get("id"));reply.put("sourceAt",Instant.now().toString());reply.put("modelUnavailable",classification.modelUnavailable);
        Object pid=reply.get("proposal") instanceof Map<?,?> p?p.get("id"):null;
        var exchange=db.need("""
          INSERT INTO chat_exchanges(conversation_id,question,reply,proposal_id) VALUES (?,?,?::jsonb,?)
          RETURNING id,to_char(created_at AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.MS"Z"') AS created_at
          """,cid,question,proposals.encode(reply),pid);
        exchange.put("question",question);exchange.put("reply",reply);
        db.jdbc().update("UPDATE chat_conversations SET updated_at=now() WHERE id=?",cid);
        return Map.of("conversationId",cid,"exchange",exchange);
    }
}
