package vn.huce.repository;

import java.sql.Date;
import vn.huce.validation.Inputs;

/** Persistence for the local setup CLI; caller owns the transaction. */
public final class SetupRepository {
    private final SqlStore store;

    public SetupRepository(SqlStore store) {
        this.store = store;
    }

    public void unit(String code, String name) {
        store.jdbc().update("INSERT INTO organizational_units(code,name) VALUES (?,?) ON CONFLICT(code) DO NOTHING", code, name);
    }

    public void campus(String code, String name) {
        store.jdbc().update("INSERT INTO campuses(code,name) VALUES (?,?) ON CONFLICT(code) DO NOTHING", code, name);
    }

    public void semester(String year, String code, Date from, Date to) {
        store.jdbc().update("INSERT INTO academic_years(code) VALUES (?) ON CONFLICT(code) DO NOTHING", year);
        store.jdbc().update("INSERT INTO semesters(year_id,code,start_date,end_date) SELECT id,?,?,? FROM academic_years WHERE code=? ON CONFLICT(code) DO NOTHING", code, from, to, year);
    }

    public long unitId(String code, String label) {
        return reference("SELECT id FROM organizational_units WHERE code=?", label, code);
    }

    public void subject(String code, String name, long unit, long credits) {
        store.jdbc().update("INSERT INTO subjects(code,name,managing_unit_id,credits) VALUES (?,?,?,?) ON CONFLICT(code) DO NOTHING", code, name, unit, credits);
    }

    public void group(String curriculum, String code, long capacity) {
        store.jdbc().update("INSERT INTO curriculum_versions(code) VALUES (?) ON CONFLICT(code) DO NOTHING", curriculum);
        store.jdbc().update("INSERT INTO academic_groups(code,curriculum_id,capacity) SELECT ?,id,? FROM curriculum_versions WHERE code=? ON CONFLICT(code) DO NOTHING", code, capacity, curriculum);
    }

    public long studentId(String username, String label) {
        return reference("SELECT u.id FROM users u JOIN roles r ON r.role_id=u.role_id WHERE u.username=? AND r.code='STUDENT' AND u.status='ACTIVE'", label, username);
    }

    public long sectionId(String sectionCode, String semesterCode, String label) {
        return reference("SELECT c.id FROM course_sections c JOIN semesters s ON s.id=c.semester_id WHERE c.code=? AND s.code=? AND c.status<>'CANCELLED'", label, sectionCode, semesterCode);
    }

    public void enrollment(long sectionId, long studentId) {
        store.jdbc().update("INSERT INTO section_enrollments(section_id,student_user_id) VALUES (?,?) ON CONFLICT(section_id,student_user_id) DO NOTHING", sectionId, studentId);
    }

    public long activeCampusId(String code, String label) {
        return reference("SELECT id FROM campuses WHERE code=? AND status='ACTIVE'", label, code);
    }

    public long createUser(String username, String email, String hash, String role) {
        return Inputs.n(store.need("INSERT INTO users(role_id,username,email,password_hash) SELECT role_id,?,?,? FROM roles WHERE code=? RETURNING id", username, email, hash, role), "id");
    }

    public long createStaff(long user, String code, Long unit) {
        return Inputs.n(store.need("INSERT INTO staff_profiles(user_id,code,unit_id) VALUES (?,?,?) RETURNING id", user, code, unit), "id");
    }

    public void assignCampus(long staff, long campus) {
        store.jdbc().update("INSERT INTO staff_campuses(staff_id,campus_id) VALUES (?,?)", staff, campus);
    }

    private long reference(String sql, String label, Object... args) {
        var row = store.one(sql, args);
        if (row == null) {
            throw Inputs.invalid("Không tìm thấy " + label + ".");
        }
        return Inputs.n(row, "id");
    }
}
