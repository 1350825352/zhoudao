<template>
  <div class="page-container">
    <el-card class="page-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">项目管理</span>
          <el-button type="primary" @click="handleSync">
            <el-icon><Refresh /></el-icon>
            同步项目
          </el-button>
        </div>
      </template>
      <el-table :data="tableData" stripe border style="width: 100%">
        <el-table-column prop="id" label="序号" width="80" />
        <el-table-column prop="projectName" label="项目名称" />
        <el-table-column prop="orgName" label="所属组织" />
        <el-table-column prop="projectUuid" label="项目UUID" />
        <el-table-column prop="introduction" label="项目介绍" />
        <el-table-column prop="centerLongitude" label="中心经度" />
        <el-table-column prop="centerLatitude" label="中心纬度" />
        <el-table-column prop="lastSyncTime" label="最后同步时间" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { request } from '@/api/request'

interface ProjectRow {
  id: number
  projectName: string
  orgName: string
  projectUuid: string
  introduction: string
  centerLongitude: number
  centerLatitude: number
  lastSyncTime: string
}

const tableData = ref<ProjectRow[]>([])

const loadData = async () => {
  try {
    const data = await request.get('/project/list')
    tableData.value = data
  } catch (e) {
    console.error('加载项目数据失败', e)
    ElMessage.error('加载项目数据失败')
  }
}

const handleSync = async () => {
  ElMessage.info('正在同步项目数据...')
  try {
    const data = await request.post('/project/sync')
    tableData.value = data.data || data
    ElMessage.success('项目同步完成')
  } catch (e) {
    console.error('项目同步失败', e)
    ElMessage.error('项目同步失败')
  }
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
</style>
