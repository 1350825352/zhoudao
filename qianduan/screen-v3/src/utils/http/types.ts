export interface Response<T = any> {
  code: number | string
  message: string
  data: T
}

export interface ResponsePageType<T = any[]> {
  page: number
  rows: T[]
  total: number
}
