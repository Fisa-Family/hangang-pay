import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { AppProviders } from '@/app/providers'

async function prepare() {
  if (import.meta.env.VITE_ENABLE_MSW === 'true') {
    const { worker } = await import('./mocks/browser')
    await worker.start({ onUnhandledRequest: 'bypass' })
    // 서비스워커가 실제로 이 페이지를 제어하도록 보장
    await navigator.serviceWorker.ready
    const role = import.meta.env.VITE_MSW_ROLE ?? 'USER'
    localStorage.setItem('role', role)
  }
}

prepare().then(() => {
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <AppProviders>
        <App />
      </AppProviders>
    </StrictMode>
  )
})
