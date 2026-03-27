// frontend/src/api/axios.js
import axios from 'axios'

// ==================== CONFIGURATION ====================
const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api'

// ==================== AXIOS INSTANCES ====================
// Main instance - สำหรับ API ทั่วไป (ใช้ access_token)
const api = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
  timeout: 10000
})

// Refresh instance - สำหรับ refresh endpoint โดยเฉพาะ (ใช้ refresh_token)
const refreshApi = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
  timeout: 10000
})

// ==================== STATE ====================
let isRefreshing = false
let failedQueue = []

// ==================== UTILITIES ====================
const processQueue = (error, token = null) => {
  failedQueue.forEach(prom => {
    if (error) {
      prom.reject(error)
    } else {
      prom.resolve(token)
    }
  })
  failedQueue = []
}

const redirectToLogin = () => {
  console.log('🔴 Session expired, redirecting to login...')

  // Clear all auth data
  localStorage.removeItem('user')
  sessionStorage.clear()

  // Clear all cookies
  document.cookie.split(';').forEach(cookie => {
    const name = cookie.split('=')[0].trim()
    document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;`
  })

  // Redirect if not already on login page
  if (!window.location.pathname.includes('/login')) {
    window.location.href = '/login'
  }
}

// ==================== REQUEST INTERCEPTOR ====================
api.interceptors.request.use(
  config => {
    console.log(`📤 [${config.method?.toUpperCase()}] ${config.url}`)
    return config
  },
  error => {
    console.error('❌ Request error:', error)
    return Promise.reject(error)
  }
)

refreshApi.interceptors.request.use(
  config => {
    console.log(`🔄 [REFRESH] ${config.method?.toUpperCase()} ${config.url}`)
    return config
  },
  error => Promise.reject(error)
)

// ==================== RESPONSE INTERCEPTOR ====================
api.interceptors.response.use(
  response => {
    console.log(`📥 [${response.status}] ${response.config.url}`)
    return response
  },
  async error => {
    const originalRequest = error.config

    console.log(`⚠️ Response error: ${error.response?.status} - ${originalRequest?.url}`)

    // Check if this is a 401 and not a refresh/login request
    const shouldRefresh = error.response?.status === 401 &&
      !originalRequest._retry &&
      !originalRequest.url?.includes('/auth/refresh') &&
      !originalRequest.url?.includes('/auth/login')

    if (!shouldRefresh) {
      return Promise.reject(error)
    }

    console.log('🔄 Token expired, attempting refresh...')

    if (isRefreshing) {
      console.log('⏳ Refresh in progress, queuing request...')
      return new Promise((resolve, reject) => {
        failedQueue.push({ resolve, reject })
      }).then(() => {
        console.log('▶️ Retrying queued request')
        return api(originalRequest)
      }).catch(err => Promise.reject(err))
    }

    originalRequest._retry = true
    isRefreshing = true

    try {
      // Use refreshApi for the refresh call (handles different cookie path)
      console.log('📡 Calling refresh endpoint...')
      const refreshResponse = await refreshApi.post('/auth/refresh')

      if (refreshResponse.status === 200) {
        console.log('✅ Refresh successful')
        processQueue(null)
        return api(originalRequest)
      } else {
        throw new Error('Refresh failed')
      }
    } catch (refreshError) {
      console.error('❌ Refresh failed:', refreshError.response?.data?.message || refreshError.message)
      processQueue(refreshError)
      redirectToLogin()
      return Promise.reject(refreshError)
    } finally {
      isRefreshing = false
    }
  }
)

// ==================== EXPORT ====================
export default api