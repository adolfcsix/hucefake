package vn.huce.model;

/** Effective room attributes used by manual and automatic scheduling. */
public record SchedulingRoom(long id, long campusId, String code, int capacity,
                             String type, String status) {
}
