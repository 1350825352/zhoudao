import axios, { AxiosResponse, AxiosRequestConfig } from 'axios'
import router from '@/router'
import { ElMessage } from 'element-plus'

const baseURL = import.meta.env.VITE_API_BASE_URL
const service = axios.create({
  baseURL,
  timeout: 60000
})

service.interceptors.request.use(
  async (config: any) => {
    const token = sessionStorage.getItem('access_token')
    if (token) {
      config.headers.Authorization = token
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

service.interceptors.response.use(
  async (response) => {
    const { code, message } = response.data
    if (code === '0' || code === 0) return Promise.resolve(response)
    let errMessage = ''
    switch (String(code).slice(0, 3)) {
      case '210':
        if (code === '21001' || code === '21003') {
          return Promise.reject({ code, message })
        } else {
          errMessage = message
        }
        break
      case '200':
        sessionStorage.clear()
        router.push({ path: '/' })
        break
      default:
        errMessage = message
        break
    }
    if (errMessage) ElMessage.error(errMessage)
    return Promise.reject(errMessage)
  },
  (error) => {
    if (error.message) {
      ElMessage.error(error.message)
    }
    return Promise.reject(error)
  }
)

export type { AxiosResponse, AxiosRequestConfig }
export default service
