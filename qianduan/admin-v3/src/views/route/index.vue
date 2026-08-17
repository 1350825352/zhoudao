<template>
  <div class="page-container">
    <el-card class="page-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">航线管理</span>
          <div class="card-actions">
            <el-button type="primary" @click="handleSync">
              <el-icon><Download /></el-icon>
              同步航线
            </el-button>
            <el-button @click="loadData">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </div>
      </template>
      <el-table :data="tableData" stripe border style="width: 100%">
        <el-table-column prop="id" label="序号" width="80" />
        <el-table-column prop="waylineName" label="航线名称" min-width="180" />
        <el-table-column prop="waylineUuid" label="航线UUID" min-width="300" />
        <el-table-column prop="projectName" label="所属项目" min-width="150" />
        <el-table-column 
          prop="totalDistance" 
          label="总里程(m)" 
          width="120"
          :formatter="formatDistance"
        />
        <el-table-column prop="waypointCount" label="航点数" width="100" />
        <el-table-column prop="height" label="高度(m)" width="100" />
        <el-table-column 
          prop="createTime" 
          label="创建时间" 
          width="160"
          :formatter="formatTime"
        />
        <el-table-column 
          prop="updateTime" 
          label="更新时间" 
          width="160"
          :formatter="formatTime"
        />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox, ElLoading } from 'element-plus'
import { Download, Refresh } from '@element-plus/icons-vue'
import { request } from '@/api/request'
import JSZip from 'jszip'

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

interface Wayline {
  id: number
  waylineUuid: string
  waylineName: string
  projectUuid: string
  projectName: string
  waylineType: number
  templateType: number
  totalDistance: number
  estimatedTime: number
  waypointCount: number
  height: number
  speed: number
  actionCount: number
  syncSource: number
  wpmlDownloadUrl: string
  createTime: string
  updateTime: string
}

const tableData = ref<Wayline[]>([])

const LOCAL_STORAGE_KEY = 'uav_route_data'

const loadData = async () => {
  try {
    const data = await request.get<Wayline[]>('/wayline/list')
    tableData.value = data || []
  } catch (e) {
    console.error('加载航线数据失败', e)
    ElMessage.error('加载航线数据失败')
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

const parseWaylineFile = async (downloadUrl: string): Promise<Waypoint[]> => {
  const isKmz = downloadUrl.toLowerCase().endsWith('.kmz')
  try {
    console.log(`[Route] 开始下载航线文件: ${downloadUrl}`)
    
    const proxyUrl = '/api/wayline/download'
    console.log(`[Route] 使用后端代理(POST): ${proxyUrl}`)
    
    const response = await fetch(proxyUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ url: downloadUrl })
    })
    if (!response.ok) {
      console.error(`[Route] 下载失败，HTTP状态码: ${response.status}`)
      return []
    }
    console.log(`[Route] 下载成功，文件类型: ${isKmz ? 'KMZ' : '其他'}`)
    
    if (isKmz) {
      const arrayBuffer = await response.arrayBuffer()
      console.log(`[Route] KMZ文件大小: ${arrayBuffer.byteLength} bytes`)
      const kmlContent = await parseKmzFile(arrayBuffer)
      if (kmlContent) {
        console.log(`[Route] KMZ解压成功，KML内容长度: ${kmlContent.length}`)
        return parseKmlContent(kmlContent)
      } else {
        console.error('[Route] KMZ解压失败，未找到KML文件')
      }
    } else {
      const content = await response.text()
      try {
        const jsonData = JSON.parse(content)
        const waypoints: Waypoint[] = []
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
          console.log(`[Route] JSON解析成功，航点数: ${waypoints.length}`)
          return waypoints
        } else {
          console.log('[Route] JSON无waypoints数组，尝试KML解析')
          return parseKmlContent(content)
        }
      } catch {
        console.log('[Route] JSON解析失败，尝试KML解析')
        return parseKmlContent(content)
      }
    }
  } catch (e) {
    console.error('[Route] 下载或解析航线文件失败', e)
  }
  return []
}

const saveRouteToLocal = (route: RouteData) => {
  try {
    const existingData = localStorage.getItem(LOCAL_STORAGE_KEY)
    let routes: RouteData[] = existingData ? JSON.parse(existingData) : []
    const index = routes.findIndex(r => r.waylineUuid === route.waylineUuid)
    if (index >= 0) {
      routes[index] = route
    } else {
      routes.push(route)
    }
    localStorage.setItem(LOCAL_STORAGE_KEY, JSON.stringify(routes))
    console.log(`[Route] 已保存航线: ${route.waylineName}, 航点数: ${route.waypoints.length}`)
  } catch (e) {
    console.error('保存航线数据到本地失败', e)
  }
}

