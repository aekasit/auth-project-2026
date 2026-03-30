// frontend/src/api/axios.js
import axios from 'axios'

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api'

const api = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
  timeout: 10000
})

let isRefreshing = false
let failedQueue = []

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
  console.log('🔴 Redirecting to login...')
  localStorage.removeItem('user')
  sessionStorage.clear()
  document.cookie.split(';').forEach(cookie => {
    const name = cookie.split('=')[0].trim()
    document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;`
  })
  if (!window.location.pathname.includes('/login')) {
    window.location.href = '/login'
  }
}

// 🔥 Request interceptor - แสดง cookie
api.interceptors.request.use(
  config => {
    console.log(`📤 [${config.method?.toUpperCase()}] ${config.url}`)
    console.log(`   Cookies: ${document.cookie || '(none)'}`)
    return config
  },
  error => Promise.reject(error)
)

// 🔥 Response interceptor - แสดงทุก response
api.interceptors.response.use(
  response => {
    console.log(`📥 [${response.status}] ${response.config.url}`)
    return response
  },
  async error => {
    const originalRequest = error.config
    
    console.log('========== INTERCEPTOR DEBUG ==========')
    console.log('Error status:', error.response?.status)
    console.log('Error URL:', originalRequest?.url)
    console.log('Error message:', error.response?.data?.message)
    console.log('_retry:', originalRequest?._retry)
    console.log('isRefreshing:', isRefreshing)
    console.log('Current cookies:', document.cookie)
    console.log('=======================================')
    
    // 🔥 ตรวจสอบเงื่อนไข refresh
    const isRefreshRequest = originalRequest?.url?.includes('/auth/refresh')
    const isLoginRequest = originalRequest?.url?.includes('/auth/login')
    const isUnauthorized = error.response?.status === 401 || error.response?.status === 403
    
    console.log('isRefreshRequest:', isRefreshRequest)
    console.log('isLoginRequest:', isLoginRequest)
    console.log('isUnauthorized:', isUnauthorized)
    
    // 🔥 ถ้าเป็น 401 และไม่ใช่ refresh/login
    if (isUnauthorized && !isRefreshRequest && !isLoginRequest) {
      console.log('🔄 Attempting to refresh...')
      
      if (isRefreshing) {
        console.log('⏳ Already refreshing, queueing...')
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject })
        }).then(() => {
          return api(originalRequest)
        }).catch(err => Promise.reject(err))
      }
      
      originalRequest._retry = true
      isRefreshing = true
      
      try {
        console.log('📡 Calling refresh endpoint...')
        const refreshResponse = await axios.post(`${API_BASE_URL}/auth/refresh`, {}, {
          withCredentials: true,
          headers: {
            'Content-Type': 'application/json'
          }
        })
        
        console.log('Refresh response status:', refreshResponse.status)
        console.log('Refresh response data:', refreshResponse.data)
        
        if (refreshResponse.status === 200) {
          console.log('✅ Refresh successful!')
          processQueue(null)
          return api(originalRequest)
        } else {
          throw new Error('Refresh failed')
        }
      } catch (refreshError) {
        console.error('❌ Refresh error:', refreshError)
        processQueue(refreshError)
        redirectToLogin()
        return Promise.reject(refreshError)
      } finally {
        isRefreshing = false
      }
    }
    
    // 🔥 403 หรืออื่นๆ
    if (error.response?.status === 403) {
      console.log('🚫 403 Forbidden - redirecting')
      redirectToLogin()
    }
    
    return Promise.reject(error)
  }
)

export default api