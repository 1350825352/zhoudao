import { createRouter, createWebHashHistory, Router, RouteRecordRaw } from 'vue-router'

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

router.beforeEach((to, _from, next) => {
  if (to.meta && to.meta.title) {
    document.title = to.meta.title as string
  }
  
  const token = sessionStorage.getItem('screen-token')
  
  if (to.meta.requireAuth && !token) {
    next('/login')
  } else {
    next()
  }
})

export default router
