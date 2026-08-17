<template>
  <div class="page-container">
    <div v-if="loading" class="loading-overlay">
      <div class="loading-content">
        <div class="loading-spinner"></div>
        <div class="loading-text">{{ loadingText }}</div>
      </div>
    </div>

    <div v-else-if="error" class="page-container">
      <div class="card">
        <div class="error-message" style="text-align: center; padding: 50px 20px;">
          {{ error }}
        </div>
      </div>
    </div>

    <div v-else-if="success" class="page-container">
      <div class="card">
        <div class="success-message">
          <div style="font-size: 48px; margin-bottom: 15px;">✓</div>
          <div style="font-size: 18px; font-weight: 600; margin-bottom: 10px;">提交成功</div>
          <div>您的处理意见已提交，请等待审核。</div>
        </div>
      </div>
    </div>

    <template v-else-if="workOrder">
      <div class="page-header">
        <button class="back-btn" @click="goBack">←</button>
        <div class="header-title">工单详情</div>
      </div>

      <div class="card">
        <div class="card-title">基本信息</div>
        <div class="info-row">
          <span class="info-label">工单编号</span>
          <span class="info-value">{{ workOrder.code }}</span>
        </div>
        <div class="info-row">
          <span class="info-label">告警类型</span>
          <span class="info-value">{{ workOrder.alarm_type }}</span>
        </div>
        <div class="info-row">
          <span class="info-label">飞机SN</span>
          <span class="info-value">{{ workOrder.aircraft_sn }}</span>
        </div>
        <div class="info-row">
          <span class="info-label">飞机名称</span>
          <span class="info-value">{{ workOrder.aircraft_name }}</span>
        </div>
        <div class="info-row">
          <span class="info-label">航线名称</span>
          <span class="info-value">{{ workOrder.wayline_name || '-' }}</span>
        </div>
        <div class="info-row">
          <span class="info-label">处理状态</span>
          <span :class="['status-tag', getStatusClass(workOrder.status)]">
            {{ workOrder.status_text }}
          </span>
        </div>
        <div class="info-row">
          <span class="info-label">告警时间</span>
          <span class="info-value">{{ formatTime(workOrder.event_time) }}</span>
        </div>
        <div class="info-row">
          <span class="info-label">创建时间</span>
          <span class="info-value">{{ formatTime(workOrder.create_time) }}</span>
        </div>
        <div v-if="workOrder.status === 'REJECTED' && workOrder.reject_reason" class="info-row">
          <span class="info-label">驳回原因</span>
          <span class="info-value reject-reason">{{ workOrder.reject_reason }}</span>
        </div>
      </div>

      <div class="card">
        <div class="card-title">告警图片</div>
        <div class="image-container">
          <img
            v-if="workOrder.alarm_image_url"
            :src="workOrder.alarm_image_url"
            class="image-item"
            alt="告警图片"
          />
          <span v-else>-</span>
        </div>
      </div>

      <div v-if="workOrder.handler_images && workOrder.handler_images.length > 0" class="card">
        <div class="card-title">处理图片</div>
        <div class="image-container">
          <img
            v-for="(img, index) in workOrder.handler_images"
            :key="index"
            :src="img"
            class="image-item"
            :alt="'处理图片' + (index + 1)"
          />
        </div>
      </div>

      <div v-if="workOrder.reject_reason" class="card">
        <div class="card-title">驳回原因</div>
        <p style="color: #f56c6c; line-height: 1.6;">{{ workOrder.reject_reason }}</p>
      </div>

      <div v-if="workOrder.handle_comment" class="card">
        <div class="card-title">处理意见</div>
        <p style="line-height: 1.6;">{{ workOrder.handle_comment }}</p>
      </div>

      <div v-if="canSubmit" class="card">
        <div class="card-title">处理区域</div>

        <div class="upload-section">
          <div class="comment-label">处理图片（{{ uploadedImages.length }}/3）</div>
          <div class="image-grid">
            <div
              v-for="(img, index) in uploadedImages"
              :key="index"
              class="image-wrapper"
              :style="{ width: 'calc(50% - 5px)' }"
            >
              <img :src="img" class="image-item" :alt="'上传图片' + (index + 1)" />
              <button class="delete-btn" @click="deleteImage(index)">✕</button>
            </div>
            <div v-if="uploadedImages.length < 3" class="upload-btn" @click="triggerUpload">
              <div class="upload-icon">📷</div>
              <div>拍照上传/照片上传</div>
            </div>
          </div>
          <input
            ref="fileInput"
            type="file"
            accept="image/*"
            multiple
            capture="environment"
            style="display: none;"
            @change="handleFileChange"
          />
        </div>

        <div class="comment-section">
          <div class="comment-label">处理意见</div>
          <textarea
            v-model="handleComment"
            class="comment-textarea"
            placeholder="请输入处理意见（必填）"
          ></textarea>
        </div>

        <div v-if="submitError" class="error-message">{{ submitError }}</div>
      </div>

      <button
        v-if="canSubmit"
        class="submit-btn"
        :disabled="!canSubmitNow"
        @click="handleSubmit"
      >
        提交处理结果
      </button>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getWorkOrderByToken, uploadHandlerImage, submitWorkOrder, type WorkOrderData } from '@/api/workorder'

