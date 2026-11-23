import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api/agents': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/api/executions': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/api/me': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/api/llm': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/api/admin': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/m1-health': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/m1-health/, '/actuator/health'),
      },
      '/api': {
        target: 'http://localhost:8086',
        changeOrigin: true,
      },
    },
  },
})
