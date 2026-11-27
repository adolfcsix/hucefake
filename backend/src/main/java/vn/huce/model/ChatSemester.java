package vn.huce.model;

import java.time.LocalDate;

/** Academic context shared by HUCEChat and its proposal workflow. */
public record ChatSemester(long id, String code, LocalDate startDate, LocalDate endDate) {
}
