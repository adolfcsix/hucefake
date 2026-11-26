package vn.huce;

import java.util.*;
import java.util.regex.*;
import java.text.Normalizer;
import java.time.*;
import static vn.huce.Inputs.*;

class ChatIntent {
    static String normalize(String value){return Normalizer.normalize(value.toLowerCase(Locale.ROOT),Normalizer.Form.NFD).replaceAll("\\p{M}","").replace('đ','d');}
    static boolean has(String q,String regex){return Pattern.compile(regex).matcher(q).find();}
    static final String[][] RULES={
      {"READ_NOTICES","danh dau.*(?:da doc|thong bao)|doc het.*thong bao"},{"NOTICES","thong bao|tin moi"},
      {"APPLY","ap dung.*(?:phuong an|lan chay|de xuat)"},{"GENERATE","(?:xep|tao|chay).*lich.*(?:tu dong|con thieu)|chay xep lich"},
      {"DRAFT","(?:tao|them|lap|mo).*(?:lop nhap|lop hoc phan)"},{"REQUESTS","(?:de nghi|yeu cau).*(?:doi lich|doi buoi)|xin doi lich"},
      {"APPROVALS","duyet.*(?:lich|ho so)|cong bo.*(?:lich|phuong an)|phe duyet|ho so cho duyet"},
      {"AVAILABILITY","thoi gian ban|gio ban|lich ban"},{"STATS","thong ke|su dung phong|bao nhieu buoi.*phong"},
      {"NEXT","(?:buoi|tiet|mon).*(?:tiep theo|sap toi|ke tiep|gan nhat)|(?:tiep theo|sap toi).*(?:buoi|tiet|mon)"},
      {"SCHEDULE","thoi khoa bieu|lich hoc|lich day|lich giang|lich cua toi|lich (?:hom nay|ngay mai|tuan nay|tuan sau|thu )|co hoc khong|co (?:mon|tiet|buoi hoc)|hoc gi|day gi"},
      {"SECTIONS","lop hoc phan|lop nhap|danh sach lop|tra lop"}
    };
    static Set<String> allowed(String role){
        var common=new LinkedHashSet<>(List.of("NEXT","SCHEDULE","NOTICES","READ_NOTICES","GUIDE"));
        switch(role){
            case "STAFF" -> common.addAll(List.of("SECTIONS","APPROVALS","REQUESTS","GENERATE","APPLY","DRAFT"));
            case "MANAGER" -> common.addAll(List.of("SECTIONS","APPROVALS","STATS","GENERATE","APPLY"));
            case "LECTURER" -> common.addAll(List.of("REQUESTS","AVAILABILITY"));
            case "STUDENT" -> {}
            default -> common.clear();
        }return common;
    }
    static String recognize(String question,String previous){
        var q=normalize(question);
        if(has(q,"^(?:xin )?chao[!. ]*$|^huong dan$|^ban (?:co the|lam duoc)|^tro giup"))return "GUIDE";
        for(var rule:RULES)if(has(q,rule[1])){
            if(has(q,"^(?:lam sao|cach |huong dan)")&&Set.of("READ_NOTICES","APPLY","GENERATE","DRAFT","APPROVALS","REQUESTS","AVAILABILITY").contains(rule[0]))return "GUIDE";
            return rule[0];
        }
        if(("NEXT".equals(previous)||"SCHEDULE".equals(previous))&&has(q,"^(?:con |the con )?(?:hom nay|hom qua|ngay mai|tuan nay|tuan sau|tuan truoc|thu (?:hai|ba|tu|nam|sau|bay|[2-7])|chu nhat|ngay [0-9]|[0-9]{4}-[0-9]{2}-[0-9]{2})"))return "SCHEDULE";
        return null;
    }
    record Window(String from,String to){}
    static Window window(String message,String action,LocalDate now,Map<String,Object> sem){
        String q=normalize(message);var dates=new ArrayList<LocalDate>();
        var m=Pattern.compile("\\b([0-9]{4}-[0-9]{2}-[0-9]{2}|[0-9]{1,2}/[0-9]{1,2}(?:/[0-9]{4})?)\\b").matcher(q);
        try{
            while(m.find()){String v=m.group();if(v.contains("-"))dates.add(LocalDate.parse(date(v)));else{var p=v.split("/");dates.add(LocalDate.of(p.length==3?Integer.parseInt(p[2]):now.getYear(),Integer.parseInt(p[1]),Integer.parseInt(p[0])));}}
        }catch(RuntimeException e){throw invalid("Ngày không hợp lệ.");}
        if(dates.size()>2)throw invalid("Chọn một ngày hoặc một khoảng gồm hai ngày.");
        if(!dates.isEmpty()){
            var from=dates.getFirst();var to=dates.getLast();if(to.isBefore(from)||to.isAfter(from.plusDays(30)))throw invalid("Khoảng tra cứu cần theo thứ tự và tối đa 31 ngày.");return new Window(from.toString(),to.toString());
        }
        if(q.contains("ngay mai"))return single(now.plusDays(1));
        if(q.contains("hom qua"))return single(now.minusDays(1));
        if(q.contains("hom nay"))return single(now);
        var start=now.minusDays(now.getDayOfWeek().getValue()-1).plusDays(q.contains("tuan sau")?7:q.contains("tuan truoc")?-7:0);
        var weekday=Pattern.compile("\\bthu (hai|ba|tu|nam|sau|bay|[2-7])\\b").matcher(q);
        if(weekday.find()){
            int offset=switch(weekday.group(1)){case "hai"->0;case "ba"->1;case "tu"->2;case "nam"->3;case "sau"->4;case "bay"->5;default->Integer.parseInt(weekday.group(1))-2;};
            return single(start.plusDays(offset));
        }
        if(q.contains("chu nhat"))return single(start.plusDays(6));
        if(action.equals("NEXT")&&!q.contains("tuan "))return new Window(now.toString(),s(sem,"end_date"));
        return new Window(start.toString(),start.plusDays(6).toString());
    }
    static Window single(LocalDate d){return new Window(d.toString(),d.toString());}
}
