const utf8 = new TextEncoder()
const text = value => String(value ?? '').replaceAll('\\', '\\\\').replace(/\r\n|\r|\n/g, '\\n')
  .replaceAll(';', '\\;').replaceAll(',', '\\,')
export const sessionInstant = value => Date.parse(value + '+07:00')

function utcStamp(milliseconds) {
  return new Date(milliseconds).toISOString().slice(0, 19).replace(/[-:]/g, '') + 'Z'
}

function fold(line) {
  const segments = ['']
  let size = 0
  for (const character of line) {
    const bytes = utf8.encode(character).length
    if (size + bytes > 75) {
      segments.push(' ')
      size = 1
    }
    segments[segments.length - 1] += character
    size += bytes
  }
  return segments.join('\r\n')
}

export function makeCalendar(rows, generatedAt = Date.now()) {
  const lines = ['BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//HUCE//Thoi khoa bieu//VI', 'CALSCALE:GREGORIAN']
  const stamp = utcStamp(generatedAt)
  let events = 0
  for (const row of rows) {
    const start = sessionInstant(row.start_at)
    const end = sessionInstant(row.end_at)
    if (row.status === 'CANCELLED' || !Number.isFinite(start) || !Number.isFinite(end) || start >= end) continue
    events++
    const description = [row.section_code, row.staff_code && 'Giảng viên ' + row.staff_code,
      row.source_session_id && 'Buổi thay thế'].filter(Boolean).join('\n')
    lines.push('BEGIN:VEVENT', 'UID:fm05-session-' + text(row.id) + '@campus.local', 'DTSTAMP:' + stamp,
      'DTSTART:' + utcStamp(start), 'DTEND:' + utcStamp(end), 'SUMMARY:' + text(row.subject_name || row.section_code),
      ...(row.room_code ? ['LOCATION:' + text(row.room_code)] : []), 'DESCRIPTION:' + text(description), 'END:VEVENT')
  }
  if (!events) return null
  return [...lines, 'END:VCALENDAR'].map(fold).join('\r\n') + '\r\n'
}
