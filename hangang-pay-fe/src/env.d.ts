/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_AUTH_MOCK?: 'true' | 'false'
  readonly VITE_AUTH_MOCK_AUTHENTICATED?: 'true' | 'false'
  readonly VITE_AUTH_MOCK_ROLE?: 'USER' | 'MERCHANT'
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
