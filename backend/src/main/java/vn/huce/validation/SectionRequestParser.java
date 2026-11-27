package vn.huce.validation;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import vn.huce.dto.SectionAssignmentRequest;
import vn.huce.dto.SectionTargetGroupsRequest;

/** Parse the existing JSON contract after authorization and version checks.
 * The unmodified request remains available to the idempotency layer.
 */
public final class SectionRequestParser {
    private SectionRequestParser() {
    }

    public static SectionAssignmentRequest assignment(Map<String, Object> input) {
        long staffId = Inputs.id(input.get("staffId"));
        Set<Long> assistants = ids(input.get("assistantIds"));
        if (assistants.contains(staffId)) {
            throw Inputs.invalid("Giảng viên chính không thể đồng thời là trợ giảng.");
        }
        return new SectionAssignmentRequest(staffId, assistants);
    }

    public static SectionTargetGroupsRequest groups(Map<String, Object> input) {
        return new SectionTargetGroupsRequest(ids(input.get("groupIds")));
    }

    private static Set<Long> ids(Object value) {
        Set<Long> result = new LinkedHashSet<>();
        if (value == null) {
            return result;
        }
        if (!(value instanceof List<?> list) || list.size() > 200) {
            throw Inputs.invalid("Danh sách lựa chọn không hợp lệ.");
        }
        for (Object item : list) {
            result.add(Inputs.id(item));
        }
        return result;
    }
}
