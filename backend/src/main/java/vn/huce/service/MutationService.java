package vn.huce.service;

import vn.huce.Accounts;
import vn.huce.exception.ApiFailure;
import vn.huce.repository.SqlStore;
import vn.huce.validation.Inputs;

import java.util.*;
import java.util.function.Supplier;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MutationService {
    private final SqlStore store;
    private final ObjectMapper json;
    MutationService(SqlStore store,ObjectMapper mapper){this.store=store;this.json=mapper.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);}
    @Transactional
    public Object write(Accounts.Actor actor,HttpServletRequest req,Object body,Supplier<Object> operation){
        store.jdbc().execute("SELECT pg_advisory_xact_lock(5060505)");
        String key=req.getHeader("Idempotency-Key");
        if(key==null)return operation.get();
        if(!key.matches("[a-zA-Z0-9._:-]{1,128}"))throw Inputs.invalid("Idempotency-Key không hợp lệ.");
        String fingerprint;
        try{
            var data=Arrays.asList(req.getMethod(),req.getRequestURI(),actor.role(),actor.unitId(),actor.staffId(),req.getParameterMap(),body);
            fingerprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(data).getBytes(StandardCharsets.UTF_8)));
        }catch(Exception e){throw new IllegalStateException(e);}
        store.jdbc().update("DELETE FROM fm05_idempotency_keys WHERE expires_at<=now()");
        var prior=store.one("SELECT fingerprint,response::text AS response FROM fm05_idempotency_keys WHERE actor_id=? AND key=?",actor.id(),key);
        if(prior!=null){
            if(!fingerprint.equals(prior.get("fingerprint")))throw new ApiFailure(409,"DUPLICATE_REQUEST","Mã chống lặp đã được dùng cho một yêu cầu khác.");
            try{return json.readValue((String)prior.get("response"),Object.class);}catch(Exception e){throw new IllegalStateException(e);}
        }
        Object result=operation.get();
        try{store.jdbc().update("INSERT INTO fm05_idempotency_keys(actor_id,key,fingerprint,response) VALUES (?,?,?,?::jsonb)",actor.id(),key,fingerprint,json.writeValueAsString(result));}
        catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}
        return result;
    }
}
