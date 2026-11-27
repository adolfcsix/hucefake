export const formatDate = value => value?.slice(0, 10).split('-').reverse().join('/') || '—'
export const vietnamToday = () => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date())
export function addCalendarDays(value, amount) {
  const date = new Date(value + 'T12:00:00Z')
  date.setUTCDate(date.getUTCDate() + amount)
  return date.toISOString().slice(0,10)
}
