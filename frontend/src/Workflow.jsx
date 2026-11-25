import { useEffect, useState } from 'react'
import { api } from './api'
import { builder, days, dayOptions, Editor, ErrorBox, Field, numeric, Options, Panel, Select, statuses, Table, Tag, useFetch, WeekGrid } from './ui'

const defaults = { population:100, generations:500, crossover:0.9, mutation:0.02, seed:42 }
const activeRun = status => status === 'QUEUED' || status === 'RUNNING'

export function Automatic({ user, semester, revision, reload, setSchedulingContext }) {
  const [params, set] = useState(defaults)
  const [selected, setSelected] = useState(null)
  const [detail, setDetail] = useState(null)
  const [retry, setRetry] = useState(0)
  const [refresh, setRefresh] = useState(0)
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState('')
  const [discard, setDiscard] = useState(null)
  const query = '?semesterId=' + semester.id
  const history = useFetch(async () => {
    const [runs, semesterState] = await Promise.all([api('/schedule-runs' + query),api('/schedule-runs/active' + query)])
    return { runs, active:semesterState.active, revision }
  }, [query, revision, refresh, detail?.run?.status])
  useEffect(() => {
    const timer = setTimeout(() => setRefresh(value => value + 1), history.data?.active ? 2000 : 5000)
    return () => clearTimeout(timer)
  }, [history.data, history.error, refresh])

  const runs = history.data?.runs || []
  const runId = selected ?? runs[0]?.id
  const key = [runId,revision,retry].join(':')
  const current = detail?.key === key ? detail : null
  const result = current?.run
  const items = current?.items
  const progress = result?.progress?.at(-1)
  const hard = result?.hard_violations ?? progress?.hard_violations
  const soft = result?.soft_penalty ?? progress?.soft_penalty
  const fitness = result?.fitness ?? (hard == null || soft == null ? null : 1/(1 + 1000*hard + Number(soft)))
  const options = runs.map(run => [run.id, 'Lần ' + run.id + ' · ' + (statuses[run.status] || run.status)])
  if (runId && !runs.some(run => run.id === runId)) options.unshift([runId, 'Lần ' + runId])
  const occupied = history.data?.active || activeRun(result?.status)
  const canLaunch = history.data?.revision === revision && !occupied
  const field = builder(set)

  useEffect(() => {
    setSchedulingContext({semesterId:semester.id,runId,parameters:numeric(params,Object.keys(defaults))})
  },[params,runId,semester.id,setSchedulingContext])

  useEffect(() => {
    if (!runId) return
    let alive = true
    let timer
    let failures = 0
    let run = null
    async function poll() {
      try {
        run = await api('/schedule-runs/' + runId)
        if (!alive) return
        setDetail({ key, run })
        if (activeRun(run.status)) timer = setTimeout(poll, 900)
        else if (['COMPLETED','APPLIED'].includes(run.status)) {
          const rows = await api('/schedule-runs/' + runId + '/items')
          if (alive) setDetail({ key, run, items:rows })
        }
        failures = 0
      } catch (error) {
        if (!alive) return
        setDetail({ key, run, error })
        if ((!error.status || error.status >= 500) && failures++ < 2) timer = setTimeout(poll, 1200)
      }
    }
    poll()
    return () => { alive = false; clearTimeout(timer) }
  }, [runId, key])

  async function execute(action) {
    if (busy) return
    setBusy(true)
    setError(null)
    setNotice('')
    try {
      if (action === 'run') {
        const next = await api('/schedule-runs','POST',{ semesterId:semester.id, parameters:numeric(params,Object.keys(defaults)) })
        setSelected(next.id)
      } else {
        await api('/schedule-runs/' + runId + '/' + action, 'POST')
      }
      reload()
    } catch (error) {
      setError(error)
      if (error.code === 'DUPLICATE_REQUEST') setRefresh(value => value + 1)
    } finally { setBusy(false) }
  }

  const formFields = [
    ['population','Kích thước quần thể',8,200,1], ['generations','Số thế hệ tối đa',1,1000,1],
    ['crossover','Xác suất lai ghép',0,1,0.01], ['mutation','Xác suất đột biến',0,1,0.01],
    ['seed','Hạt giống',-2147483648,2147483647,1]
  ]
  const savedParams = result?.parameters && formFields.every(([name]) => Number.isFinite(result.parameters[name]))
  return <>
    <ErrorBox error={error || history.error} />
    <div className="split">
      <Panel title="Tham số giải thuật di truyền">
        {notice && <p className="notice success" role="status">{notice}</p>}
        {occupied && <p className="notice">Học kỳ đang có lượt xếp lịch. Có thể xem các phương án đã lưu.</p>}
        <form className="form-grid" onSubmit={e => { e.preventDefault(); if (canLaunch) execute('run') }}>
          {formFields.map(([name,label,min,max,step]) => <Field key={name} label={label}><input type="number" required={name!=='seed'} min={min} max={max} step={step}
            value={params[name]} onChange={e => { field(name)(e.target.value); setNotice('') }} /></Field>)}
          <div className="form-actions"><button disabled={busy || !canLaunch}>Chạy xếp lịch</button></div>
        </form>
      </Panel>
      <Panel title="Tiến độ">
        <Field label="Lần chạy"><Select value={runId} disabled={busy} set={value => {
          setSelected(value ? Number(value) : null)
          setError(null)
        }} options={options} placeholder="Lần mới nhất" /></Field>
        <ErrorBox error={current?.error} />
        {current?.error && <button type="button" className="quiet" onClick={() => setRetry(value => value + 1)}>Tải lại lần chạy</button>}
        {runId && !result && !current?.error && <p className="notice" role="status">Đang tải lần chạy…</p>}
        {!runId && history.data && <p className="muted">Chưa có lần chạy trong học kỳ này.</p>}
        {result && <div className="stack">
          <div className="button-row" role="status"><Tag value={result.status} /><span>{result.generations_run} thế hệ</span></div>
          {result.message && <p>{result.message}</p>}
          <div className="metrics">
            {[['Vi phạm cứng',hard],['Điểm phạt mềm',soft],['Độ thích nghi',fitness?.toFixed(4)]].map(([label,value]) =>
              <div key={label}><span>{label}</span><strong>{value ?? '—'}</strong></div>)}
          </div>
          {result.progress?.length > 0 && <CostChart progress={result.progress} />}
          <div className="button-row">
            {savedParams && <button type="button" className="quiet" disabled={busy} onClick={() => {
              set(Object.fromEntries(formFields.map(([name]) => [name,result.parameters[name]])))
              setNotice('Đã điền tham số của lần chạy #' + result.id + '.')
            }}>Dùng tham số lần này</button>}
            {result.status === 'COMPLETED' && <button disabled={busy || occupied || !items?.length || result.hard_violations !== 0} onClick={() => execute('apply')}>Áp dụng phương án</button>}
            {['COMPLETED','FAILED','QUEUED'].includes(result.status) && (user.role === 'MANAGER' || user.id === result.requested_by) &&
              <button className="quiet" disabled={busy} onClick={() => setDiscard(result.id)}>Bỏ phương án</button>}
          </div>
        </div>}
      </Panel>
    </div>
    {!!items?.length && <RunPlan key={runId} rows={items} />}
    {discard && <Editor title="Bỏ phương án xếp lịch" submit="Bỏ phương án" onClose={() => setDiscard(null)} onSubmit={async () => {
      await api('/schedule-runs/' + discard + '/discard','POST')
      reload()
    }}><p>Bỏ lần chạy #{discard} khỏi danh sách phương án có thể áp dụng?</p></Editor>}
  </>
}

function RunPlan({ rows }) {
  const [search,setSearch] = useState('')
  const [onlyIssues,setOnlyIssues] = useState(false)
  const [layout,setLayout] = useState(() => window.matchMedia('(max-width: 760px)').matches ? 'list' : 'grid')
  const query=search.trim().toLocaleLowerCase('vi')
  const matches=rows.filter(row => (!onlyIssues || row.violation_note) &&
    [row.section_code,row.subject_name,row.room_code].some(value => value?.toLocaleLowerCase('vi').includes(query)))
  const maxPeriod=rows.reduce((last,row) => Math.max(last,row.period_from + row.period_count - 1),12)
  return <Panel title="Phương án đề xuất" action={<div className="calendar-modes" role="group" aria-label="Cách xem phương án">
    <button type="button" aria-pressed={layout === 'grid'} onClick={() => setLayout('grid')}>Lưới theo tiết</button>
    <button type="button" aria-pressed={layout === 'list'} onClick={() => setLayout('list')}>Danh sách</button>
  </div>}>
    <div className="toolbar run-plan-tools">
      <Field label="Tìm lớp hoặc phòng"><input type="search" value={search} onChange={e => setSearch(e.target.value)} /></Field>
      {rows.some(row => row.violation_note) && <label className="inline-choice">
        <input type="checkbox" checked={onlyIssues} onChange={e => setOnlyIssues(e.target.checked)} /> Chỉ hiện buổi có vi phạm
      </label>}
      <small role="status">{matches.length}/{rows.length} buổi</small>
    </div>
    {layout === 'grid' && matches.length > 0 ? <WeekGrid rows={matches} maxPeriod={maxPeriod} /> :
    <Table rows={matches} empty="Không tìm thấy buổi phù hợp." columns={[
      ['Lớp học phần','section_code'],['Thứ',r => days[r.day_of_week - 1]],['Tiết','period_from'],
      ['Phòng','room_code'],['Vi phạm','violation_note']
    ]} />}
  </Panel>
}

function CostChart({ progress }) {
  const costs = progress.map(p => 1000 * p.hard_violations + Number(p.soft_penalty))
  const max = Math.max(1, ...costs)
  const total = Math.max(1, progress.at(-1).generation)
  const points = progress.map((p,i) => (6 + 308 * p.generation / total) + ',' + (75 - 67 * costs[i] / max)).join(' ')
  return <figure className="cost-chart"><figcaption>Chi phí tốt nhất theo thế hệ</figcaption>
    <svg viewBox="0 0 320 80" role="img" aria-label={'Chi phí từ ' + costs[0] + ' xuống ' + costs.at(-1)}>
      <path d="M 6 5 V 75 H 314" fill="none" stroke="currentColor" opacity=".22" />
      <polyline points={points} fill="none" stroke="currentColor" strokeWidth="2.5" />
      {progress.length === 1 && <circle cx={6} cy={75-67*costs[0]/max} r="3" fill="currentColor" />}
    </svg>
    <small>Thế hệ 0 <span>{total === 1 && progress.length === 1 ? '' : 'Thế hệ ' + total}</span></small>
  </figure>
}

export function Approvals({ user, semester, catalog, revision, reload }) {
  const [selected, setSelected] = useState(null)
  const [returning, setReturning] = useState(false)
  const [reason, setReason] = useState('')
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)
  const state = useFetch(() => api('/timetable-versions?semesterId=' + semester.id), [semester.id, revision])
  const staff = user.role === 'STAFF'
  const validation = useFetch(async () => staff ? {
    revision, result:await api('/timetable-versions/readiness?semesterId=' + semester.id)
  } : null, [staff, semester.id, revision])
  const rejected = error?.details?.blockedSections ? error.details : null
  const readiness = rejected || (validation.data?.revision === revision ? validation.data.result : null)
  const versions = state.data || []
  const current = versions.find(r => r.id === selected)
  const approval = useFetch(() => current?.approval_request_id ? api('/approvals/' + current.approval_request_id) : Promise.resolve(null), [current?.approval_request_id, revision])
  const patterns = useFetch(() => api('/schedule-patterns?semesterId=' + semester.id), [semester.id, revision])
  const snapshot = (approval.data?.id === current?.approval_request_id ? approval.data?.payload || [] : []).map(slot => {
    const pattern = (patterns.data || []).find(p => p.id === slot.id)
    return { id:slot.id, day_of_week:slot.day, period_from:slot.first, period_count:slot.count,
      section_code:slot.sectionCode || pattern?.section_code || 'Lớp #' + slot.section,
      subject_name:slot.subjectName || pattern?.subject_name,
      room_code:slot.roomCode || catalog.rooms.find(r => r.id === slot.room)?.code }
  })
  const roomUsage = new Map()
  for (const slot of snapshot) {
    const code = slot.room_code || '—'
    const room = roomUsage.get(code) || { id:code, slots:0, periods:0 }
    room.slots++
    room.periods += slot.period_count
    roomUsage.set(code,room)
  }
  async function perform(func) {
    setBusy(true)
    setError(null)
    try { await func(); reload() }
    catch (e) { setError(e) }
    finally { setBusy(false) }
  }
  async function submit() {
    const version = await api('/timetable-versions','POST',{ semesterId:semester.id })
    await api('/timetable-versions/' + version.id + '/submit','POST')
    setSelected(version.id)
  }
  async function publish() {
    await api('/approvals/' + current.approval_request_id + '/publish','POST')
  }
  return <>
    <Panel title="Phiên bản thời khóa biểu" action={staff && <button disabled={busy || !readiness?.ready || versions.some(r => r.status === 'SUBMITTED')}
      onClick={() => perform(submit)}>{busy ? 'Đang gửi…' : 'Gửi duyệt thời khóa biểu'}</button>}>
      <ErrorBox error={(!rejected && error) || state.error || approval.error || patterns.error || validation.error} />
      {staff && <SubmissionReadiness data={readiness} hasError={!!validation.error} requestId={rejected && error.request_id}
        refresh={() => { setError(null); reload() }} />}
      <Table rows={versions} columns={[
        ['Phiên bản',r => 'Bản ' + r.version_no],['Người lập','created_by_name'],['Số lớp','section_count'],['Trạng thái',r => <Tag value={r.status}/>],
        ['Quyết định',r => <>{r.decision_status && <Tag value={r.decision_status} />}{r.decision_reason && <small>{r.decision_reason}</small>}</>],
        ['Hồ sơ',r => r.approval_request_id && <button className="quiet" onClick={() => setSelected(r.id)}>Xem phương án</button>]
      ]}/>
    </Panel>
    {current?.approval_request_id && <Panel title={'Phương án bản ' + current.version_no}
      action={user.role === 'MANAGER' && current.status === 'SUBMITTED' && <div className="button-row">
        {['PENDING','APPROVED'].includes(current.decision_status) && <button className="quiet" disabled={busy} onClick={() => {setReason(''); setReturning(true)}}>Trả lại</button>}
        <button disabled={busy} onClick={() => perform(publish)}>{current.decision_status === 'APPROVED' ? 'Công bố' : 'Duyệt và công bố'}</button>
      </div>}>
      {snapshot.length > 0 && <>
        <WeekGrid rows={snapshot} maxPeriod={Math.max(12, ...snapshot.map(r => r.period_from + r.period_count - 1))} />
        <h3 className="room-usage-heading">Sử dụng phòng trong phương án</h3>
        <Table rows={[...roomUsage.values()]} columns={[[ 'Phòng','id' ],[ 'Buổi/tuần','slots' ],[ 'Tiết/tuần','periods' ]]} />
      </>}
      {!snapshot.length && !approval.error && <div className="empty">Đang tải phương án…</div>}
    </Panel>}
    {returning && <Editor title="Trả lại thời khóa biểu" onClose={() => setReturning(false)} submit="Trả lại" onSubmit={async () => {
      await api('/approvals/' + current.approval_request_id + '/decide','POST',{ decision:'RETURNED', reason })
      reload()
    }}><Field label="Lý do trả lại"><textarea value={reason} onChange={e => setReason(e.target.value)} maxLength={500} required rows={4} /></Field></Editor>}
  </>
}

