package vn.huce.service.chat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;
import vn.huce.repository.CatalogRepository;
import vn.huce.service.ApprovalService;
import vn.huce.service.SectionService;
import vn.huce.service.SessionMutationService;
import vn.huce.service.SessionReadService;

import static vn.huce.validation.Inputs.*;

@Service
class ChatReplyService {
    private final CatalogRepository catalog;
    private final ProposalService proposals;
    private final SectionService sections;
    private final ApprovalService approvals;
    private final SessionReadService reads;
    private final SessionMutationService sessions;

    ChatReplyService(CatalogRepository catalog, ProposalService proposals, SectionService sections,
                     ApprovalService approvals, SessionReadService reads, SessionMutationService sessions) {
        this.catalog = catalog;
        this.proposals = proposals;
        this.sections = sections;
        this.approvals = approvals;
        this.reads = reads;
        this.sessions = sessions;
    }

    private static final String EMPTY = "Chưa tìm thấy dữ liệu trong phạm vi đã chọn.";

    Map<String, Object> answer(Actor actor, String action, Map<String, Object> semester,
                               String question, Map<String, Object> input) {
        if (!Set.of("UNRELATED", "DENIED").contains(action)
                && !ChatPermissions.allowed(actor.role()).contains(action)) {
            throw new ApiFailure(403, "FORBIDDEN", "Bạn không có quyền trong phạm vi này.");
        }
        switch (action) {
            case "GUIDE":
                return guide(actor, question);
            case "UNRELATED":
                return report("Bạn có thể chọn một câu gợi ý để tra cứu hoặc cho tôi biết mục cần mở.", "schedule");
            case "DENIED":
                return report("Tài khoản hiện tại không có quyền dùng chức năng này.", "schedule");
            case "NOTICES":
                return notices(actor);
            case "READ_NOTICES":
                return readNotices(actor);
            default:
                break;
        }

        if (semester == null) {
            return report("Hãy chọn học kỳ trong khu vực làm việc rồi thử lại.", "schedule");
        }

        Map<String, String> query = query(semester);
        String code = s(semester, "code");
        return switch (action) {
            case "SCHEDULE", "NEXT" -> schedule(actor, action, semester, question, query);
            case "SECTIONS" -> sections(actor, question, query, code);
            case "APPROVALS" -> approvals(actor, question, query, code);
            case "REQUESTS" -> requests(actor, query, code);
            case "AVAILABILITY" -> availability(actor, semester, code);
            case "STATS" -> statistics(actor, semester);
            case "DRAFT" -> draft(actor, semester, input);
            case "GENERATE", "APPLY" -> scheduleProposal(actor, action, semester, question, input);
            default -> throw invalid("Thao tác không hợp lệ.");
        };
    }

    private Map<String, Object> notices(Actor actor) {
        Map<String, Object> page = page(reads.notifications(actor, Map.of("page", "1", "pageSize", "8")));
        long total = n(page, "total");
        var rows = items(page).stream().map(row -> item(s(row, "message"),
                shortDate(row.get("created_at")) + (row.get("read_at") == null ? " · Chưa đọc" : " · Đã đọc"))).toList();
        return report(total > 0 ? "Thông báo gần đây của bạn" : "Bạn chưa có thông báo.",
                "notifications", rows, total);
    }

    private Map<String, Object> readNotices(Actor actor) {
        var proposal = proposals.prepare(actor, "READ_NOTICES", null, Map.of());
        var response = report(proposal == null ? "Bạn đã đọc tất cả thông báo."
                : "Xem số thông báo sẽ được đánh dấu đã đọc.", "notifications");
        response.put("proposal", proposal);
        return response;
    }

