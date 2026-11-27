package vn.huce.model;

public record RoomDraft(long campusId, String code, String name, long capacity,
                        String type, String status) {
}
