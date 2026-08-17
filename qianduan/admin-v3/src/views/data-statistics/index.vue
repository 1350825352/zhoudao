<template>
  <div class="page-container">
    <el-card class="page-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">数据统计</span>
          <div class="card-actions">
            <el-button @click="loadData">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
            <el-button type="success" :loading="exporting" @click="handleExport">
              <el-icon><Download /></el-icon>
              导出Excel
            </el-button>
          </div>
        </div>
      </template>

      <div class="summary-bar" v-if="total > 0">
        <span class="summary-item">目标总数(去重): <b>{{ total }}</b></span>
        <span class="summary-item">识别总次数: <b>{{ totalCount }}</b></span>
        <span class="summary-item">重复识别: <b>{{ totalCount - total }}</b></span>
      </div>

      <div class="search-bar">
        <el-input v-model="searchKeyword" placeholder="目标ID/名称" clearable class="search-item" />
        <el-select v-model="searchTargetName" placeholder="目标名称" clearable class="search-item" filterable>
          <el-option label="全部" value="" />
          <el-option v-for="name in targetNameList" :key="name" :label="name" :value="name" />
        </el-select>
        <el-select v-model="searchTargetColor" placeholder="目标颜色" clearable class="search-item">
          <el-option label="全部" value="" />
          <el-option label="无" value="无" />
          <el-option v-for="color in targetColorList" :key="color" :label="color" :value="color" />
        </el-select>
        <el-select v-model="searchRuleName" placeholder="告警类型" clearable class="search-item" filterable>
          <el-option label="全部" value="" />
          <el-option v-for="rule in ruleList" :key="rule" :label="rule" :value="rule" />
        </el-select>
        <el-select v-model="searchDeviceName" placeholder="飞机名称" clearable class="search-item" filterable>
          <el-option label="全部" value="" />
          <el-option v-for="(name, sn) in deviceOptions" :key="sn" :label="name" :value="name" />
        </el-select>
        <el-select v-model="searchRouteName" placeholder="航线名称" clearable class="search-item" filterable>
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

      <el-table :data="tableData" stripe border style="width: 100%">
        <el-table-column prop="target_id" label="目标ID" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.target_id || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="target_name" label="目标名称" min-width="120">
          <template #default="{ row }">
            <span>{{ row.target_name || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="目标颜色" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.target_color" :color="getColorValue(row.target_color)" style="color: #fff; border: none;">
              {{ row.target_color }}
            </el-tag>
            <span v-else class="no-color">—</span>
          </template>
        </el-table-column>
        <el-table-column label="颜色中文" width="100">
          <template #default="{ row }">
            <span v-if="row.target_color">{{ getColorName(row.target_color) }}</span>
            <span v-else class="no-color">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="count" label="数量" width="80" align="center">
          <template #default="{ row }">
            <b style="color: #409eff;">{{ row.count }}</b>
          </template>
        </el-table-column>
        <el-table-column label="最高置信度" width="110" align="center">
          <template #default="{ row }">
            <span>{{ formatConfidence(row.max_confidence) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="rule_name" label="告警类型" min-width="120">
          <template #default="{ row }">
            <span>{{ row.rule_name || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="飞机名称" min-width="120">
          <template #default="{ row }">
            <span>{{ getDeviceName(row.aircraft_sn) || row.aircraft_sn || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="航线" min-width="140">
          <template #default="{ row }">
            <span>{{ getRouteName(row.wayline_uuid) || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="首次识别时间" width="170">
          <template #default="{ row }">
            <span>{{ formatTime(row.first_event_time) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="最近识别时间" width="170">
          <template #default="{ row }">
            <span>{{ formatTime(row.last_event_time) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="缩略图" width="120">
          <template #default="{ row }">
            <el-image
              v-if="row.image_http_url"
              :src="row.image_http_url"
              class="image-thumbnail"
              fit="cover"
              @click="openImage(row)"
            />
            <span v-else class="no-image">无图片</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.result_id"
              type="primary"
              size="small"
              @click="goToAlarm(row)"
            >
              查看告警
            </el-button>
            <span v-else class="no-action">-</span>
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
        <div class="preview-img-wrapper">
          <img :src="previewImage" alt="" class="preview-img" draggable="false" />
          <div class="preview-info">
            <div>目标ID: {{ previewItem?.target_id }}</div>
            <div>目标名称: {{ previewItem?.target_name || '-' }}</div>
            <div>目标颜色: {{ previewItem?.target_color || '无' }}</div>
            <div>识别次数: {{ previewItem?.count }}</div>
            <div>最近识别: {{ formatTime(previewItem?.last_event_time) }}</div>
          </div>
        </div>
        <button class="preview-close" @click="closePreview">✕</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElLoading } from 'element-plus'
import { Refresh, Download } from '@element-plus/icons-vue'
import { request } from '@/api/request'
import * as XLSX from 'xlsx'
import { saveAs } from 'file-saver'

interface StatisticsData {
  target_id: string
  target_name: string
  target_color: string
  count: number
  max_confidence: number
  rule_name: string
  aircraft_sn: string
  wayline_uuid: string
  first_event_time: string
  last_event_time: string
  result_id: number | null
  image_http_url: string
}

interface DeviceInfo { sn: string; deviceName: string }
interface RouteInfo { waylineUuid: string; waylineName: string }

const router = useRouter()
const tableData = ref<StatisticsData[]>([])
const deviceMap = ref<Map<string, string>>(new Map())
const routeMap = ref<Map<string, string>>(new Map())

const deviceOptions = computed(() => {
  const options: Record<string, string> = {}
  deviceMap.value.forEach((name, sn) => { if (name && name.trim()) options[sn] = name })
  return options
})

const routeOptions = computed(() => {
  const options: Record<string, string> = {}
  routeMap.value.forEach((name, uuid) => { if (name && name.trim()) options[uuid] = name })
  return options
})

const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const totalCount = ref(0)
const exporting = ref(false)

const searchKeyword = ref('')
const searchTargetName = ref('')
const searchTargetColor = ref('')
const searchRuleName = ref('')
const searchDeviceName = ref('')
const searchRouteName = ref('')
const searchStartTime = ref('')
const searchEndTime = ref('')

const ruleList = ref<string[]>([])
const targetNameList = ref<string[]>([])
const targetColorList = ref<string[]>([])

const showPreview = ref(false)
const previewImage = ref('')
const previewItem = ref<StatisticsData | null>(null)

// 颜色名 -> 实际颜色值映射
const colorMap: Record<string, string> = {
  red: '#f56c6c',
  green: '#67c23a',
  blue: '#409eff',
  yellow: '#e6a23c',
  orange: '#ff9900',
  purple: '#9b59b6',
  pink: '#ff69b4',
  black: '#303133',
  white: '#ffffff',
  gray: '#909399',
  grey: '#909399'
}
const getColorValue = (color: string): string => colorMap[color.toLowerCase()] || color

// 颜色值 -> 中文名映射(支持英文名 + 十六进制)
const colorNameMap: Record<string, string> = {
  red: '红色',
  green: '绿色',
  blue: '蓝色',
  yellow: '黄色',
  orange: '橙色',
  purple: '紫色',
  pink: '粉色',
  black: '黑色',
  white: '白色',
  gray: '灰色',
  grey: '灰色',
  brown: '棕色',
  cyan: '青色'
}
const hexColorNameMap: Record<string, string> = {
  '#f5222d': '红色',
  '#ef660b': '橙色',
  '#faad14': '黄色',
  '#52c41a': '绿色',
  '#1890ff': '蓝色',
  '#722ed1': '紫色',
  '#eb2f96': '粉色',
  '#000000': '黑色',
  '#ffffff': '白色',
  '#8c8c8c': '灰色',
  '#f56c6c': '红色',
  '#67c23a': '绿色',
  '#409eff': '蓝色',
  '#e6a23c': '黄色',
  '#ff9900': '橙色',
  '#9b59b6': '紫色',
  '#ff69b4': '粉色',
  '#303133': '黑色',
  '#909399': '灰色'
}
const getColorName = (color: string): string => {
  if (!color) return ''
  const lower = color.toLowerCase()
  if (colorNameMap[lower]) return colorNameMap[lower]
  if (hexColorNameMap[lower]) return hexColorNameMap[lower]
  return color
}

const loadDevices = async () => {
  try {
    const devices = await request.get<DeviceInfo[]>('/device/list', { params: { deviceType: 3 } })
    if (devices) deviceMap.value = new Map(devices.map(d => [d.sn, d.deviceName || '']))
  } catch (e) { console.warn('加载设备列表失败', e) }
}

const loadRoutes = async () => {
  try {
    const routes = await request.get<RouteInfo[]>('/wayline/list')
    if (routes) routeMap.value = new Map(routes.map(r => [r.waylineUuid, r.waylineName || '']))
  } catch (e) { console.warn('加载航线列表失败', e) }
}

const loadTargetNames = async () => {
  try {
    const list = await request.get<string[]>('/zhimou/statistics/target-names')
    targetNameList.value = list || []
  } catch (e) { console.warn('加载目标名称失败', e) }
}

const loadTargetColors = async () => {
  try {
    const list = await request.get<string[]>('/zhimou/statistics/target-colors')
    targetColorList.value = list || []
  } catch (e) { console.warn('加载目标颜色失败', e) }
}

const getDeviceName = (sn: string | undefined): string => deviceMap.value.get(sn || '') || ''
const getRouteName = (waylineUuid: string | undefined): string => routeMap.value.get(waylineUuid || '') || ''

interface PageResult { list: StatisticsData[]; total: number; total_count: number; page: number; size: number }

const loadData = async () => {
  try {
    const loading = ElLoading.service({ text: '加载数据统计...' })
    await Promise.all([loadDevices(), loadRoutes(), loadTargetNames(), loadTargetColors()])
    const params = {
      page: currentPage.value,
      size: pageSize.value,
      keyword: searchKeyword.value || undefined,
      target_name: searchTargetName.value || undefined,
      target_color: searchTargetColor.value || undefined,
      rule_name: searchRuleName.value || undefined,
      device_name: searchDeviceName.value || undefined,
      route_name: searchRouteName.value || undefined,
      start_time: searchStartTime.value || undefined,
      end_time: searchEndTime.value || undefined
    }
    const data = await request.get<PageResult>('/zhimou/statistics/page', { params })
    tableData.value = data?.list || []
    total.value = data?.total || 0
    totalCount.value = data?.total_count || 0
    const rules = new Set(tableData.value.map(item => item.rule_name).filter(Boolean))
    ruleList.value = Array.from(rules)
    loading.close()
  } catch (e) {
    console.error('加载数据统计失败', e)
    ElMessage.error('加载数据统计失败')
  }
}

const handleSearch = () => { currentPage.value = 1; loadData() }

const resetSearch = () => {
  searchKeyword.value = ''
  searchTargetName.value = ''
  searchTargetColor.value = ''
  searchRuleName.value = ''
  searchDeviceName.value = ''
  searchRouteName.value = ''
  searchStartTime.value = ''
  searchEndTime.value = ''
  currentPage.value = 1
  loadData()
}

const handleSizeChange = (size: number) => { pageSize.value = size; currentPage.value = 1; loadData() }
const handleCurrentChange = (page: number) => { currentPage.value = page; loadData() }

const formatTime = (time: string | undefined): string => {
  if (!time) return '-'
  return String(time).replace('T', ' ')
}

const formatConfidence = (val: number | undefined): string => {
  if (val === null || val === undefined) return '-'
  return (val * 100).toFixed(1) + '%'
}

const openImage = (row: StatisticsData) => {
  previewImage.value = row.image_http_url
  previewItem.value = row
  showPreview.value = true
}

const closePreview = () => {
  showPreview.value = false
  previewImage.value = ''
  previewItem.value = null
}

// 跳转告警管理并定位到对应记录
const goToAlarm = (row: StatisticsData) => {
  const query: Record<string, string> = {}
  if (row.result_id) query.result_id = String(row.result_id)
  if (row.aircraft_sn) query.aircraft_sn = row.aircraft_sn
  router.push({ path: '/alarm', query })
}

// 导出 Excel
const handleExport = async () => {
  if (exporting.value) return
  exporting.value = true
  try {
    const loading = ElLoading.service({ text: '正在导出...' })
    const params = {
      keyword: searchKeyword.value || undefined,
      target_name: searchTargetName.value || undefined,
      target_color: searchTargetColor.value || undefined,
      rule_name: searchRuleName.value || undefined,
      device_name: searchDeviceName.value || undefined,
      route_name: searchRouteName.value || undefined,
      start_time: searchStartTime.value || undefined,
      end_time: searchEndTime.value || undefined,
      max_size: 5000
    }
    const list = await request.get<any[]>('/zhimou/statistics/export', { params })
    loading.close()

    if (!list || list.length === 0) {
      ElMessage.warning('没有可导出的数据')
      exporting.value = false
      return
    }

    const exportData = list.map((item, idx) => ({
      '序号': idx + 1,
      '目标ID': item.target_id || '',
      '目标名称': item.target_name || '',
      '目标颜色': item.target_color || '无',
      '颜色中文': item.target_color ? getColorName(item.target_color) : '无',
      '数量': item.count || 0,
      '最高置信度': formatConfidence(item.max_confidence),
      '告警类型': item.rule_name || '',
      '飞机名称': getDeviceName(item.aircraft_sn) || item.aircraft_sn || '',
      '航线': getRouteName(item.wayline_uuid) || '',
      '首次识别时间': formatTime(item.first_event_time),
      '最近识别时间': formatTime(item.last_event_time),
      '图片URL': item.image_http_url || ''
    }))

    const ws = XLSX.utils.json_to_sheet(exportData)
    ws['!cols'] = [
      { wch: 6 }, { wch: 18 }, { wch: 14 }, { wch: 12 }, { wch: 10 }, { wch: 8 },
      { wch: 12 }, { wch: 14 }, { wch: 14 }, { wch: 18 }, { wch: 20 }, { wch: 20 }, { wch: 50 }
    ]
    const wb = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(wb, ws, '数据统计')
    const wbout = XLSX.write(wb, { bookType: 'xlsx', type: 'array' })
    const fileName = `数据统计_${new Date().toLocaleString('zh-CN').replace(/[\/\s:]/g, '-')}.xlsx`
    saveAs(new Blob([wbout], { type: 'application/octet-stream' }), fileName)
    ElMessage.success(`导出成功, 共 ${list.length} 条`)
  } catch (e) {
    console.error('导出失败', e)
    ElMessage.error('导出失败')
  } finally {
    exporting.value = false
  }
}

onMounted(() => { loadData() })
</script>

<style lang="less" scoped>
.page-container { width: 100%; }
.page-card { border-radius: 4px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.card-title { font-size: 16px; font-weight: 600; color: #303133; }
.card-actions { display: flex; gap: 10px; }

.summary-bar {
  display: flex;
  gap: 24px;
  padding: 8px 0 12px;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 12px;
}
.summary-item { font-size: 14px; color: #606266; }
.summary-item b { color: #409eff; margin-left: 4px; }

.search-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #ebeef5;
}
.search-item { width: 200px; }

.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }

.image-thumbnail { width: 80px; height: 60px; border-radius: 4px; cursor: pointer; }
.no-image, .no-color { color: #909399; font-size: 12px; }
.no-action { color: #909399; font-size: 12px; }

.preview-overlay {
  position: fixed; top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex; align-items: center; justify-content: center; z-index: 9999;
}
.preview-content { position: relative; display: inline-block; }
.preview-img-wrapper {
  position: relative; display: inline-block; overflow: hidden;
  cursor: grab; border: 2px solid rgba(255, 255, 255, 0.5); border-radius: 4px;
}
.preview-img {
  display: block; max-width: 90vw; max-height: 90vh;
  transition: transform 0.1s ease-out; user-select: none;
}
.preview-info {
  position: absolute; bottom: 10px; left: 10px;
  background: rgba(0, 0, 0, 0.75); color: #fff;
  padding: 8px 12px; border-radius: 4px; font-size: 12px; line-height: 1.6;
}
.preview-close {
  position: absolute; top: 15px; right: 15px;
  width: 40px; height: 40px;
  background: rgba(0, 0, 0, 0.6); border: none; border-radius: 50%;
  color: #fff; font-size: 20px; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
}
.preview-close:hover { background: rgba(255, 68, 68, 0.8); }
</style>
