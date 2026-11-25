let csrfRequest
const pendingWrites = new Map()
const backendUnavailable = 'Không thể kết nối với máy chủ. Vui lòng thử lại.'

function token() {
  if (!csrfRequest) {
    csrfRequest = request('/csrf', 'GET').catch(error => {
      csrfRequest = null
      throw error
    })
  }
  return csrfRequest
}

async function request(path, method, data, retry = true, key) {
  const headers = { Accept: 'application/json' }
  if (method !== 'GET') {
    const csrf = await token()
    headers[csrf.header] = csrf.token
    if (key) headers['Idempotency-Key'] = key
    if (data !== undefined) headers['Content-Type'] = 'application/json'
  }
  let response
  try {
    response = await fetch('/api' + path, {
      method, headers, body: data === undefined ? undefined : JSON.stringify(data)
    })
  } catch (error) {
    throw new Error(backendUnavailable, { cause: error })
  }
  if (response.status === 204) {
    if (path === '/auth/logout') csrfRequest = null
    return
  }
  const result = await response.json().catch(() => null)
  if (result?.code === 'CSRF_INVALID' && retry) {
    csrfRequest = null
    return request(path, method, data, false, key)
  }
  if (!response.ok || result === null) {
    if (response.status === 401) csrfRequest = null
    throw Object.assign(new Error(result?.message || backendUnavailable), result, { status: response.status })
  }
  return result
}

export async function api(path, method = 'GET', data) {
  if (method === 'GET' || path.startsWith('/auth/')) {
    if (method !== 'GET') pendingWrites.clear()
    return request(path, method, data)
  }
  const signature = JSON.stringify([path, method, data])
  const now = Date.now()
  for (const [key, value] of pendingWrites) if (value.until < now) pendingWrites.delete(key)
  let entry = pendingWrites.get(signature)
  if (!entry) {
    const random = crypto.getRandomValues(new Uint8Array(16))
    entry = { key:Array.from(random, byte => byte.toString(16).padStart(2,'0')).join(''), until:now+600_000 }
    pendingWrites.set(signature,entry)
  }
  try {
    const result = await request(path,method,data,true,entry.key)
    if (pendingWrites.get(signature) === entry) pendingWrites.delete(signature)
    return result
  } catch (error) {
    if (error.status >= 400 && error.status < 500 && pendingWrites.get(signature) === entry) pendingWrites.delete(signature)
    throw error
  }
}

export const checkScheduleConflicts = slot => request('/schedule/conflicts/check', 'POST', slot)

export function downloadFile(data, type, filename) {
  const url = URL.createObjectURL(new Blob([data], { type }))
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

export function downloadCsv(rows) {
  const cell = value => {
    let text = String(value ?? '')
    if (/^[=+@\-\t\r\n]/.test(text)) text = "'" + text
    return '"' + text.replaceAll('"', '""') + '"'
  }
  const csv = [
    ['Mã lớp', 'Học phần', 'Bắt đầu', 'Kết thúc', 'Phòng', 'Giảng viên', 'Trạng thái', 'Lý do'],
    ...rows.map(r => [r.section_code, r.subject_name, r.start_at, r.end_at, r.room_code, r.staff_code, r.status, r.exception_reason])
  ].map(row => row.map(cell).join(',')).join('\r\n')
  downloadFile('\uFEFF' + csv, 'text/csv;charset=utf-8', 'thoikhoabieu.csv')
}
