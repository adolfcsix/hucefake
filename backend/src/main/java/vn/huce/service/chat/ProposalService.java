package vn.huce.service.chat;

import vn.huce.service.RunService;
import vn.huce.service.SectionService;
import vn.huce.service.GeneticScheduler;

import vn.huce.Accounts;
import vn.huce.exception.ApiFailure;
import vn.huce.repository.SqlStore;

import static vn.huce.validation.Inputs.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;

@Service
public class ProposalService {
    private final SqlStore db;private final ObjectMapper json;private final RunService runs;private final SectionService sections;
    ProposalService(SqlStore db,ObjectMapper json,RunService runs,SectionService sections){this.db=db;this.json=json;this.runs=runs;this.sections=sections;}
    public String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    public Map<String,Object> decode(Object value){if(value==null)return null;try{return json.readValue(value.toString(),new TypeReference<Map<String,Object>>(){});}catch(Exception e){throw new IllegalStateException(e);}}
    private void permission(Accounts.Actor a,String action){if(!ChatPermissions.allowed(a.role()).contains(action))throw new ApiFailure(403,"FORBIDDEN","Bạn không có quyền trong phạm vi này.");}
    public Map<String,Object> prepare(Accounts.Actor a,String action,Map<String,Object> sem,Map<String,Object> input){
        permission(a,action);Object params=null;Long runId=null;Map<String,Object> preview;
        if("GENERATE".equals(action)){
            var p=GeneticScheduler.parameters(input.get("parameters"));params=p;preview=Map.of("parameters",p);
        }else if("APPLY".equals(action)){
            var r=runs.get(a,id(input.get("runId")));runId=n(r,"id");
            if(n(r,"semester_id")!=n(sem,"id"))throw new ApiFailure(403,"FORBIDDEN","Bạn không có quyền trong phạm vi này.");
            if(!"COMPLETED".equals(r.get("status"))||n(r,"hard_violations")!=0)throw state("Chọn phương án đã hoàn tất với 0 vi phạm cứng.");
            long count=db.jdbc().queryForObject("SELECT count(*) FROM schedule_run_items WHERE run_id=?",Long.class,runId);
            preview=Map.of("runId",runId,"count",count,"hardViolations",0);
        }else if("DRAFT".equals(action)){
            if(!(input.get("draft") instanceof Map<?,?> draft))throw invalid("Thông tin lớp nháp không hợp lệ.");
            var form=new LinkedHashMap<String,Object>();draft.forEach((k,v)->form.put(k.toString(),v));form.put("semesterId",sem.get("id"));
            var normalized=sections.previewDraft(a,form);params=normalized;
            var subject=db.need("SELECT code,name FROM subjects WHERE id=?",normalized.get("subjectId"));
            preview=new LinkedHashMap<>(normalized);preview.put("subject",subject.get("code")+" · "+subject.get("name"));
        }else if("READ_NOTICES".equals(action)){
            var count=db.need("SELECT count(*) AS count,max(id) AS last_id FROM notifications WHERE user_id=? AND read_at IS NULL",a.id());
            if(n(count,"count")==0)return null;params=Map.of("lastId",count.get("last_id"));preview=Map.of("count",count.get("count"));
        }else throw invalid("Thao tác không hợp lệ.");
        var result=db.need("""
          INSERT INTO ai_proposals(requested_by,semester_id,action,run_id,parameters)
          VALUES (?,?,?,?,?::jsonb) RETURNING id,to_char(expires_at AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.MS"Z"') AS expires_at
          """,a.id(),sem==null?null:sem.get("id"),action,runId,params==null?null:encode(params));
        result.put("action",action);result.put("semester",sem==null?null:sem.get("code"));result.put("preview",preview);return result;
    }
    public Map<String,Object> owned(Accounts.Actor a,long id){
        var row=db.need("SELECT *,parameters::text AS params_json,result::text AS result_json,expires_at<=clock_timestamp() AS expired FROM ai_proposals WHERE id=? FOR UPDATE",id);
        if(n(row,"requested_by")!=a.id())throw new ApiFailure(403,"FORBIDDEN","Bạn không có quyền trong phạm vi này.");
        permission(a,s(row,"action"));
        if(db.exists("""
          SELECT 1 FROM chat_exchanges e JOIN chat_conversations c ON c.id=e.conversation_id
          WHERE e.proposal_id=? AND (c.user_id<>? OR c.role<>? OR c.staff_id IS DISTINCT FROM ?::bigint OR c.unit_id IS DISTINCT FROM ?::bigint)
          """,id,a.id(),a.role(),a.staffId(),a.unitId()))throw new ApiFailure(403,"FORBIDDEN","Bạn không có quyền trong phạm vi này.");
        row.put("parameters",decode(row.remove("params_json")));row.put("result",decode(row.remove("result_json")));return row;
    }
    @SuppressWarnings("unchecked")
    public Object confirm(Accounts.Actor a,long id){
        var p=owned(a,id);if(p.get("result")!=null)return p.get("result");
        if(p.get("cancelled_at")!=null)throw state("Đề xuất đã hủy.");
        if(Boolean.TRUE.equals(p.get("expired")))throw state("Đề xuất đã hết hạn. Hãy tạo đề xuất mới.");
        String action=s(p,"action");var params=(Map<String,Object>)p.get("parameters");var result=new LinkedHashMap<String,Object>();result.put("action",action);
        switch(action){
            case "GENERATE" -> result.putAll((Map<String,Object>)runs.enqueue(a,n(p,"semester_id"),params));
            case "APPLY" -> {
                var run=runs.get(a,n(p,"run_id"));if(n(run,"semester_id")!=n(p,"semester_id"))throw new ApiFailure(403,"FORBIDDEN","Bạn không có quyền trong phạm vi này.");
                result.putAll((Map<String,Object>)runs.apply(a,n(p,"run_id")));result.put("id",p.get("run_id"));
            }
            case "DRAFT" -> {result.putAll((Map<String,Object>)sections.saveDraft(a,params));result.put("code",params.get("code"));}
            case "READ_NOTICES" -> result.put("marked",db.jdbc().update("UPDATE notifications SET read_at=now() WHERE user_id=? AND read_at IS NULL AND id<=?",a.id(),params.get("lastId")));
            default -> throw invalid("Thao tác không hợp lệ.");
        }
        db.jdbc().update("UPDATE ai_proposals SET result=?::jsonb,confirmed_at=now() WHERE id=?",encode(result),id);db.audit(a,"CONFIRM","ai_proposals",id);return result;
    }
    public Object cancel(Accounts.Actor a,long id){
        var row=owned(a,id);if(row.get("result")!=null)throw state("Đề xuất đã thực hiện.");
        db.jdbc().update("UPDATE ai_proposals SET cancelled_at=COALESCE(cancelled_at,now()) WHERE id=?",id);return Map.of("status","CANCELLED");
    }

    public Map<String,Object> prepareForSemester(Accounts.Actor actor, String action, Map<String,Object> input) {
        return prepare(actor, action, db.need("SELECT id,code FROM semesters WHERE id=?",
                id(input.get("semesterId"))), input);
    }
}
