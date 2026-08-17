<template>
  <div class="main-map">
    <div class="map-content">
      <div id="tiandituMap" class="map-canvas"></div>
    </div>

    <div class="bottom">
      <div class="bitem1" :class="{ disabled: !isLiveAvailable }" @click="openLiveVideo">
        <span class="btn-text">实时直播</span>
      </div>
      <div class="bitem2" :class="{ disabled: !isRecognitionAvailable }" @click="openRecognitionVideo">
        <span class="btn-text">实时识别</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, nextTick, ref, watch, onUnmounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useAppStore } from '@/store'
import { deviceApi, type LiveVideoUrlDTO, type RecognitionVideoUrlDTO } from '@/api/uav'

const emit = defineEmits<{
  (e: 'open-video', video: { id: string; url: string; title: string }): void
  (e: 'close-video', id: string): void
}>()

const props = defineProps<{
  pointInfo?: any
}>()

declare global {
  interface Window { T: any }
}

let mapInstance: any = null
let centerLng = 118.796877
let centerLat = 32.060255
let routeOverlays: any[] = []
let deviceMarkers: any[] = []
let alertMarker: any = null
let alertLabel: any = null
let alertTimer: ReturnType<typeof setTimeout> | null = null
let videoUrlRefreshTimer: ReturnType<typeof setInterval> | null = null

// 保存告警信息，用于地图重绘后重新添加
let alertInfo: { lng: number; lat: number; name: string } | null = null

const store = useAppStore()
const liveVideoUrl = ref<LiveVideoUrlDTO | null>(null)
const recognitionVideoUrl = ref<RecognitionVideoUrlDTO | null>(null)

const isLiveAvailable = computed(() => liveVideoUrl.value?.available ?? false)
const isRecognitionAvailable = computed(() => recognitionVideoUrl.value?.available ?? false)

const TEST_LIVE_URL = 'http://192.168.2.201:2022/live/1581F8HGX252F00A018X/99-0-0.flv'
const TEST_RECOGNITION_URL = 'http://192.168.2.201:2022/live/ai/1581F8HGX252F00A018X/99-0-0.flv'

import planeIcon from './img/plane.png'
import airportIcon from './img/airport-white.png'
import alertIcon from './img/gaojing.png'

function initTiandituMap() {
  const T = (window as any).T
  if (!T) return
  try {
    const container = document.getElementById('tiandituMap')
    if (!container) return
    mapInstance = new T.Map('tiandituMap', { projection: 'EPSG:4326' })
    
    const airports = store.devices.filter(d => d.kind === 'airport' && d.lng != null && d.lat != null)
    if (airports.length > 0) {
      const totalLng = airports.reduce((sum, d) => sum + (d.lng || 0), 0)
      const totalLat = airports.reduce((sum, d) => sum + (d.lat || 0), 0)
      centerLng = totalLng / airports.length
      centerLat = totalLat / airports.length
    }
    
    mapInstance.centerAndZoom(new T.LngLat(centerLng, centerLat), 14)
    const baseLayer = new T.TileLayer()
    mapInstance.addLayer(baseLayer)
    
    updateMapMarkers()
  } catch (e) {
    console.warn('[Tianditu init warn]', e)
  }
}

