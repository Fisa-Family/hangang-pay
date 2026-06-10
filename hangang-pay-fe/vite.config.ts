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
})
