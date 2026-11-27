export const navigation = {
  STAFF: [
    ['Đào tạo', ['sections','planning','automatic','approvals']],
    ['Lịch và danh mục', ['schedule','requests','catalog']],
    ['Tài khoản', ['notifications']],
  ],
  MANAGER: [
    ['Đào tạo', ['approvals','sections','automatic']],
    ['Lịch và báo cáo', ['schedule','statistics']],
    ['Tài khoản', ['notifications']],
  ],
  LECTURER: [['Giảng dạy', ['schedule','availability','requests']], ['Tài khoản', ['notifications']]],
  STUDENT: [['Lịch học', ['overview','schedule']], ['Tài khoản', ['notifications']]],
}

export function resolvePage(role, requested) {
  const groups = navigation[role]
  return groups.some(([, keys]) => keys.includes(requested)) ? requested : role === 'STUDENT' ? 'schedule' : groups[0][1][0]
}
