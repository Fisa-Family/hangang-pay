/// <reference types="vite/client" />

interface ImportMetaEnv {
  // 인증 mock 활성화 여부
  readonly VITE_AUTH_MOCK?: 'true' | 'false'
  readonly VITE_AUTH_MOCK_AUTHENTICATED?: 'true' | 'false'
  readonly VITE_AUTH_MOCK_ROLE?: 'USER' | 'MERCHANT'
  // API 베이스 URL, 미설정 시 로컬 기본값
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
