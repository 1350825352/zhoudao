<template>
  <div class="page-container">
    <el-card class="page-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">告警管理</span>
          <div class="card-actions">
            <el-button @click="loadData">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </div>
      </template>
      <div class="search-bar">
        <el-select v-model="searchStatus" placeholder="处理状态" clearable class="search-item">
          <el-option label="全部" value="" />
          <el-option label="未处理" value="unhandled" />
          <el-option label="已处理" value="handled" />
        </el-select>
        <el-select v-model="searchRuleName" placeholder="告警类型" clearable class="search-item">
          <el-option label="全部" value="" />
          <el-option v-for="rule in ruleList" :key="rule" :label="rule" :value="rule" />
        </el-select>
        <el-input v-model="searchSn" placeholder="飞机SN" clearable class="search-item" />
        <el-select v-model="searchDeviceName" placeholder="飞机名称" clearable class="search-item">
          <el-option label="全部" value="" />
          <el-option v-for="(name, sn) in deviceOptions" :key="sn" :label="name" :value="name" />
        </el-select>
        <el-select v-model="searchRouteName" placeholder="航线名称" clearable class="search-item">
          <el-option label="全部" value="" />
          <el-option v-for="(name, uuid) in routeOptions" :key="uuid" :label="name" :value="name" />
        </el-select>
        <el-date-picker
          v-model="searchStartTime"
          type="datetime"
          placeholder="开始时间"
          class="search-item"
          format="YYYY-MM-DD HH:mm:ss"
          value-format="YYYY-MM-DD HH:mm:ss"
        />
        <el-date-picker
          v-model="searchEndTime"
          type="datetime"
          placeholder="结束时间"
          class="search-item"
          format="YYYY-MM-DD HH:mm:ss"
          value-format="YYYY-MM-DD HH:mm:ss"
        />
        <el-button type="primary" @click="handleSearch">搜索</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>

      <el-table :data="tableData" stripe border style="width: 100%" :row-class-name="tableRowClassName" :row-key="row => row.id">
        <el-table-column prop="id" label="序号" width="80" />
        <el-table-column prop="rule_name" label="告警类型" min-width="120">
          <template #default="{ row }">
            <span>{{ row.rule_name || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="aircraft_sn" label="飞机SN" min-width="150">
          <template #default="{ row }">
            <span>{{ row.aircraft_sn || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="飞机名称" min-width="120">
          <template #default="{ row }">
            <span>{{ getDeviceName(row.aircraft_sn) || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="告警图片" width="120">
          <template #default="{ row }">
            <el-image
              v-if="row.image_http_url"
              :src="row.image_http_url"
              class="image-thumbnail"
              fit="cover"
              @click="openImage(row)"
            />
            <span v-else class="no-image">-</span>
          </template>
        </el-table-column>
        <el-table-column label="航线名称" min-width="150">
          <template #default="{ row }">
            <span>{{ getRouteName(row.wayline_uuid) || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="处理状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.work_order_id ? 'success' : 'warning'">
              {{ row.work_order_id ? '已处理' : '未处理' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="转工单" width="100">
          <template #default="{ row }">
            <el-button
              v-if="!row.work_order_id"
              type="primary"
              size="small"
              @click="handleCreateWorkOrder(row)"
            >
              转工单
            </el-button>
            <el-tag v-else type="success" size="small">已转</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="恢复" width="100">
          <template #default="{ row }">
            <el-button
              v-if="row.work_order_id"
              type="warning"
              size="small"
              @click="handleRestore(row)"
            >
              恢复
            </el-button>
            <span v-else class="no-action">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="event_time" label="告警时间" width="180">
          <template #default="{ row }">
            <span>{{ formatTime(row.event_time) }}</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <div v-if="showPreview" class="preview-overlay" @click="closePreview">
      <div class="preview-content" @click.stop>
        <div 
          class="preview-img-wrapper" 
          @wheel.prevent="handleWheel"
          @mousedown="handleMouseDown"
        >
          <img 
            :src="previewImage" 
            alt="" 
            class="preview-img"
            draggable="false"
            :style="{ 
              transform: `scale(${scale}) translate(${translateX}px, ${translateY}px)` 
            }"
          />
          <div class="preview-info">
            <div>告警类型: {{ previewItem?.rule_name }}</div>
            <div>飞机SN: {{ previewItem?.aircraft_sn }}</div>
            <div>告警时间: {{ formatTime(previewItem?.event_time) }}</div>
            <div v-if="previewItem?.latitude && previewItem?.longitude">
              经度: {{ previewItem?.longitude }} 纬度: {{ previewItem?.latitude }}
            </div>
          </div>
          <button 
            v-if="previewItem && !previewItem.work_order_id"
            class="preview-workorder-btn" 
            @click.stop="handlePreviewWorkOrder"
          >
            <svg class="workorder-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
              <path d="M9 9h6"/>
              <path d="M9 13h6"/>
              <path d="M9 17h6"/>
              <path d="M3 9h2"/>
              <path d="M3 13h2"/>
              <path d="M3 17h2"/>
              <circle cx="18" cy="6" r="1.5" fill="currentColor"/>
            </svg>
            <span>转工单</span>
          </button>
          <div class="preview-zoom-controls">
            <button class="zoom-btn" @click.stop="zoomOut">−</button>
            <span class="zoom-level">{{ Math.round(scale * 100) }}%</span>
            <button class="zoom-btn" @click.stop="zoomIn">+</button>
          </div>
        </div>
        
        <div class="preview-counter">
          {{ currentIndex + 1 }} / {{ imageItems.length }}
        </div>

        <button class="preview-close" @click="closePreview">✕</button>
        
        <button 
          class="preview-prev" 
          @click="prevImage"
          :disabled="currentIndex <= 0"
        >
          ◀
        </button>
        <button 
          class="preview-next" 
          @click="nextImage"
          :disabled="currentIndex >= imageItems.length - 1"
        >
          ▶
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElLoading } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { request } from '@/api/request'

const route = useRoute()
const highlightResultId = ref<number | null>(null)

interface AlarmData {
  id: number
  zhimou_task_id: string
  aircraft_sn: string
  rule_id: string
  rule_name: string
  hit_count: number
  event_time: string
  event_timestamp_ms: number
  latitude: number
  longitude: number
  height: number
  image_http_url: string
  image_has_osd: number
  image_width: number
  image_height: number
  wayline_uuid: string
  work_order_id: number | null
}

interface DeviceInfo {
  sn: string
  deviceName: string
}

interface RouteInfo {
  waylineUuid: string
  waylineName: string
}

const tableData = ref<AlarmData[]>([])
const deviceMap = ref<Map<string, string>>(new Map())
const routeMap = ref<Map<string, string>>(new Map())

const deviceOptions = computed(() => {
  const options: Record<string, string> = {}
  deviceMap.value.forEach((name, sn) => {
    if (name && name.trim()) {
      options[sn] = name
    }
  })
  return options
})

const routeOptions = computed(() => {
  const options: Record<string, string> = {}
  routeMap.value.forEach((name, uuid) => {
    if (name && name.trim()) {
      options[uuid] = name
    }
  })
  return options
})

const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const searchStatus = ref('')
const searchSn = ref('')
const searchRuleName = ref('')
const searchDeviceName = ref('')
const searchRouteName = ref('')
const searchStartTime = ref('')
const searchEndTime = ref('')
const ruleList = ref<string[]>([])

const showPreview = ref(false)
const previewImage = ref('')
const previewItem = ref<AlarmData | null>(null)
const currentIndex = ref(0)
const imageItems = ref<AlarmData[]>([])

const scale = ref(1)
const translateX = ref(0)
const translateY = ref(0)
const isDragging = ref(false)
const startX = ref(0)
const startY = ref(0)
const startTranslateX = ref(0)
const startTranslateY = ref(0)

const loadDevices = async () => {
  try {
    const devices = await request.get<DeviceInfo[]>('/device/list', { params: { deviceType: 3 } })
    if (devices) {
      deviceMap.value = new Map(devices.map(d => [d.sn, d.deviceName || '']))
    }
  } catch (e) {
    console.warn('加载设备列表失败', e)
  }
}

const loadRoutes = async () => {
  try {
    const routes = await request.get<RouteInfo[]>('/wayline/list')
    if (routes) {
      routeMap.value = new Map(routes.map(r => [r.waylineUuid, r.waylineName || '']))
    }
  } catch (e) {
    console.warn('加载航线列表失败', e)
  }
}

const getDeviceName = (sn: string | undefined): string => {
  return deviceMap.value.get(sn || '') || ''
}

const getRouteName = (waylineUuid: string | undefined): string => {
  return routeMap.value.get(waylineUuid || '') || ''
}

interface PageResult {
  list: AlarmData[]
  total: number
  page: number
  size: number
}

const loadData = async (silent = false) => {
  let loading: ReturnType<typeof ElLoading.service> | null = null
  if (!silent) loading = ElLoading.service({ text: '加载告警数据...' })
  try {
    await Promise.all([loadDevices(), loadRoutes()])
    const params = {
      page: currentPage.value,
      size: pageSize.value,
      status: searchStatus.value || undefined,
      aircraft_sn: searchSn.value || undefined,
      rule_name: searchRuleName.value || undefined,
      device_name: searchDeviceName.value || undefined,
      route_name: searchRouteName.value || undefined,
      start_time: searchStartTime.value || undefined,
      end_time: searchEndTime.value || undefined
    }
    const data = await request.get<PageResult>('/zhimou/results/page', { params })
    tableData.value = data?.list || []
    total.value = data?.total || 0
    imageItems.value = tableData.value.filter(item => item.image_http_url)
    const rules = new Set(tableData.value.map(item => item.rule_name).filter(Boolean))
    ruleList.value = Array.from(rules)
  } catch (e) {
    console.error('加载告警数据失败', e)
    if (!silent) ElMessage.error('加载告警数据失败')
  } finally {
    loading?.close()
  }
}

// 飞行时1秒静默轮询, 待机停止(飞行判定与地图一致: modeCode 非0且非14)
let pollTimer: ReturnType<typeof setInterval> | null = null
const checkAndRefresh = async () => {
  try {
    const devices = await request.get<Array<{ kind: string; modeCode?: number }>>('/devices/list')
    const flying = (devices || []).some(d => d.kind === 'uav' && d.modeCode != null && d.modeCode !== 0 && d.modeCode !== 14)
    if (flying) await loadData(true)
  } catch {
    // 静默
  }
}

const handleSearch = () => {
  currentPage.value = 1
  loadData()
}

const resetSearch = () => {
  searchStatus.value = ''
  searchSn.value = ''
  searchRuleName.value = ''
  searchDeviceName.value = ''
  searchRouteName.value = ''
  searchStartTime.value = ''
  searchEndTime.value = ''
  currentPage.value = 1
  loadData()
}

const handleSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  loadData()
}

const handleCurrentChange = (page: number) => {
  currentPage.value = page
  loadData()
}

const formatTime = (time: string | undefined): string => {
  if (!time) return '-'
  return time.replace('T', ' ')
}

const handleCreateWorkOrder = async (row: AlarmData) => {
  const oldWorkOrderId = row.work_order_id
  row.work_order_id = Date.now()
  try {
    await request.post('/workorder/create', {
      alarm_id: row.id,
      alarm_type: row.rule_name,
      aircraft_sn: row.aircraft_sn,
      wayline_uuid: row.wayline_uuid,
      event_time: row.event_time,
      latitude: row.latitude,
      longitude: row.longitude
    })
    ElMessage.success('转工单成功')
  } catch (e) {
    row.work_order_id = oldWorkOrderId
    console.error('转工单失败', e)
    ElMessage.error('转工单失败')
  }
}

const handlePreviewWorkOrder = async () => {
  if (previewItem.value) {
    await handleCreateWorkOrder(previewItem.value)
  }
}

const handleRestore = async (row: AlarmData) => {
  try {
    await request.post(`/zhimou/results/${row.id}/restore`)
    row.work_order_id = null
    ElMessage.success('恢复成功')
  } catch (e) {
    console.error('恢复失败', e)
    ElMessage.error('恢复失败')
  }
}

const openImage = (row: AlarmData) => {
  previewImage.value = row.image_http_url
  previewItem.value = row
  currentIndex.value = imageItems.value.findIndex(item => item.id === row.id)
  scale.value = 1
  translateX.value = 0
  translateY.value = 0
  showPreview.value = true
}

const closePreview = () => {
  showPreview.value = false
  previewImage.value = ''
  previewItem.value = null
}

const prevImage = () => {
  if (currentIndex.value > 0) {
    currentIndex.value--
    const item = imageItems.value[currentIndex.value]
    previewImage.value = item.image_http_url
    previewItem.value = item
    resetZoom()
  }
}

const nextImage = () => {
  if (currentIndex.value < imageItems.value.length - 1) {
    currentIndex.value++
    const item = imageItems.value[currentIndex.value]
    previewImage.value = item.image_http_url
    previewItem.value = item
    resetZoom()
  }
}

const zoomIn = () => {
  if (scale.value < 5) {
    scale.value = Math.min(5, scale.value + 0.2)
  }
}

const zoomOut = () => {
  if (scale.value > 0.2) {
    scale.value = Math.max(0.2, scale.value - 0.2)
  }
}

const resetZoom = () => {
  scale.value = 1
  translateX.value = 0
  translateY.value = 0
}

const handleWheel = (e: WheelEvent) => {
  const delta = e.deltaY > 0 ? -0.1 : 0.1
  scale.value = Math.max(0.2, Math.min(5, scale.value + delta))
}

const handleMouseDown = (e: MouseEvent) => {
  if (scale.value > 1) {
    isDragging.value = true
    startX.value = e.clientX
    startY.value = e.clientY
    startTranslateX.value = translateX.value
    startTranslateY.value = translateY.value
  }
}

const handleMouseMove = (e: MouseEvent) => {
  if (isDragging.value) {
    translateX.value = startTranslateX.value + (e.clientX - startX.value)
    translateY.value = startTranslateY.value + (e.clientY - startY.value)
  }
}

const handleMouseUp = () => {
  isDragging.value = false
}

onMounted(async () => {
  // 读取URL参数(从数据统计页"查看告警"跳转过来时带 result_id / aircraft_sn)
  const queryResultId = route.query.result_id as string
  const queryAircraftSn = route.query.aircraft_sn as string
  if (queryAircraftSn) searchSn.value = queryAircraftSn
  if (queryResultId) highlightResultId.value = Number(queryResultId)

  await loadData()

  if (highlightResultId.value) {
    setTimeout(() => { highlightResultId.value = null }, 6000)
  }

  window.addEventListener('mousemove', handleMouseMove)
  window.addEventListener('mouseup', handleMouseUp)

  pollTimer = setInterval(checkAndRefresh, 1000)
})

const tableRowClassName = ({ row }: { row: AlarmData }): string => {
  if (highlightResultId.value && row.id === highlightResultId.value) {
    return 'highlight-row'
  }
  return ''
}

onUnmounted(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
  window.removeEventListener('mousemove', handleMouseMove)
  window.removeEventListener('mouseup', handleMouseUp)
})
</script>

<style lang="less" scoped>
.page-container {
  width: 100%;
}

.page-card {
  border-radius: 4px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.card-actions {
  display: flex;
  gap: 10px;
}

.search-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #ebeef5;
}

.search-item {
  width: 200px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.image-thumbnail {
  width: 80px;
  height: 60px;
  border-radius: 4px;
  cursor: pointer;
}

.no-image {
  color: #909399;
  font-size: 12px;
}

.preview-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}

.preview-content {
  position: relative;
  display: inline-block;
}

.preview-img-wrapper {
  position: relative;
  display: inline-block;
  overflow: hidden;
  cursor: grab;
  border: 2px solid rgba(255, 255, 255, 0.5);
  border-radius: 4px;
}

.preview-img-wrapper:active {
  cursor: grabbing;
}

.preview-img {
  display: block;
  max-width: 90vw;
  max-height: 90vh;
  transition: transform 0.1s ease-out;
  user-select: none;
}

.preview-info {
  position: absolute;
  bottom: 10px;
  left: 10px;
  background: rgba(0, 0, 0, 0.75);
  color: #fff;
  padding: 8px 12px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.6;
}

.preview-counter {
  position: absolute;
  top: 15px;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  padding: 6px 14px;
  border-radius: 20px;
  font-size: 13px;
}

.preview-close {
  position: absolute;
  top: 15px;
  right: 15px;
  width: 40px;
  height: 40px;
  background: rgba(0, 0, 0, 0.6);
  border: none;
  border-radius: 50%;
  color: #fff;
  font-size: 20px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;
}

.preview-close:hover {
  background: rgba(255, 68, 68, 0.8);
}

.preview-prev,
.preview-next {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 50px;
  height: 50px;
  background: rgba(0, 0, 0, 0.6);
  border: none;
  border-radius: 50%;
  color: #fff;
  font-size: 24px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;
}

.preview-prev {
  left: 15px;
}

.preview-next {
  right: 15px;
}

.preview-prev:hover:not(:disabled),
.preview-next:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.2);
}

.preview-prev:disabled,
.preview-next:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.preview-workorder-btn {
  position: absolute;
  bottom: 75px;
  right: 15px;
  background: linear-gradient(135deg, #0f172a 0%, #1e293b 50%, #0ea5e9 100%);
  border: 1px solid rgba(56, 189, 248, 0.3);
  border-radius: 12px;
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  padding: 12px 24px;
  cursor: pointer;
  transition: all 0.4s ease;
  z-index: 10;
  box-shadow: 
    0 0 20px rgba(56, 189, 248, 0.2),
    0 4px 15px rgba(0, 0, 0, 0.4),
    inset 0 1px 0 rgba(255, 255, 255, 0.1);
  display: flex;
  align-items: center;
  gap: 8px;
  backdrop-filter: blur(10px);
}

.preview-workorder-btn:hover {
  background: linear-gradient(135deg, #1e293b 0%, #334155 50%, #38bdf8 100%);
  transform: translateY(-3px) scale(1.02);
  box-shadow: 
    0 0 40px rgba(56, 189, 248, 0.4),
    0 8px 25px rgba(0, 0, 0, 0.5),
    inset 0 1px 0 rgba(255, 255, 255, 0.2);
  border-color: rgba(56, 189, 248, 0.6);
}

.preview-workorder-btn:active {
  transform: translateY(-1px) scale(0.98);
  box-shadow: 
    0 0 15px rgba(56, 189, 248, 0.2),
    0 2px 8px rgba(0, 0, 0, 0.3),
    inset 0 1px 0 rgba(255, 255, 255, 0.1);
}

.workorder-icon {
  width: 18px;
  height: 18px;
  filter: drop-shadow(0 0 6px rgba(56, 189, 248, 0.6));
}

.preview-workorder-btn:hover .workorder-icon {
  filter: drop-shadow(0 0 10px rgba(56, 189, 248, 0.8));
}

.preview-zoom-controls {
  position: absolute;
  bottom: 15px;
  right: 15px;
  display: flex;
  align-items: center;
  gap: 12px;
  background: rgba(0, 0, 0, 0.6);
  padding: 6px 12px;
  border-radius: 20px;
}

.zoom-btn {
  width: 32px;
  height: 32px;
  background: rgba(255, 255, 255, 0.15);
  border: none;
  border-radius: 50%;
  color: #fff;
  font-size: 18px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;
}

.zoom-btn:hover {
  background: rgba(255, 255, 255, 0.3);
}

.zoom-level {
  color: #fff;
  font-size: 13px;
  min-width: 45px;
  text-align: center;
}

:deep(.el-table .highlight-row) {
  background-color: #ecf5ff !important;
  animation: highlight-fade 6s ease-out;
}

@keyframes highlight-fade {
  0% { background-color: #ffd54f !important; }
  20% { background-color: #fff3cd !important; }
  100% { background-color: transparent !important; }
}
</style>