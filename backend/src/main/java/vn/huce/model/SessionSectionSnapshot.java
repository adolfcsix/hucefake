package vn.huce.model;

/** Section attributes required by session adjustments and lecturer requests. */
public record SessionSectionSnapshot(long id, long semesterId, String code, String status,
                                     int capacity, String requiredRoomType, Long managingUnitId) {
}
