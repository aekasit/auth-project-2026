// frontend/src/main.js
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import api from './api/axios'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)
app.mount('#app')

// 🔥 ตรวจจับ session expiration แบบ real-time (ใช้ polling หรือ WebSocket)
// วิธีง่าย: ตรวจสอบทุก 30 วินาที
let checkInterval = null

const checkSession = async () => {
    const token = document.cookie.includes('access_token')
    const user = localStorage.getItem('user')

    if (token && user) {
        try {
            await api.get('/user/profile', { timeout: 5000 })
        } catch (error) {
            if (error.response?.status === 401) {
                console.log('Session expired, redirecting...')
                localStorage.removeItem('user')
                window.location.href = '/login'
            }
        }
    }
}

// เริ่มตรวจสอบ session ทุก 30 วินาที (เฉพาะเมื่อ user login)
const startSessionCheck = () => {
    if (checkInterval) clearInterval(checkInterval)
    checkInterval = setInterval(checkSession, 30000)
}

const stopSessionCheck = () => {
    if (checkInterval) {
        clearInterval(checkInterval)
        checkInterval = null
    }
}

// เก็บ function ไว้ใน window เพื่อให้ store เรียกใช้
window.startSessionCheck = startSessionCheck
window.stopSessionCheck = stopSessionCheck

export { startSessionCheck, stopSessionCheck }