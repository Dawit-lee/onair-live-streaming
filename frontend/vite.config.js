import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 기본: 로컬 백엔드(8080). DEV_PROXY=https://localhost 로 주면 Docker 스택(Caddy)의 /api, /rtc 로 프록시한다.
const target = process.env.DEV_PROXY || 'http://localhost:8080'
const proxy = { '/api': { target, changeOrigin: true, secure: false } }
if (process.env.DEV_PROXY) proxy['/rtc'] = { target, changeOrigin: true, secure: false, ws: true }

export default defineConfig({
  plugins: [vue()],
  server: { host: true, proxy },
})