function updateMapMarkers() {
  const T = (window as any).T
  if (!T || !mapInstance) return
  try {
    // 清除之前的设备标记
    deviceMarkers.forEach(marker => {
      try { mapInstance.removeOverLay(marker) } catch (_) {}
    })
    deviceMarkers = []
    
    const deviceAddrs = store.devices
      .filter(d => d.lng != null && d.lat != null)
      .map(d => ({ name: d.name, kind: d.kind, lng: d.lng!, lat: d.lat! }))
    sessionStorage.setItem('addr', JSON.stringify(deviceAddrs))
    
    if (deviceAddrs.length > 0) {
      centerLng = deviceAddrs[0].lng
      centerLat = deviceAddrs[0].lat
    }
    
    deviceAddrs.forEach((item) => {
      const point = new T.LngLat(item.lng, item.lat)
      const isAirport = item.kind === 'airport'
      const icon = new T.Icon({
        iconUrl: isAirport ? airportIcon : planeIcon,
        iconSize: new T.Point(isAirport ? 106 : 64, 64),
        iconAnchor: new T.Point(isAirport ? 53 : 32, 32)
      })
      const marker = new T.Marker(point, { icon })
      mapInstance.addOverLay(marker)
      deviceMarkers.push(marker)
      
      const label = new T.Label({
        text: item.name,
        position: new T.LngLat(item.lng, item.lat),
        offset: new T.Point(0, -36)
      })
      mapInstance.addOverLay(label)
      deviceMarkers.push(label)
    })
    
    // 如果有告警信息，重新添加告警标记（关键！解决地图重绘导致告警标记消失的问题）
    if (alertInfo && mapInstance) {
      const T = (window as any).T
      try {
        // 先清除可能存在的旧标记
        if (alertMarker) {
          try { mapInstance.removeOverLay(alertMarker) } catch (_) {}
        }
        if (alertLabel) {
          try { mapInstance.removeOverLay(alertLabel) } catch (_) {}
        }
        
        // 创建新的告警图标
        const icon = new T.Icon({
          iconUrl: alertIcon,
          iconSize: new T.Point(40, 40),
          iconAnchor: new T.Point(20, 36)
        })
        
        // 创建标记
        const point = new T.LngLat(alertInfo.lng, alertInfo.lat)
        alertMarker = new T.Marker(point, { icon })
        mapInstance.addOverLay(alertMarker)
        
        // 创建标签
        alertLabel = new T.Label({
          text: alertInfo.name,
          position: new T.Point(alertInfo.lng, alertInfo.lat),
          offset: new T.Point(0, -28)
        })
        mapInstance.addOverLay(alertLabel)
      } catch (e) {
        console.warn('[Tianditu re-add alert marker warn]', e)
      }
    }
  } catch (e) {
    console.warn('[Tianditu updateMarkers warn]', e)
  }
}

function showAlertMarker(lng: number, lat: number, name: string) {
  const T = (window as any).T
  if (!T || !mapInstance) return
  
  // 清除之前的告警标记
  removeAlertMarker()
  
  // 保存告警信息（关键！用于地图重绘后重新添加）
  alertInfo = { lng, lat, name }
  
  // 创建告警图标
  const icon = new T.Icon({
    iconUrl: alertIcon,
    iconSize: new T.Point(40, 40),
    iconAnchor: new T.Point(20, 36)
  })
  
  // 创建标记
  const point = new T.LngLat(lng, lat)
  alertMarker = new T.Marker(point, { icon })
  mapInstance.addOverLay(alertMarker)
  
  // 创建标签
  alertLabel = new T.Label({
    text: name,
    position: new T.Point(lng, lat),
    offset: new T.Point(0, -28)
  })
  mapInstance.addOverLay(alertLabel)
  
  // 移动地图到告警位置
  mapInstance.centerAndZoom(point, 16)
  
  // 30秒后自动消失
  alertTimer = setTimeout(() => {
    removeAlertMarker()
  }, 30000)
}

function removeAlertMarker() {
  // 清除定时器
  if (alertTimer) {
    clearTimeout(alertTimer)
    alertTimer = null
  }
  
  // 清除告警信息（关键！这样updateMapMarkers就不会重新添加了）
  alertInfo = null
  
  // 使用天地图API移除标记
  if (alertMarker && mapInstance && mapInstance.removeOverLay) {
    try { mapInstance.removeOverLay(alertMarker) } catch (_) {}
  }
  if (alertLabel && mapInstance && mapInstance.removeOverLay) {
    try { mapInstance.removeOverLay(alertLabel) } catch (_) {}
  }
  
  alertMarker = null
  alertLabel = null
}

