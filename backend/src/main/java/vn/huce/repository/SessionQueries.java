package vn.huce.repository;

/** Shared projection of the main lecturer and active assistants of a session. */
public final class SessionQueries {
    private SessionQueries() {
    }

    public static final String STAFF_IDS = "(ARRAY[x.staff_id] || ARRAY(SELECT a.staff_id FROM section_assignments a WHERE a.section_id=x.section_id AND a.role='ASSISTANT' AND a.valid_to IS NULL))";
}
