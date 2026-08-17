import { createApp } from 'vue'
import { createPinia } from 'pinia'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import JSZip from 'jszip'
import App from './App.vue'
import router from './router'
import './styles/global.less'

dayjs.locale('zh-cn')
;(window as any).JSZip = JSZip

const app = createApp(App)
const pinia = createPinia()
pinia.use(piniaPluginPersistedstate)

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component as any)
}

app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')
