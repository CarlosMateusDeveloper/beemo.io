import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { fileURLToPath } from 'node:url'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // A página pública é compartilhada com o projeto de prévia da landing.
  resolve: { dedupe: ['react', 'react-dom'] },
  server: {
    fs: {
      allow: [fileURLToPath(new URL('.', import.meta.url)), fileURLToPath(new URL('../landing-page', import.meta.url))],
    },
  },
})
