<template>
  <div class="page-container">
    <el-card class="page-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">设备管理</span>
          <el-button type="primary" @click="handleSync">
            <el-icon><Refresh /></el-icon>
            设备同步
          </el-button>
        </div>
      </template>
      <el-table :data="tableData" stripe border style="width: 100%">
        <el-table-column prop="id" label="序号" width="80" />
        <el-table-column prop="name" label="设备名称" />
        <el-table-column prop="sn" label="设备序列号" />
        <el-table-column prop="childNames" label="子设备名称" />
        <el-table-column prop="childSns" label="子设备序列号" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { request } from '@/api/request'

interface DeviceRow {
  id: number
  name: string
  sn: string
  childNames: string
  childSns: string
}

const tableData = ref<DeviceRow[]>([])

const loadData = async () => {
  try {
    const topology: any[] = await request.get('/device/topology')
    const rows: DeviceRow[] = []
    topology.forEach((item, index) => {
      const childNames = item.aircrafts?.map((ac: any) => ac.aircraftName).join(', ') || '-'
      const childSns = item.aircrafts?.map((ac: any) => ac.aircraftSn).join(', ') || '-'
      rows.push({
        id: index + 1,
        name: item.gatewayName,
        sn: item.gatewaySn,
        childNames,
        childSns
      })
    })
    tableData.value = rows
  } catch (e) {
    console.error('加载设备数据失败', e)
    ElMessage.error('加载设备数据失败')
  }
}

const handleSync = async () => {
  ElMessage.info('正在同步设备数据...')
  try {
    await request.post('/devices/sync-names')
    await loadData()
    ElMessage.success('设备同步完成')
  } catch (e) {
    console.error('设备同步失败', e)
    ElMessage.error('设备同步失败')
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
