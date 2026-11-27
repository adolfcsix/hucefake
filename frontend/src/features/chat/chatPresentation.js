export const topics = {
  STAFF:['Tra lớp nháp','Tạo lớp học phần','Chạy xếp lịch tự động','Hồ sơ chờ duyệt','Đề nghị đổi lịch'],
  MANAGER:['Hồ sơ chờ duyệt','Thống kê sử dụng phòng','Tra lớp học phần','Chạy xếp lịch tự động'],
  LECTURER:['Lịch giảng tuần này','Buổi dạy tiếp theo','Thời gian bận','Đề nghị đổi lịch'],
  STUDENT:['Buổi học tiếp theo','Lịch học ngày mai','Thời khóa biểu tuần này'],
}
export const pageTopics = {
  schedule:'Thời khóa biểu tuần này', sections:'Tra lớp học phần', automatic:'Chạy xếp lịch tự động',
  approvals:'Hồ sơ chờ duyệt', requests:'Đề nghị đổi lịch', availability:'Thời gian bận',
  statistics:'Thống kê sử dụng phòng', notifications:'Thông báo của tôi',
}
export const proposalNames = {
  GENERATE:'Chạy xếp lịch tự động', APPLY:'Áp dụng phương án', DRAFT:'Tạo lớp học phần nháp',
  READ_NOTICES:'Đánh dấu thông báo đã đọc',
}
export const displayTime = new Intl.DateTimeFormat('vi-VN',{dateStyle:'short',timeStyle:'short',timeZone:'Asia/Ho_Chi_Minh'})