function SubmissionReadiness({ data, hasError, requestId, refresh }) {
  const message = data ? data.pending ? 'Học kỳ đang có phiên bản chờ duyệt.' :
    !data.draftCount ? 'Học kỳ chưa có lớp nháp để gửi duyệt.' :
    !data.eligible ? 'Khoa của bạn chưa có lớp nháp trong học kỳ này.' :
    data.ready ? data.draftCount + ' lớp nháp đủ điều kiện gửi duyệt.' :
    data.blockedSections.length + ' lớp nháp cần xử lý trước khi gửi duyệt.' :
    hasError ? 'Chưa tải được kết quả kiểm tra.' : 'Đang kiểm tra điều kiện gửi duyệt…'
  return <section className="submission-readiness" aria-label="Điều kiện gửi duyệt">
    <div className="button-row">
      <p role="status">{message}</p>
      <button type="button" className="quiet" onClick={refresh} disabled={!data && !hasError}>Kiểm tra lại</button>
    </div>
    {requestId && <small className="request-reference">Mã yêu cầu: {requestId}</small>}
    {data?.blockedSections.length > 0 && <Table rows={data.blockedSections} columns={[
      ['Lớp học phần', row => <><strong>{row.code}</strong><small>{row.subjectName}</small></>],
      ['Buổi đã xếp', row => row.scheduled + '/' + row.required],
      ['Cần xử lý', row => <ul className="submission-issues">
        {row.missing.map(issue => <li key={issue.rule}>{issue.message}</li>)}
        {row.conflicts.map(slot => <li key={slot.id}>
          <strong>{days[slot.dayOfWeek - 1]}, tiết {slot.periodFrom} · {slot.roomCode}</strong>
          {slot.issues.map((issue,index) => <small key={index}>
            {issue.rule} · {issue.message || 'Trùng lịch đã công bố.'}
            {(issue.sectionCode || issue.section_code) && ' · ' + (issue.sectionCode || issue.section_code)}
            {issue.groupCodes?.length > 0 && ' · ' + issue.groupCodes.join(', ')}
          </small>)}
        </li>)}
      </ul>],
      ['Thao tác', row => row.editable ? <>
        {row.missing.some(issue => issue.rule === 'BR02') && <a href="#sections">Phân công</a>}
        {(row.conflicts.length > 0 || row.missing.some(issue => issue.rule === 'BR10')) && <a href="#planning">Xếp lịch thủ công</a>}
      </> : <small>Liên hệ khoa phụ trách</small>]
    ]} />}
  </section>
}

