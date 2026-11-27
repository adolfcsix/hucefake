package vn.huce.dto;

import vn.huce.model.SectionDraft;

/** Validated draft parameters persisted with an HUCEChat proposal.
 * Dates use the existing ISO string contract, independent of mapper settings.
 */
public record SectionDraftPreview(long semesterId, long subjectId, String code,
                                  long capacity, long sessionsPerWeek, long periodsPerSession,
                                  String requiredRoomType, String startDate, String endDate) {
    public static SectionDraftPreview from(SectionDraft draft) {
        return new SectionDraftPreview(draft.semesterId(), draft.subjectId(), draft.code(),
                draft.capacity(), draft.sessionsPerWeek(), draft.periodsPerSession(),
                draft.requiredRoomType(), draft.startDate().toString(), draft.endDate().toString());
    }
}
