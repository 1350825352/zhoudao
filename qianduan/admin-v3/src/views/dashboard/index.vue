<template>
  <div class="dashboard-container">
    <div class="welcome-section">
      <div class="welcome-content">
        <div class="welcome-icon">
          <img src="/logo.png" style="width:64px;height:64px;border-radius:12px;" />
        </div>
        <h1 class="welcome-title">欢迎来到{{ companyName }}</h1>
        <p class="welcome-subtitle">{{ appTitle }}</p>
      </div>
    </div>

    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-icon alarm-icon">🔔</div>
        <div class="stat-info">
          <div class="stat-value">{{ alarmCount }}</div>
          <div class="stat-label">今日告警</div>
        </div>
      </div>

      <div class="stat-card">
        <div class="stat-icon order-icon">📋</div>
        <div class="stat-info">
          <div class="stat-value">{{ pendingCount }}</div>
          <div class="stat-label">待处理工单</div>
        </div>
      </div>

      <div class="stat-card">
        <div class="stat-icon device-icon">📱</div>
        <div class="stat-info">
          <div class="stat-value">{{ deviceCount }}</div>
          <div class="stat-label">在线设备</div>
        </div>
      </div>

      <div class="stat-card">
        <div class="stat-icon route-icon">🗺️</div>
        <div class="stat-info">
          <div class="stat-value">{{ routeCount }}</div>
          <div class="stat-label">航线数量</div>
        </div>
      </div>
    </div>

    <div class="quick-actions">
      <h3 class="section-title">快捷操作</h3>
      <div class="action-grid">
        <router-link to="/alarm" class="action-card">
          <div class="action-icon">⚠️</div>
          <div class="action-text">告警管理</div>
        </router-link>

        <router-link to="/workorder" class="action-card">
          <div class="action-icon">📝</div>
          <div class="action-text">工单管理</div>
        </router-link>

        <router-link to="/device" class="action-card">
          <div class="action-icon">⚙️</div>
          <div class="action-text">设备管理</div>
        </router-link>

        <router-link to="/user" class="action-card">
          <div class="action-icon">👤</div>
          <div class="action-text">用户管理</div>
        </router-link>
      </div>
    </div>

    <div class="recent-section">
      <h3 class="section-title">最近告警</h3>
      <el-card class="recent-card" shadow="never">
        <el-table :data="recentAlarms" border style="width: 100%">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="rule_name" label="告警类型" width="150" />
          <el-table-column prop="aircraft_sn" label="设备SN" width="180" />
          <el-table-column prop="hit_count" label="触发次数" width="100" />
          <el-table-column prop="event_time" label="告警时间" width="180" />
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import request from '@/api/request'

const companyName = import.meta.env.VITE_COMPANY_NAME || '公司'
const appTitle = import.meta.env.VITE_APP_TITLE || '无人机管理系统'

const alarmCount = ref(0)
const pendingCount = ref(0)
const deviceCount = ref(0)
const routeCount = ref(0)
const recentAlarms = ref<any[]>([])

const loadStats = async () => {
  try {
    const res = await request.get('/dashboard/stats')
    alarmCount.value = res.alarmCount || 0
    pendingCount.value = res.pendingCount || 0
    deviceCount.value = res.deviceCount || 0
    routeCount.value = res.routeCount || 0
  } catch (e) {
    console.error('加载统计数据失败', e)
  }
}

const loadRecentAlarms = async () => {
  try {
    const res = await request.get('/zhimou/results/latest?limit=5')
    recentAlarms.value = res || []
  } catch (e) {
    console.error('加载最近告警失败', e)
  }
}

onMounted(() => {
  loadStats()
  loadRecentAlarms()
})
</script>

<style lang="less" scoped>
.dashboard-container {
  padding: 20px;
}

.welcome-section {
  background: linear-gradient(135deg, #409eff 0%, #66b1ff 100%);
  border-radius: 12px;
  padding: 40px;
  margin-bottom: 20px;
  text-align: center;
}

.welcome-content {
  color: white;
}

.welcome-icon {
  font-size: 64px;
  margin-bottom: 16px;
}

.welcome-title {
  font-size: 28px;
  font-weight: 600;
  margin: 0 0 8px;
}

.welcome-subtitle {
  font-size: 16px;
  opacity: 0.9;
  margin: 0;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  background: white;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
}

.alarm-icon {
  background: linear-gradient(135deg, #f56c6c 0%, #f78989 100%);
}

.order-icon {
  background: linear-gradient(135deg, #409eff 0%, #66b1ff 100%);
}

.device-icon {
  background: linear-gradient(135deg, #67c23a 0%, #85ce61 100%);
}

.route-icon {
  background: linear-gradient(135deg, #e6a23c 0%, #ebb563 100%);
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 4px;
}

.quick-actions {
  margin-bottom: 20px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}

.action-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.action-card {
  background: white;
  border-radius: 12px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  text-decoration: none;
  color: #303133;
  transition: all 0.3s;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.action-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
}

.action-icon {
  font-size: 36px;
}

.action-text {
  font-size: 14px;
  font-weight: 500;
}

.recent-section {
  background: white;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.recent-card {
  border: none;
}
</style>
