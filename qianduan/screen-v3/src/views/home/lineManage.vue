<template>
  <div class="line-manage">
    <view-title title="航线管理" />
    
    <div class="line-list">
      <div 
        class="line-item" 
        v-for="(item, index) in tableData" 
        :key="item.id || index"
        @click="handleLineClick(item)"
      >
        <div class="line-info">
          <div class="line-name">{{ item.fileName }}</div>
          <div class="line-detail">
            <span class="detail-item">航点数: {{ getWaypointCount(item) }}</span>
            <span class="detail-divider">|</span>
            <span class="detail-item">{{ formatTime(item.modiTime) }}</span>
          </div>
        </div>
        <div class="line-actions">
          <button class="action-btn" @click.stop="openExecuteModal(item)">执行</button>
        </div>
      </div>
    </div>

    <Teleport to="body">
      <div v-if="showModal" class="modal-overlay" @click.self="closeModal">
        <div class="modal-content">
          <div class="modal-header">
            <span class="modal-title">执行航线任务</span>
            <button class="close-btn" @click="closeModal">×</button>
          </div>
          <div class="modal-body">
            <div class="form-item">
              <label class="form-label">计划名称 <span class="required">*</span></label>
              <input v-model="formData.taskName" type="text" class="form-input" placeholder="请输入计划名称" />
            </div>

            <div class="form-item">
              <label class="form-label">选择机场 <span class="required">*</span></label>
              <select v-model="formData.gatewaySn" class="form-select">
                <option value="" disabled>请选择机场</option>
                <option v-for="airport in airports" :key="airport.sn" :value="airport.sn">
                  {{ airport.name }} ({{ airport.sn }})
                </option>
              </select>
            </div>

            <div class="form-item">
              <label class="form-label">航线失控动作 <span class="required">*</span></label>
              <select v-model="formData.outOfControlActionInFlight" class="form-select">
                <option value="return_home">返航</option>
                <option value="continue_task">继续任务</option>
              </select>
            </div>

            <div class="form-item">
              <label class="form-label">返航高度 (米) <span class="required">*</span></label>
              <input v-model.number="formData.rthAltitude" type="number" class="form-input" min="20" max="1500" placeholder="请输入返航高度" />
            </div>
          </div>
          <div class="modal-footer">
            <button class="cancel-btn" @click="closeModal">取消</button>
            <button class="confirm-btn" @click="submitTask" :disabled="isSubmitting">
              {{ isSubmitting ? '提交中...' : '确认执行' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import viewTitle from './-title.vue'
import { deviceApi, type LineDTO, type ExecuteFlightTaskDTO, type DeviceDTO } from '@/api/uav'
import { ElMessage } from 'element-plus'
import { useAppStore, type Waypoint } from '@/store'
import JSZip from 'jszip'

const store = useAppStore()
const emit = defineEmits(['line'])

interface LineRow extends LineDTO {}

const tableData = ref<LineRow[]>([])
const airports = ref<DeviceDTO[]>([])
const showModal = ref(false)
const isSubmitting = ref(false)
const selectedLine = ref<LineRow | null>(null)

const LOCAL_STORAGE_KEY = 'uav_route_data'

const formData = reactive<ExecuteFlightTaskDTO>({
  taskName: '',
  gatewaySn: '',
  waylineUuid: '',
  rthAltitude: 23,
  rthMode: 'preset',
  outOfControlActionInFlight: 'return_home',
  waylinePrecisionType: 'rtk',
  resumableStatus: 'manual',
  repeatType: 'nonrepeating'
})

const formatTime = (timestamp: string): string => {
  if (!timestamp) return ''
  const ts = parseInt(timestamp)
  if (isNaN(ts)) return timestamp
  const date = new Date(ts)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:00`
}

const getLocalRoutes = (): { waylineUuid: string; waylineName: string; waypoints: Waypoint[]; modiTime?: string }[] => {
  try {
    const data = localStorage.getItem(LOCAL_STORAGE_KEY)
    return data ? JSON.parse(data) : []
  } catch {
    return []
  }
}

const getWaypointCount = (item: LineRow): string => {
  const routes = getLocalRoutes()
  const route = routes.find(r => r.waylineUuid === item.waylineUuid)
  if (route && route.waypoints && route.waypoints.length > 0) {
    return `${route.waypoints.length}个`
  }
  if (item.waypointCount && item.waypointCount > 0) {
    return `${item.waypointCount}个`
  }
  return '未缓存'
}

const getLocalRouteByUuid = (waylineUuid: string, modiTime?: string): { waylineName: string; waypoints: Waypoint[] } | null => {
  const routes = getLocalRoutes()
  const route = routes.find(r => r.waylineUuid === waylineUuid)
  if (!route) return null
  // 缓存过期判断: 航线更新时间(modiTime)变化或旧缓存无modiTime则重新下载
  if (modiTime && route.modiTime !== modiTime) return null
  return { waylineName: route.waylineName, waypoints: route.waypoints }
}

const loadLines = async () => {
  try {
    const res = await deviceApi.getLineList()
    tableData.value = res.data
    // 只清理已不存在的航线缓存, 保留已有航点数显示(modiTime变化时getLocalRouteByUuid会自动失效)
    const validUuids = new Set(res.data.map((l: LineRow) => l.waylineUuid))
    const routes = getLocalRoutes()
    const filtered = routes.filter(r => validUuids.has(r.waylineUuid))
    if (filtered.length !== routes.length) {
      localStorage.setItem(LOCAL_STORAGE_KEY, JSON.stringify(filtered))
    }
  } catch (e) {
    console.warn('获取航线列表失败', e)
  }
}

const loadAirports = async () => {
  try {
    const res = await deviceApi.getDeviceList()
    airports.value = res.data.filter(d => d.kind === 'airport')
  } catch (e) {
    console.warn('获取设备列表失败', e)
  }
}

const openExecuteModal = (item: LineRow) => {
  selectedLine.value = item
  formData.taskName = `航线任务-${item.fileName}-${new Date().toLocaleDateString().replace(/\//g, '-')}`
  formData.waylineUuid = item.waylineUuid || ''
  formData.gatewaySn = airports.value.length > 0 ? airports.value[0].sn : ''
  formData.rthAltitude = 23
  formData.rthMode = 'preset'
  formData.outOfControlActionInFlight = 'return_home'
  formData.waylinePrecisionType = 'rtk'
  formData.resumableStatus = 'manual'
  showModal.value = true
}

const closeModal = () => {
  showModal.value = false
  selectedLine.value = null
}

const submitTask = async () => {
  if (!formData.taskName.trim()) {
    ElMessage.error('请输入计划名称')
    return
  }
  if (!formData.gatewaySn) {
    ElMessage.error('请选择机场')
    return
  }
  if (!formData.waylineUuid) {
    ElMessage.error('航线UUID不能为空')
    return
  }
  if (!formData.rthAltitude || formData.rthAltitude < 20) {
    ElMessage.error('返航高度不能低于20米')
    return
  }

  isSubmitting.value = true
  try {
    await deviceApi.executeFlightTask({ ...formData })
    ElMessage.success('航线任务已下发')
    closeModal()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || '任务下发失败')
  } finally {
    isSubmitting.value = false
  }
}

const parseKmlContent = (content: string): Waypoint[] => {
  const waypoints: Waypoint[] = []
  try {
    const parser = new DOMParser()
    const xmlDoc = parser.parseFromString(content, 'text/xml')
    
    const coordinatesElements = xmlDoc.getElementsByTagName('coordinates')
    for (let i = 0; i < coordinatesElements.length; i++) {
      const text = coordinatesElements[i].textContent
      if (!text) continue
      const lines = text.trim().split(/[\n\r]+/)
      lines.forEach(line => {
        const coords = line.trim().split(',')
        if (coords.length >= 2) {
          waypoints.push({
            lng: parseFloat(coords[0]),
            lat: parseFloat(coords[1]),
            alt: coords.length > 2 ? parseFloat(coords[2]) : undefined
          })
        }
      })
    }
    
    if (waypoints.length === 0) {
      const placemarks = xmlDoc.getElementsByTagName('Placemark')
      for (let i = 0; i < placemarks.length; i++) {
        const coordinates = placemarks[i].getElementsByTagName('coordinates')
        if (coordinates.length > 0 && coordinates[0].textContent) {
          const coords = coordinates[0].textContent.trim().split(',')
          if (coords.length >= 2) {
            waypoints.push({
              lng: parseFloat(coords[0]),
              lat: parseFloat(coords[1]),
              alt: coords.length > 2 ? parseFloat(coords[2]) : undefined
            })
          }
        }
      }
    }
  } catch (e) {
    console.warn('解析KML格式失败', e)
  }
  return waypoints
}

const parseKmzFile = async (arrayBuffer: ArrayBuffer): Promise<string> => {
  try {
    const zip = new JSZip()
    const content = await zip.loadAsync(arrayBuffer)
    for (const filename of Object.keys(content.files)) {
      if (filename.toLowerCase().endsWith('.kml')) {
        const file = content.files[filename]
        return await file.async('string')
      }
    }
    for (const filename of Object.keys(content.files)) {
      const file = content.files[filename]
      if (!file.dir) {
        return await file.async('string')
      }
    }
  } catch (e) {
    console.error('解压KMZ文件失败', e)
  }
  return ''
}

const parseWaylineFile = async (content: string, isKmz: boolean, arrayBuffer?: ArrayBuffer): Promise<Waypoint[]> => {
  const waypoints: Waypoint[] = []
  
  if (isKmz && arrayBuffer) {
    const kmlContent = await parseKmzFile(arrayBuffer)
    if (kmlContent) {
      return parseKmlContent(kmlContent)
    }
    return waypoints
  }
  
  try {
    const jsonData = JSON.parse(content)
    if (jsonData.waypoints && Array.isArray(jsonData.waypoints)) {
      jsonData.waypoints.forEach((wp: any) => {
        if (wp.latitude !== undefined && wp.longitude !== undefined) {
          waypoints.push({
            lng: parseFloat(wp.longitude),
            lat: parseFloat(wp.latitude),
            alt: wp.altitude ? parseFloat(wp.altitude) : undefined
          })
        } else if (wp.lat !== undefined && wp.lng !== undefined) {
          waypoints.push({
            lng: parseFloat(wp.lng),
            lat: parseFloat(wp.lat),
            alt: wp.alt ? parseFloat(wp.alt) : undefined
          })
        }
      })
    } else if (jsonData.data && Array.isArray(jsonData.data)) {
      jsonData.data.forEach((wp: any) => {
        if (wp.latitude !== undefined && wp.longitude !== undefined) {
          waypoints.push({
            lng: parseFloat(wp.longitude),
            lat: parseFloat(wp.latitude),
            alt: wp.altitude ? parseFloat(wp.altitude) : undefined
          })
        } else if (wp.lat !== undefined && wp.lng !== undefined) {
          waypoints.push({
            lng: parseFloat(wp.lng),
            lat: parseFloat(wp.lat),
            alt: wp.alt ? parseFloat(wp.alt) : undefined
          })
        }
      })
    }
  } catch (e) {
    console.warn('解析JSON航线文件失败，尝试KML格式', e)
    return parseKmlContent(content)
  }
  return waypoints
}

const handleLineClick = async (item: LineRow) => {
  const localRoute = getLocalRouteByUuid(item.waylineUuid || '', item.modiTime)
  
  if (localRoute && localRoute.waypoints.length > 0) {
    store.updateCurrentRoute({
      waylineUuid: item.waylineUuid || '',
      waylineName: localRoute.waylineName || item.fileName || '未知航线',
      waypoints: localRoute.waypoints
    })
    ElMessage.success(`成功加载航线（本地缓存），共 ${localRoute.waypoints.length} 个航点`)
    return
  }
  
  if (!item.downloadUrl) {
    ElMessage.warning('该航线没有下载链接，请先在管理平台同步航线数据')
    return
  }
  
  try {
    ElMessage.info('正在加载航线数据...')
    
    const proxyUrl = `/api/wayline/download?url=${encodeURIComponent(item.downloadUrl)}&_t=${Date.now()}`
    const response = await fetch(proxyUrl, { cache: 'no-cache' })
    
    if (!response.ok) {
      ElMessage.error('下载航线文件失败')
      return
    }
    
    const isKmz = item.downloadUrl.toLowerCase().endsWith('.kmz')
    let content = ''
    let arrayBuffer: ArrayBuffer | undefined
    
    if (isKmz) {
      arrayBuffer = await response.arrayBuffer()
    } else {
      content = await response.text()
    }
    
    const waypoints = await parseWaylineFile(content, isKmz, arrayBuffer)
    
    if (waypoints.length === 0) {
      ElMessage.warning('未解析到航点数据')
      return
    }
    
    store.updateCurrentRoute({
      waylineUuid: item.waylineUuid || '',
      waylineName: item.fileName || '未知航线',
      waypoints
    })
    
    try {
      const existingData = localStorage.getItem(LOCAL_STORAGE_KEY)
      let routes: { waylineUuid: string; waylineName: string; waypoints: Waypoint[]; modiTime?: string }[] = existingData ? JSON.parse(existingData) : []
      const index = routes.findIndex(r => r.waylineUuid === item.waylineUuid)
      if (index >= 0) {
        routes[index] = { waylineUuid: item.waylineUuid || '', waylineName: item.fileName || '', waypoints, modiTime: item.modiTime }
      } else {
        routes.push({ waylineUuid: item.waylineUuid || '', waylineName: item.fileName || '', waypoints, modiTime: item.modiTime })
      }
      localStorage.setItem(LOCAL_STORAGE_KEY, JSON.stringify(routes))
    } catch {}
    
    ElMessage.success(`成功加载航线，共 ${waypoints.length} 个航点`)
  } catch (e) {
    console.error('加载航线失败', e)
    ElMessage.error('加载航线失败，请检查网络连接')
  }
}

onMounted(() => {
  loadLines()
  loadAirports()
})
</script>

<style lang="less" scoped>
.line-manage {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.line-list {
  flex: 1;
  padding: 0 12px 12px;
  overflow-y: auto;
  min-height: 0;
  scrollbar-width: thin;
  scrollbar-color: rgba(46, 139, 255, 0.55) rgba(46, 139, 255, 0.06);

  &::-webkit-scrollbar {
    width: 6px;
    background: rgba(46, 139, 255, 0.06);
    border-radius: 3px;
  }

  &::-webkit-scrollbar-thumb {
    background: rgba(46, 139, 255, 0.55);
    border-radius: 3px;
  }
}

.line-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  margin-bottom: 8px;
  background: rgba(23, 39, 66, 0.6);
  border: 1px solid rgba(73, 123, 207, 0.3);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background: rgba(46, 139, 255, 0.15);
    border-color: rgba(46, 139, 255, 0.6);
    transform: translateX(-4px);
  }

  &:last-child {
    margin-bottom: 0;
  }
}

.line-info {
  flex: 1;
  min-width: 0;

  .line-name {
    font-size: 13px;
    font-weight: bold;
    color: #fff;
    margin-bottom: 4px;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  .line-detail {
    display: flex;
    align-items: center;
    font-size: 11px;
    color: #6c91b5;

    .detail-item {
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;
    }

    .detail-divider {
      margin: 0 8px;
      color: rgba(255, 255, 255, 0.2);
    }
  }
}

.line-actions {
  flex-shrink: 0;
  margin-left: 12px;

  .action-btn {
    padding: 6px 14px;
    background: rgba(46, 139, 255, 0.2);
    color: #4ac8ff;
    border: 1px solid rgba(46, 139, 255, 0.5);
    border-radius: 4px;
    font-size: 12px;
    font-weight: bold;
    cursor: pointer;
    transition: all 0.2s;

    &:hover {
      background: rgba(46, 139, 255, 0.3);
      border-color: rgba(46, 139, 255, 0.8);
      transform: scale(1.05);
    }

    &:active {
      transform: scale(0.95);
    }
  }
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}

.modal-content {
  background: linear-gradient(180deg, rgba(23, 39, 66, 0.98), rgba(10, 20, 40, 0.98));
  border: 1px solid rgba(73, 123, 207, 0.5);
  border-radius: 8px;
  width: 480px;
  max-width: 90vw;
  overflow: hidden;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.5);
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  background: rgba(46, 139, 255, 0.1);
  border-bottom: 1px solid rgba(73, 123, 207, 0.3);

  .modal-title {
    font-size: 18px;
    font-weight: bold;
    color: #fff;
  }

  .close-btn {
    width: 30px;
    height: 30px;
    background: transparent;
    border: none;
    color: #fff;
    font-size: 24px;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
    transition: all 0.2s;

    &:hover {
      background: rgba(255, 255, 255, 0.1);
    }
  }
}

.modal-body {
  padding: 20px;
  max-height: 60vh;
  overflow-y: auto;
}

.form-item {
  margin-bottom: 16px;

  .form-label {
    display: block;
    font-size: 14px;
    color: #6c91b5;
    margin-bottom: 8px;

    .required {
      color: #ff4d4f;
    }
  }

  .form-input,
  .form-select {
    width: 100%;
    padding: 10px 12px;
    background: rgba(255, 255, 255, 0.05);
    border: 1px solid rgba(73, 123, 207, 0.3);
    border-radius: 4px;
    color: #fff;
    font-size: 14px;
    box-sizing: border-box;
    outline: none;
    transition: all 0.2s;

    &:focus {
      border-color: #2E8BFF;
      box-shadow: 0 0 0 3px rgba(46, 139, 255, 0.1);
    }

    &::placeholder {
      color: #6c91b5;
    }
  }

  .form-select {
    appearance: none;
    background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%236c91b5' d='M6 8L0 2h12z'/%3E%3C/svg%3E");
    background-repeat: no-repeat;
    background-position: right 10px center;
    padding-right: 30px;
  }
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 20px;
  border-top: 1px solid rgba(73, 123, 207, 0.3);

  .cancel-btn {
    padding: 10px 24px;
    background: rgba(255, 255, 255, 0.1);
    border: 1px solid rgba(73, 123, 207, 0.3);
    color: #fff;
    border-radius: 4px;
    font-size: 14px;
    cursor: pointer;
    transition: all 0.2s;

    &:hover {
      background: rgba(255, 255, 255, 0.15);
    }
  }

  .confirm-btn {
    padding: 10px 24px;
    background: linear-gradient(135deg, #2E8BFF, #4169E1);
    color: #fff;
    border: none;
    border-radius: 4px;
    font-size: 14px;
    cursor: pointer;
    transition: all 0.2s;

    &:hover:not(:disabled) {
      transform: scale(1.02);
      box-shadow: 0 0 15px rgba(46, 139, 255, 0.4);
    }

    &:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }
  }
}
</style>
