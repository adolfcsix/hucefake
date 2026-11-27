export const roomTypes = { LECTURE: 'Giảng đường', COMPUTER_LAB: 'Phòng máy', LAB: 'Phòng thí nghiệm', STUDIO: 'Xưởng thực hành' }
export const roleNames = { STAFF: 'Nhân viên đào tạo', MANAGER: 'Quản lý', LECTURER: 'Giảng viên', STUDENT: 'Sinh viên' }
export const days = ['Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy', 'Chủ nhật']
export const statuses = {
  DRAFT: 'Nháp', OPEN: 'Đang mở', CLOSED: 'Đã đóng', CANCELLED: 'Đã hủy', ACTIVE: 'Hoạt động',
  INACTIVE: 'Ngừng sử dụng', MAINTENANCE: 'Bảo trì', PLANNED: 'Đã lên lịch', COMPLETED: 'Hoàn tất',
  QUEUED: 'Đang chờ', RUNNING: 'Đang xử lý', FAILED: 'Thất bại', DISCARDED: 'Đã bỏ',
  APPLIED: 'Đã áp dụng', PENDING: 'Chờ xử lý', APPROVED: 'Đã duyệt', RETURNED: 'Đã trả lại',
  SUBMITTED: 'Chờ duyệt', PUBLISHED: 'Đã công bố', SUPERSEDED: 'Đã thay thế',
  CANCEL: 'Nghỉ học', RESCHEDULE: 'Học bù', ROOM_CHANGE: 'Đổi phòng', BUSY: 'Bận', AVOID: 'Hạn chế xếp'
}

export const dayOptions = days.map((day, i) => [i + 1, day])
