import { fileURLToPath } from 'node:url'

import { reactRouter } from '@react-router/dev/vite'
import { defineConfig } from 'vite'

const backendTarget = process.env.KYC_BACKEND_URL ?? 'http://127.0.0.1:8080'

export default defineConfig({
  envDir: false,
  resolve: {
    dedupe: ['react', 'react-dom'],
    alias: {
      '@kyc/ui': fileURLToPath(new URL('../DS/index.ts', import.meta.url)),
    },
  },
  server: {
    proxy: {
      '/api/v1': backendTarget,
      '^/reviewer$': backendTarget,
      '^/administrator$': backendTarget,
    },
  },
  plugins: [reactRouter()],
})
