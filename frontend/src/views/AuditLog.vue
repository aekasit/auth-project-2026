<!-- frontend/src/views/AuditLog.vue -->
<template>
    <div class="audit-container">
        <nav class="navbar">
            <h1>Audit Log</h1>
            <div class="user-info">
                <span>{{ authStore.username }}</span>
                <button @click="goToDashboard" class="dashboard-btn">Dashboard</button>
                <button @click="goToAdmin" class="admin-btn">Admin</button>
                <button @click="handleLogout" class="logout-btn">Logout</button>
            </div>
        </nav>

        <div class="content">
            <!-- Filters -->
            <div class="filters">
                <input v-model="searchUsername" placeholder="Search by username..." />
                <select v-model="filterAction">
                    <option value="">All Actions</option>
                    <option value="LOGIN_SUCCESS">Login Success</option>
                    <option value="LOGIN_FAILED">Login Failed</option>
                    <option value="LOGOUT">Logout</option>
                    <option value="PASSWORD_CHANGE">Password Change</option>
                    <option value="ADMIN_KICK_USER">Admin Kick User</option>
                </select>
                <input type="date" v-model="startDate" />
                <input type="date" v-model="endDate" />
                <button @click="search" class="search-btn">Search</button>
                <button @click="exportCSV" class="export-btn">Export CSV</button>
            </div>

            <!-- Stats -->
            <div class="stats-cards">
                <div class="stat-card">
                    <h3>Login Success</h3>
                    <p class="stat-number">{{ stats.loginSuccess || 0 }}</p>
                </div>
                <div class="stat-card">
                    <h3>Login Failed</h3>
                    <p class="stat-number">{{ stats.loginFailed || 0 }}</p>
                </div>
                <div class="stat-card">
                    <h3>Logout</h3>
                    <p class="stat-number">{{ stats.logout || 0 }}</p>
                </div>
                <div class="stat-card">
                    <h3>Total Actions</h3>
                    <p class="stat-number">{{ stats.totalActions || 0 }}</p>
                </div>
            </div>

            <!-- Logs Table -->
            <div class="logs-table">
                <table>
                    <thead>
                        <tr>
                            <th>Time</th>
                            <th>Username</th>
                            <th>Action</th>
                            <th>Details</th>
                            <th>IP Address</th>
                            <th>Device</th>
                            <th>Browser</th>
                            <th>Status</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr v-for="log in logs" :key="log.id">
                            <td>{{ formatDate(log.timestamp) }}</td>
                            <td><strong>{{ log.username }}</strong></td>
                            <td>
                                <span :class="getActionClass(log.action)">
                                    {{ formatAction(log.action) }}
                                </span>
                            </td>
                            <td>{{ log.details || '-' }}</td>
                            <td>{{ log.ipAddress }}</td>
                            <td>{{ log.device }}</td>
                            <td>{{ log.browser }}</td>
                            <td>
                                <span :class="log.status === 'SUCCESS' ? 'success' : 'failed'">
                                    {{ log.status }}
                                </span>
                            </td>
                        </tr>
                        <tr v-if="logs.length === 0">
                            <td colspan="8" class="empty">No logs found</td>
                        </tr>
                    </tbody>
                </table>

                <!-- Pagination -->
                <div class="pagination">
                    <button @click="prevPage" :disabled="page === 0">Previous</button>
                    <span>Page {{ page + 1 }} of {{ totalPages }}</span>
                    <button @click="nextPage" :disabled="page >= totalPages - 1">Next</button>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import api from '@/api/axios'

const router = useRouter()
const authStore = useAuthStore()

const logs = ref([])
const stats = ref({})
const page = ref(0)
const totalPages = ref(1)
const searchUsername = ref('')
const filterAction = ref('')
const startDate = ref('')
const endDate = ref('')

const fetchLogs = async () => {
    try {
        // ตรวจสอบว่าเป็น admin หรือไม่
        if (!authStore.isAdmin) {
            console.log('Not admin, redirecting...')
            router.push('/dashboard')
            return
        }

        const response = await api.get('/audit/logs', {
            params: { page: page.value, size: 20 }
        })
        logs.value = response.data.content
        totalPages.value = response.data.totalPages
    } catch (error) {
        if (error.response?.status === 403) {
            console.log('Access denied, redirecting...')
            router.push('/dashboard')
        }
        console.error('Failed to fetch logs:', error)
    }
}

