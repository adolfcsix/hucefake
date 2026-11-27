import { cloneElement, useId } from 'react'

export function Field({ label, children }) {
  const id = useId()
  return <div className="field">
    <label htmlFor={id}>{label}</label>
    {cloneElement(children, { id })}
  </div>
}
export function Select({ value, set, options, placeholder, ...props }) {
  return <select value={value ?? ''} onChange={e => set(e.target.value)} {...props}>
    {placeholder && <option value="">{placeholder}</option>}
    {options.map(([id, label]) => <option key={id} value={id}>{label}</option>)}
  </select>
}
export function Options({ value, set, values, ...props }) {
  return <Select value={value} set={set} options={Object.entries(values)} {...props} />
}