function drawRouteOnMap() {
  const T = (window as any).T
  if (!T || !mapInstance) {
    console.log('[drawRouteOnMap] T or mapInstance is null')
    return
  }
  try {
    const route = store.currentRoute
    console.log('[drawRouteOnMap] route:', route)
    
    if (!route || !route.waypoints || route.waypoints.length < 2) {
      routeOverlays.forEach(overlay => {
        try { mapInstance.removeOverLay(overlay) } catch (_) {}
      })
      routeOverlays = []
      return
    }
    
    routeOverlays.forEach(overlay => {
      try { mapInstance.removeOverLay(overlay) } catch (_) {}
    })
    routeOverlays = []
    
    console.log('[drawRouteOnMap] waypoints:', route.waypoints)
    
    const lngLatList = route.waypoints.map(wp => new T.LngLat(wp.lng, wp.lat))
    
    const polyline = new T.Polyline(lngLatList, {
      color: '#FF4444',
      weight: 6,
      opacity: 0.9,
      lineStyle: 'solid'
    })
    mapInstance.addOverLay(polyline)
    routeOverlays.push(polyline)
    
    const polylineBorder = new T.Polyline(lngLatList, {
      color: '#FFFFFF',
      weight: 10,
      opacity: 0.5,
      lineStyle: 'solid'
    })
    mapInstance.addOverLay(polylineBorder)
    routeOverlays.push(polylineBorder)
    
    route.waypoints.forEach((wp, index) => {
      const point = new T.LngLat(wp.lng, wp.lat)
      const color = index === 0 ? '#4CAF50' : (index === route.waypoints.length - 1 ? '#F44336' : '#2E8BFF')
      const size = index === 0 || index === route.waypoints.length - 1 ? 24 : 16
      const iconUrl = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(
        `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">
          <circle cx="${size/2}" cy="${size/2}" r="${size/2 - 2}" fill="${color}" stroke="#fff" stroke-width="2"/>
          ${(index === 0 || index === route.waypoints.length - 1) ? `<text x="${size/2}" y="${size/2 + 4}" text-anchor="middle" fill="#fff" font-size="${size/2.4}" font-weight="bold">${index + 1}</text>` : ''}
        </svg>`
      )
      const markerIcon = new T.Icon({
        iconUrl,
        iconSize: new T.Point(size, size),
        iconAnchor: new T.Point(size/2, size/2)
      })
      const marker = new T.Marker(point, { icon: markerIcon })
      mapInstance.addOverLay(marker)
      routeOverlays.push(marker)
    })
    
    const firstWaypoint = route.waypoints[0]
    const centerLng = firstWaypoint.lng
    const centerLat = firstWaypoint.lat
    const zoom = 18
    
    const centerPoint = new T.LngLat(centerLng, centerLat)
    mapInstance.centerAndZoom(centerPoint, zoom)
    
    console.log('[drawRouteOnMap] route drawn successfully, zoom:', zoom, 'center:', centerLng, centerLat)
    
  } catch (e) {
    console.error('[Tianditu drawRoute error]', e)
  }
}

function flyToPosition(lat: number, lng: number) {
  const T = (window as any).T
  if (!T || !mapInstance) return
  try {
    const point = new T.LngLat(lng, lat)
    mapInstance.panTo(point)
    centerLat = lat
    centerLng = lng
  } catch (e) {
    console.warn('[Tianditu flyToPosition warn]', e)
  }
}

const loadLiveVideoUrl = async (sn: string) => {
  try {
    const res = await deviceApi.getUavLiveVideoUrl(sn)
    liveVideoUrl.value = res.data
    store.updateUavLiveVideoUrl(sn, res.data)
  } catch (e) {
    console.warn('获取实时直播视频链接失败，使用测试链接', e)
    liveVideoUrl.value = { sn, url: TEST_LIVE_URL, available: true }
    store.updateUavLiveVideoUrl(sn, liveVideoUrl.value)
  }
}

const loadRecognitionVideoUrl = async (sn: string) => {
  try {
    const res = await deviceApi.getUavRecognitionVideoUrl(sn)
    recognitionVideoUrl.value = res.data
    store.updateUavRecognitionVideoUrl(sn, res.data)
  } catch (e) {
    console.warn('获取实时识别视频链接失败，使用测试链接', e)
    recognitionVideoUrl.value = { sn, url: TEST_RECOGNITION_URL, available: true }
    store.updateUavRecognitionVideoUrl(sn, recognitionVideoUrl.value)
  }
}

const openLiveVideo = () => {
  if (!isLiveAvailable.value) {
    ElMessage.warning('实时直播视频暂不可用')
    return
  }
  const url = liveVideoUrl.value?.url || TEST_LIVE_URL
  const whepUrl = liveVideoUrl.value?.whepUrl || undefined
  if (!url) {
    ElMessage.warning('实时直播视频暂不可用')
    return
  }
  emit('open-video', { id: 'live', url, whepUrl, title: '实时直播' })
}

const openRecognitionVideo = () => {
  if (!isRecognitionAvailable.value) {
    ElMessage.warning('实时识别视频暂不可用')
    return
  }
  const url = recognitionVideoUrl.value?.url || TEST_RECOGNITION_URL
  const whepUrl = recognitionVideoUrl.value?.whepUrl || undefined
  if (!url) {
    ElMessage.warning('实时识别视频暂不可用')
    return
  }
  emit('open-video', { id: 'recognition', url, whepUrl, title: '实时识别' })
}



