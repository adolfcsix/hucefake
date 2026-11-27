package vn.huce.service.chat;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

final class ChatIntent {
    private ChatIntent() {
    }

    static String normalize(String value) {
        return Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd');
    }

    static boolean has(String question, String regex) {
        return Pattern.compile(regex).matcher(question).find();
    }

    private static final String[][] RULES = {
      {"READ_NOTICES","danh dau.*(?:da doc|thong bao)|doc het.*thong bao"},
        {"NOTICES","thong bao|tin moi"},
      {"APPLY","ap dung.*(?:phuong an|lan chay|de xuat)"},
        {"GENERATE","(?:xep|tao|chay).*lich.*(?:tu dong|con thieu)|chay xep lich"},
      {"DRAFT","(?:tao|them|lap|mo).*(?:lop nhap|lop hoc phan)"},
        {"REQUESTS","(?:de nghi|yeu cau).*(?:doi lich|doi buoi)|xin doi lich"},
      {"APPROVALS","duyet.*(?:lich|ho so)|cong bo.*(?:lich|phuong an)|phe duyet|ho so cho duyet"},
      {"AVAILABILITY","thoi gian ban|gio ban|lich ban"},
        {"STATS","thong ke|su dung phong|bao nhieu buoi.*phong"},
      {"NEXT","(?:buoi|tiet|mon).*(?:tiep theo|sap toi|ke tiep|gan nhat)|(?:tiep theo|sap toi).*(?:buoi|tiet|mon)"},
      {"SCHEDULE","thoi khoa bieu|lich hoc|lich day|lich giang|lich cua toi|lich (?:hom nay|ngay mai|tuan nay|tuan sau|thu )|co hoc khong|co (?:mon|tiet|buoi hoc)|hoc gi|day gi"},
      {"SECTIONS","lop hoc phan|lop nhap|danh sach lop|tra lop"}
    };

    static String recognize(String question, String previous) {
        String normalized = normalize(question);
        if (has(normalized, "^(?:xin )?chao[!. ]*$|^huong dan$|^ban (?:co the|lam duoc)|^tro giup")) {
            return "GUIDE";
        }
        for (String[] rule : RULES) {
            if (has(normalized, rule[1])) {
                if (has(normalized, "^(?:lam sao|cach |huong dan)")
                        && Set.of("READ_NOTICES", "APPLY", "GENERATE", "DRAFT", "APPROVALS",
                                "REQUESTS", "AVAILABILITY").contains(rule[0])) {
                    return "GUIDE";
                }
                return rule[0];
            }
        }
        if (("NEXT".equals(previous) || "SCHEDULE".equals(previous))
                && has(normalized, "^(?:con |the con )?(?:hom nay|hom qua|ngay mai|tuan nay|tuan sau|tuan truoc|thu (?:hai|ba|tu|nam|sau|bay|[2-7])|chu nhat|ngay [0-9]|[0-9]{4}-[0-9]{2}-[0-9]{2})")) {
            return "SCHEDULE";
        }
        return null;
    }
}
