<template>
  <div class="danger-sum">
    <view-title title="警情信息" />
    <div class="alarm-list">
      <div 
        class="alarm-item" 
        v-for="(item, index) in tableData" 
        :key="index"
        @click="handleItemClick(item, index)"
      >
        <div class="alarm-image" @click.stop="openImage(item, index)">
          <img 
            v-if="item.imageUrl" 
            :src="item.imageUrl" 
            alt="" 
            class="alarm-img"
          />
          <div v-else class="alarm-icon">
            <img :src="warningIcon" alt="" />
          </div>
        </div>
        <div class="alarm-info">
          <div class="alarm-title">{{ item.appName }}</div>
          <div class="alarm-meta">
            <span class="meta-item">{{ item.ruleName }}</span>
            <span class="meta-divider">|</span>
            <span class="meta-item">{{ item.alarmTime }}</span>
          </div>
          <div v-if="item.lng && item.lat" class="alarm-location">
            <span class="location-icon">📍</span>
            <span class="location-text">{{ item.lng }}, {{ item.lat }}</span>
          </div>
        </div>
        <div class="alarm-priority">
          <span class="priority-tag">告警</span>
        </div>
      </div>
    </div>

    <div v-if="showPreview" class="preview-overlay" @click="closePreview">
      <div class="preview-content" @click.stop>
        <div 
          class="preview-img-container" 
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
          <div class="preview-info-overlay">
            <div>事件类型: {{ previewItem?.appName }}</div>
            <div>巡检状态: {{ previewItem?.ruleName }}</div>
            <div>告警时间: {{ previewItem?.alarmTime }}</div>
            <div v-if="previewItem?.lng && previewItem?.lat">
              经度: {{ previewItem?.lng }} 纬度: {{ previewItem?.lat }}
            </div>
            <div v-if="previewItem?.waylineUuid">
              航线UUID: {{ previewItem?.waylineUuid }}
            </div>
          </div>
          
          <button 
            class="preview-prev" 
            @click.stop="prevImage"
            :disabled="currentIndex <= 0"
          >
            ◀
          </button>
          <button 
            class="preview-next" 
            @click.stop="nextImage"
            :disabled="currentIndex >= imageItems.length - 1"
          >
            ▶
          </button>
          
          <button class="preview-close" @click.stop="closePreview">✕</button>
        </div>
        
        <div class="preview-counter">
          {{ currentIndex + 1 }} / {{ imageItems.length }}
        </div>

        <div class="preview-zoom-controls">
          <button class="zoom-btn" @click="zoomOut">−</button>
          <span class="zoom-level">{{ Math.round(scale * 100) }}%</span>
          <button class="zoom-btn" @click="zoomIn">+</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import viewTitle from './-title.vue'
import warningIcon from './img/dangerIcon.png'
import { deviceApi } from '@/api/uav'
import { useAppStore } from '@/store'

const emit = defineEmits(['point'])
const store = useAppStore()

interface DangerRow {
  id: string
  appName: string
  ruleName: string
  alarmTime: string
  level?: string
  imageUrl?: string
  lng?: number
  lat?: number
}

const tableData = ref<DangerRow[]>([])
const showPreview = ref(false)
const previewImage = ref('')
const previewItem = ref<DangerRow | null>(null)
const currentIndex = ref(0)
const scale = ref(1)
const translateX = ref(0)
const translateY = ref(0)
const isDragging = ref(false)
const dragStartX = ref(0)
const dragStartY = ref(0)
const dragStartTranslateX = ref(0)
const dragStartTranslateY = ref(0)
let refreshTimer: ReturnType<typeof setInterval> | null = null

const imageItems = computed(() => tableData.value.filter(item => item.imageUrl))
const pendingCount = computed(() => tableData.value.length)
const processedCount = ref(0)

const loadAlerts = async () => {
  try {
    const res = await deviceApi.getAlertList()
    tableData.value = res.data || []
  } catch (e) {
    console.warn('加载警情信息失败', e)
  }
}

