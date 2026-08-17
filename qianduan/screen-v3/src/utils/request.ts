import axios from 'axios'
import { ElMessage } from 'element-plus'

export interface ApiResponse<T = any> {
  code: number
  data: T
  message: string
}

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json;charset=utf-8'
  }
})

request.interceptors.request.use(
  (config) => {
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

request.interceptors.response.use(
  (response) => {
    const res = response.data as ApiResponse
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  (error) => {
    const status = error.response?.status
    if (status === 500) {
      console.warn('[API 500] 后端服务暂不可用，使用本地数据', error.config?.url)
    } else if (status === 404) {
      console.warn('[API 404] 接口不存在', error.config?.url)
    } else {
      ElMessage.error(error.message || '网络异常')
    }
    return Promise.reject(error)
  }
)

export default request