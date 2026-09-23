import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [uni()],
  server: {
    host: '0.0.0.0',
    port: 5174,
    // 开发环境把 /api 代理到本地 Spring Boot（8080），
    // 这样 VITE_USE_MOCK=false 时 H5 端可以直接打真实后端。
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