const fetchStats = async () => {
  try {
    // ส่งเป็น ISO datetime หรือใช้ since=7d
    const response = await api.get('/audit/stats', {
      params: { since: '7d' }  // หรือส่ง startDate/endDate
    })
    stats.value = response.data
  } catch (error) {
    console.error('Failed to fetch stats:', error)
  }
}

const search = () => {
    page.value = 0
    fetchLogs()
}

const prevPage = () => {
    if (page.value > 0) {
        page.value--
        fetchLogs()
    }
}

const nextPage = () => {
    if (page.value < totalPages.value - 1) {
        page.value++
        fetchLogs()
    }
}

const exportCSV = async () => {
    try {
        const response = await api.get('/audit/export', {
            params: {
                username: searchUsername.value,
                action: filterAction.value,
                startDate: startDate.value,
                endDate: endDate.value
            },
            responseType: 'blob'
        })

        const url = window.URL.createObjectURL(new Blob([response.data]))
        const link = document.createElement('a')
        link.href = url
        link.setAttribute('download', 'audit_logs.csv')
        document.body.appendChild(link)
        link.click()
        link.remove()
    } catch (error) {
        console.error('Export failed:', error)
    }
}

const formatDate = (date) => {
    return new Date(date).toLocaleString()
}

const formatAction = (action) => {
    const actions = {
        LOGIN_SUCCESS: 'Login',
        LOGIN_FAILED: 'Login Failed',
        LOGOUT: 'Logout',
        PASSWORD_CHANGE: 'Change Password',
        ADMIN_KICK_USER: 'Kick User',
        SESSION_EXPIRED: 'Session Expired'
    }
    return actions[action] || action
}

const getActionClass = (action) => {
    if (action.includes('SUCCESS')) return 'badge success'
    if (action.includes('FAILED')) return 'badge failed'
    if (action === 'LOGOUT') return 'badge info'
    return 'badge'
}

const goToDashboard = () => router.push('/dashboard')
const goToAdmin = () => router.push('/admin')
const handleLogout = async () => await authStore.logout()

onMounted(() => {
    fetchLogs()
    fetchStats()
})
</script>

<style scoped>
/* Styles similar to Admin.vue */
.audit-container {
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
.admin-btn,
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

.admin-btn {
    background: #6c757d;
    color: white;
}

.logout-btn {
    background: #dc3545;
    color: white;
}

.content {
    padding: 2rem;
    max-width: 1400px;
    margin: 0 auto;
}

.filters {
    display: flex;
    gap: 1rem;
    margin-bottom: 2rem;
    flex-wrap: wrap;
}

.filters input,
.filters select {
    padding: 0.5rem;
    border: 1px solid #ddd;
    border-radius: 5px;
}

.search-btn,
.export-btn {
    padding: 0.5rem 1rem;
    border: none;
    border-radius: 5px;
    cursor: pointer;
}

.search-btn {
    background: #667eea;
    color: white;
}

.export-btn {
    background: #28a745;
    color: white;
}

.stats-cards {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 1rem;
    margin-bottom: 2rem;
}

.stat-card {
    background: white;
    padding: 1rem;
    border-radius: 10px;
    text-align: center;
}

.stat-number {
    font-size: 2rem;
    font-weight: bold;
    margin: 0.5rem 0 0;
}

.logs-table {
    background: white;
    border-radius: 10px;
    overflow-x: auto;
}

table {
    width: 100%;
    border-collapse: collapse;
}

th,
td {
    padding: 0.75rem;
    text-align: left;
    border-bottom: 1px solid #eee;
}

th {
    background: #f8f9fa;
    font-weight: 600;
}

.badge {
    padding: 0.25rem 0.5rem;
    border-radius: 3px;
    font-size: 0.75rem;
}

.badge.success {
    background: #d4edda;
    color: #155724;
}

.badge.failed {
    background: #f8d7da;
    color: #721c24;
}

.badge.info {
    background: #d1ecf1;
    color: #0c5460;
}

.success {
    color: #28a745;
}

.failed {
    color: #dc3545;
}

.pagination {
    display: flex;
    justify-content: center;
    gap: 1rem;
    padding: 1rem;
}

.pagination button {
    padding: 0.5rem 1rem;
    background: #667eea;
    color: white;
    border: none;
    border-radius: 5px;
    cursor: pointer;
}

.empty {
    text-align: center;
    padding: 2rem;
    color: #999;
}
</style>