import { HttpError } from '../middleware/errors.js'

const SYSTEM = `Phân loại yêu cầu về FM05. GENERATE khi người dùng muốn chạy xếp lịch tự động;
APPLY khi muốn áp dụng một phương án; UNRELATED cho nội dung khác.
Không quyết định học kỳ, người dùng hay lần chạy. Không có chức năng ghi dữ liệu.
Nếu yêu cầu chỉ hỏi cách thao tác, chưa yêu cầu thực hiện, chọn UNRELATED.`

export function geminiAdvisor({key=process.env.GEMINI_API_KEY, model=process.env.GEMINI_MODEL, request=fetch}={}) {
  if (!key?.trim() || !model?.trim()) return null
  if (!/^gemini-[a-z0-9.-]+$/.test(model.trim())) return async()=>{
    throw new HttpError(503,'ASSISTANT_CONFIGURATION','GEMINI_MODEL trên backend không hợp lệ.')
  }
  const url='https://generativelanguage.googleapis.com/v1beta/models/'+model.trim()+':generateContent'
  return async (message,{actions=['GENERATE','APPLY','UNRELATED'],instruction=SYSTEM}={})=>{
    try {
      const response=await request(url,{
        method:'POST', redirect:'error', signal:AbortSignal.timeout(12000),
        headers:{'content-type':'application/json','x-goog-api-key':key.trim()},
        body:JSON.stringify({
          systemInstruction:{parts:[{text:instruction}]},
          contents:[{role:'user',parts:[{text:message}]}],
          generationConfig:{
            responseMimeType:'application/json',
            responseJsonSchema:{type:'object',properties:{
              action:{type:'string',enum:actions},
            },required:['action']},
          },
        }),
      })
      if (!response.ok) throw new Error('Provider request failed')
      const data=await response.json()
      const text=data.candidates?.[0]?.content?.parts?.filter(part=>!part.thought).map(part=>part.text || '').join('')
      const result=JSON.parse(text)
      if (!actions.includes(result?.action)) throw new Error('Provider output invalid')
      return result.action
    } catch {
      throw new HttpError(502,'ASSISTANT_UNAVAILABLE','Gemini chưa trả về đề xuất hợp lệ. Bạn có thể tiếp tục xếp lịch từ biểu mẫu.')
    }
  }
}
