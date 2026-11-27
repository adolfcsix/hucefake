package vn.huce.validation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;

public final class Inputs {
    private Inputs() {
    }

    public static ApiFailure invalid(String message) {
        return new ApiFailure(400, "VALIDATION_ERROR", message);
    }

    public static ApiFailure state(String message) {
        return new ApiFailure(409, "INVALID_STATE", message);
    }

    public static void role(Actor actor, String... allowed) {
        if (!Arrays.asList(allowed).contains(actor.role())) {
            throw new ApiFailure(403, "FORBIDDEN", "Bạn không có quyền trong phạm vi này.");
        }
    }

    public static void unit(Actor actor, Object id) {
        if ("STAFF".equals(actor.role()) && (actor.unitId() == null
                || !(id instanceof Number number) || !Objects.equals(actor.unitId(), number.longValue()))) {
            throw new ApiFailure(403, "FORBIDDEN", "Bạn không có quyền trong phạm vi này.");
        }
    }

    public static long number(Object value, long min, long max) {
        try {
            if (!(value instanceof Number || value instanceof String)) {
                throw new NumberFormatException();
            }
            long number = new BigDecimal(value.toString().trim()).longValueExact();
            if (number < min || number > max) {
                throw new NumberFormatException();
            }
            return number;
        } catch (ArithmeticException | NumberFormatException error) {
            throw invalid("Số nhập vào nằm ngoài phạm vi cho phép.");
        }
    }

    public static long id(Object value) {
        return number(value, 1, 9007199254740991L);
    }

    public static String text(Object value, int max) {
        if (!(value instanceof String text) || text.trim().isEmpty() || text.trim().length() > max) {
            throw invalid("Nội dung trống hoặc vượt giới hạn ký tự.");
        }
        return text.trim();
    }

    public static String date(Object value) {
        try {
            if (!(value instanceof String date) || !date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
                throw new IllegalArgumentException();
            }
            return LocalDate.parse(date).toString();
        } catch (RuntimeException error) {
            throw invalid("Ngày không hợp lệ.");
        }
    }

    public static String choice(Object value, String... choices) {
        if (!(value instanceof String choice) || !Arrays.asList(choices).contains(choice)) {
            throw invalid("Lựa chọn không hợp lệ.");
        }
        return choice;
    }

    public static void version(Object value, Map<String, Object> row) {
        if (number(value, 0, 9007199254740991L) != ((Number) row.get("version")).longValue()) {
            throw new ApiFailure(409, "VERSION_CONFLICT",
                    "Dữ liệu đã thay đổi. Hãy tải lại trước khi lưu.", row);
        }
    }

    public static void version(Object value, long currentVersion, Supplier<Object> details) {
        if (number(value, 0, 9007199254740991L) != currentVersion) {
            throw new ApiFailure(409, "VERSION_CONFLICT",
                    "Dữ liệu đã thay đổi. Hãy tải lại trước khi lưu.", details.get());
        }
    }

    public static long n(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    public static String s(Map<String, Object> row, String key) {
        return String.valueOf(row.get(key));
    }
}
