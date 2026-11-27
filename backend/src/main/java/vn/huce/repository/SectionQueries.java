package vn.huce.repository;

/** Shared SQL projections for section and schedule-pattern reads. */
public final class SectionQueries {
    private SectionQueries() {
    }
    public static final String SECTION_SQL="""
      SELECT c.*,s.managing_unit_id,s.name AS subject_name,s.code AS subject_code,a.staff_id,sp.code AS staff_code,
        (SELECT count(*) FROM section_schedule_patterns p WHERE p.section_id=c.id) AS scheduled,
        ARRAY(SELECT g.group_id FROM section_target_groups g WHERE g.section_id=c.id ORDER BY g.group_id) AS group_ids,
        ARRAY(SELECT ag.code FROM section_target_groups g JOIN academic_groups ag ON ag.id=g.group_id WHERE g.section_id=c.id ORDER BY g.group_id) AS group_codes,
        ARRAY(SELECT aa.staff_id FROM section_assignments aa WHERE aa.section_id=c.id AND aa.role='ASSISTANT' AND aa.valid_to IS NULL ORDER BY aa.staff_id) AS assistant_ids
      FROM course_sections c JOIN subjects s ON s.id=c.subject_id
      LEFT JOIN section_assignments a ON a.section_id=c.id AND a.role='MAIN' AND a.valid_to IS NULL
      LEFT JOIN staff_profiles sp ON sp.id=a.staff_id
      """;
    public static final String PATTERN_SQL="""
      SELECT p.*,c.code AS section_code,s.name AS subject_name,t.code AS room_code,sp.code AS staff_code,
        c.status AS section_status,c.subject_id,s.managing_unit_id,
        ARRAY(SELECT g.group_id FROM section_target_groups g WHERE g.section_id=c.id ORDER BY g.group_id) AS group_ids
      FROM section_schedule_patterns p JOIN course_sections c ON c.id=p.section_id
      JOIN subjects s ON s.id=c.subject_id JOIN teaching_rooms t ON t.id=p.room_id JOIN staff_profiles sp ON sp.id=p.staff_id
      """;
}
