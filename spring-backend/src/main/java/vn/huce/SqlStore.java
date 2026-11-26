package vn.huce;

import java.util.*;
import java.sql.*;
import java.time.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class SqlStore {
    final JdbcTemplate db;
    SqlStore(JdbcTemplate db){this.db=db;}
    List<Map<String,Object>> rows(String sql,Object... args){
        return db.query(sql,(rs,n)->{
            var out=new LinkedHashMap<String,Object>();
            var md=rs.getMetaData();
            for(int i=1;i<=md.getColumnCount();i++){
                Object value=rs.getObject(i);
                if(value instanceof java.sql.Array arr){try{value=arr.getArray();}finally{arr.free();}}
                else if(value instanceof java.sql.Date d)value=d.toLocalDate().toString();
                else if(value instanceof Timestamp t)value=t.toLocalDateTime().toString();
                else if(value instanceof Time t)value=t.toLocalTime().toString();
                out.put(md.getColumnLabel(i),value);
            }
            return out;
        },args);
    }
    Map<String,Object> one(String sql,Object... args){var list=rows(sql,args);return list.isEmpty()?null:list.getFirst();}
    Map<String,Object> need(String sql,Object... args){var row=one(sql,args);if(row==null)throw new ApiFailure(404,"NOT_FOUND","Không tìm thấy dữ liệu.");return row;}
    boolean exists(String sql,Object... args){return one(sql,args)!=null;}
    void audit(Accounts.Actor actor,String action,String type,long id){db.update("INSERT INTO audit_events(actor_id,action,entity_type,entity_id) VALUES (?,?,?,?)",actor.id(),action,type,id);}
    Object paged(Map<String,String> q,String sql,List<Object> args,String order){
        if(!q.containsKey("page")&&!q.containsKey("pageSize"))return rows(sql+" ORDER BY "+order,args.toArray());
        int page=(int)Inputs.number(q.getOrDefault("page","1"),1,100000);
        int size=(int)Inputs.number(q.getOrDefault("pageSize","25"),1,100);
        long total=db.queryForObject("SELECT count(*) FROM ("+sql+") matched",Long.class,args.toArray());
        var bound=new ArrayList<>(args);bound.add(size);bound.add((long)(page-1)*size);
        return Map.of("items",rows(sql+" ORDER BY "+order+" LIMIT ? OFFSET ?",bound.toArray()),"total",total,"page",page,"pageSize",size);
    }
}
class Inputs {
    static ApiFailure invalid(String msg){return new ApiFailure(400,"VALIDATION_ERROR",msg);}
    static ApiFailure state(String msg){return new ApiFailure(409,"INVALID_STATE",msg);}
    static void role(Accounts.Actor a,String... allowed){if(!Arrays.asList(allowed).contains(a.role()))throw new ApiFailure(403,"FORBIDDEN","Bạn không có quyền trong phạm vi này.");}
    static void unit(Accounts.Actor a,Object id){if("STAFF".equals(a.role())&&!Objects.equals(a.unitId(),((Number)id).longValue()))throw new ApiFailure(403,"FORBIDDEN","Bạn không có quyền trong phạm vi này.");}
    static long number(Object value,long min,long max){
        try{if(!(value instanceof Number || value instanceof String))throw new NumberFormatException();long n=new java.math.BigDecimal(value.toString().trim()).longValueExact();if(n<min||n>max)throw new NumberFormatException();return n;}catch(ArithmeticException|NumberFormatException e){throw invalid("Số nhập vào nằm ngoài phạm vi cho phép.");}
    }
    static long id(Object value){return number(value,1,9007199254740991L);}
    static String text(Object value,int max){if(!(value instanceof String s)||s.trim().isEmpty()||s.trim().length()>max)throw invalid("Nội dung trống hoặc vượt giới hạn ký tự.");return s.trim();}
    static String date(Object value){try{if(!(value instanceof String s)||!s.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw new IllegalArgumentException();return LocalDate.parse(s).toString();}catch(RuntimeException e){throw invalid("Ngày không hợp lệ.");}}
    static String choice(Object value,String... choices){if(!(value instanceof String s)||!Arrays.asList(choices).contains(s))throw invalid("Lựa chọn không hợp lệ.");return s;}
    static void version(Object value,Map<String,Object> row){if(number(value,0,9007199254740991L)!=((Number)row.get("version")).longValue())throw new ApiFailure(409,"VERSION_CONFLICT","Dữ liệu đã thay đổi. Hãy tải lại trước khi lưu.",row);}
    static long n(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
    static String s(Map<String,Object> row,String key){return String.valueOf(row.get(key));}
}
