import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/modules/user'

interface ApiResponse<T = any> {
  code?: number
  success?: boolean
  message?: string
  msg?: string
  data?: T
}

const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 15000
})

service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

service.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const res = response.data
    const isSuccess =
      res.code === 0 ||
      res.code === 200 ||
      (res.code === undefined && res.success === true)

    if (isSuccess) {
      return res.data !== undefined ? res.data : res
    } else {
      const errorMsg = res.message || res.msg || '请求失败'
      ElMessage.error(errorMsg)
      return Promise.reject(new Error(errorMsg))
    }
  },
  (error) => {
    const errorMsg = error.response?.data?.message || error.response?.data?.msg || error.message || '网络错误'
    ElMessage.error(errorMsg)
    return Promise.reject(error)
  }
)

export const request = {
  get<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return service.get(url, config)
  },
  post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    return service.post(url, data, config)
  },
  put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    return service.put(url, data, config)
  },
  delete<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return service.delete(url, config)
  }
}

export default service