export function Availability({ semester, revision, reload }) {
  const state = useFetch(() => api('/me/availability?semesterId=' + semester.id), [semester.id, revision])
  if (state.error) return <ErrorBox error={state.error} />
  if (!state.data) return <Panel title="Thời gian bận"><p>Đang tải…</p></Panel>
  return <BusyList key={revision} items={state.data} semester={semester} reload={reload} />
}
function BusyList({ items, semester, reload }) {
  const [rows, setRows] = useState(items.map(r => ({ key:r.id,dayOfWeek:r.day_of_week,periodFrom:r.period_from,periodTo:r.period_to,kind:r.kind,reason:r.reason || '' })))
  const [error, setError] = useState(null)
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  function edit(key, field, value) { setRows(list => list.map(r => r.key === key ? { ...r, [field]:value } : r)) }
  async function save(event) {
    event.preventDefault()
    setError(null); setMessage(''); setBusy(true)
    try {
      await api('/me/availability','PUT',{ semesterId:semester.id, entries:rows.map(r => numeric(r,['dayOfWeek','periodFrom','periodTo'])) })
      setMessage('Đã lưu thời gian bận.')
    } catch (e) { setError(e) }
    finally { setBusy(false) }
  }
  return <Panel title="Khai báo thời gian bận">
    <p className="muted">Khoảng Bận chặn xếp lịch. Khoảng Hạn chế xếp được tính khi hệ thống chọn phương án tự động.</p>
    <ErrorBox error={error} />
    {message && <p className="notice success" role="status">{message}</p>}
    <form className="stack" onSubmit={save}>
      {rows.map(r => <div className="busy-row" key={r.key}>
        <Field label="Thứ"><Select value={r.dayOfWeek} set={v => edit(r.key,'dayOfWeek',v)} options={dayOptions} /></Field>
        {['periodFrom','periodTo'].map((k,i) => <Field label={i ? 'Đến tiết' : 'Từ tiết'} key={k}>
          <input type="number" min="1" max="15" required value={r[k]} onChange={e => edit(r.key,k,e.target.value)} /></Field>)}
        <Field label="Loại"><Options value={r.kind} set={v => edit(r.key,'kind',v)} values={{ BUSY:'Bận', AVOID:'Hạn chế xếp' }} /></Field>
        <Field label="Lý do"><input value={r.reason} maxLength={500} onChange={e => edit(r.key,'reason',e.target.value)} /></Field>
        <button type="button" className="quiet danger" onClick={() => setRows(list => list.filter(row => row.key !== r.key))}>Bỏ</button>
      </div>)}
      {!rows.length && <div className="empty">Chưa có khoảng thời gian bận.</div>}
      <div className="button-row"><button type="button" className="quiet" onClick={() => setRows(list => [...list, { key:crypto.randomUUID(),dayOfWeek:1,periodFrom:1,periodTo:2,kind:'BUSY',reason:'' }])}>Thêm khoảng</button>
        <button disabled={busy}>{busy ? 'Đang lưu…' : 'Lưu thời gian bận'}</button></div>
    </form>
  </Panel>
}
