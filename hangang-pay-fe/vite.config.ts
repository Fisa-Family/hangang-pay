import { defineConfig } from 'vite'
import react, { reactCompilerPreset } from '@vitejs/plugin-react'
import babel from '@rolldown/plugin-babel'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'

const apiProxy = {
  target: 'http://localhost:8080',
  changeOrigin: true,
  configure: (proxy: {
    on: (
      event: 'proxyReq',
      handler: { (proxyReq: { removeHeader: (name: string) => void }): void }
    ) => void
  }) => {
    proxy.on('proxyReq', (proxyReq) => {
      // Local Vite proxy should behave like a same-origin hop for the backend.
      proxyReq.removeHeader('origin')
    })
  },
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [tailwindcss(), react(), babel({ presets: [reactCompilerPreset()] })],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    allowedHosts: ['.trycloudflare.com'],
    proxy: {
      '/api': apiProxy,
    },
  },
  preview: {
    proxy: {
      '/api': apiProxy,
    },
  },
  build: {
    rollupOptions: {
      output: {
        // 코어 라이브러리를 별도 청크로 분리해 캐싱 효율을 높인다.
        manualChunks(id) {
          if (!id.includes('node_modules')) return undefined
          if (id.includes('react-router') || id.includes('/react/') || id.includes('/react-dom/')) {
            return 'vendor-react'
          }
          if (id.includes('@tanstack')) {
            return 'vendor-query'
          }
          if (id.includes('zustand')) {
            return 'vendor-zustand'
          }
          if (id.includes('@zxing') || id.includes('jsqr')) {
            return 'vendor-qr'
          }
          return 'vendor'
        },
      },
    },
  },
})
