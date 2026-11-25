import { fileURLToPath } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

const projectRoot = fileURLToPath(new URL('../', import.meta.url))

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, projectRoot, ['BACKEND_URL', 'PORT'])
  const target = env.BACKEND_URL || 'http://127.0.0.1:' + (env.PORT || '8080')
  const proxy = { '/api': target, '/health': target }
  return { plugins: [react()], server: { proxy }, preview: { proxy } }
})
