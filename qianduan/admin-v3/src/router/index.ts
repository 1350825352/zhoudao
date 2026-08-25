import { createRouter, createWebHashHistory, RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/store/modules/user'
import { request } from '@/api/request'
import { ElMessage } from 'element-plus'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '账号登录', hidden: true }
  },
  {
    path: '/',
    component: () => import('@/layout/index.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '工作台', icon: 'Odometer' }
      },
      {
        path: 'device',
        name: 'Device',
        component: () => import('@/views/device/index.vue'),
        meta: { title: '设备管理', icon: 'Monitor' }
      },
      {
        path: 'project',
        name: 'Project',
        component: () => import('@/views/project/index.vue'),
        meta: { title: '项目管理', icon: 'Files' }
      },
      {
        path: 'route',
        name: 'Route',
        component: () => import('@/views/route/index.vue'),
        meta: { title: '航线管理', icon: 'Connection' }
      },
      {
        path: 'alarm',
        name: 'Alarm',
        component: () => import('@/views/alarm/index.vue'),
        meta: { title: '告警管理', icon: 'Warning' }
      },
      {
        path: 'data-statistics',
        name: 'DataStatistics',
        component: () => import('@/views/data-statistics/index.vue'),
        meta: { title: '数据统计', icon: 'DataAnalysis' }
      },
      {
        path: 'workorder',
        name: 'Workorder',
        component: () => import('@/views/workorder/index.vue'),
        meta: { title: '工单管理', icon: 'Tickets' }
      },
      {
        path: 'user',
        name: 'User',
        component: () => import('@/views/user/index.vue'),
        meta: { title: '用户管理', icon: 'User' }
      },
      {
        path: 'algorithm',
        name: 'Algorithm',
        component: () => import('@/views/algorithm/index.vue'),
        meta: { title: '算法配置', icon: 'Settings' }
      },
      {
        path: 'data-source',
        name: 'DataSource',
        component: () => import('@/views/data-source/index.vue'),
        meta: { title: '换数据源', icon: 'Switch' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach(async (to, from, next) => {
  const userStore = useUserStore()
  
  // 优先检查URL中的token参数（从大屏端跳转过来的情况）
  const urlToken = to.query.token as string
  if (urlToken) {
    try {
      // 调用后端验证接口，验证token和部门权限
      const result: any = await request.get('/auth/verify', { params: { token: urlToken } })
      userStore.setToken(urlToken)
      userStore.setUserInfo({
        username: result.username,
        nickname: result.name,
        dept: result.dept
      })
      next('/dashboard')
      return
    } catch (error) {
      ElMessage.error('您没有权限登录管理后台，请联系管理员')
      // 验证失败时清除token，避免死循环
      next('/login')
      return
    }
  }
  
  // 如果已登录且访问登录页，直接跳转到工作台
  if (to.path === '/login' && userStore.token) {
    next('/dashboard')
    return
  }
  
  if (to.path === '/login') {
    next()
    return
  }
  if (!userStore.token) {
    next('/login')
    return
  }
  next()
})

export default router
