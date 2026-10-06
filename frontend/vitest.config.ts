import { fileURLToPath } from 'node:url'

import { defineConfig } from 'vitest/config'

export default defineConfig({
  resolve: {
    dedupe: ['react', 'react-dom'],
    alias: {
      '@kyc/ui': fileURLToPath(new URL('../DS/index.ts', import.meta.url)),
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    exclude: ['dist/**', 'node_modules/**', 'e2e/**'],
  },
})
