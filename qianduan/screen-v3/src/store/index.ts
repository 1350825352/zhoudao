import { defineStore } from 'pinia'
import { ref, reactive, computed } from 'vue'
import type { UavStatusDTO, DeviceDTO, DeviceStatsDTO, LiveVideoUrlDTO, RecognitionVideoUrlDTO } from '@/api/uav'

export interface Waypoint {
  lng: number
  lat: number
  alt?: number
}

export interface RouteData {
  waylineUuid: string
  waylineName: string
  waypoints: Waypoint[]
}

export type UavMode = number
export type UavStatus = 'flying' | 'online' | 'offline'

export const MODE_TEXT: Record<UavMode, string> = {
  0: '待机',
  1: '起飞准备',
  2: '起飞准备完毕',
  3: '手动飞行',
  4: '自动起飞',
  5: '航线飞行',
  6: '全景拍照',
  7: '智能跟随',
  8: 'ADS-B躲避',
  9: '自动返航',
  10: '自动降落',
  11: '强制降落',
  12: '三桨叶降落',
  13: '升级中',
  14: '未连接',
  15: 'APAS',
  16: '虚拟摇杆状态',
  17: '指令飞行',
  18: '空中RTK收敛模式',
  19: '机场选址中',
  20: 'POI环绕',
  21: '进离场航线飞行过程中'
}

export const MODE_ICON: Record<UavMode, string> = {
  0: '⏸️',
  1: '�',
  2: '✅',
  3: '🕹️',
  4: '⬆️',
  5: '�️',
  6: '📷',
  7: '👀',
  8: '🛡️',
  9: '🏠',
  10: '⬇️',
  11: '⚠️',
  12: '🌀',
  13: '🔄',
  14: '❌',
  15: '🤖',
  16: '🎮',
  17: '🎯',
  18: '📡',
  19: '�',
  20: '⭕',
  21: '✈️'
}

export const MODE_CLASS: Record<UavMode, string> = {
  0: 'mode-standby',
  1: 'mode-prepare',
  2: 'mode-ready',
  3: 'mode-manual',
  4: 'mode-takeoff',
  5: 'mode-route',
  6: 'mode-photo',
  7: 'mode-follow',
  8: 'mode-adsb',
  9: 'mode-rtl',
  10: 'mode-land',
  11: 'mode-forced-land',
  12: 'mode-three-blade',
  13: 'mode-upgrade',
  14: 'mode-disconnected',
  15: 'mode-apas',
  16: 'mode-virtual',
  17: 'mode-command',
  18: 'mode-rtk',
  19: 'mode-locating',
  20: 'mode-poi',
  21: 'mode-arrival'
}

export const getModeText = (mode?: number): string => {
  if (mode === undefined || mode === null || !(mode in MODE_TEXT)) {
    return '未定状态'
  }
  return MODE_TEXT[mode]
}

export type AirportTaskCode = number

export const AIRPORT_TASK_TEXT: Record<AirportTaskCode, string> = {
  0: '作业准备中',
  1: '飞行作业中',
  2: '作业后状态恢复',
  3: '自定义飞行区更新中',
  4: '地形障碍物更新中',
  5: '任务空闲',
  255: '飞行器异常',
  256: '未知状态'
}

export const getAirportTaskText = (code?: number): string => {
  if (code === undefined || code === null || !(code in AIRPORT_TASK_TEXT)) {
    return '其他状态'
  }
  return AIRPORT_TASK_TEXT[code]
}

const randomFrom = <T,>(arr: T[]): T => arr[Math.floor(Math.random() * arr.length)]

const getModesForStatus = (s: UavStatus): UavMode[] => {
  if (s === 'flying') return [3, 5, 6, 7, 8, 9, 17, 20, 21]
  if (s === 'online') return [0, 1, 2, 4, 13]
  return [14]
}

interface UavMock {
  sn: string
  status: UavStatus
  _lngNum: number
  _latNum: number
  mode: UavMode
  _modeTick: number
}

export interface UserInfo {
  id: number
  username: string
  name: string
  token: string
}

