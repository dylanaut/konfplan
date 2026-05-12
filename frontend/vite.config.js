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
        port: 5173,
        strictPort: true, // Wichtig, damit Vite nicht auf 5174 ausweicht
    },
    build: {
        sourcemap: true,
      outDir: 'dist'
    },
    css: {
        devSourcemap: true
    }
})