    private Map<String, Object> schedule(Actor actor, String action, Map<String, Object> semester,
                                          String question, Map<String, String> query) {
        String code = s(semester, "code");
        var window = ChatDateRangeParser.window(question, action,
                LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")), semester);
        if (window.from().compareTo(window.to()) > 0) {
            return report("Chưa có buổi sắp tới trong học kỳ " + code, "schedule");
        }
        query.put("from", window.from());
        query.put("to", window.to());
        boolean next = "NEXT".equals(action);
        if (next) {
            query.put("pageSize", "1");
        }
        var page = page(next ? reads.nextSession(actor, query) : reads.schedule(actor, query));
        var rows = items(page).stream().map(row -> item(s(row, "subject_name"),
                s(row, "section_code") + " · " + shortDate(row.get("start_at"))
                        + " · " + s(row, "start_at").substring(11, 16) + "–" + s(row, "end_at").substring(11, 16)
                        + " · " + Objects.toString(row.get("room_code"), "Chưa gán phòng")
                        + " · " + (row.get("source_session_id") == null ? label(row.get("status")) : "Buổi thay thế"))).toList();
        String heading;
        if (next) {
            heading = (rows.isEmpty() ? "Chưa có buổi sắp tới trong học kỳ " : "Buổi tiếp theo trong học kỳ ") + code;
        } else {
            heading = (rows.isEmpty() ? "Chưa có buổi trong khoảng " : n(page, "total") + " buổi trong khoảng ")
                    + shortDate(window.from()) + " – " + shortDate(window.to());
        }
        return report(heading, "schedule", rows, next ? rows.size() : n(page, "total"));
    }

    private Map<String, Object> sections(Actor actor, String question, Map<String, String> query, String code) {
        var matcher = Pattern.compile("(?:mã|ma)\\s+([\\w.-]+)",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(question);
        if (matcher.find()) {
            query.put("search", matcher.group(1));
        }
        if (ChatIntent.normalize(question).contains("lop nhap")) {
            query.put("status", "DRAFT");
        }
        var page = page(sections.sections(actor, query));
        long total = n(page, "total");
        var rows = items(page).stream().map(row -> item(s(row, "code") + " · " + s(row, "subject_name"),
                label(row.get("status")) + " · " + row.get("scheduled") + "/" + row.get("sessions_per_week")
                        + " buổi/tuần")).toList();
        return report(total > 0 ? total + " lớp học phần · " + code : EMPTY, "sections", rows, total);
    }

    private Map<String, Object> approvals(Actor actor, String question, Map<String, String> query, String code) {
        boolean pending = ChatIntent.normalize(question).contains("cho duyet");
        if (pending) {
            query.put("filterStatus", "SUBMITTED");
        }
        var page = page(approvals.versions(actor, query));
        long total = n(page, "total");
        String heading = total > 0
                ? (pending ? "Hồ sơ đang chờ duyệt" : "Hồ sơ thời khóa biểu") + " · " + code
                : (pending ? "Học kỳ không có hồ sơ đang chờ duyệt." : "Học kỳ chưa có hồ sơ thời khóa biểu.");
        var rows = items(page).stream().map(row -> item("Phiên bản " + row.get("version_no"),
                label(row.get("status")) + " · " + row.get("section_count") + " lớp")).toList();
        return report(heading, "approvals", rows, total);
    }

    private Map<String, Object> requests(Actor actor, Map<String, String> query, String code) {
        var page = page(sessions.requests(actor, query));
        long total = n(page, "total");
        var rows = items(page).stream().map(row -> item(s(row, "section_code") + " · " + s(row, "subject_name"),
                shortDate(row.get("session_time")) + " · " + label(row.get("status")))).toList();
        return report(total > 0 ? "Đề nghị đổi lịch · " + code : "Chưa có đề nghị đổi lịch trong học kỳ.",
                "requests", rows, total);
    }

    private Map<String, Object> availability(Actor actor, Map<String, Object> semester, String code) {
        var rows = sessions.availability(actor, n(semester, "id"));
        var entries = rows.stream().limit(8).map(row -> item(
                (n(row, "day_of_week") == 7 ? "Chủ nhật" : "Thứ " + (n(row, "day_of_week") + 1))
                        + " · tiết " + row.get("period_from") + "–" + row.get("period_to"),
                "BUSY".equals(row.get("kind")) ? "Bận" : "Hạn chế xếp")).toList();
        return report(rows.isEmpty() ? "Bạn chưa khai báo thời gian bận." : "Thời gian bận đã khai báo · " + code,
                "availability", entries, rows.size());
    }

    private Map<String, Object> statistics(Actor actor, Map<String, Object> semester) {
        var statistics = page(reads.stats(actor, s(semester, "id")));
        var rooms = items(Map.of("items", statistics.get("rooms")));
        var lecturers = items(Map.of("items", statistics.get("lecturers")));
        List<Map<String, Object>> rows = new ArrayList<>();
        rooms.stream().limit(4).forEach(row -> rows.add(item("Phòng " + Objects.toString(row.get("code"), "chưa gán"),
                row.get("sessions") + " buổi · " + row.get("periods") + " tiết")));
        lecturers.stream().limit(4).forEach(row -> rows.add(item("Giảng viên " + row.get("code"),
                row.get("sessions") + " buổi · " + row.get("periods") + " tiết")));
        return report(rows.isEmpty() ? "Chưa có lịch công bố để tổng hợp."
                        : "Mức sử dụng theo lịch công bố · " + s(semester, "code"),
                "statistics", rows, rooms.size() + lecturers.size());
    }

    private Map<String, Object> draft(Actor actor, Map<String, Object> semester, Map<String, Object> input) {
        if (input.get("draft") == null) {
            var response = report("Điền thông tin lớp để xem trước đề xuất.", "sections");
            response.put("draft", Map.of("semesterId", semester.get("id"), "from", semester.get("start_date"),
                    "to", semester.get("end_date"), "subjects", catalog.subjects(false, actor.unitId())));
            return response;
        }
        var response = report("Kiểm tra thông tin lớp nháp trước khi tạo.", "sections");
        response.put("proposal", proposals.prepare(actor, "DRAFT", semester, input));
        return response;
    }

    private Map<String, Object> scheduleProposal(Actor actor, String action, Map<String, Object> semester,
                                                 String question, Map<String, Object> body) {
        var input = new LinkedHashMap<>(body);
        var matcher = Pattern.compile("#([0-9]+)|(?:lan chay|phuong an)\\s+([0-9]+)")
                .matcher(ChatIntent.normalize(question));
        Object runId = matcher.find() ? (matcher.group(1) == null ? matcher.group(2) : matcher.group(1))
                : body.get("selectedRunId");
        if ("APPLY".equals(action) && runId == null) {
            return report("Nhập mã lần chạy, ví dụ “Áp dụng phương án #12”.", "automatic");
        }
        input.put("runId", runId);
        var response = report("Kiểm tra phương án trước khi xác nhận.", "automatic");
        response.put("proposal", proposals.prepare(actor, action, semester, input));
        return response;
    }

    private Map<String, Object> guide(Actor actor, String question) {
        String normalized = ChatIntent.normalize(question);
        if (ChatIntent.has(normalized, "doi lich|doi buoi") && Set.of("STAFF", "LECTURER").contains(actor.role())) {
            return report("Chọn buổi ở Thời khóa biểu. Giảng viên gửi đề nghị, nhân viên xử lý trong Đề nghị đổi lịch.", "requests");
        }
        if (ChatIntent.has(normalized, "duyet|cong bo") && Set.of("STAFF", "MANAGER").contains(actor.role())) {
            return report("Nhân viên gửi lịch đã xếp đủ. Quản lý kiểm tra, trả lại hoặc duyệt và công bố.", "approvals");
        }
        if (normalized.contains("xep lich") && Set.of("STAFF", "MANAGER").contains(actor.role())) {
            return report("Tạo lớp nháp, phân công giảng viên, xếp đủ buổi rồi gửi duyệt.", "automatic");
        }
        String message = switch (actor.role()) {
            case "STAFF" -> "Tạo lớp nháp, phân công, xếp lịch và gửi quản lý duyệt.";
            case "MANAGER" -> "Kiểm tra hồ sơ và công bố lịch; xem thống kê sử dụng.";
            case "LECTURER" -> "Xem lịch giảng, khai báo giờ bận và gửi đề nghị đổi lịch.";
            default -> "Xem lịch học đã công bố, buổi tiếp theo và thông báo.";
        };
        return report(message, "schedule");
    }

    private static Map<String, Object> report(String message, String target, List<Map<String, Object>> items, long total) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", message);
        response.put("target", target);
        response.put("items", items);
        response.put("total", total);
        return response;
    }

    private static Map<String, Object> report(String message, String target) {
        return report(message, target, List.of(), 0);
    }

    private static Map<String, Object> item(String title, String detail) {
        return Map.of("title", title, "detail", detail);
    }

    private static String label(Object status) {
        return switch (String.valueOf(status)) {
            case "DRAFT" -> "Nháp";
            case "OPEN" -> "Đang mở";
            case "CLOSED" -> "Đã đóng";
            case "PLANNED" -> "Đã lên lịch";
            case "CANCELLED" -> "Đã hủy";
            case "SUBMITTED" -> "Chờ duyệt";
            case "PUBLISHED" -> "Đã công bố";
            case "APPROVED" -> "Đã duyệt";
            case "RETURNED" -> "Đã trả lại";
            case "APPLIED" -> "Đã áp dụng";
            case "SUPERSEDED" -> "Đã thay thế";
            case "PENDING" -> "Chờ xử lý";
            default -> String.valueOf(status);
        };
    }

    private static String shortDate(Object value) {
        String[] parts = value.toString().substring(0, 10).split("-");
        return parts[2] + "/" + parts[1] + "/" + parts[0];
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> page(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> page) {
        return (List<Map<String, Object>>) page.get("items");
    }

    private static Map<String, String> query(Map<String, Object> semester) {
        Map<String, String> query = new HashMap<>();
        query.put("semesterId", s(semester, "id"));
        query.put("page", "1");
        query.put("pageSize", "8");
        return query;
    }
}
