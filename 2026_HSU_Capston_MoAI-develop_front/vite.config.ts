import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const backendTarget = process.env.VITE_API_BASE_URL || 'http://localhost:8080'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  define: {
    // sockjs-client(CJS)가 참조하는 Node.js 전역 변수를 브라우저 환경에 폴리필
    global: 'globalThis',
  },
  optimizeDeps: {
    include: ['sockjs-client'],
  },
  server: {
    proxy: {
      '/api': {
        target: backendTarget,
        changeOrigin: true,
      },
    },
  },
})
