<template>
  <div class="dashboard">
    <nav class="navbar">
      <h1>Dashboard</h1>
      <div class="user-info">
        <div v-if="authStore.profileLoading" class="loading-spinner">
          Loading...
        </div>

        <template v-else-if="authStore.profile">
          <div class="user-avatar">
            <img :src="authStore.avatar" alt="Avatar" />
          </div>
          <div class="user-details">
            <strong>{{ authStore.fullName }}</strong>
            <small>{{ authStore.email }}</small>
          </div>
        </template>

        <button @click="handleLogout" class="logout-btn">Logout</button>
      </div>
    </nav>

    <div class="content">
      <div class="profile-card" v-if="authStore.profile">
        <h3>User Profile</h3>
        <div class="profile-info">
          <div class="info-row"><label>Username:</label><span>{{ authStore.username }}</span></div>
          <div class="info-row"><label>Full Name:</label><span>{{ authStore.profile.fullName || '-' }}</span></div>
          <div class="info-row"><label>Email:</label><span>{{ authStore.profile.email || '-' }}</span></div>
          <div class="info-row"><label>Roles:</label><span><span v-for="role in authStore.roles" :key="role"
                class="role-badge">{{ role }}</span></span></div>
        </div>
      </div>

      <div class="test-section">
        <h3>Debug Tools</h3>
        <button @click="testRefresh" class="test-btn">Test Refresh</button>
        <button @click="checkCookies" class="test-btn info">Show Cookies</button>
        <div v-if="debugInfo" class="debug-output">
          <pre>{{ debugInfo }}</pre>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import api from '@/api/axios'

const router = useRouter()
const authStore = useAuthStore()
const debugInfo = ref('')
let intervalId = null

const handleLogout = async () => {
  await authStore.logout()
}

const testRefresh = async () => {
  debugInfo.value = 'Testing refresh...\n'
  try {
    const response = await api.post('/auth/refresh')
    debugInfo.value += `✅ Success: ${JSON.stringify(response.data, null, 2)}`
  } catch (error) {
    debugInfo.value += `❌ Failed: ${error.response?.status} - ${error.response?.data?.message}`
  }
}

const checkCookies = () => {
  debugInfo.value = '=== Cookies ===\n'
  debugInfo.value += document.cookie || '(empty)'
  debugInfo.value += '\n\n=== Local Storage ===\n'
  debugInfo.value += JSON.stringify(localStorage.getItem('user') || 'empty', null, 2)
}

const checkSession = async () => {
  try {
    await api.get('/user/profile')
  } catch (error) {
    // Silent fail - interceptor handles redirect
  }
}

onMounted(() => {
  intervalId = setInterval(checkSession, 30000)
})

onUnmounted(() => {
  if (intervalId) clearInterval(intervalId)
})
</script>

<style scoped>
.dashboard {
  min-height: 100vh;
  background: #f5f5f5;
}

.navbar {
  background: white;
  padding: 1rem 2rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.user-info {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.user-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  overflow: hidden;
}

.user-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.user-details {
  display: flex;
  flex-direction: column;
}

.user-details strong {
  font-size: 0.9rem;
}

.user-details small {
  font-size: 0.7rem;
  color: #666;
}

.logout-btn {
  padding: 0.5rem 1rem;
  background: #dc3545;
  color: white;
  border: none;
  border-radius: 5px;
  cursor: pointer;
}

.content {
  padding: 2rem;
  max-width: 800px;
  margin: 0 auto;
}

.profile-card {
  background: white;
  padding: 1.5rem;
  border-radius: 10px;
  margin-bottom: 2rem;
}

.profile-info {
  margin: 1rem 0;
}

.info-row {
  display: flex;
  padding: 0.5rem 0;
  border-bottom: 1px solid #eee;
}

.info-row label {
  width: 100px;
  font-weight: bold;
}

.role-badge {
  display: inline-block;
  background: #667eea;
  color: white;
  padding: 0.25rem 0.5rem;
  border-radius: 3px;
  font-size: 0.8rem;
  margin-right: 0.5rem;
}

.test-section {
  background: #f8f9fa;
  padding: 1.5rem;
  border-radius: 10px;
}

.test-btn {
  margin-right: 1rem;
  padding: 0.5rem 1rem;
  background: #6c757d;
  color: white;
  border: none;
  border-radius: 5px;
  cursor: pointer;
}

.test-btn.info {
  background: #17a2b8;
}

.debug-output {
  margin-top: 1rem;
  padding: 0.75rem;
  background: #1e1e1e;
  color: #d4d4d4;
  border-radius: 5px;
  font-family: monospace;
  font-size: 0.8rem;
  overflow-x: auto;
}

.debug-output pre {
  margin: 0;
  white-space: pre-wrap;
}

.loading-spinner {
  color: #666;
}
</style>