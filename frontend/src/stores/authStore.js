import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import api from '@/api/axios'
import router from '@/router'

export const useAuthStore = defineStore('auth', () => {
  const user = ref(null)
  const isAuthenticated = ref(false)
  const loading = ref(false)

  const username = computed(() => user.value?.username)
  const roles = computed(() => user.value?.roles || [])
  const isAdmin = computed(() => roles.value.includes('ROLE_ADMIN'))

  const getDeviceInfo = () => {
    return JSON.stringify({
      userAgent: navigator.userAgent,
      platform: navigator.platform,
      timestamp: new Date().toISOString()
    })
  }

  const login = async (credentials) => {
    loading.value = true
    
    try {
      const response = await api.post('/auth/login', {
        username: credentials.username,
        password: credentials.password,
        deviceInfo: getDeviceInfo()
      })
      
      if (response.data.success) {
        user.value = {
          username: response.data.username,
          roles: response.data.roles
        }
        isAuthenticated.value = true
        localStorage.setItem('user', JSON.stringify(user.value))
        return { success: true }
      }
      
      return { success: false, error: response.data.message }
    } catch (err) {
      return { success: false, error: err.response?.data?.message || 'Login failed' }
    } finally {
      loading.value = false
    }
  }

  const logout = async () => {
    try {
      await api.post('/auth/logout')
    } catch (error) {
      console.error('Logout error:', error)
    } finally {
      user.value = null
      isAuthenticated.value = false
      localStorage.removeItem('user')
      router.push('/login')
    }
  }

  const checkAuth = () => {
    const savedUser = localStorage.getItem('user')
    if (savedUser) {
      user.value = JSON.parse(savedUser)
      isAuthenticated.value = true
    }
  }

  return {
    user,
    isAuthenticated,
    loading,
    username,
    roles,
    isAdmin,
    login,
    logout,
    checkAuth
  }
})
