<template>
  <div class="layout-container">
    <div class="layout-header">
      <div class="header-title">{{ systemTitle }}</div>
      <div class="header-right">
        <span class="user-info">
          <el-icon><UserFilled /></el-icon>
          {{ userStore.userInfo.nickname || '管理员' }}
        </span>
        <el-button type="danger" link @click="handleLogout">
          <el-icon><SwitchButton /></el-icon>
          退出
        </el-button>
      </div>
    </div>
    <div class="layout-body">
      <div class="layout-sidebar">
        <el-menu
          :default-active="activeMenu"
          :router="true"
          class="sidebar-menu"
          background-color="#ffffff"
          text-color="#333333"
          active-text-color="#0552bc"
        >
          <el-menu-item index="/dashboard">
            <el-icon><Odometer /></el-icon>
            <span>工作台</span>
          </el-menu-item>
          <el-menu-item index="/device">
            <el-icon><Monitor /></el-icon>
            <span>设备管理</span>
          </el-menu-item>
          <el-menu-item index="/project">
            <el-icon><Files /></el-icon>
            <span>项目管理</span>
          </el-menu-item>
          <el-menu-item index="/route">
            <el-icon><Connection /></el-icon>
            <span>航线管理</span>
          </el-menu-item>
          <el-menu-item index="/alarm">
            <el-icon><Warning /></el-icon>
            <span>告警管理</span>
          </el-menu-item>
          <el-menu-item index="/data-statistics">
            <el-icon><DataAnalysis /></el-icon>
            <span>数据统计</span>
          </el-menu-item>
          <el-menu-item index="/user">
            <el-icon><User /></el-icon>
            <span>用户管理</span>
          </el-menu-item>
          <el-menu-item index="/algorithm">
            <el-icon><Tools /></el-icon>
            <span>算法配置</span>
          </el-menu-item>
          <el-menu-item index="/data-source">
            <el-icon><Connection /></el-icon>
            <span>换数据源</span>
          </el-menu-item>
          <el-menu-item index="/workorder">
            <el-icon><Tickets /></el-icon>
            <span>工单管理</span>
          </el-menu-item>
        </el-menu>
      </div>
      <div class="layout-main">
        <div class="layout-breadcrumb">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item v-for="(item, index) in breadcrumbs" :key="index">
              {{ item }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="layout-content">
          <router-view />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import {
  Odometer,
  Monitor,
  Files,
  Connection,
  Warning,
  DataAnalysis,
  Tickets,
  User,
  UserFilled,
  SwitchButton,
  Tools
} from '@element-plus/icons-vue'
import { useUserStore } from '@/store/modules/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const companyName = import.meta.env.VITE_COMPANY_NAME || ''
const appTitle = import.meta.env.VITE_APP_TITLE || '无人机管理系统'
const systemTitle = `${companyName}${companyName ? ' ' : ''}${appTitle}`

const activeMenu = computed(() => route.path)

const breadcrumbs = computed(() => {
  const list: string[] = ['首页']
  if (route.meta?.title) {
    list.push(route.meta.title as string)
  }
  return list
})

const handleLogout = async () => {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    userStore.logout()
    window.location.href = '/login'
  } catch {}
}
</script>

<style lang="less" scoped>
@primary-color: #0552bc;
@header-bg: #0a2342;
@sidebar-width: 210px;

.layout-container {
  width: 100%;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
}

.layout-header {
  height: 60px;
  background: @header-bg;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  flex-shrink: 0;
}

.header-title {
  font-size: 20px;
  font-weight: 600;
  color: #fff;
  letter-spacing: 1px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #fff;
  font-size: 14px;

  .el-icon {
    font-size: 16px;
  }
}

.layout-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.layout-sidebar {
  width: @sidebar-width;
  background: #fff;
  border-right: 1px solid #e4e7ed;
  flex-shrink: 0;
  overflow-y: auto;
}

.sidebar-menu {
  height: 100%;
  padding-top: 10px;

  .el-menu-item {
    height: 50px;
    line-height: 50px;
    margin: 2px 10px;
    border-radius: 4px;

    .el-icon {
      font-size: 18px;
      margin-right: 8px;
    }
  }

  .el-menu-item.is-active {
    background-color: #e6f0ff !important;
    color: @primary-color !important;
  }

  .el-menu-item:hover {
    background-color: #f0f5ff !important;
  }
}

.layout-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.layout-breadcrumb {
  height: 50px;
  background: #fff;
  padding: 0 20px;
  display: flex;
  align-items: center;
  border-bottom: 1px solid #e4e7ed;
  flex-shrink: 0;

  :deep(.el-breadcrumb__item) {
    font-size: 14px;
  }

  :deep(.el-breadcrumb__inner) {
    color: #606266;
  }

  :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
    color: @primary-color;
    font-weight: 500;
  }
}

.layout-content {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
}
</style>
