<template>
  <div class="page-container">
    <el-card class="page-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">工单管理</span>
          <div class="card-actions">
            <el-button type="primary" @click="showRuleDialog = true">
              <el-icon><Setting /></el-icon>
              工单规则
            </el-button>
            <el-button @click="loadData">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </div>
      </template>
      <div class="search-bar">
        <el-select v-model="searchStatus" placeholder="工单状态" clearable class="search-item">
          <el-option label="全部" value="" />
          <el-option label="待处理" value="PENDING" />
          <el-option label="处理中" value="PROCESSING" />
          <el-option label="已提交" value="SUBMITTED" />
          <el-option label="已完成" value="COMPLETED" />
          <el-option label="已驳回" value="REJECTED" />
        </el-select>
        <el-input v-model="searchCode" placeholder="工单编号" clearable class="search-item" />
        <el-input v-model="searchAlarmType" placeholder="告警类型" clearable class="search-item" />
        <el-input v-model="searchAircraftSn" placeholder="飞机SN" clearable class="search-item" />
        <el-button type="primary" @click="handleSearch">搜索</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>

      <el-table :data="tableData" stripe border style="width: 100%">
        <el-table-column prop="id" label="序号" width="80" />
        <el-table-column prop="code" label="工单编号" min-width="150" />
        <el-table-column prop="alarm_type" label="告警类型" min-width="120" />
        <el-table-column prop="aircraft_sn" label="飞机SN" min-width="150" />
        <el-table-column prop="aircraft_name" label="飞机名称" min-width="120" />
        <el-table-column label="告警图片" width="120">
          <template #default="{ row }">
            <el-image
              v-if="row.alarm_image_url"
              :src="row.alarm_image_url"
              class="image-thumbnail"
              fit="cover"
              @click="openImage(row)"
            />
            <span v-else class="no-image">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="wayline_name" label="航线名称" min-width="150" />
        <el-table-column prop="handler_name" label="处理人" min-width="100" />
        <el-table-column prop="status" label="工单状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">
              {{ row.status_text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="create_time" label="创建时间" width="180" />
        <el-table-column label="操作" width="300">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="showDetail(row)">详情</el-button>
            <el-button
              v-if="row.status === 'SUBMITTED'"
              type="success"
              size="small"
              @click="handleComplete(row)"
            >完成</el-button>
            <el-button
              v-if="row.status === 'SUBMITTED'"
              type="danger"
              size="small"
              @click="openRejectDialog(row)"
            >驳回</el-button>
            <el-button
              type="danger"
              size="small"
              @click="handleDelete(row)"
            >删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <el-dialog v-model="showDetailDialog" title="工单详情" width="700px" append-to-body>
      <div v-if="detailData" class="detail-content">
        <div class="detail-row">
          <span class="detail-label">工单编号：</span>
          <span class="detail-value">{{ detailData.code }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">告警类型：</span>
          <span class="detail-value">{{ detailData.alarm_type || '-' }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">飞机SN：</span>
          <span class="detail-value">{{ detailData.aircraft_sn || '-' }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">飞机名称：</span>
          <span class="detail-value">{{ detailData.aircraft_name || '-' }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">航线名称：</span>
          <span class="detail-value">{{ detailData.wayline_name || '-' }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">处理人：</span>
          <span class="detail-value">{{ detailData.handler_name || '-' }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">处理状态：</span>
          <el-tag :type="getStatusType(detailData.status)">{{ detailData.status_text }}</el-tag>
        </div>
        <div class="detail-row">
          <span class="detail-label">告警时间：</span>
          <span class="detail-value">{{ detailData.event_time || '-' }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">创建时间：</span>
          <span class="detail-value">{{ detailData.create_time || '-' }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">提交时间：</span>
          <span class="detail-value">{{ detailData.submit_time || '-' }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">完成时间：</span>
          <span class="detail-value">{{ detailData.complete_time || '-' }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">驳回时间：</span>
          <span class="detail-value">{{ detailData.reject_time || '-' }}</span>
        </div>

        <div class="detail-section">
          <h4>告警图片</h4>
          <el-image
            v-if="detailData.alarm_image_url"
            :src="detailData.alarm_image_url"
            class="detail-image"
            fit="contain"
            @click="openDetailImage(detailData.alarm_image_url)"
          />
          <span v-else>-</span>
        </div>

        <div class="detail-section">
          <h4>处理图片</h4>
          <div v-if="detailData.handler_images && detailData.handler_images.length > 0" class="image-grid">
            <el-image
              v-for="(img, index) in detailData.handler_images"
              :key="index"
              :src="img"
              class="handler-image"
              fit="cover"
              @click="openDetailImage(img)"
            />
          </div>
          <span v-else>-</span>
        </div>

        <div class="detail-section">
          <h4>处理意见</h4>
          <p class="detail-text">{{ detailData.handle_comment || '-' }}</p>
        </div>

        <div class="detail-section">
          <h4>驳回原因</h4>
          <p class="detail-text">{{ detailData.reject_reason || '-' }}</p>
        </div>
      </div>
    </el-dialog>

    <el-dialog v-model="showRejectDialog" title="驳回工单" width="500px" append-to-body>
      <div class="reject-form">
        <div class="form-item">
          <label class="form-label">驳回原因</label>
          <textarea
            v-model="rejectForm.reject_reason"
            rows="4"
            placeholder="请输入驳回原因"
            class="reject-textarea"
          ></textarea>
        </div>
        <div class="form-item">
          <label class="form-label">抄送邮箱</label>
          <el-select v-model="rejectForm.cc_email" placeholder="请选择抄送邮箱（可选）" clearable class="email-select">
            <el-option v-for="user in userList" :key="user.id" :label="user.email" :value="user.email" />
          </el-select>
        </div>
      </div>
      <template #footer>
        <el-button @click="showRejectDialog = false">取消</el-button>
        <el-button type="danger" @click="handleReject">确认驳回</el-button>
      </template>
    </el-dialog>

    <div v-if="showPreview" class="preview-overlay">
      <div class="preview-content">
        <div class="preview-header">
          <span class="preview-counter">{{ currentIndex + 1 }} / {{ imageItems.length }}</span>
          <button class="preview-close" @click="closePreview">✕</button>
        </div>
        <div
          class="preview-img-wrapper"
          @mousedown="startDrag"
          @mousemove="onDrag"
          @mouseup="endDrag"
          @mouseleave="endDrag"
          @wheel.prevent="onWheel"
        >
          <img
            class="preview-img"
            :src="previewImage"
            :style="{
              transform: `scale(${scale}) translate(${translateX}px, ${translateY}px)`
            }"
            alt="预览图片"
          />
        </div>
        <button
          class="preview-prev"
          @click="prevImage"
          :disabled="currentIndex <= 0"
        >◀</button>
        <button
          class="preview-next"
          @click="nextImage"
          :disabled="currentIndex >= imageItems.length - 1"
        >▶</button>
        <div class="preview-zoom-controls">
          <button class="zoom-btn" @click="zoomOut">-</button>
          <span class="zoom-text">{{ Math.round(scale * 100) }}%</span>
          <button class="zoom-btn" @click="zoomIn">+</button>
        </div>
      </div>
    </div>

    <div v-if="showDetailPreview" class="preview-overlay" @click.self="closeDetailPreview">
      <div class="preview-content">
        <div class="preview-header">
          <span class="preview-counter">{{ detailCurrentIndex + 1 }} / {{ detailImageList.length }}</span>
          <button class="preview-close" @click="closeDetailPreview">✕</button>
        </div>
        <div
          class="preview-img-wrapper"
          @mousedown="startDetailDrag"
          @mousemove="onDetailDrag"
          @mouseup="endDetailDrag"
          @mouseleave="endDetailDrag"
          @wheel.prevent="onDetailWheel"
        >
          <img
            class="preview-img"
            :src="detailPreviewImage"
            :style="{
              transform: `scale(${detailScale}) translate(${detailTranslateX}px, ${detailTranslateY}px)`
            }"
            alt="预览图片"
          />
        </div>
        <button
          class="preview-prev"
          @click="detailPrevImage"
          :disabled="detailCurrentIndex <= 0"
        >◀</button>
        <button
          class="preview-next"
          @click="detailNextImage"
          :disabled="detailCurrentIndex >= detailImageList.length - 1"
        >▶</button>
        <div class="preview-zoom-controls">
          <button class="zoom-btn" @click="detailZoomOut">-</button>
          <span class="zoom-text">{{ Math.round(detailScale * 100) }}%</span>
          <button class="zoom-btn" @click="detailZoomIn">+</button>
        </div>
        <div v-if="detailData && detailData.status === 'SUBMITTED'" class="preview-reject-section">
          <div class="reject-form-item">
            <label class="reject-label">驳回原因</label>
            <textarea
              v-model="detailRejectReason"
              class="reject-textarea"
              placeholder="请输入驳回原因"
              rows="3"
            ></textarea>
          </div>
          <div class="reject-form-item">
            <label class="reject-label">抄送邮箱</label>
            <el-select v-model="detailCcEmail" placeholder="请选择抄送邮箱（可选）" clearable class="email-select" popper-class="cc-email-popper">
              <el-option v-for="user in userList" :key="user.id" :label="user.email" :value="user.email" />
            </el-select>
          </div>
          <div class="reject-complete-btns">
            <button class="reject-btn" @click="handleDetailReject">驳回工单</button>
            <button class="complete-btn" @click="handleDetailComplete">完成工单</button>
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="showRuleDialog" title="工单发送规则配置" width="1100px" append-to-body>
      <div class="rule-toolbar">
        <el-button type="primary" @click="addRule">新增规则</el-button>
      </div>
      <el-table :data="ruleList" border style="width: 100%" class="rule-table">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="alarmType" label="告警类型" width="150">
          <template #default="{ row }">
            <span>{{ row.alarmType || '全部' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="告警时间" width="180">
          <template #default="{ row }">
            <span>{{ row.startTime || '00:00' }} - {{ row.endTime || '23:59' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="aircraftSn" label="飞机SN" width="150">
          <template #default="{ row }">
            <span>{{ row.aircraftSn || '全部' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="handlerName" label="处理人" width="120" />
        <el-table-column prop="handlerEmail" label="邮箱" width="150" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button size="small" @click="editRule(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="deleteRule(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-dialog v-model="showRuleFormDialog" :title="ruleForm.id ? '编辑规则' : '新增规则'" width="500px">
        <el-form :model="ruleForm" label-width="100px">
          <el-form-item label="告警类型">
            <el-input v-model="ruleForm.alarmType" placeholder="为空则匹配所有告警类型" />
          </el-form-item>
          <el-form-item label="开始时间">
            <el-time-select v-model="ruleForm.startTime" :options="timeOptions" placeholder="开始时间" />
          </el-form-item>
          <el-form-item label="结束时间">
            <el-time-select v-model="ruleForm.endTime" :options="timeOptions" placeholder="结束时间" />
          </el-form-item>
          <el-form-item label="飞机SN">
            <el-input v-model="ruleForm.aircraftSn" placeholder="为空则匹配所有飞机" />
          </el-form-item>
          <el-form-item label="处理人">
            <el-select v-model="ruleForm.handlerUserId" placeholder="请选择处理人">
              <el-option v-for="user in userList" :key="user.id" :label="user.nickname" :value="user.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="处理人邮箱">
            <el-input v-model="ruleForm.handlerEmail" readonly />
          </el-form-item>
          <el-form-item label="状态">
            <el-switch v-model="ruleForm.status" :active-value="1" :inactive-value="0" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="showRuleFormDialog = false">取消</el-button>
          <el-button type="primary" @click="saveRule">保存</el-button>
        </template>
      </el-dialog>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElLoading, ElMessageBox } from 'element-plus'
import { Refresh, Setting } from '@element-plus/icons-vue'
import { request } from '@/api/request'

interface WorkOrderData {
  id: number
  code: string
  alarm_id: number
  alarm_type: string
  aircraft_sn: string
  aircraft_name: string
  wayline_uuid: string
  wayline_name: string
  alarm_image_url: string
  handler_email: string
  handler_name: string
  status: string
  status_text: string
  handle_comment: string
  reject_reason: string
  latitude: number
  longitude: number
  event_time: string
  submit_time: string
  complete_time: string
  reject_time: string
  create_time: string
  handler_images: string[]
}

const tableData = ref<WorkOrderData[]>([])
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const searchStatus = ref('')
const searchCode = ref('')
const searchAlarmType = ref('')
const searchAircraftSn = ref('')

const showDetailDialog = ref(false)
const detailData = ref<WorkOrderData | null>(null)

const showRejectDialog = ref(false)
const rejectForm = ref({
  reject_reason: '',
  cc_email: ''
})
const rejectRow = ref<WorkOrderData | null>(null)

const showPreview = ref(false)
const previewImage = ref('')
const previewItem = ref<WorkOrderData | null>(null)
const currentIndex = ref(0)
const imageItems = ref<WorkOrderData[]>([])

const scale = ref(1)
const translateX = ref(0)
const translateY = ref(0)
const isDragging = ref(false)
const startX = ref(0)
const startY = ref(0)
const startTranslateX = ref(0)
const startTranslateY = ref(0)

const showDetailPreview = ref(false)
const detailPreviewImage = ref('')
const detailRejectReason = ref('')
const detailCcEmail = ref('')
const detailScale = ref(1)
const detailTranslateX = ref(0)
const detailTranslateY = ref(0)
const isDetailDragging = ref(false)
const detailStartX = ref(0)
const detailStartY = ref(0)
const detailStartTranslateX = ref(0)
const detailStartTranslateY = ref(0)
const detailImageList = ref<string[]>([])
const detailCurrentIndex = ref(0)

const getStatusType = (status: string): string => {
  switch (status) {
    case 'PENDING': return 'warning'
    case 'PROCESSING': return 'primary'
    case 'SUBMITTED': return 'info'
    case 'COMPLETED': return 'success'
    case 'REJECTED': return 'danger'
    default: return 'info'
  }
}

interface PageResult {
  list: WorkOrderData[]
  total: number
  page: number
  size: number
}

const loadData = async () => {
  try {
    const loading = ElLoading.service({ text: '加载工单数据...' })
    const params = {
      page: currentPage.value,
      size: pageSize.value,
      status: searchStatus.value || undefined,
      code: searchCode.value || undefined,
      alarmType: searchAlarmType.value || undefined,
      aircraftSn: searchAircraftSn.value || undefined
    }
    const res = await request.get<PageResult>('/workorder/list', { params })
    if (res && res.list) {
      tableData.value = res.list
      total.value = res.total
    }
    loading.close()
  } catch (e) {
    console.error('加载工单数据失败', e)
    ElMessage.error('加载工单数据失败')
  }
}

const handleSearch = () => {
  currentPage.value = 1
  loadData()
}

const resetSearch = () => {
  searchStatus.value = ''
  searchCode.value = ''
  searchAlarmType.value = ''
  searchAircraftSn.value = ''
  currentPage.value = 1
  loadData()
}

const showDetail = async (row: WorkOrderData) => {
  try {
    const res = await request.get<WorkOrderData>(`/workorder/${row.id}`)
    if (res) {
      detailData.value = res
      showDetailDialog.value = true
    }
  } catch (e) {
    console.error('查询工单详情失败', e)
    ElMessage.error('查询工单详情失败')
  }
}

const handleComplete = async (row: WorkOrderData) => {
  try {
    await request.post(`/workorder/${row.id}/complete`)
    ElMessage.success('工单已完成')
    loadData()
  } catch (e) {
    console.error('完成工单失败', e)
    ElMessage.error('完成工单失败')
  }
}

const openRejectDialog = (row: WorkOrderData) => {
  rejectRow.value = row
  rejectForm.value = {
    reject_reason: '',
    cc_email: ''
  }
  showRejectDialog.value = true
}

const handleReject = async () => {
  if (!rejectRow.value || !rejectForm.value.reject_reason.trim()) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  try {
    await request.post(`/workorder/${rejectRow.value.id}/reject`, {
      reject_reason: rejectForm.value.reject_reason,
      cc_email: rejectForm.value.cc_email || undefined
    })
    ElMessage.success('工单已驳回')
    showRejectDialog.value = false
    loadData()
  } catch (e) {
    console.error('驳回工单失败', e)
    ElMessage.error('驳回工单失败')
  }
}

const handleDelete = async (row: WorkOrderData) => {
  try {
    await ElMessageBox.confirm(`确定要删除工单 ${row.code} 吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await request.delete(`/workorder/${row.id}`)
    ElMessage.success('工单已删除')
    loadData()
  } catch (e) {
    if (e !== 'cancel') {
      console.error('删除工单失败', e)
      ElMessage.error('删除工单失败')
    }
  }
}

const openImage = (row: WorkOrderData) => {
  imageItems.value = tableData.value.filter(item => item.alarm_image_url)
  currentIndex.value = imageItems.value.findIndex(item => item.id === row.id)
  if (currentIndex.value === -1) currentIndex.value = 0
  updatePreview()
  showPreview.value = true
}

const updatePreview = () => {
  const item = imageItems.value[currentIndex.value]
  if (item) {
    previewImage.value = item.alarm_image_url
    previewItem.value = item
    scale.value = 1
    translateX.value = 0
    translateY.value = 0
  }
}

const closePreview = () => {
  showPreview.value = false
  scale.value = 1
  translateX.value = 0
  translateY.value = 0
}

const prevImage = () => {
  if (currentIndex.value > 0) {
    currentIndex.value--
    updatePreview()
  }
}

const nextImage = () => {
  if (currentIndex.value < imageItems.value.length - 1) {
    currentIndex.value++
    updatePreview()
  }
}

const zoomIn = () => {
  if (scale.value < 5) {
    scale.value += 0.2
  }
}

const zoomOut = () => {
  if (scale.value > 0.2) {
    scale.value -= 0.2
  }
}

const onWheel = (e: WheelEvent) => {
  const delta = e.deltaY > 0 ? -0.1 : 0.1
  if (scale.value + delta >= 0.2 && scale.value + delta <= 5) {
    scale.value += delta
  }
}

const startDrag = (e: MouseEvent) => {
  if (scale.value > 1) {
    isDragging.value = true
    startX.value = e.clientX
    startY.value = e.clientY
    startTranslateX.value = translateX.value
    startTranslateY.value = translateY.value
  }
}

const onDrag = (e: MouseEvent) => {
  if (isDragging.value) {
    translateX.value = startTranslateX.value + (e.clientX - startX.value)
    translateY.value = startTranslateY.value + (e.clientY - startY.value)
  }
}

const endDrag = () => {
  isDragging.value = false
}

const openDetailImage = (imageUrl: string) => {
  detailImageList.value = []
  if (detailData.value) {
    if (detailData.value.alarm_image_url) {
      detailImageList.value.push(detailData.value.alarm_image_url)
    }
    if (detailData.value.handler_images && detailData.value.handler_images.length > 0) {
      detailImageList.value.push(...detailData.value.handler_images)
    }
  }
  detailCurrentIndex.value = detailImageList.value.indexOf(imageUrl)
  if (detailCurrentIndex.value === -1) detailCurrentIndex.value = 0
  detailPreviewImage.value = detailImageList.value[detailCurrentIndex.value] || ''
  detailScale.value = 1
  detailTranslateX.value = 0
  detailTranslateY.value = 0
  detailRejectReason.value = ''
  showDetailDialog.value = false
  showDetailPreview.value = true
}

const closeDetailPreview = () => {
  showDetailPreview.value = false
  detailScale.value = 1
  detailTranslateX.value = 0
  detailTranslateY.value = 0
  detailRejectReason.value = ''
  detailCcEmail.value = ''
  detailImageList.value = []
  detailCurrentIndex.value = 0
}

const detailPrevImage = () => {
  if (detailCurrentIndex.value > 0) {
    detailCurrentIndex.value--
    detailPreviewImage.value = detailImageList.value[detailCurrentIndex.value]
    detailScale.value = 1
    detailTranslateX.value = 0
    detailTranslateY.value = 0
  }
}

const detailNextImage = () => {
  if (detailCurrentIndex.value < detailImageList.value.length - 1) {
    detailCurrentIndex.value++
    detailPreviewImage.value = detailImageList.value[detailCurrentIndex.value]
    detailScale.value = 1
    detailTranslateX.value = 0
    detailTranslateY.value = 0
  }
}

const detailZoomIn = () => {
  if (detailScale.value < 5) {
    detailScale.value += 0.2
  }
}

const detailZoomOut = () => {
  if (detailScale.value > 0.2) {
    detailScale.value -= 0.2
  }
}

const onDetailWheel = (e: WheelEvent) => {
  const delta = e.deltaY > 0 ? -0.1 : 0.1
  if (detailScale.value + delta >= 0.2 && detailScale.value + delta <= 5) {
    detailScale.value += delta
  }
}

const startDetailDrag = (e: MouseEvent) => {
  if (detailScale.value > 1) {
    isDetailDragging.value = true
    detailStartX.value = e.clientX
    detailStartY.value = e.clientY
    detailStartTranslateX.value = detailTranslateX.value
    detailStartTranslateY.value = detailTranslateY.value
  }
}

const onDetailDrag = (e: MouseEvent) => {
  if (isDetailDragging.value) {
    detailTranslateX.value = detailStartTranslateX.value + (e.clientX - detailStartX.value)
    detailTranslateY.value = detailStartTranslateY.value + (e.clientY - detailStartY.value)
  }
}

const endDetailDrag = () => {
  isDetailDragging.value = false
}

const handleDetailReject = async () => {
  if (!detailData.value || !detailRejectReason.value.trim()) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  try {
    await request.post(`/workorder/${detailData.value.id}/reject`, {
      reject_reason: detailRejectReason.value,
      cc_email: detailCcEmail.value || undefined
    })
    ElMessage.success('工单已驳回')
    closeDetailPreview()
    showDetailDialog.value = false
    loadData()
  } catch (e) {
    console.error('驳回工单失败', e)
    ElMessage.error('驳回工单失败')
  }
}

const handleDetailComplete = async () => {
  if (!detailData.value) {
    return
  }
  try {
    await request.post(`/workorder/${detailData.value.id}/complete`)
    ElMessage.success('工单已完成')
    closeDetailPreview()
    showDetailDialog.value = false
    loadData()
  } catch (e) {
    console.error('完成工单失败', e)
    ElMessage.error('完成工单失败')
  }
}

const showRuleDialog = ref(false)
const showRuleFormDialog = ref(false)
const ruleList = ref<any[]>([])
const userList = ref<any[]>([])

const timeOptions = [
  { label: '00:00', value: '00:00:00' },
  { label: '01:00', value: '01:00:00' },
  { label: '02:00', value: '02:00:00' },
  { label: '03:00', value: '03:00:00' },
  { label: '04:00', value: '04:00:00' },
  { label: '05:00', value: '05:00:00' },
  { label: '06:00', value: '06:00:00' },
  { label: '07:00', value: '07:00:00' },
  { label: '08:00', value: '08:00:00' },
  { label: '09:00', value: '09:00:00' },
  { label: '10:00', value: '10:00:00' },
  { label: '11:00', value: '11:00:00' },
  { label: '12:00', value: '12:00:00' },
  { label: '13:00', value: '13:00:00' },
  { label: '14:00', value: '14:00:00' },
  { label: '15:00', value: '15:00:00' },
  { label: '16:00', value: '16:00:00' },
  { label: '17:00', value: '17:00:00' },
  { label: '18:00', value: '18:00:00' },
  { label: '19:00', value: '19:00:00' },
  { label: '20:00', value: '20:00:00' },
  { label: '21:00', value: '21:00:00' },
  { label: '22:00', value: '22:00:00' },
  { label: '23:00', value: '23:00:00' },
]

const ruleForm = ref({
  id: null,
  alarmType: '',
  startTime: '00:00:00',
  endTime: '23:59:59',
  aircraftSn: '',
  handlerUserId: null,
  handlerName: '',
  handlerEmail: '',
  status: 1
})

const loadRuleList = async () => {
  try {
    const res = await request.get('/workorder/rule/list')
    if (res) {
      ruleList.value = res
    }
  } catch (e) {
    console.error('加载规则列表失败', e)
  }
}

const loadUserList = async () => {
  try {
    const res = await request.get('/user/all')
    if (res) {
      userList.value = res
    }
  } catch (e) {
    console.error('加载用户列表失败', e)
  }
}

const addRule = () => {
  ruleForm.value = {
    id: null,
    alarmType: '',
    startTime: '00:00:00',
    endTime: '23:59:59',
    aircraftSn: '',
    handlerUserId: null,
    handlerName: '',
    handlerEmail: '',
    status: 1
  }
  showRuleFormDialog.value = true
}

const editRule = (row: any) => {
  ruleForm.value = {
    id: row.id,
    alarmType: row.alarmType || '',
    startTime: row.startTime || '00:00:00',
    endTime: row.endTime || '23:59:59',
    aircraftSn: row.aircraftSn || '',
    handlerUserId: row.handlerUserId,
    handlerName: row.handlerName || '',
    handlerEmail: row.handlerEmail || '',
    status: row.status
  }
  showRuleFormDialog.value = true
}

const deleteRule = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确定要删除规则 ID=${row.id} 吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await request.delete(`/workorder/rule/${row.id}`)
    ElMessage.success('删除成功')
    loadRuleList()
  } catch (e) {
    if (e !== 'cancel') {
      console.error('删除规则失败', e)
      ElMessage.error('删除规则失败')
    }
  }
}

const saveRule = async () => {
  if (!ruleForm.value.handlerUserId) {
    ElMessage.warning('请选择处理人')
    return
  }

  const user = userList.value.find(u => u.id === ruleForm.value.handlerUserId)
  if (user) {
    ruleForm.value.handlerName = user.nickname
    ruleForm.value.handlerEmail = user.email
  }

  try {
    if (ruleForm.value.id) {
      await request.put(`/workorder/rule/${ruleForm.value.id}`, ruleForm.value)
      ElMessage.success('更新成功')
    } else {
      await request.post('/workorder/rule', ruleForm.value)
      ElMessage.success('创建成功')
    }
    showRuleFormDialog.value = false
    loadRuleList()
  } catch (e) {
    console.error('保存规则失败', e)
    ElMessage.error('保存规则失败')
  }
}

onMounted(() => {
  loadData()
  loadRuleList()
  loadUserList()
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

.search-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.search-item {
  width: 150px;
}

.image-thumbnail {
  width: 100px;
  height: 80px;
  cursor: pointer;
  border-radius: 4px;
}

.no-image {
  color: #909399;
  font-size: 14px;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

.detail-content {
  padding: 10px 0;
}

.detail-row {
  display: flex;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}

.detail-label {
  font-weight: 600;
  color: #606266;
  width: 120px;
}

.detail-value {
  color: #303133;
}

.detail-section {
  margin-top: 20px;
}

.detail-section h4 {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 10px;
}

.detail-image {
  max-width: 100%;
  max-height: 300px;
  border-radius: 4px;
}

.image-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.handler-image {
  width: 150px;
  height: 120px;
  border-radius: 4px;
}

.detail-text {
  color: #303133;
  line-height: 1.6;
  white-space: pre-wrap;
}

.preview-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  z-index: 1000;
  cursor: pointer;
}

.preview-content {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: default;
}

.preview-header {
  position: absolute;
  top: -40px;
  left: 0;
  right: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.preview-counter {
  color: #fff;
  font-size: 14px;
}

.preview-close {
  background: rgba(255, 255, 255, 0.2);
  border: none;
  color: #fff;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  cursor: pointer;
  font-size: 16px;
  display: flex;
  justify-content: center;
  align-items: center;
}

.preview-close:hover {
  background: rgba(255, 255, 255, 0.3);
}

.preview-img-wrapper {
  display: inline-block;
  overflow: hidden;
  border-radius: 8px;
  border: 2px solid rgba(255, 255, 255, 0.5);
}

.preview-img {
  max-width: 800px;
  max-height: 600px;
  cursor: grab;
}

.preview-img:active {
  cursor: grabbing;
}

.preview-prev,
.preview-next {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  background: rgba(0, 0, 0, 0.5);
  border: none;
  color: #fff;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  cursor: pointer;
  font-size: 18px;
  display: flex;
  justify-content: center;
  align-items: center;
}

.preview-prev {
  left: -50px;
}

.preview-next {
  right: -50px;
}

.preview-prev:hover,
.preview-next:hover {
  background: rgba(0, 0, 0, 0.7);
}

.preview-prev:disabled,
.preview-next:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.preview-zoom-controls {
  position: absolute;
  bottom: -40px;
  right: 0;
  display: flex;
  align-items: center;
  gap: 10px;
}

.zoom-btn {
  background: rgba(255, 255, 255, 0.2);
  border: none;
  color: #fff;
  width: 30px;
  height: 30px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 16px;
}

.zoom-btn:hover {
  background: rgba(255, 255, 255, 0.3);
}

.zoom-text {
  color: #fff;
  font-size: 14px;
  min-width: 50px;
  text-align: center;
}

.preview-reject-section {
  display: flex;
  flex-direction: column;
  gap: 15px;
  align-items: center;
  padding: 20px;
  background: rgba(0, 0, 0, 0.8);
  border-radius: 8px;
  margin-top: 20px;
}

.reject-form-item {
  width: 100%;
  max-width: 450px;
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.reject-label {
  display: inline-block;
  width: 80px;
  text-align: right;
  padding-top: 8px;
  color: #fff;
  font-size: 14px;
  flex-shrink: 0;
}

.reject-textarea {
  flex: 1;
  padding: 10px;
  border-radius: 6px;
  border: 1px solid #ddd;
  font-size: 14px;
  resize: none;
  box-sizing: border-box;
}

.email-select {
  flex: 1;
  z-index: 10000;
}

.detail-reject-form {
  width: 100%;
  max-width: 500px;
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.detail-reject-form .reject-form-item {
  display: flex;
  align-items: flex-start;
  gap: 15px;
}

.detail-reject-form .reject-label {
  width: 80px;
  text-align: right;
  padding-top: 8px;
  color: #333;
  font-size: 14px;
  flex-shrink: 0;
}

.detail-reject-form .reject-textarea {
  flex: 1;
  padding: 10px;
  border-radius: 6px;
  border: 1px solid #ddd;
  font-size: 14px;
  resize: none;
  box-sizing: border-box;
}

.detail-reject-form .email-select {
  flex: 1;
}

.detail-reject-form .reject-complete-btns {
  justify-content: center;
}

.reject-complete-btns {
  display: flex;
  gap: 15px;
}

.reject-btn {
  background: #f56c6c;
  border: none;
  color: #fff;
  padding: 10px 30px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
}

.reject-btn:hover {
  background: #e65c5c;
}

.complete-btn {
  background: #67c23a;
  border: none;
  color: #fff;
  padding: 10px 30px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
}

.complete-btn:hover {
  background: #5eb838;
}

.rule-toolbar {
  margin-bottom: 15px;
}

.rule-table {
  font-size: 13px;
}

.reject-form {
  padding: 10px 0;
}

.form-item {
  margin-bottom: 20px;
}

.form-label {
  display: inline-block;
  width: 100px;
  text-align: right;
  margin-right: 15px;
  font-weight: 600;
  color: #606266;
}

.reject-textarea {
  width: calc(100% - 120px);
  padding: 10px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 14px;
  resize: vertical;
  box-sizing: border-box;
}

.reject-textarea:focus {
  outline: none;
  border-color: #409eff;
}

.email-select {
  width: calc(100% - 120px);
}
</style>

<style lang="less">
.cc-email-popper {
  z-index: 99999 !important;
}
</style>