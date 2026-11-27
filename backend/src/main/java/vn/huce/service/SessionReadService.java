package vn.huce.service;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;
import vn.huce.model.ScheduleFilter;
import vn.huce.repository.NotificationRepository;
import vn.huce.repository.SessionReadRepository;

import static vn.huce.validation.Inputs.*;

@Service
public class SessionReadService {
    private final SessionReadRepository repository;
    private final NotificationRepository notifications;

    public SessionReadService(SessionReadRepository repository, NotificationRepository notifications) {
        this.repository = repository;
        this.notifications = notifications;
    }

    ScheduleFilter filter(Actor actor, Map<String, String> query) {
        role(actor, "STAFF", "MANAGER", "STUDENT", "LECTURER");
        String viewName = query.getOrDefault("view", "personal");
        if (viewName.isEmpty()) {
            viewName = "personal";
        }
        ScheduleFilter.View view = ScheduleFilter.View.valueOf(choice(viewName,
                "personal", "all", "room", "staff", "group", "section").toUpperCase(Locale.ROOT));
        if (!Set.of("STAFF", "MANAGER").contains(actor.role())
                && view != ScheduleFilter.View.PERSONAL && view != ScheduleFilter.View.SECTION) {
            throw new ApiFailure(403, "FORBIDDEN", "Bạn không có quyền trong phạm vi này.");
        }
        LocalDate from = optionalDate(query.get("from"));
        LocalDate to = optionalDate(query.get("to"));
        if (from != null && to != null && from.isAfter(to)) {
            throw invalid("Khoảng ngày không hợp lệ.");
        }
        long semesterId = id(query.get("semesterId"));
        Long targetId = switch (view) {
            case PERSONAL, ALL -> null;
            default -> id(query.get("id"));
        };
        return new ScheduleFilter(semesterId, from, to, view, targetId);
    }

    private LocalDate optionalDate(String value) {
        return value == null || value.isEmpty() ? null : LocalDate.parse(date(value));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Object schedule(Actor actor, Map<String, String> query) {
        return repository.schedule(actor, filter(actor, query), query, false);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Object nextSession(Actor actor, Map<String, String> query) {
        return repository.schedule(actor, filter(actor, query), query, true);
    }

    public Object sections(Actor actor, Map<String, String> query) {
        return repository.sections(actor, filter(actor, query));
    }

    public Object stats(Actor actor, String semesterId) {
        role(actor, "MANAGER");
        long id = id(semesterId);
        return Map.of("rooms", repository.roomStatistics(id),
                "lecturers", repository.lecturerStatistics(id));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Object notifications(Actor actor, Map<String, String> query) {
        return notifications.findForUser(actor.id(), query);
    }

    public int markAll(Actor actor) {
        return notifications.markAll(actor.id());
    }

    public void markRead(Actor actor, long id) {
        notifications.markRead(actor.id(), id);
    }
}
