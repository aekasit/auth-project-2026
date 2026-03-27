<!-- frontend/src/views/Admin.vue -->
<template>
    <div class="admin-container">
        <nav class="navbar">
            <h1>Admin Dashboard</h1>
            <div class="user-info">
                <span>Welcome, {{ authStore.username }}</span>
                <button @click="goToDashboard" class="dashboard-btn">Dashboard</button>
                <button @click="handleLogout" class="logout-btn">Logout</button>
            </div>
        </nav>

        <div class="content">
            <div class="stats-bar">
                <div class="stat-card">
                    <h3>Online Users</h3>
                    <p class="stat-number">{{ sessions.length }}</p>
                </div>
                <div class="stat-card">
                    <h3>Total Users</h3>
                    <p class="stat-number">{{ totalUsers }}</p>
                </div>
                <button @click="kickAllUsers" class="kick-all-btn" :disabled="loading">
                    Kick All Users
                </button>
            </div>

            <div class="sessions-table">
                <h2>Active Sessions</h2>

                <div v-if="loading" class="loading">
                    <div class="spinner"></div>
                    Loading...
                </div>

                <table v-else>
                    <thead>
                        <tr>
                            <th>Username</th>
                            <th>Device</th>
                            <th>Login Time</th>
                            <th>Last Active</th>
                            <th>Online Time</th>
                            <th>Actions</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr v-for="session in sessions" :key="session.username">
                            <td>
                                <strong>{{ session.username }}</strong>
                                <span v-if="session.username === authStore.username" class="badge">You</span>
                            </td>
                            <td>
                                <div class="device-info">
                                    <span class="device-icon">{{ getDeviceIcon(session.deviceInfo) }}</span>
                                    <span>{{ parseDeviceInfo(session.deviceInfo) }}</span>
                                </div>
                            </td>
                            <td>{{ formatDate(session.loginTime) }}</td>
                            <td>{{ formatDate(session.lastActive) }}</td>
                            <td>{{ session.onlineTime || '-' }}</td>
                            <td>
                                <button v-if="session.username !== authStore.username"
                                    @click="kickUser(session.username)" class="kick-btn"
                                    :disabled="kicking === session.username">
                                    {{ kicking === session.username ? 'Kicking...' : 'Kick' }}
                                </button>
                                <span v-else class="current-user-badge">Current</span>
                            </td>
                        </tr>
                        <tr v-if="sessions.length === 0">
                            <td colspan="6" class="empty">No active sessions</td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </div>

        <!-- Toast Notification -->
        <div v-if="toast.show" class="toast" :class="toast.type">
            {{ toast.message }}
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
const sessions = ref([])
const totalUsers = ref(0)
const loading = ref(false)
const kicking = ref(null)
let intervalId = null

const toast = ref({
    show: false,
    message: '',
    type: 'success'
})

const showToast = (message, type = 'success') => {
    toast.value = { show: true, message, type }
    setTimeout(() => {
        toast.value.show = false
    }, 3000)
}

const fetchSessions = async () => {
    loading.value = true
    try {
        const response = await api.get('/admin/sessions')
        sessions.value = response.data
        totalUsers.value = sessions.value.length
    } catch (error) {
        console.error('Failed to fetch sessions:', error)
        if (error.response?.status === 403) {
            showToast('Admin access required', 'error')
            router.push('/dashboard')
        }
    } finally {
        loading.value = false
    }
}

const kickUser = async (username) => {
    kicking.value = username
    try {
        const response = await api.post(`/admin/kick/${username}`)
        if (response.data.success) {
            showToast(`${username} has been kicked`, 'success')
            await fetchSessions()  // Refresh list
        } else {
            showToast(response.data.message, 'error')
        }
    } catch (error) {
        showToast(`Failed to kick ${username}`, 'error')
    } finally {
        kicking.value = null
    }
}

const kickAllUsers = async () => {
    if (!confirm('Are you sure you want to kick all users? You will remain logged in.')) {
        return
    }

    try {
        const response = await api.post(`/admin/kick-all-except-me?currentUser=${authStore.username}`)
        showToast(`Kicked ${response.data.kickedCount} users`, 'success')
        await fetchSessions()
    } catch (error) {
        showToast('Failed to kick all users', 'error')
    }
}

const formatDate = (dateStr) => {
    if (!dateStr) return '-'
    try {
        const date = new Date(dateStr)
        return date.toLocaleString()
    } catch {
        return dateStr
    }
}

