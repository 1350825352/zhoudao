import { ElLoading } from 'element-plus'
import service, { AxiosRequestConfig } from './axios'
import type { ResponsePageType } from './types'
import { ref } from 'vue'

export * from './types'

const loading = ref()
type ConfigType = AxiosRequestConfig & { loading?: boolean; loadingText?: string }

export const request = <T = any>(config: ConfigType): Promise<T> => {
  if (config.loading) {
    loading.value = ElLoading.service({
      lock: true,
      text: config.loadingText || '数据加载中...',
      background: 'rgba(0, 0, 0, 0.7)'
    })
  }
  return new Promise((resolve, reject) => {
    service
      .request(config)
      .then((res) => {
        if (loading.value) {
          loading.value.close()
        }
        const { data } = res.data
        resolve(data)
      })
      .catch((err) => {
        if (loading.value) {
          loading.value.close()
        }
        reject(err)
      })
  })
}

const http = {
  get<T = any>(url: string, params = {}, config?: ConfigType): Promise<T> {
    return request<T>({ url, params, ...config, method: 'GET' })
  },
  pagesPost<T = any>(
    url: string,
    data = {} as any,
    params = {},
    config?: ConfigType
  ): Promise<ResponsePageType<T>> {
    return request<ResponsePageType<T>>({
      url,
      data,
      params,
      ...config,
      method: 'POST'
    })
  },
  post<T = any>(url: string, data = {}, config?: ConfigType): Promise<T> {
    return request<T>({
      url,
      data,
      ...config,
      method: 'POST'
    })
  },
  put<T = any>(url: string, data = {}, config?: ConfigType): Promise<T> {
    return request<T>({ url, data, ...config, method: 'PUT' })
  },
  delete<T = any>(url: string, data = {}, config?: ConfigType): Promise<T> {
    return request<T>({ url, data, ...config, method: 'DELETE' })
  },
  upload<T = any>(url: string, data = {}, params = {}, config?: ConfigType): Promise<T> {
    return request<T>({
      url,
      data,
      params,
      ...config,
      method: 'POST',
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  }
}

export default http
