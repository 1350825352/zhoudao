<template>
  <div class="page-container">
    <el-card class="page-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">照片告警</span>
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

      <el-table :data="tableData" stripe border style="width: 100%" :row-key="row => row.id">
        <el-table-column prop="id" label="序号" width="80" />
        <el-table-column prop="rule_name" label="告警类型" min-width="120">
          <template #default="{ row }">
            <span>{{ row.rule_name || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="hit_count" label="识别目标数" width="110">
          <template #default="{ row }">
            <span>{{ row.hit_count ?? '-' }}</span>
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
        <el-table-column prop="status" label="处理状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.work_order_id ? 'success' : 'warning'">
              {{ row.work_order_id ? '已处理' : '未处理' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button
              v-if="!row.work_order_id"
              type="primary"
              size="small"
              @click="handleMark(row)"
            >
              处理
            </el-button>
            <el-tag v-else type="success" size="small">已处理</el-tag>
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
            <div>识别目标数: {{ previewItem?.hit_count ?? '-' }}</div>
            <div>飞机SN: {{ previewItem?.aircraft_sn }}</div>
            <div>告警时间: {{ formatTime(previewItem?.event_time) }}</div>
            <div v-if="previewItem?.latitude && previewItem?.longitude">
              经度: {{ previewItem?.longitude }} 纬度: {{ previewItem?.latitude }}
            </div>
          </div>
          <button
            v-if="previewItem && !previewItem.work_order_id"
            class="preview-handle-btn"
            @click.stop="handlePreviewMark"
          >
            <svg class="handle-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M20 6L9 17l-5-5"/>
            </svg>
            <span>标记处理</span>
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
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElLoading } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { request } from '@/api/request'

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
  image_width: number
  image_height: number
  local_image_name: string
  clean_status: number
  work_order_id: number | null
  status: string
  data_source: string
}

interface DeviceInfo {
  sn: string
  deviceName: string
}

const tableData = ref<AlarmData[]>([])
const deviceMap = ref<Map<string, string>>(new Map())

const deviceOptions = computed(() => {
  const options: Record<string, string> = {}
  deviceMap.value.forEach((name, sn) => {
    if (name && name.trim()) {
      options[sn] = name
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

const getDeviceName = (sn: string | undefined): string => {
  return deviceMap.value.get(sn || '') || ''
}

interface PageResult {
  list: AlarmData[]
  total: number
  page: number
  size: number
}

const loadData = async () => {
  try {
    const loading = ElLoading.service({ text: '加载照片告警数据...' })
    await loadDevices()
    const params = {
      page: currentPage.value,
      size: pageSize.value,
      status: searchStatus.value || undefined,
      aircraft_sn: searchSn.value || undefined,
      rule_name: searchRuleName.value || undefined,
      start_time: searchStartTime.value || undefined,
      end_time: searchEndTime.value || undefined
    }
    const data = await request.get<PageResult>('/zhimou/image-results/page', { params })
    tableData.value = data?.list || []
    total.value = data?.total || 0
    imageItems.value = tableData.value.filter(item => item.image_http_url)
    const rules = new Set(tableData.value.map(item => item.rule_name).filter(Boolean))
    ruleList.value = Array.from(rules)
    loading.close()
  } catch (e) {
    console.error('加载照片告警数据失败', e)
    ElMessage.error('加载照片告警数据失败')
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

const handleMark = async (row: AlarmData) => {
  try {
    await request.put(`/zhimou/image-results/${row.id}/handle`)
    row.work_order_id = Date.now()
    ElMessage.success('已标记处理')
  } catch (e) {
    console.error('标记处理失败', e)
    ElMessage.error('标记处理失败')
  }
}

const handlePreviewMark = async () => {
  if (previewItem.value) {
    await handleMark(previewItem.value)
  }
}

const applyPreviewItem = (item: AlarmData) => {
  previewItem.value = item
  previewImage.value = item.image_http_url
}

const openImage = (row: AlarmData) => {
  applyPreviewItem(row)
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
    applyPreviewItem(imageItems.value[currentIndex.value])
    resetZoom()
  }
}

const nextImage = () => {
  if (currentIndex.value < imageItems.value.length - 1) {
    currentIndex.value++
    applyPreviewItem(imageItems.value[currentIndex.value])
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

onMounted(() => {
  loadData()
  window.addEventListener('mousemove', handleMouseMove)
  window.addEventListener('mouseup', handleMouseUp)
})

onUnmounted(() => {
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

.preview-handle-btn {
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

.preview-handle-btn:hover {
  background: linear-gradient(135deg, #1e293b 0%, #334155 50%, #38bdf8 100%);
  transform: translateY(-3px) scale(1.02);
  box-shadow:
    0 0 40px rgba(56, 189, 248, 0.4),
    0 8px 25px rgba(0, 0, 0, 0.5),
    inset 0 1px 0 rgba(255, 255, 255, 0.2);
  border-color: rgba(56, 189, 248, 0.6);
}

.preview-handle-btn:active {
  transform: translateY(-1px) scale(0.98);
}

.handle-icon {
  width: 18px;
  height: 18px;
  filter: drop-shadow(0 0 6px rgba(56, 189, 248, 0.6));
}

.preview-handle-btn:hover .handle-icon {
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
</style>
