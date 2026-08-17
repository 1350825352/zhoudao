import { createRouter, createWebHistory } from 'vue-router'
import WorkOrderDetail from '@/views/WorkOrderDetail.vue'

const router = createRouter({
  history: createWebHistory('/h5/'),
  routes: [
    {
      path: '/:code',
      name: 'WorkOrderDetail',
      component: WorkOrderDetail
    },
    {
      path: '/',
      name: 'Home',
      component: WorkOrderDetail
    }
  ]
})

export default router