watch(() => store.selectedSn, (sn) => {
  if (sn) {
    loadLiveVideoUrl(sn)
    loadRecognitionVideoUrl(sn)
    // 每1秒刷新一次视频链接状态，确保按钮自动切换高亮/灰色(加速)
    if (videoUrlRefreshTimer) clearInterval(videoUrlRefreshTimer)
    videoUrlRefreshTimer = setInterval(() => {
      loadLiveVideoUrl(sn)
      loadRecognitionVideoUrl(sn)
    }, 1000)
  } else {
    // 没有选中飞机时，清除定时器并重置状态
    if (videoUrlRefreshTimer) {
      clearInterval(videoUrlRefreshTimer)
      videoUrlRefreshTimer = null
    }
    liveVideoUrl.value = null
    recognitionVideoUrl.value = null
  }
}, { immediate: true })

watch(() => store.devices, () => {
  updateMapMarkers()
}, { deep: true })

watch(() => store.currentRoute, () => {
  updateMapMarkers()
  drawRouteOnMap()
}, { deep: true })

onMounted(() => {
  // 挂载showAlertMarker到window对象供外部调用
  ;(window as any).showAlertMarker = showAlertMarker
  
  nextTick(() => {
    const T = (window as any).T
    const safeInit = () => setTimeout(() => {
      initTiandituMap()
      setTimeout(() => { try { mapInstance && mapInstance.checkResize && mapInstance.checkResize() } catch (_) {} }, 400)
    }, 600)
    if (T) {
      safeInit()
    } else {
      const tryCount = { n: 0 }
      const timer = setInterval(() => {
        tryCount.n++
        if ((window as any).T) {
          clearInterval(timer)
          safeInit()
        } else if (tryCount.n > 30) {
          clearInterval(timer)
        }
      }, 200)
    }
  })
})

watch(
  () => props.pointInfo,
  (nv) => {
    if (nv && typeof nv.lat === 'number' && typeof nv.lng === 'number') {
      flyToPosition(nv.lat, nv.lng)
    }
  },
  { deep: true }
)

onUnmounted(() => {
  closeAllVideos()
  // 清理window对象上的函数
  delete (window as any).showAlertMarker
  // 清理告警标记和定时器
  if (alertTimer) {
    clearTimeout(alertTimer)
    alertTimer = null
  }
  // 清理视频链接刷新定时器
  if (videoUrlRefreshTimer) {
    clearInterval(videoUrlRefreshTimer)
    videoUrlRefreshTimer = null
  }
})
</script>

<style lang="less" scoped>
:host {
  width: 100%;
  height: 100%;
  display: block;
}

.main-map {
  width: 100%;
  height: 100%;
  display: block;
  position: relative;

  .map-content {
    width: 100%;
    height: 100%;
    position: relative;
    overflow: hidden;

    .map-canvas {
      position: absolute;
      inset: 0;
      width: 100%;
      height: 100%;
      z-index: 1;
      background: transparent;
      filter: grayscale(60%) invert(88%) sepia(15%) hue-rotate(205deg)
        saturate(220%) contrast(1.3) brightness(0.85)
        drop-shadow(0 0 1px rgba(4, 27, 84, 0.4));
    }
  }

  .bottom {
    z-index: 100000;
    position: fixed;
    bottom: 40px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    width: 600px;
    left: 50%;
    transform: translateX(-50%);

    .bitem1 {
      width: 240px;
      height: 190px;
      background-image: url("./img/mainBox.png");
      background-size: 100% 100%;
      position: relative;
      cursor: pointer;

      .btn-text {
        font-size: 20px;
        font-weight: bold;
        color: #ffffff;
        position: absolute;
        left: 50%;
        top: 70px;
        transform: translateX(-50%);
        text-shadow: 0 2px 4px rgba(0, 0, 0, 0.5);
        font-family: 'SourceHanSansCN', 'Microsoft YaHei', 'PingFang SC', Arial, sans-serif;
        letter-spacing: 1px;
        white-space: nowrap;
      }
    }

    .bitem2 {
      margin-left: 196px;
      width: 240px;
      height: 190px;
      background-image: url("./img/mainBox.png");
      background-size: 100% 100%;
      position: relative;
      cursor: pointer;

      .btn-text {
        font-size: 20px;
        font-weight: bold;
        color: #ffffff;
        position: absolute;
        left: 50%;
        top: 70px;
        transform: translateX(-50%);
        text-shadow: 0 2px 4px rgba(0, 0, 0, 0.5);
        font-family: 'SourceHanSansCN', 'Microsoft YaHei', 'PingFang SC', Arial, sans-serif;
        letter-spacing: 1px;
        white-space: nowrap;
      }
    }

    .bitem1.disabled,
    .bitem2.disabled {
      opacity: 0.4;
      filter: grayscale(60%);
      cursor: not-allowed;
      pointer-events: none;
    }
  }
}</style>