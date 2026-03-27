// frontend/src/stores/authStore.js
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import api from '@/api/axios'
import router from '@/router'

export const useAuthStore = defineStore('auth', () => {
  // State
  const user = ref(null)
  const profile = ref(null)
  const isAuthenticated = ref(false)
  const loading = ref(false)
  const profileLoading = ref(false)
  const profileFetched = ref(false)
  const error = ref(null)

  // Getters
  const username = computed(() => user.value?.username)
  const roles = computed(() => user.value?.roles || [])
  const isAdmin = computed(() => roles.value.includes('ROLE_ADMIN'))
  const fullName = computed(() => profile.value?.fullName || username.value)
  const email = computed(() => profile.value?.email)
  const avatar = computed(() => profile.value?.avatar || '/default-avatar.png')

  const getDeviceInfo = () => {
    return JSON.stringify({
      userAgent: navigator.userAgent,
      platform: navigator.platform,
      language: navigator.language,
      timestamp: new Date().toISOString()
    })
  }

  const login = async (credentials) => {
    loading.value = true
    error.value = null
    profileFetched.value = false
    
    try {
      const response = await api.post('/auth/login', {
        username: credentials.username,
        password: credentials.password,
        deviceInfo: getDeviceInfo()
      })
      
      if (response.data.success) {
        user.value = {
          username: response.data.username,
          roles: response.data.roles,
          expiresIn: response.data.expiresIn
        }
        isAuthenticated.value = true
        localStorage.setItem('user', JSON.stringify(user.value))
        
        await fetchUserProfile()
        
        return { success: true, data: response.data }
      }
      
      error.value = response.data.message
      return { success: false, error: response.data.message }
      
    } catch (err) {
      error.value = err.response?.data?.message || 'Login failed'
      return { success: false, error: error.value }
    } finally {
      loading.value = false
    }
  }

  const fetchUserProfile = async (force = false) => {
    if (profile.value && !force) {
      console.log('Profile already loaded, skipping...')
      return profile.value
    }
    
    if (profileLoading.value) {
      console.log('Profile already loading, skipping...')
      return null
    }
    
    profileLoading.value = true
    
    try {
      const response = await api.get('/user/profile')
      if (response.data) {
        profile.value = response.data
        profileFetched.value = true
        console.log('Profile loaded:', profile.value)
        return response.data
      }
    } catch (error) {
      console.error('Failed to fetch profile:', error)
      if (error.response?.status === 401) {
        clearAuthData()
        router.push('/login')
      }
      return null
    } finally {
      profileLoading.value = false
    }
  }

  const logout = async () => {
    loading.value = true
    
    try {
      await api.post('/auth/logout')
      clearAuthData()
      router.push('/login')
      return { success: true }
    } catch (error) {
      console.error('Logout error:', error)
      clearAuthData()
      router.push('/login')
      return { success: false, error: error.message }
    } finally {
      loading.value = false
    }
  }

  const clearAuthData = () => {
    user.value = null
    profile.value = null
    isAuthenticated.value = false
    profileFetched.value = false
    error.value = null
    localStorage.removeItem('user')
    sessionStorage.clear()
  }

  const checkAuth = async () => {
    const savedUser = localStorage.getItem('user')
    if (savedUser && !profile.value && !profileFetched.value) {
      user.value = JSON.parse(savedUser)
      isAuthenticated.value = true
      await fetchUserProfile()
    }
  }

  // Return all state and actions
  return {
    // State
    user,
    profile,
    isAuthenticated,
    loading,
    profileLoading,
    error,
    
    // Getters
    username,
    roles,
    isAdmin,
    fullName,
    email,
    avatar,
    
    // Actions
    login,
    logout,
    fetchUserProfile,
    checkAuth,
    clearAuthData
  }
})