export const useAppStore = defineStore('app', () => {
  const title = ref(import.meta.env.VITE_APP_TITLE || '无人机监控大屏')
  const selectedSn = ref<string>('')
  const user = ref<UserInfo | null>(null)
  const deviceStats = reactive<DeviceStatsDTO>({ uavCount: 3, airportCount: 2, onlineCount: 4, flyingCount: 2 })
  const devices = reactive<DeviceDTO[]>([
    { sn: 'UAV-001', name: 'UAV-001', kind: 'uav', status: 'flying', lng: 118.7968771234, lat: 32.0602556789 },
    { sn: 'UAV-002', name: 'UAV-002', kind: 'uav', status: 'flying', lng: 118.8108774321, lat: 32.0702559876 },
    { sn: 'UAV-003', name: 'UAV-003', kind: 'uav', status: 'online', lng: 118.7828775678, lat: 32.0522551234 },
    { sn: 'AIRPORT-001', name: 'AIRPORT-001', kind: 'airport', status: 'online', lng: 118.7900000000, lat: 32.0550000000, flighttaskStepCode: 1 },
    { sn: 'AIRPORT-002', name: 'AIRPORT-002', kind: 'airport', status: 'offline', lng: 118.7750000000, lat: 32.0450000000, flighttaskStepCode: 5 }
  ])
  const uavStatusMap = reactive<Record<string, UavStatusDTO>>({
    'UAV-001': { sn: 'UAV-001', name: 'UAV-001', status: 'flying', mode: 5, modeText: '航线飞行', height: 120.5, speed: 18.3, battery: 78, satelliteCount: 16, lng: 118.7968771234, lat: 32.0602556789 },
    'UAV-002': { sn: 'UAV-002', name: 'UAV-002', status: 'flying', mode: 3, modeText: '手动飞行', height: 85.3, speed: 15.2, battery: 65, satelliteCount: 14, lng: 118.8108774321, lat: 32.0702559876 },
    'UAV-003': { sn: 'UAV-003', name: 'UAV-003', status: 'online', mode: 0, modeText: '待机', height: 0, speed: 0, battery: 92, satelliteCount: 10, lng: 118.7828775678, lat: 32.0522551234 }
  })
  const uavLiveVideoMap = reactive<Record<string, LiveVideoUrlDTO>>({})
  const uavRecognitionVideoMap = reactive<Record<string, RecognitionVideoUrlDTO>>({})
  const currentRoute = ref<RouteData | null>(null)

  const mockUavs = reactive<UavMock[]>([
    { sn: 'UAV-001', status: 'flying', _lngNum: 118.796877, _latNum: 32.060255, mode: 5, _modeTick: 0 },
    { sn: 'UAV-002', status: 'flying', _lngNum: 118.810877, _latNum: 32.070255, mode: 3, _modeTick: 0 },
    { sn: 'UAV-003', status: 'online', _lngNum: 118.782877, _latNum: 32.052255, mode: 0, _modeTick: 0 }
  ])

  const deviceSum = reactive({
    onlineCnt: 4,
    offlineCnt: 1,
    totalFlightSorties: 128,
    totalFlightDistance: '486.2',
    _distanceNum: 486.2
  })

  const airports = reactive({
    windSpeed: '3.8',
    temperature: '26.4'
  })

  const devicesMock = reactive({
    totalSearchStarNum: '14',
    capacityPercent: '78'
  })

  let tickTimer: any = null
  let sortieTimer: any = null

  const tickMockData = () => {
    airports.windSpeed = (3.5 + Math.random() * 2.5).toFixed(1)
    airports.temperature = (25.5 + Math.random() * 3).toFixed(1)

    let totalBat = 0
    let totalSat = 0
    let onlineCnt = 0
    let totalDist = 0

    mockUavs.forEach(u => {
      u._modeTick++
      if (u._modeTick >= 5 + Math.floor(Math.random() * 4)) {
        const pool = getModesForStatus(u.status)
        const candidates = pool.filter(m => m !== u.mode)
        if (candidates.length && Math.random() < 0.85) {
          u.mode = randomFrom(candidates)
        }
        u._modeTick = 0
      }

      if (u.status === 'flying') {
        const speed = 12 + Math.random() * 4
        const bearingRad = (Math.random() * 360 * Math.PI) / 180
        const distKm = speed / 3600
        const deltaLat = (distKm * Math.cos(bearingRad)) / 111
        const deltaLng =
          (distKm * Math.sin(bearingRad)) /
          (111 * Math.cos((u._latNum * Math.PI) / 180))
        u._latNum += deltaLat
        u._lngNum += deltaLng

        const bat = Math.max(20, 100 - (deviceSum.totalFlightSorties % 80) - Math.random() * 8)
        const sat = Math.round(10 + Math.random() * 12)
        totalBat += bat
        totalSat += sat
        totalDist += distKm
        onlineCnt++
      } else if (u.status === 'online') {
        totalBat += 90 + Math.random() * 8
        totalSat += Math.round(8 + Math.random() * 6)
        onlineCnt++
      }
    })

    const airportsOnline = 1
    const totalDevices = 5
    deviceSum.onlineCnt = onlineCnt + airportsOnline
    deviceSum.offlineCnt = totalDevices - deviceSum.onlineCnt

    const flyingN = mockUavs.filter(u => u.status !== 'offline').length || 1
    devicesMock.capacityPercent = String(Math.round(totalBat / flyingN))
    devicesMock.totalSearchStarNum = String(Math.round(totalSat / flyingN))

    deviceSum._distanceNum += totalDist
    deviceSum.totalFlightDistance = deviceSum._distanceNum.toFixed(1)
  }

  const startTick = () => {
    if (tickTimer) return
    tickTimer = setInterval(tickMockData, 1000)
    sortieTimer = setInterval(() => {
      deviceSum.totalFlightSorties++
    }, 12000)
  }

  const stopTick = () => {
    if (tickTimer) clearInterval(tickTimer)
    if (sortieTimer) clearInterval(sortieTimer)
    tickTimer = null
    sortieTimer = null
  }

  const selectUav = (sn: string) => {
    selectedSn.value = sn
  }

  const getSelectedUavStatus = (): UavStatusDTO | undefined => {
    return uavStatusMap[selectedSn.value]
  }

  const getSelectedVideoUrl = (): VideoUrlDTO | undefined => {
    return uavVideoMap[selectedSn.value]
  }

  const updateDevices = (list: DeviceDTO[]) => {
    devices.splice(0, devices.length, ...list)
  }

  const updateDeviceStats = (stats: DeviceStatsDTO) => {
    Object.assign(deviceStats, stats)
  }

  const updateUavStatus = (sn: string, status: UavStatusDTO) => {
    uavStatusMap[sn] = status
  }

  const updateUavLiveVideoUrl = (sn: string, url: LiveVideoUrlDTO) => {
    uavLiveVideoMap[sn] = url
  }

  const updateUavRecognitionVideoUrl = (sn: string, url: RecognitionVideoUrlDTO) => {
    uavRecognitionVideoMap[sn] = url
  }

  const updateCurrentRoute = (route: RouteData | null) => {
    currentRoute.value = route
  }

  const setUser = (userInfo: UserInfo) => {
    user.value = userInfo
  }

  const clearUser = () => {
    user.value = null
  }

  const getUavBySn = (sn: string): UavMock | undefined => {
    return mockUavs.find(u => u.sn === sn)
  }

  const getSelectedUav = (): UavMock | undefined => {
    return getUavBySn(selectedSn.value) || mockUavs[0]
  }

  const uavModeMap = computed<Record<string, UavMode>>(() => {
    const map: Record<string, UavMode> = {}
    mockUavs.forEach(u => { map[u.sn] = u.mode })
    return map
  })

  const modeText = (m: UavMode | null): string => {
    if (m === null || !(m in MODE_TEXT)) return '未定状态'
    return MODE_TEXT[m]
  }

  const modeIcon = (m: UavMode | null): string => {
    if (m === null || !(m in MODE_ICON)) return '❓'
    return MODE_ICON[m]
  }

  const modeClass = (m: UavMode | null): string => {
    if (m === null || !(m in MODE_CLASS)) return 'mode-unknown'
    return MODE_CLASS[m]
  }

  return {
    title,
    selectedSn,
    user,
    deviceStats,
    devices,
    uavStatusMap,
    uavLiveVideoMap,
    uavRecognitionVideoMap,
    currentRoute,
    mockUavs,
    deviceSum,
    airports,
    devicesMock,
    startTick,
    stopTick,
    selectUav,
    getSelectedUavStatus,
    updateDevices,
    updateDeviceStats,
    updateUavStatus,
    updateUavLiveVideoUrl,
    updateUavRecognitionVideoUrl,
    updateCurrentRoute,
    setUser,
    clearUser,
    getUavBySn,
    getSelectedUav,
    uavModeMap,
    modeText,
    modeIcon,
    modeClass
  }
})