const openImage = (item: DangerRow, index: number) => {
  if (item.imageUrl) {
    const imgItems = imageItems.value
    const imgIndex = imgItems.findIndex(i => i.id === item.id)
    currentIndex.value = imgIndex >= 0 ? imgIndex : 0
    updatePreview()
    showPreview.value = true
  }
}

const handleItemClick = (item: DangerRow, index: number) => {
  // 点击非图片部分，在地图上显示经纬度位置
  if (item.lng && item.lat) {
    emit('point', {
      lng: item.lng,
      lat: item.lat,
      name: item.appName
    })
  }
}

const updatePreview = () => {
  const imgItems = imageItems.value
  if (imgItems.length > 0 && currentIndex.value >= 0 && currentIndex.value < imgItems.length) {
    previewItem.value = imgItems[currentIndex.value]
    previewImage.value = imgItems[currentIndex.value].imageUrl!
    scale.value = 1
    translateX.value = 0
    translateY.value = 0
  }
}

const closePreview = () => {
  showPreview.value = false
  previewImage.value = ''
  previewItem.value = null
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
    scale.value += 0.25
  }
}

const zoomOut = () => {
  if (scale.value > 0.25) {
    scale.value -= 0.25
  }
}

const handleWheel = (e: WheelEvent) => {
  e.preventDefault()
  const delta = e.deltaY > 0 ? -0.1 : 0.1
  scale.value = Math.max(0.25, Math.min(5, scale.value + delta))
}

const handleMouseDown = (e: MouseEvent) => {
  if (scale.value > 1 && e.button === 0) {
    e.preventDefault()
    isDragging.value = true
    dragStartX.value = e.clientX
    dragStartY.value = e.clientY
    dragStartTranslateX.value = translateX.value
    dragStartTranslateY.value = translateY.value
    window.addEventListener('mousemove', handleMouseMove)
    window.addEventListener('mouseup', handleMouseUp)
  }
}

const handleMouseMove = (e: MouseEvent) => {
  if (isDragging.value) {
    translateX.value = dragStartTranslateX.value + (e.clientX - dragStartX.value)
    translateY.value = dragStartTranslateY.value + (e.clientY - dragStartY.value)
  }
}

const handleMouseUp = () => {
  isDragging.value = false
  window.removeEventListener('mousemove', handleMouseMove)
  window.removeEventListener('mouseup', handleMouseUp)
}

// 飞行判定与地图一致: modeCode 非0且非14, 飞行时1秒轮询, 待机停止
const isUavFlying = () =>
  store.devices.some(d => d.kind === 'uav' && d.modeCode != null && d.modeCode !== 0 && d.modeCode !== 14)

const pollAlerts = () => {
  if (isUavFlying()) loadAlerts()
}

onMounted(() => {
  loadAlerts()
  refreshTimer = setInterval(pollAlerts, 1000)
})

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
  }
})
</script>

