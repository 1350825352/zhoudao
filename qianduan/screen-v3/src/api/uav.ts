import request, { ApiResponse } from '@/utils/request'

export const deviceApi = {
  getDeviceList() {
    return request.get<ApiResponse<DeviceDTO[]>>('/devices/list')
  },

  getDeviceStats() {
    return request.get<ApiResponse<DeviceStatsDTO>>('/devices/stats')
  },

  getUavStatus(sn: string) {
    return request.get<ApiResponse<UavStatusDTO>>(`/uavs/${sn}/status`)
  },

  getUavLiveVideoUrl(sn: string) {
    return request.get<ApiResponse<LiveVideoUrlDTO>>(`/uavs/${sn}/video/live`)
  },

  getUavRecognitionVideoUrl(sn: string) {
    return request.get<ApiResponse<RecognitionVideoUrlDTO>>(`/uavs/${sn}/video/recognition`)
  },

  getAlertList() {
    return request.get<ApiResponse<AlertDTO[]>>('/alerts')
  },

  getLineList() {
    return request.get<ApiResponse<LineDTO[]>>('/lines')
  },

  executeFlightTask(data: ExecuteFlightTaskDTO) {
    return request.post<ApiResponse<FlightTaskDTO>>('/flight-task/execute', data)
  }
}

export interface DeviceDTO {
  sn: string
  name: string
  kind: 'uav' | 'airport'
  status: 'online' | 'offline' | 'flying'
  lng?: number
  lat?: number
  flighttaskStepCode?: number
  modeCode?: number  // mode_code(0待机,1-12工作,14未连接) - 地图过滤用
  mqttActive?: boolean  // 5秒内有MQTT OSD上报 - 机场显示判断
}

export interface DeviceStatsDTO {
  uavCount: number
  airportCount: number
  onlineCount: number
  flyingCount: number
}

export interface UavStatusDTO {
  sn: string
  name: string
  status: 'online' | 'offline' | 'flying'
  mode: number
  modeText: string
  height: number
  altitude: number
  elevation: number
  speed: number
  battery: number
  satelliteCount: number
  lng: number
  lat: number
  distanceToHome: number
}

export interface LiveVideoUrlDTO {
  sn: string
  url: string | null
  whepUrl: string | null
  available: boolean
}

export interface RecognitionVideoUrlDTO {
  sn: string
  url: string | null
  whepUrl: string | null
  available: boolean
}

export interface AlertDTO {
  id: string
  appName: string
  ruleName: string
  alarmTime: string
  level: 'high' | 'medium' | 'low'
  imageUrl?: string
  lng?: number
  lat?: number
  waylineUuid?: string
}

export interface LineDTO {
  id: string
  fileName: string
  actionTypeFmt: string
  modiTime: string
  waylineUuid?: string
  downloadUrl?: string
}

export interface ExecuteFlightTaskDTO {
  taskName: string
  gatewaySn: string
  waylineUuid: string
  aircraftSn?: string
  rthAltitude?: number
  rthMode?: 'optimal' | 'preset'
  outOfControlActionInFlight?: 'return_home' | 'continue_task'
  waylinePrecisionType?: 'gps' | 'rtk'
  resumableStatus?: 'auto' | 'manual'
  repeatType?: 'nonrepeating' | 'daily' | 'weekly' | 'absolute_monthly' | 'relative_monthly'
  projectUuid?: string
  operator?: string
}

export interface FlightTaskDTO {
  id: number
  taskName: string
  taskUuid: string
  waylineUuid: string
  waylineName: string
  aircraftSn: string
  aircraftName: string
  gatewaySn: string
  taskStatus: number
  statusMessage: string
  progress: number
  createTime: string
}