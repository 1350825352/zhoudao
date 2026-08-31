<template>
  <div class="data-source-page">
    <el-card class="switch-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <el-icon class="header-icon"><Switch /></el-icon>
          <span>换数据源</span>
        </div>
      </template>

      <el-skeleton :rows="3" :loading="loading" animated />

      <div v-if="!loading" class="switch-list">
        <!-- 视频流任务开关 -->
        <div class="switch-item">
          <div class="switch-info">
            <div class="switch-label">视频流任务开关</div>
            <div class="switch-desc">控制知眸视频流自动创建任务。关闭时立即关闭所有运行中的视频任务。</div>
          </div>
          <el-switch
            v-model="videoTaskEnabled"
            :loading="switchLoading.video_task"
            @change="(val) => onSwitchChange('video_task', val ? '1' : '0')"
            active-text="开启"
            inactive-text="关闭"
            inline-prompt
          />
        </div>

        <el-divider />

        <!-- 图片识别任务开关 -->
        <div class="switch-item">
          <div class="switch-info">
            <div class="switch-label">图片识别任务开关</div>
            <div class="switch-desc">控制图片识别流程。当前控制测试入口，MinIO 接入后控制事件接收接口。</div>
          </div>
          <el-switch
            v-model="imageTaskEnabled"
            :loading="switchLoading.image_task"
            @change="(val) => onSwitchChange('image_task', val ? '1' : '0')"
            active-text="开启"
            inactive-text="关闭"
            inline-prompt
          />
        </div>

        <el-divider />

        <!-- 警情数据源切换 -->
        <div class="switch-item">
          <div class="switch-info">
            <div class="switch-label">大屏警情数据源</div>
            <div class="switch-desc">仅控制大屏警情模块的数据源；admin 的告警管理/照片告警页面不受影响。</div>
          </div>
          <el-radio-group
            v-model="alertDataSource"
            :loading="switchLoading.alert_data_source"
            @change="(val) => onSwitchChange('alert_data_source', val)"
          >
            <el-radio-button label="video">视频识别</el-radio-button>
            <el-radio-button label="image">图片识别(已清洗)</el-radio-button>
          </el-radio-group>
        </div>
      </div>
    </el-card>

    <el-card class="info-card" shadow="never">
      <template #header>
        <div class="card-header">
          <el-icon class="header-icon"><InfoFilled /></el-icon>
          <span>说明</span>
        </div>
      </template>
      <ul class="info-list">
        <li><b>视频流任务开关</b>：关闭时立即关闭所有运行中的知眸视频任务，并停止自动创建新任务；开启后恢复自动调度。</li>
        <li><b>图片识别任务开关</b>：关闭时图片识别测试入口和 MinIO 事件接收接口都将忽略请求；开启后恢复识别流程。</li>
        <li><b>大屏警情数据源</b>：切换为"图片识别"后，大屏警情列表只显示已清洗（clean_status != 0）的图片识别结果；admin 的"告警管理"固定显示视频识别告警、"照片告警"固定显示图片识别告警，不随此开关变化。</li>
        <li>所有设置<b>全局生效</b>，screen-v3 大屏等所有用户看到的警情数据源与此处设置一致。</li>
      </ul>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Switch, InfoFilled } from '@element-plus/icons-vue'
import { request } from '@/api/request'

const loading = ref(true)
const videoTaskEnabled = ref(false)
const imageTaskEnabled = ref(false)
const alertDataSource = ref('video')
const switchLoading = reactive({
  video_task: false,
  image_task: false,
  alert_data_source: false
})

const loadSwitches = async () => {
  try {
    const res: any = await request.get('/task-switch')
    const data = res.data || res
    videoTaskEnabled.value = data.video_task === '1'
    imageTaskEnabled.value = data.image_task === '1'
    alertDataSource.value = data.alert_data_source || 'video'
  } catch (e: any) {
    ElMessage.error('加载开关状态失败: ' + (e.message || e))
  } finally {
    loading.value = false
  }
}

const onSwitchChange = async (key: string, value: string) => {
  switchLoading[key as keyof typeof switchLoading] = true
  try {
    await request.put(`/task-switch/${key}`, null, { params: { value } })
    ElMessage.success(`${getLabel(key)} 已${value === '1' || value === 'image' ? '开启' : '关闭'}`)
  } catch (e: any) {
    ElMessage.error(`修改失败: ${e.message || e}`)
    // 失败时回滚
    await loadSwitches()
  } finally {
    switchLoading[key as keyof typeof switchLoading] = false
  }
}

const getLabel = (key: string) => {
  if (key === 'video_task') return '视频流任务开关'
  if (key === 'image_task') return '图片识别任务开关'
  if (key === 'alert_data_source') return '大屏警情数据源'
  return key
}

onMounted(() => {
  loadSwitches()
})
</script>

<style scoped>
.data-source-page {
  padding: 20px;
}

.switch-card {
  max-width: 800px;
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  align-items: center;
  font-size: 16px;
  font-weight: 600;
}

.header-icon {
  margin-right: 8px;
  font-size: 18px;
}

.switch-list {
  padding: 10px 0;
}

.switch-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
}

.switch-info {
  flex: 1;
}

.switch-label {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 6px;
}

.switch-desc {
  font-size: 13px;
  color: #909399;
  line-height: 1.5;
}

.info-card {
  max-width: 800px;
}

.info-list {
  margin: 0;
  padding-left: 20px;
  font-size: 13px;
  color: #606266;
  line-height: 2;
}

.info-list b {
  color: #303133;
}
</style>