const parseDeviceInfo = (deviceInfo) => {
    if (!deviceInfo) return 'Unknown'
    try {
        const info = JSON.parse(deviceInfo)
        return info.userAgent ? info.userAgent.split(' ')[0] : deviceInfo.substring(0, 30)
    } catch {
        return deviceInfo.substring(0, 30)
    }
}

const getDeviceIcon = (deviceInfo) => {
    if (!deviceInfo) return '💻'
    const info = deviceInfo.toLowerCase()
    if (info.includes('mobile') || info.includes('android') || info.includes('iphone')) return '📱'
    if (info.includes('tablet') || info.includes('ipad')) return '📟'
    return '💻'
}

const goToDashboard = () => {
    router.push('/dashboard')
}

const handleLogout = async () => {
    await authStore.logout()
}

onMounted(() => {
    fetchSessions()
    intervalId = setInterval(fetchSessions, 10000) // Refresh every 10 seconds
})

onUnmounted(() => {
    if (intervalId) clearInterval(intervalId)
})
</script>

<style scoped>
.admin-container {
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

.dashboard-btn,
.logout-btn {
    padding: 0.5rem 1rem;
    border: none;
    border-radius: 5px;
    cursor: pointer;
}

.dashboard-btn {
    background: #28a745;
    color: white;
}

.logout-btn {
    background: #dc3545;
    color: white;
}

.content {
    padding: 2rem;
    max-width: 1200px;
    margin: 0 auto;
}

.stats-bar {
    display: flex;
    gap: 1rem;
    margin-bottom: 2rem;
    align-items: center;
}

.stat-card {
    background: white;
    padding: 1rem 2rem;
    border-radius: 10px;
    text-align: center;
    flex: 1;
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.stat-card h3 {
    margin: 0 0 0.5rem;
    font-size: 0.9rem;
    color: #666;
}

.stat-number {
    font-size: 2rem;
    font-weight: bold;
    margin: 0;
    color: #333;
}

.kick-all-btn {
    padding: 0.75rem 1.5rem;
    background: #ffc107;
    color: #333;
    border: none;
    border-radius: 5px;
    cursor: pointer;
    font-weight: bold;
}

.kick-all-btn:disabled {
    opacity: 0.6;
    cursor: not-allowed;
}

.sessions-table {
    background: white;
    border-radius: 10px;
    overflow: hidden;
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.sessions-table h2 {
    padding: 1rem 1.5rem;
    margin: 0;
    border-bottom: 1px solid #eee;
}

table {
    width: 100%;
    border-collapse: collapse;
}

th,
td {
    padding: 1rem;
    text-align: left;
    border-bottom: 1px solid #eee;
}

th {
    background: #f8f9fa;
    font-weight: 600;
    color: #666;
}

tr:hover {
    background: #fafafa;
}

.badge {
    display: inline-block;
    background: #28a745;
    color: white;
    font-size: 0.7rem;
    padding: 0.2rem 0.5rem;
    border-radius: 10px;
    margin-left: 0.5rem;
}

.device-info {
    display: flex;
    align-items: center;
    gap: 0.5rem;
}

.device-icon {
    font-size: 1.2rem;
}

.kick-btn {
    padding: 0.25rem 0.75rem;
    background: #dc3545;
    color: white;
    border: none;
    border-radius: 3px;
    cursor: pointer;
    font-size: 0.8rem;
}

.kick-btn:disabled {
    opacity: 0.6;
    cursor: not-allowed;
}

.current-user-badge {
    color: #28a745;
    font-size: 0.8rem;
}

.empty {
    text-align: center;
    color: #999;
    padding: 2rem;
}

.loading {
    display: flex;
    justify-content: center;
    align-items: center;
    gap: 1rem;
    padding: 2rem;
    color: #666;
}

.spinner {
    width: 20px;
    height: 20px;
    border: 2px solid #f3f3f3;
    border-top: 2px solid #667eea;
    border-radius: 50%;
    animation: spin 1s linear infinite;
}

@keyframes spin {
    0% {
        transform: rotate(0deg);
    }

    100% {
        transform: rotate(360deg);
    }
}

.toast {
    position: fixed;
    bottom: 20px;
    right: 20px;
    padding: 1rem 1.5rem;
    border-radius: 8px;
    color: white;
    z-index: 1000;
    animation: slideIn 0.3s ease;
}

.toast.success {
    background: #28a745;
}

.toast.error {
    background: #dc3545;
}

@keyframes slideIn {
    from {
        transform: translateX(100%);
        opacity: 0;
    }

    to {
        transform: translateX(0);
        opacity: 1;
    }
}
</style>