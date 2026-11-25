export class HttpError extends Error {
  constructor(status, code, message, details = {}) {
    super(message)
    Object.assign(this, { status, code, details })
  }
}
export const invalid = message => new HttpError(400, 'VALIDATION_ERROR', message)
export const missing = () => new HttpError(404, 'NOT_FOUND', 'Không tìm thấy dữ liệu.')
export const forbidden = () => new HttpError(403, 'FORBIDDEN', 'Bạn không có quyền trong phạm vi này.')
export const state = (message, details) => new HttpError(409, 'INVALID_STATE', message, details)
export const duplicate = (message, details) => new HttpError(409, 'DUPLICATE_REQUEST', message, details)
export const stale = details => new HttpError(409, 'VERSION_CONFLICT', 'Dữ liệu đã thay đổi. Hãy tải lại trước khi lưu.', details)
export const conflict = details => new HttpError(409, 'SCHEDULE_CONFLICT', 'Lịch dự kiến có xung đột.', details)

export function role(req, ...roles) {
  if (!roles.includes(req.actor.role)) throw forbidden()
}
export function unit(actor, id) {
  if (actor.role === 'STAFF' && actor.unitId !== id) throw forbidden()
}
export function need(row) {
  if (!row) throw missing()
  return row
}
export function integer(value, min = 1, max = Number.MAX_SAFE_INTEGER) {
  if ((typeof value !== 'string' && typeof value !== 'number') || String(value).trim() === '' ||
      !Number.isSafeInteger(Number(value)) || Number(value) < min || Number(value) > max) {
    throw invalid('Số nhập vào nằm ngoài phạm vi cho phép.')
  }
  return Number(value)
}
export function choice(value, ...choices) {
  if (!choices.includes(value)) throw invalid('Lựa chọn không hợp lệ.')
  return value
}
export function text(value, max) {
  if (typeof value !== 'string' || !value.trim() || value.trim().length > max) throw invalid('Nội dung trống hoặc vượt giới hạn ký tự.')
  return value.trim()
}
export function date(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) throw invalid('Ngày phải có định dạng YYYY-MM-DD.')
  const parsed = new Date(value + 'T00:00:00Z')
  if (!Number.isFinite(parsed.valueOf()) || parsed.toISOString().slice(0, 10) !== value) throw invalid('Ngày không hợp lệ.')
  return value
}
export function version(value, row) {
  if (integer(value, 0) !== row.version) throw stale(row)
}
export function errors(e, req, res, next) {
  if (res.headersSent) return next(e)
  let error = e
  if (e.code === '23505') error = e.constraint === 'uk_cs_semester_code'
    ? duplicate('Mã lớp đã có trong học kỳ này.',{field:'code'})
    : duplicate('Mã hoặc bản ghi đã tồn tại.')
  if (['23503', '23514', '23502', '22P02'].includes(e.code) || e.type === 'entity.parse.failed') error = invalid('Dữ liệu không hợp lệ hoặc không đáp ứng ràng buộc.')
  if (!(error instanceof HttpError)) {
    console.error({request_id:req.requestId},e)
    error = new HttpError(500, 'INTERNAL_ERROR', 'Máy chủ chưa thể xử lý yêu cầu.')
  }
  res.status(error.status).json({ code: error.code, message: error.message, details: error.details, request_id: req.requestId })
}
