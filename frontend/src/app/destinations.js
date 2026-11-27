import Catalog from '../features/catalog/Catalog'
import Sections from '../features/sections/Sections'
import StudentHome from '../features/student/StudentHome'
import { ChangeRequests } from '../features/timetable/ChangeRequests'
import { Planning } from '../features/timetable/Planning'
import { Published } from '../features/timetable/Published'
import { Approvals, Automatic, Availability } from '../features/workflow/Workflow'
import Statistics from '../features/statistics/Statistics'
import Notifications from '../features/notifications/Notifications'

export const destinations = {
  overview: ['Tổng quan', 'home', StudentHome],
  schedule: ['Thời khóa biểu', 'calendar', Published],
  sections: ['Lớp học phần', 'layers', Sections],
  planning: ['Xếp lịch thủ công', 'grid', Planning],
  automatic: ['Xếp lịch tự động', 'spark', Automatic],
  approvals: ['Duyệt thời khóa biểu', 'check', Approvals],
  catalog: ['Danh mục lịch học', 'folder', Catalog],
  availability: ['Thời gian bận', 'clock', Availability],
  requests: ['Đề nghị đổi lịch', 'repeat', ChangeRequests],
  statistics: ['Thống kê sử dụng', 'chart', Statistics],
  notifications: ['Thông báo', 'bell', Notifications]
}
