import {defineConfig} from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'


export default defineConfig({
  plugins: [
    vue(),
    // Tailwind v4: eigener Vite-Plugin statt PostCSS-Plugin (postcss.config.mjs entfaellt
    // dadurch komplett) - das ist der von Tailwind selbst empfohlene Weg fuer Vite-Projekte.
    tailwindcss()],
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
