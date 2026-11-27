import { useEffect, useState } from 'react'
import { api } from '../../services/api'
import { builder, numeric } from '../../utils/forms'
import { statuses } from '../../constants/domain'
import { Editor } from '../../components/forms/Editor'
import { ErrorBox } from '../../components/common/ErrorBox'
import { Field, Select } from '../../components/forms/FormFields'
import { Panel } from '../../components/common/Panel'
import { Tag } from '../../components/common/Tag'
import { useFetch } from '../../hooks/useFetch'
import { RunPlan } from './RunPlan'
import { CostChart } from './CostChart'

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
