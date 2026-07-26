import {defineConfig} from 'vite'
import vue from '@vitejs/plugin-vue'
import {createHtmlPlugin} from 'vite-plugin-html'
import vueDevTools from 'vite-plugin-vue-devtools'


export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
    createHtmlPlugin({})],
  server: {
    host: 'localhost',
    port: 5173,
    strictPort: true,
    cors: true,
    allowedHosts: ['localhost'],
    hmr: {
      host: 'localhost',
      port: 5173,
    },
    open: false
  },
  build: {
    sourcemap: true,
    outDir: 'dist',
    manifest: true
  },
  css: {
    devSourcemap: true
  }
})
