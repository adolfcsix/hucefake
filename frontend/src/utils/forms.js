export const codes = rows => rows.map(r => [r.id, r.name ? r.code + ' · ' + r.name : r.code])
export const values = (start, end) => Array.from({ length: end - start + 1 }, (_, i) => start + i)
export const builder = set => key => value => set(previous => ({ ...previous, [key]: value }))
export const numeric = (form, names) => ({ ...form, ...Object.fromEntries(names.map(n => [n, form[n] === '' ? null : Number(form[n])])) })