const route = useRoute()

const token = ref('')
const workOrder = ref<WorkOrderData | null>(null)
const loading = ref(true)
const loadingText = ref('加载中...')
const error = ref('')
const success = ref(false)

const handleComment = ref('')
const uploadedImages = ref<string[]>([])
const submitError = ref('')

const fileInput = ref<HTMLInputElement | null>(null)

const canSubmit = computed(() => {
  return workOrder.value && (workOrder.value.status === 'PENDING' || workOrder.value.status === 'REJECTED')
})

const canSubmitNow = computed(() => {
  return handleComment.value.trim().length > 0
})

const getStatusClass = (status: string): string => {
  switch (status) {
    case 'PENDING': return 'status-pending'
    case 'PROCESSING': return 'status-processing'
    case 'SUBMITTED': return 'status-submitted'
    case 'COMPLETED': return 'status-completed'
    case 'REJECTED': return 'status-rejected'
    default: return 'status-pending'
  }
}

const formatTime = (timeStr: string): string => {
  if (!timeStr) return '-'
  const date = new Date(timeStr)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const seconds = String(date.getSeconds()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
}

const goBack = () => {
  window.history.back()
}

const getTokenFromUrl = () => {
  const urlParams = new URLSearchParams(window.location.search)
  return urlParams.get('token') || ''
}

const loadWorkOrder = async () => {
  try {
    loading.value = true
    loadingText.value = '加载中...'
    token.value = getTokenFromUrl()
    const workOrderCode = (route.params as { code?: string }).code || ''
    if (!token.value || !workOrderCode) {
      error.value = '无效的访问链接'
      loading.value = false
      return
    }
    workOrder.value = await getWorkOrderByToken(token.value)
    if (workOrder.value && workOrder.value.code !== workOrderCode) {
      error.value = '链接无效，工单编号不匹配'
      workOrder.value = null
    }
    loading.value = false
  } catch (e) {
    error.value = (e as Error).message || '加载工单失败'
    loading.value = false
  }
}

const triggerUpload = () => {
  fileInput.value?.click()
}

const handleFileChange = async (event: Event) => {
  const target = event.target as HTMLInputElement
  const files = target.files
  if (!files || files.length === 0) return

  loading.value = true
  loadingText.value = '上传图片中...'

  try {
    for (const file of Array.from(files)) {
      if (uploadedImages.value.length >= 3) break

      const formData = new FormData()
      formData.append('file', file)

      const result = await uploadHandlerImage(formData, workOrder.value!.id)
      if (result && result.image_url) {
        uploadedImages.value.push(result.image_url)
      }
    }
  } catch (e) {
    submitError.value = (e as Error).message || '上传图片失败'
  } finally {
    loading.value = false
    if (target) {
      target.value = ''
    }
  }
}

const deleteImage = (index: number) => {
  uploadedImages.value.splice(index, 1)
}

const handleSubmit = async () => {
  if (!canSubmitNow.value) return

  loading.value = true
  loadingText.value = '提交中...'
  submitError.value = ''

  try {
    await submitWorkOrder({
      work_order_id: workOrder.value!.id,
      handle_comment: handleComment.value.trim()
    })
    success.value = true
  } catch (e) {
    submitError.value = (e as Error).message || '提交失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadWorkOrder()
})
</script>