const downloadWaylineData = async (wayline: Wayline): Promise<number> => {
  if (!wayline.wpmlDownloadUrl) {
    console.log(`[Route] 航线 ${wayline.waylineName} 没有下载链接`)
    return 0
  }
  try {
    console.log(`[Route] 开始处理航线: ${wayline.waylineName}, UUID: ${wayline.waylineUuid}`)
    const waypoints = await parseWaylineFile(wayline.wpmlDownloadUrl)
    if (waypoints.length > 0) {
      console.log(`[Route] 解析到 ${waypoints.length} 个航点`)
      
      const heights = waypoints.filter(wp => wp.alt !== undefined && wp.alt !== null).map(wp => wp.alt!)
      const avgHeight = heights.length > 0 ? heights.reduce((a, b) => a + b, 0) / heights.length : 0
      
      const targetRow = tableData.value.find(r => r.waylineUuid === wayline.waylineUuid)
      if (targetRow) {
        targetRow.waypointCount = waypoints.length
        targetRow.height = avgHeight
        console.log(`[Route] 更新表格数据: 航点数=${waypoints.length}, 高度=${avgHeight.toFixed(1)}m`)
      }
      
      saveRouteToLocal({
        waylineUuid: wayline.waylineUuid,
        waylineName: wayline.waylineName,
        waypoints
      })
      console.log(`[Route] 航点已保存到本地存储`)
      return waypoints.length
    } else {
      console.log(`[Route] 未解析到航点数据`)
    }
  } catch (e) {
    console.error(`下载航线 ${wayline.waylineName} 失败`, e)
  }
  return 0
}

const handleSync = async () => {
  try {
    ElMessage.info('正在同步航线数据...')
    await request.post('/wayline/sync')
    await loadData()
    
    const loading = ElLoading.service({ text: '正在下载航点数据...' })
    let successCount = 0
    let failCount = 0
    
    for (const wayline of tableData.value) {
      const count = await downloadWaylineData(wayline)
      if (count > 0) {
        successCount++
      } else {
        failCount++
      }
    }
    
    loading.close()
    ElMessage.success(`航线同步完成！成功下载 ${successCount} 条航线航点数据`)
    if (failCount > 0) {
      ElMessage.warning(`${failCount} 条航线下载失败`)
    }
  } catch (e) {
    console.error('同步航线失败', e)
    ElMessage.error('同步航线失败')
  }
}

const handleView = (row: Wayline) => {
  ElMessage.info(`查看航线: ${row.waylineName}`)
}

const handleDownloadWayline = async (row: Wayline) => {
  const loading = ElLoading.service({ text: `正在下载 ${row.waylineName} 航点数据...` })
  try {
    const count = await downloadWaylineData(row)
    loading.close()
    if (count > 0) {
      ElMessage.success(`成功下载 ${count} 个航点，已保存到本地存储`)
    } else {
      ElMessage.warning('该航线没有下载链接或未解析到航点数据')
    }
  } catch (e) {
    loading.close()
    ElMessage.error('下载失败')
  }
}

const handleDelete = async (row: Wayline) => {
  try {
    await ElMessageBox.confirm(`确定删除航线 "${row.waylineName}" 吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await request.delete(`/wayline/${row.id}`)
    
    const existingData = localStorage.getItem(LOCAL_STORAGE_KEY)
    if (existingData) {
      let routes: RouteData[] = JSON.parse(existingData)
      routes = routes.filter(r => r.waylineUuid !== row.waylineUuid)
      localStorage.setItem(LOCAL_STORAGE_KEY, JSON.stringify(routes))
    }
    
    ElMessage.success('删除成功')
    await loadData()
  } catch {}
}

const formatDistance = (row: Wayline) => {
  if (row.totalDistance === null || row.totalDistance === undefined) {
    return '-'
  }
  return row.totalDistance.toFixed(2)
}

const formatTime = (row: Wayline, column: any) => {
  const time = column.prop === 'createTime' ? row.createTime : row.updateTime
  if (!time) return '-'
  return time.replace('T', ' ')
}

onMounted(() => {
  loadData()
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
</style>
