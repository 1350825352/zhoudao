import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import JSZip from 'jszip'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import './styles/global.less'

;(window as any).JSZip = JSZip

const app = createApp(App)

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component as any)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

// ===== 一键登录支持：URL ?token= 自动写入 sessionStorage =====
;(function () {
  const q = new URLSearchParams(window.location.search)
  const urlToken = q.get('token')
    || (window.location.hash.match(/[?&]token=([^&]+)/) || [])[1]
  if (urlToken) {
    sessionStorage.setItem('screen-token', urlToken)
    q.delete('token')
    const u = new URL(window.location.href)
    u.search = q.toString()
    window.history.replaceState({}, '', u)
  }
})()
// ===== 结束 =====

app.mount('#app')
