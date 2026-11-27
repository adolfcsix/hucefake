import { codes } from '../../utils/forms'

export const same = (a, b) => Number(a) === Number(b)

export function filters(catalog, sections) {
  return { room: codes(catalog.rooms), staff: codes(catalog.lecturers), group: codes(catalog.groups), section: codes(sections) }
}
