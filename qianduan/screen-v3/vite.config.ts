import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'
import fs from 'fs'

export default defineConfig(({ mode }) => ({
  base: mode === 'production' ? '/screen/' : '/',
  plugins: [
    vue(),
    {
      name: 'spa-fallback',
      configureServer(server) {
        const VITE_INTERNAL_RE = /^\/(api|node_modules|@[^/]*|src|assets|favicon|__vite_ping)\b/
        const HAS_EXT_RE = /\.[a-zA-Z0-9]{1,8}($|\?|#)/
        server.middlewares.use((req, res, next) => {
          try {
            if (req.method !== 'GET' && req.method !== 'HEAD') return next()
            const rawUrl = req.url || '/'
            const pathOnly = rawUrl.split(/[?#]/)[0]
            if (VITE_INTERNAL_RE.test(pathOnly)) return next()
            if (HAS_EXT_RE.test(pathOnly)) return next()
            const accept = (req.headers && req.headers.accept) ? String(req.headers.accept) : ''
            if (accept.includes('text/html') || accept.includes('*/*') || pathOnly === '/') {
              req.url = '/index.html' + (rawUrl.indexOf('?') >= 0 ? rawUrl.substring(rawUrl.indexOf('?')) : '')
            }
            return next()
          } catch (e) {
            return next(e)
          }
        })
      }
    }
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  optimizeDeps: {
    cacheDir: 'C:/temp/vite-cache/screen-v3',
    include: [
      'vue',
      'vue-router',
      'pinia',
      'axios',
      'element-plus/es/locale/lang/zh-cn',
      'dayjs',
      'echarts'
    ]
  },
  server: {
    host: '0.0.0.0',
    port: 3202,
    open: false,
    hmr: true,
    fs: {
      strict: false,
      allow: [process.cwd(), 'C:/temp/vite-cache']
    },
    allowedHosts: ['all'],
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
        secure: false,
        timeout: 60000
      },
      '/admin': {
        target: 'http://127.0.0.1:3201',
        changeOrigin: true,
        secure: false,
        timeout: 60000
      }
    }
  },
  build: {
    outDir: 'dist',
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules')) {
            return id.toString().split('node_modules/')[1].split('/')[0].toString()
          }
        }
      }
    }
  }
}))
