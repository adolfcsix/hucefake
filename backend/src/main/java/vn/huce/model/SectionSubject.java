package vn.huce.model;

/** Subject identity and managing-unit scope required by course-section operations. */
public record SectionSubject(long id, String code, String name, String status, Long managingUnitId) {
}
