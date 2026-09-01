import { createRouter, createWebHashHistory, Router, RouteRecordRaw } from 'vue-router'
import { useAppStore } from '@/store'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'

export const routes: Array<RouteRecordRaw> = [
  {
    path: '/login',
    name: 'login',
    meta: {
      title: '登录'
    },
    component: () => import('@/views/login/index.vue')
  },
  {
    path: '/',
    name: 'home',
    meta: {
      title: '无人机监控大屏',
      requireAuth: true
    },
    component: () => import('@/views/home/home.vue')
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
}) as Router

router.beforeEach(async (to, _from, next) => {
  if (to.meta && to.meta.title) {
    document.title = to.meta.title as string
  }

  // 优先检查URL中的token参数(从父系统或admin跳转过来的情况)
  const urlToken = to.query.token as string
  if (urlToken) {
    try {
      // 调用后端验证接口,验证token有效性
      const result: any = await request.get('/auth/verify', { params: { token: urlToken } })
      const userInfo = {
        id: result.id || 0,
        username: result.username || 'external',
        name: result.name || '外部系统',
        token: urlToken
      }
      sessionStorage.setItem('screen-token', urlToken)
      sessionStorage.setItem('screen-user', JSON.stringify(userInfo))
      const store = useAppStore()
      store.setUser(userInfo)
      // 清除URL上的token参数,避免泄露
      next({ path: '/', query: {} })
      return
    } catch (error: any) {
      ElMessage.error(error?.message || '登录验证失败，请重新登录')
      next('/login')
      return
    }
  }

  const token = sessionStorage.getItem('screen-token')

  if (to.meta.requireAuth && !token) {
    next('/login')
  } else {
    next()
  }
})

export default router
