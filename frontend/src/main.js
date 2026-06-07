import { createApp } from 'vue'
import { createPinia } from 'pinia'
import router from './router/index'
import App from './App.vue'
import Toast from 'vue-toastification'
import 'vue-toastification/dist/index.css'

import './api/axios'
import './style.css'


const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(Toast, {
    transition: "Vue-Toastification__bounce",
    maxToasts: 5,
    newestOnTop: true
})
app.mount('#app')