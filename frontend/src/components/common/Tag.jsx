import { statuses } from '../../constants/domain'

export function Tag({ value }) {
  return <span className={'tag ' + value?.toLowerCase()}>{statuses[value] || value || '—'}</span>
}
