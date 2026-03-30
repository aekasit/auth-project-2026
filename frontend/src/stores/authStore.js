// frontend/src/stores/authStore.js
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import api from '@/api/axios'
import router from '@/router'

export const useAuthStore = defineStore('auth', () => {
  // ==================== STATE ====================
  const user = ref(null)
  const profile = ref(null)
  const isAuthenticated = ref(false)
  const loading = ref(false)
  const profileLoading = ref(false)
  const profileFetched = ref(false)
  const error = ref(null)

  // ==================== GETTERS ====================
  const username = computed(() => user.value?.username)
  const roles = computed(() => user.value?.roles || [])
  const isAdmin = computed(() => roles.value.includes('ROLE_ADMIN'))
  const isManager = computed(() => roles.value.includes('ROLE_MANAGER'))
  const isUser = computed(() => roles.value.includes('ROLE_USER'))

  const fullName = computed(() => profile.value?.fullName || username.value)
  const email = computed(() => profile.value?.email)
  const avatar = computed(() => profile.value?.avatar || '/default-avatar.png')
  const phone = computed(() => profile.value?.phone)
  const address = computed(() => profile.value?.address)

  // ==================== UTILITIES ====================
  const getDeviceInfo = () => {
    return JSON.stringify({
      userAgent: navigator.userAgent,
      platform: navigator.platform,
      language: navigator.language,
      screenResolution: `${window.screen.width}x${window.screen.height}`,
      timestamp: new Date().toISOString()
    })
  }

  // ==================== ACTIONS ====================

  /**
   * Login user
   */
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

        // ดึง profile หลังจาก login สำเร็จ
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

  // frontend/src/stores/authStore.js

const fetchUserProfile = async (force = false) => {
  // ถ้ามี profile แล้วและไม่ใช่ force ให้ข้าม
  if (profile.value && !force) {
    console.log('Profile already loaded, skipping...')
    return profile.value
  }
  
  // ถ้ากำลังโหลดอยู่ ให้ข้าม
  if (profileLoading.value) {
    console.log('Profile already loading, skipping...')
    return null
  }
  
  profileLoading.value = true
  
  try {
    console.log('Fetching user profile...')
    const response = await api.get('/user/profile')
    
    if (response.data) {
      profile.value = response.data
      profileFetched.value = true
      console.log('Profile loaded:', profile.value)
      return response.data
    }
  } catch (error) {
    console.error('Failed to fetch profile:', error)
    
    // 🔥 ถ้า 403 หรือ 401 ให้ใช้ข้อมูล fallback
    if (error.response?.status === 403 || error.response?.status === 401) {
      console.log('Profile API not accessible, using fallback data')
      
      // ใช้ข้อมูลจาก user แทน
      profile.value = {
        fullName: user.value?.username,
        email: '',
        phone: '',
        address: '',
        createdAt: new Date().toISOString()
      }
      profileFetched.value = true
      return profile.value
    }
    
    return null
  } finally {
    profileLoading.value = false
  }
}

  /**
   * Fetch user profile from backend
   */
  // const fetchUserProfile = async (force = false) => {
  //   // ถ้ามี profile แล้วและไม่ใช่ force ให้ข้าม
  //   if (profile.value && !force) {
  //     console.log('Profile already loaded, skipping...')
  //     return profile.value
  //   }

  //   // ถ้ากำลังโหลดอยู่ ให้ข้าม
  //   if (profileLoading.value) {
  //     console.log('Profile already loading, skipping...')
  //     return null
  //   }

  //   profileLoading.value = true

  //   try {
  //     const response = await api.get('/user/profile')
  //     if (response.data) {
  //       profile.value = response.data
  //       profileFetched.value = true
  //       console.log('Profile loaded:', profile.value)
  //       return response.data
  //     }
  //   } catch (error) {
  //     console.error('Failed to fetch profile:', error)

  //     // 403 หมายถึงไม่มีสิทธิ์ (อาจเป็น endpoint ยังไม่มี)
  //     if (error.response?.status === 403) {
  //       console.log('Profile endpoint not accessible, using fallback')
  //       // ใช้ข้อมูล fallback
  //       profile.value = {
  //         fullName: user.value?.username,
  //         email: '',
  //         phone: '',
  //         address: '',
  //         createdAt: new Date().toISOString()
  //       }
  //       profileFetched.value = true
  //       return profile.value
  //     }

  //     // 401 หมายถึง session หมดอายุ
  //     if (error.response?.status === 401) {
  //       clearAuthData()
  //       router.push('/login')
  //     }
  //     return null
  //   } finally {
  //     profileLoading.value = false
  //   }
  // }

  /**
   * Update user profile
   */
  const updateProfile = async (profileData) => {
    try {
      const response = await api.put('/user/profile', profileData)
      if (response.data.success) {
        profile.value = { ...profile.value, ...profileData }
        return { success: true, data: response.data }
      }
      return { success: false, error: response.data.message }
    } catch (error) {
      return { success: false, error: error.response?.data?.message || 'Update failed' }
    }
  }

  /**
   * Change password
   */
  const changePassword = async (oldPassword, newPassword) => {
    try {
      const response = await api.post('/user/change-password', {
        oldPassword,
        newPassword
      })
      return { success: true, data: response.data }
    } catch (error) {
      return { success: false, error: error.response?.data?.message || 'Password change failed' }
    }
  }

  /**
   * Logout user
   */
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

  /**
   * Clear all authentication data
   */
  const clearAuthData = () => {
    user.value = null
    profile.value = null
    isAuthenticated.value = false
    profileFetched.value = false
    error.value = null
    localStorage.removeItem('user')
    sessionStorage.clear()
  }

  /**
   * Check authentication status on app start
   */
  const checkAuth = async () => {
    const savedUser = localStorage.getItem('user')
    if (savedUser && !profile.value && !profileFetched.value) {
      user.value = JSON.parse(savedUser)
      isAuthenticated.value = true
      await fetchUserProfile()
    }
  }

  // ==================== RETURN ====================
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
    isManager,
    isUser,
    fullName,
    email,
    avatar,
    phone,
    address,

    // Actions
    login,
    logout,
    fetchUserProfile,
    updateProfile,
    changePassword,
    checkAuth,
    clearAuthData
  }
})