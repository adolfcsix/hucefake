package vn.huce.service.chat;

import vn.huce.model.ChatSemester;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static vn.huce.validation.Inputs.date;
import static vn.huce.validation.Inputs.invalid;

final class ChatDateRangeParser {
    private static final Pattern DATE = Pattern.compile("\\b([0-9]{4}-[0-9]{2}-[0-9]{2}|[0-9]{1,2}/[0-9]{1,2}(?:/[0-9]{4})?)\\b");
    private static final Pattern WEEKDAY = Pattern.compile("\\bthu (hai|ba|tu|nam|sau|bay|[2-7])\\b");

    private ChatDateRangeParser() {
    }

    record Window(String from, String to) {
    }

    static Window window(String message, String action, LocalDate now, ChatSemester semester) {
        String question = ChatIntent.normalize(message);
        List<LocalDate> dates = new ArrayList<>();
        Matcher matcher = DATE.matcher(question);
        try {
            while (matcher.find()) {
                String value = matcher.group();
                if (value.contains("-")) {
                    dates.add(LocalDate.parse(date(value)));
                } else {
                    String[] parts = value.split("/");
                    int year = parts.length == 3 ? Integer.parseInt(parts[2]) : now.getYear();
                    dates.add(LocalDate.of(year, Integer.parseInt(parts[1]), Integer.parseInt(parts[0])));
                }
            }
        } catch (RuntimeException error) {
            throw invalid("Ngày không hợp lệ.");
        }
        if (dates.size() > 2) {
            throw invalid("Chọn một ngày hoặc một khoảng gồm hai ngày.");
        }
        if (!dates.isEmpty()) {
            LocalDate from = dates.getFirst();
            LocalDate to = dates.getLast();
            if (to.isBefore(from) || to.isAfter(from.plusDays(30))) {
                throw invalid("Khoảng tra cứu cần theo thứ tự và tối đa 31 ngày.");
            }
            return new Window(from.toString(), to.toString());
        }
        if (question.contains("ngay mai")) {
            return single(now.plusDays(1));
        }
        if (question.contains("hom qua")) {
            return single(now.minusDays(1));
        }
        if (question.contains("hom nay")) {
            return single(now);
        }
        LocalDate start = now.minusDays(now.getDayOfWeek().getValue() - 1)
                .plusDays(question.contains("tuan sau") ? 7 : question.contains("tuan truoc") ? -7 : 0);
        Matcher weekday = WEEKDAY.matcher(question);
        if (weekday.find()) {
            int offset = switch (weekday.group(1)) {
                case "hai" -> 0;
                case "ba" -> 1;
                case "tu" -> 2;
                case "nam" -> 3;
                case "sau" -> 4;
                case "bay" -> 5;
                default -> Integer.parseInt(weekday.group(1)) - 2;
            };
            return single(start.plusDays(offset));
        }
        if (question.contains("chu nhat")) {
            return single(start.plusDays(6));
        }
        if ("NEXT".equals(action) && !question.contains("tuan ")) {
            return new Window(now.toString(), semester.endDate().toString());
        }
        return new Window(start.toString(), start.plusDays(6).toString());
    }

    private static Window single(LocalDate date) {
        return new Window(date.toString(), date.toString());
    }
}
