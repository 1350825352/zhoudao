<template>
  <div class="page-container">
    <el-card class="page-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">算法配置</span>
          <el-button type="primary" @click="handleRefresh" :loading="loading">
            <el-icon><Refresh /></el-icon>
            算法刷新
          </el-button>
        </div>
      </template>

      <el-form :model="form" :rules="rules" ref="formRef" label-width="120px">
        <el-divider content-position="left">应用配置</el-divider>
        
        <el-form-item label="应用包ID" prop="zapkId">
          <el-select v-model="form.zapkId" placeholder="请选择应用包ID" @change="handlePackageChange" class="short-select">
            <el-option
              v-for="pkg in packageList"
              :key="pkg.zapkId"
              :label="pkg.name"
              :value="pkg.zapkId"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="应用ID" prop="appIds">
          <el-select v-model="form.appIds" multiple placeholder="请选择应用ID" class="short-select">
            <el-option
              v-for="app in appList"
              :key="app.appId"
              :label="app.name"
              :value="app.appId"
            />
          </el-select>
        </el-form-item>

        <el-divider content-position="left">OSD配置</el-divider>
        
        <el-form-item label="显示名称">
          <el-switch v-model="form.osdDisplayName" :active-value="1" :inactive-value="0" />
        </el-form-item>

        <el-form-item label="显示框">
          <el-switch v-model="form.osdDisplayBox" :active-value="1" :inactive-value="0" />
        </el-form-item>

        <el-form-item label="显示轨迹">
          <el-switch v-model="form.osdDisplayTrack" :active-value="1" :inactive-value="0" />
        </el-form-item>

        <el-form-item label="字体大小">
          <el-input-number v-model="form.osdFontSize" :min="0" :max="100" />
        </el-form-item>

        <el-form-item label="边框大小">
          <el-input-number v-model="form.osdBorderSize" :min="0" :max="100" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSave">保存配置</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { request } from '@/api/request'

const formRef = ref()
const loading = ref(false)

const form = reactive({
  id: null as number | null,
  zapkId: '',
  appIds: [] as string[],
  osdDisplayName: 1,
  osdDisplayBox: 1,
  osdDisplayTrack: 1,
  osdFontSize: 0,
  osdBorderSize: 0
})

const rules = {
  zapkId: [{ required: true, message: '请选择应用包ID', trigger: 'change' }],
  appIds: [{ required: true, message: '请选择应用ID', trigger: 'change' }]
}

const packageList = ref<{ zapkId: string; name: string }[]>([])
const appList = ref<{ appId: string; name: string; zapkId: string }[]>([])

const handlePackageChange = (zapkId: string) => {
  form.appIds = []
  appList.value = []
  if (zapkId) {
    loadApps(zapkId)
  }
}

const loadPackageList = async () => {
  try {
    const res = await request.get('/zhimou/config/packages')
    if (res && Array.isArray(res)) {
      packageList.value = res.map((item: any) => ({
        zapkId: item.zapkId,
        name: item.name || item.zapkId
      }))
    }
  } catch (e) {
    console.error('加载应用包列表失败', e)
  }
}

const loadApps = async (zapkId: string) => {
  try {
    console.log('加载应用列表, zapkId:', zapkId)
    const res = await request.get(`/zhimou/config/apps?zapk_id=${zapkId}`)
    console.log('应用列表响应:', res)
    if (res && Array.isArray(res)) {
      appList.value = res.map((item: any) => ({
        appId: item.appId,
        name: item.name || item.appId,
        zapkId: item.zapkId
      }))
      console.log('应用列表:', appList.value)
    } else {
      console.warn('应用列表响应格式不正确:', res)
    }
  } catch (e) {
    console.error('加载应用列表失败', e)
    ElMessage.error('加载应用列表失败')
  }
}

const loadConfig = async () => {
  try {
    const res = await request.get('/zhimou/config')
    if (res) {
      form.id = res.id
      form.zapkId = res.zapkId || ''
      form.osdDisplayName = res.osdDisplayName != null ? res.osdDisplayName : 1
      form.osdDisplayBox = res.osdDisplayBox != null ? res.osdDisplayBox : 1
      form.osdDisplayTrack = res.osdDisplayTrack != null ? res.osdDisplayTrack : 1
      form.osdFontSize = res.osdFontSize != null ? res.osdFontSize : 0
      form.osdBorderSize = res.osdBorderSize != null ? res.osdBorderSize : 0
      
      if (res.appIds) {
        try {
          form.appIds = JSON.parse(res.appIds)
        } catch (e) {
          form.appIds = []
        }
      }

      if (form.zapkId) {
        await loadApps(form.zapkId)
      }
    }
  } catch (e) {
    console.error('加载配置失败', e)
  }
}

const handleSave = async () => {
  if (!formRef.value) return
  
  try {
    await formRef.value.validate()
    
    const saveData = {
      id: form.id,
      zapkId: form.zapkId,
      appIds: JSON.stringify(form.appIds),
      osdDisplayName: form.osdDisplayName,
      osdDisplayBox: form.osdDisplayBox,
      osdDisplayTrack: form.osdDisplayTrack,
      osdFontSize: form.osdFontSize,
      osdBorderSize: form.osdBorderSize
    }

    await request.post('/zhimou/config', saveData)
    ElMessage.success('配置保存成功')
  } catch (e) {
    console.error('保存配置失败', e)
  }
}

const handleReset = () => {
  formRef.value?.resetFields()
}

const handleRefresh = async () => {
  loading.value = true
  try {
    await request.post('/zhimou/config/sync')
    await loadPackageList()
    form.appIds = []
    appList.value = []
    if (form.zapkId) {
      await loadApps(form.zapkId)
    }
    ElMessage.success('算法刷新成功')
  } catch (e) {
    console.error('算法刷新失败', e)
    ElMessage.error('算法刷新失败')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await loadPackageList()
  await loadConfig()
  
  if (packageList.value.length > 0 && !form.zapkId) {
    form.zapkId = packageList.value[0].zapkId
    await loadApps(form.zapkId)
  }
})
</script>

<style lang="less" scoped>
.page-container {
  padding: 20px;
}

.page-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 18px;
  font-weight: bold;
}

.short-select {
  width: 33%;
}
</style>