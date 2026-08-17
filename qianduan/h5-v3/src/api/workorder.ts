import axios from 'axios'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000
})

request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code === 200) {
      return res.data
    }
    throw new Error(res.msg || '请求失败')
  },
  (error) => {
    throw new Error(error.message || '网络错误')
  }
)

export interface WorkOrderData {
  id: number
  code: string
  alarm_id: number
  alarm_type: string
  aircraft_sn: string
  aircraft_name: string
  wayline_uuid: string
  wayline_name: string
  alarm_image_url: string
  handler_email: string
  handler_name: string
  status: string
  status_text: string
  handle_comment: string
  reject_reason: string
  latitude: number
  longitude: number
  event_time: string
  submit_time: string
  complete_time: string
  reject_time: string
  create_time: string
  handler_images: string[]
}

export const getWorkOrderByToken = async (token: string): Promise<WorkOrderData> => {
  return await request.get(`/workorder/h5/${token}`)
}

export const uploadHandlerImage = async (formData: FormData, workOrderId: number): Promise<{ image_url: string }> => {
  formData.append('work_order_id', String(workOrderId))
  return await request.post('/workorder/h5/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export const submitWorkOrder = async (data: {
  work_order_id: number
  handle_comment: string
}): Promise<void> => {
  return await request.post('/workorder/h5/submit', data)
}