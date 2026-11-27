import { numeric } from '../../utils/forms.js'

export const roomStatuses = { ACTIVE:'Hoạt động', MAINTENANCE:'Bảo trì', INACTIVE:'Ngừng sử dụng' }
export const shifts = { MORNING:'Ca sáng', AFTERNOON:'Ca chiều', EVENING:'Ca tối' }

const fields = {
  rooms: ['campusId','code','name','capacity','type','status'],
  periods: ['campusId','periodNo','startTime','endTime','shift','effectiveFrom'],
  holidays: ['campusId','name','startDate','endDate']
}

export function catalogPayload(tab, form) {
  const data = numeric(form, ['campusId','capacity','periodNo'])
  return Object.fromEntries(fields[tab].map(key => [key, data[key]]))
}
