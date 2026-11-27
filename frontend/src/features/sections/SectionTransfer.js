const fields = ['semesterCode','subjectCode','code','capacity','sessionsPerWeek','periodsPerSession',
  'requiredRoomType','startDate','endDate']
const format = 'fm05-section-drafts'
const protectedCell = /^['=+@\-\t\r\n]/

export function encodeDraftJson(rows) {
  return JSON.stringify({ format, version:1, rows },null,2) + '\n'
}

export function encodeDraftCsv(rows) {
  const cell = value => {
    const text = String(value ?? '')
    return '"' + ((protectedCell.test(text) ? "'" : '') + text).replaceAll('"','""') + '"'
  }
  return '\uFEFF' + [fields.join(','), ...rows.map(row=>fields.map(key=>cell(row[key])).join(','))].join('\r\n') + '\r\n'
}

function readCsv(source) {
  const records = []
  const separator = source.split(/\r|\n/,1)[0].includes(';') ? ';' : ','
  const token = separator===';' ? /(?:"((?:[^"]|"")*)"|([^;"\r\n]*))(;|\r\n|\n|\r|$)/gy :
    /(?:"((?:[^"]|"")*)"|([^,"\r\n]*))(,|\r\n|\n|\r|$)/gy
  while (token.lastIndex < source.length) {
    const values = []
    while (true) {
      const match = token.exec(source)
      if (!match) throw new Error('Dòng CSV ' + (records.length+1) + ' có dấu ngoặc kép hoặc dấu phân cách sai.')
      const value = match[1] === undefined ? match[2] : match[1].replaceAll('""','"')
      values.push(/^'(?=['=+@\-\t\r\n])/.test(value) ? value.slice(1) : value)
      if (match[3] !== separator) break
    }
    if (values.length>1 || values[0].trim()) records.push(values)
  }
  const [header,...data] = records
  if (header?.length !== fields.length || fields.some(key=>!header.includes(key))) {
    throw new Error('Tệp CSV cần đủ cột theo mẫu. Hãy tải mẫu CSV trên màn hình nhập.')
  }
  return data.map((values,index)=>{
    if (values.length !== header.length) throw new Error('Dòng dữ liệu ' + (index+1) + ' có sai số cột.')
    return Object.fromEntries(header.map((field,column)=>[field,values[column].trim()]))
  })
}

export function decodeDraftFile(filename,source) {
  const input = source.replace(/^\uFEFF/,'')
  let rows
  if (/\.csv$/i.test(filename)) rows = readCsv(input)
  else if (/\.json$/i.test(filename)) {
    let bundle
    try { bundle = JSON.parse(input) } catch { throw new Error('Tệp JSON không đọc được.') }
    if (bundle?.format !== format || bundle.version !== 1) throw new Error('Tệp JSON cần có định dạng lớp nháp phiên bản 1.')
    rows = bundle.rows
  } else throw new Error('Hãy chọn tệp CSV hoặc JSON.')
  if (!Array.isArray(rows) || rows.length<1 || rows.length>200) throw new Error('Mỗi tệp cần có từ 1 đến 200 lớp.')
  return rows
}

export function draftFromSection(section,semesterCode) {
  return {
    semesterCode, subjectCode:section.subject_code, code:section.code, capacity:section.capacity,
    sessionsPerWeek:section.sessions_per_week, periodsPerSession:section.periods_per_session,
    requiredRoomType:section.required_room_type, startDate:section.start_date, endDate:section.end_date
  }
}