<style lang="less" scoped>
.danger-sum {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.alarm-list {
  flex: 1;
  padding: 12px;
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

.alarm-item {
  display: flex;
  align-items: center;
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
    transform: translateX(4px);
  }

  &:last-child {
    margin-bottom: 0;
  }
}

.alarm-image {
  width: 56px;
  height: 56px;
  flex-shrink: 0;
  border-radius: 6px;
  overflow: hidden;
  background: rgba(0, 0, 0, 0.3);

  .alarm-img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .alarm-icon {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;

    img {
      width: 32px;
      height: 32px;
    }
  }
}

.alarm-info {
  flex: 1;
  min-width: 0;
  margin-left: 12px;

  .alarm-title {
    font-size: 13px;
    font-weight: bold;
    color: #fff;
    margin-bottom: 4px;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  .alarm-meta {
    display: flex;
    align-items: center;
    font-size: 11px;
    color: #6c91b5;

    .meta-item {
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;
    }

    .meta-divider {
      margin: 0 8px;
      color: rgba(255, 255, 255, 0.2);
    }
  }

  .alarm-location {
    display: flex;
    align-items: center;
    margin-top: 4px;
    font-size: 11px;
    color: #5ca8ff;

    .location-icon {
      margin-right: 4px;
    }

    .location-text {
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;
    }
  }
}

.alarm-priority {
  flex-shrink: 0;
  margin-left: 12px;

  .priority-tag {
    padding: 4px 10px;
    background: rgba(255, 149, 0, 0.2);
    color: #ff9500;
    font-size: 11px;
    font-weight: bold;
    border-radius: 4px;
    border: 1px solid rgba(255, 149, 0, 0.3);
  }
}

.preview-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;

  .preview-content {
    position: relative;
    max-width: 90%;
    max-height: 90%;
    display: flex;
    flex-direction: column;
    align-items: center;

    .preview-img-container {
      position: relative;
      display: inline-block;
      max-width: calc(100vw - 80px);
      max-height: 70vh;
      overflow: hidden;
      cursor: grab;
      border-radius: 8px;
      box-shadow: 0 0 30px rgba(46, 139, 255, 0.5);
      background: rgba(0, 0, 0, 0.8);

      &:active {
        cursor: grabbing;
      }

      .preview-img {
        display: block;
        max-width: calc(100vw - 80px);
        max-height: 70vh;
        transition: transform 0.1s ease-out;
        transform-origin: center center;
      }

      .preview-info-overlay {
        position: absolute;
        bottom: 8px;
        left: 8px;
        background: rgba(0, 0, 0, 0.7);
        padding: 8px 12px;
        border-radius: 4px;
        color: #fff;
        font-size: 12px;
        line-height: 1.6;
        z-index: 5;
        pointer-events: none;
      }

      .preview-close {
        position: absolute;
        top: 8px;
        right: 8px;
        width: 32px;
        height: 32px;
        background: rgba(255, 255, 255, 0.2);
        border: none;
        border-radius: 50%;
        color: #fff;
        font-size: 16px;
        cursor: pointer;
        transition: background 0.2s;
        z-index: 20;

        &:hover {
          background: rgba(255, 255, 255, 0.4);
        }
      }

      .preview-prev,
      .preview-next {
        position: absolute;
        top: 50%;
        transform: translateY(-50%);
        width: 40px;
        height: 40px;
        background: rgba(255, 255, 255, 0.2);
        border: none;
        border-radius: 50%;
        color: #fff;
        font-size: 18px;
        cursor: pointer;
        transition: all 0.2s;
        z-index: 20;

        &:hover:not(:disabled) {
          background: rgba(255, 255, 255, 0.4);
          transform: translateY(-50%) scale(1.1);
        }

        &:disabled {
          opacity: 0.3;
          cursor: not-allowed;
        }
      }

      .preview-prev {
        left: 8px;
      }

      .preview-next {
        right: 8px;
      }
    }

    .preview-counter {
      margin-top: 12px;
      font-size: 14px;
      font-weight: bold;
      color: #42b983;
    }

    .preview-zoom-controls {
      position: absolute;
      bottom: -50px;
      display: flex;
      align-items: center;
      gap: 12px;

      .zoom-btn {
        width: 36px;
        height: 36px;
        background: rgba(255, 255, 255, 0.2);
        border: none;
        border-radius: 50%;
        color: #fff;
        font-size: 20px;
        font-weight: bold;
        cursor: pointer;
        transition: background 0.2s;

        &:hover {
          background: rgba(255, 255, 255, 0.4);
        }
      }

      .zoom-level {
        color: #fff;
        font-size: 14px;
        min-width: 50px;
        text-align: center;
      }
    }
  }

  @media (max-width: 1400px) {
    .preview-img-container {
      max-width: calc(100vw - 80px);
    }
  }

  @media (max-width: 1200px) {
    .preview-img-container {
      max-width: calc(100vw - 80px);
    }
  }

  @media (max-width: 1000px) {
    .preview-img-container {
      max-width: calc(100vw - 80px);
    }
  }
}</style>
