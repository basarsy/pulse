/// <reference types="vitest" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const webStorageOff = process.allowedNodeEnvironmentFlags.has('--experimental-webstorage')
  ? ['--no-experimental-webstorage']
  : []

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // The dashboard calls the API same-origin; in dev, forward to the app on :8080.
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws-pulse': { target: 'http://localhost:8080', ws: true },
    },
  },
  test: {
    environment: 'jsdom',
    // Node 25+ defines its own localStorage global (undefined without --localstorage-file). Vitest
    // 1's jsdom environment only replaces Node globals on its own list, and localStorage isn't on
    // it, so tests would see Node's instead of jsdom's. Turn Node's off in the test workers where
    // the flag exists (Node 22.4+; Node 20 doesn't have it).
    poolOptions: {
      threads: { execArgv: webStorageOff },
      forks: { execArgv: webStorageOff },
    },
  },
})
