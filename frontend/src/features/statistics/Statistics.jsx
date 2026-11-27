import { api } from '../../services/api'
import { useFetch } from '../../hooks/useFetch'
import { ErrorBox } from '../../components/common/ErrorBox'
import { Panel } from '../../components/common/Panel'
import { Table } from '../../components/common/Table'

export default function Statistics({ semester, revision }) {
  const data = useFetch(() => api('/schedule/statistics?semesterId=' + semester.id), [semester.id,revision])
  return <>
    <ErrorBox error={data.error} />
    <div className="split">
      {[['rooms','Sử dụng phòng'],['lecturers','Giờ dạy theo giảng viên']].map(([key,title]) =>
        <Panel title={title} key={key}><Table rows={(data.data?.[key] || []).map(r => ({ ...r,id:r.id ?? r.code ?? 'none' }))}
          columns={[[key==='rooms' ? 'Phòng' : 'Giảng viên','code'],...(key==='rooms' ? [['Cơ sở','campus_code']] : []),['Số buổi','sessions'],['Tổng tiết','periods']]} /></Panel>)}
    </div>
  </>
}
