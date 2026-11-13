/// <reference types="vitest" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

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
  },
})
