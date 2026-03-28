<!-- frontend/src/views/Dashboard.vue -->
<template>
  <div class="dashboard">
    <!-- Sidebar Navigation -->
    <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="sidebar-header">
        <div class="logo" @click="toggleSidebar">
          <span class="logo-icon">🔐</span>
          <span v-if="!sidebarCollapsed" class="logo-text">Auth System</span>
        </div>
        <button class="toggle-btn" @click="toggleSidebar">
          <i :class="sidebarCollapsed ? 'fas fa-chevron-right' : 'fas fa-chevron-left'"></i>
        </button>
      </div>

      <nav class="sidebar-nav">
        <router-link to="/dashboard" class="nav-item" :class="{ active: currentRoute === 'dashboard' }">
          <i class="fas fa-tachometer-alt"></i>
          <span v-if="!sidebarCollapsed">Dashboard</span>
        </router-link>

        <router-link to="/profile" class="nav-item" :class="{ active: currentRoute === 'profile' }">
          <i class="fas fa-user-circle"></i>
          <span v-if="!sidebarCollapsed">My Profile</span>
        </router-link>

        <router-link to="/change-password" class="nav-item" :class="{ active: currentRoute === 'change-password' }">
          <i class="fas fa-key"></i>
          <span v-if="!sidebarCollapsed">Change Password</span>
        </router-link>

        <div v-if="authStore.isAdmin" class="nav-divider"></div>
        
        <template v-if="authStore.isAdmin">
          <router-link to="/admin" class="nav-item" :class="{ active: currentRoute === 'admin' }">
            <i class="fas fa-users-cog"></i>
            <span v-if="!sidebarCollapsed">User Management</span>
          </router-link>

          <router-link to="/audit" class="nav-item" :class="{ active: currentRoute === 'audit' }">
            <i class="fas fa-history"></i>
            <span v-if="!sidebarCollapsed">Audit Log</span>
          </router-link>
        </template>

        <div class="nav-divider"></div>

        <button @click="handleLogout" class="nav-item logout-btn">
          <i class="fas fa-sign-out-alt"></i>
          <span v-if="!sidebarCollapsed">Logout</span>
        </button>
      </nav>

      <div class="sidebar-footer" v-if="!sidebarCollapsed">
        <div class="user-info-mini">
          <div class="user-avatar-mini">
            <img :src="authStore.avatar" alt="avatar" />
          </div>
          <div class="user-details-mini">
            <strong>{{ authStore.fullName || authStore.username }}</strong>
            <small>{{ authStore.email || authStore.username }}</small>
          </div>
        </div>
      </div>
    </aside>

    <!-- Main Content -->
    <main class="main-content">
      <!-- Top Header -->
      <header class="top-header">
        <div class="header-left">
          <h1>{{ pageTitle }}</h1>
          <p class="welcome-text">Welcome back, {{ authStore.fullName || authStore.username }}!</p>
        </div>
        <div class="header-right">
          <div class="notification-btn" @click="showNotifications = !showNotifications">
            <i class="fas fa-bell"></i>
            <span class="badge" v-if="notifications.length">{{ notifications.length }}</span>
          </div>
          <div class="user-menu" @click="showUserMenu = !showUserMenu">
            <div class="user-avatar">
              <img :src="authStore.avatar" alt="avatar" />
            </div>
            <span class="user-name">{{ authStore.fullName || authStore.username }}</span>
            <i class="fas fa-chevron-down"></i>
          </div>
          
          <!-- User Dropdown Menu -->
          <div class="dropdown-menu" v-if="showUserMenu">
            <router-link to="/profile" class="dropdown-item">
              <i class="fas fa-user"></i> Profile
            </router-link>
            <router-link to="/change-password" class="dropdown-item">
              <i class="fas fa-key"></i> Change Password
            </router-link>
            <div class="dropdown-divider"></div>
            <button @click="handleLogout" class="dropdown-item logout">
              <i class="fas fa-sign-out-alt"></i> Logout
            </button>
          </div>
          
          <!-- Notification Dropdown -->
          <div class="dropdown-menu notifications" v-if="showNotifications">
            <div class="dropdown-header">Notifications</div>
            <div v-for="notif in notifications" :key="notif.id" class="notification-item">
              <i :class="notif.icon"></i>
              <div>{{ notif.message }}</div>
              <small>{{ notif.time }}</small>
            </div>
            <div v-if="notifications.length === 0" class="notification-item">
              No notifications
            </div>
          </div>
        </div>
      </header>

      <!-- Dashboard Content -->
      <div class="content">
        <!-- Stats Cards - เฉพาะ Admin -->
        <div class="stats-grid" v-if="authStore.isAdmin">
          <div class="stat-card">
            <div class="stat-icon blue">
              <i class="fas fa-users"></i>
            </div>
            <div class="stat-info">
              <h3>Total Users</h3>
              <p class="stat-value">{{ stats.totalUsers || 0 }}</p>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon green">
              <i class="fas fa-user-check"></i>
            </div>
            <div class="stat-info">
              <h3>Online Now</h3>
              <p class="stat-value">{{ stats.onlineUsers || 0 }}</p>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon purple">
              <i class="fas fa-chart-line"></i>
            </div>
            <div class="stat-info">
              <h3>Today's Logins</h3>
              <p class="stat-value">{{ stats.todayLogins || 0 }}</p>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon orange">
              <i class="fas fa-clock"></i>
            </div>
            <div class="stat-info">
              <h3>Session Time</h3>
              <p class="stat-value">{{ stats.sessionTime || '0h' }}</p>
            </div>
          </div>
        </div>

        <!-- Welcome Card (สำหรับ non-admin) -->
        <div class="welcome-card" v-if="!authStore.isAdmin">
          <div class="welcome-icon">
            <i class="fas fa-smile-wink"></i>
          </div>
          <h2>Welcome, {{ authStore.fullName || authStore.username }}!</h2>
          <p>You are logged in as a regular user.</p>
          <div class="quick-actions">
            <router-link to="/profile" class="action-btn">
              <i class="fas fa-user-edit"></i> Edit Profile
            </router-link>
            <router-link to="/change-password" class="action-btn">
              <i class="fas fa-key"></i> Change Password
            </router-link>
          </div>
        </div>

        <!-- Profile Card -->
        <div class="profile-card" v-if="authStore.profile">
          <div class="profile-header">
            <div class="profile-avatar">
              <img :src="authStore.avatar" alt="avatar" />
              <button class="edit-avatar" @click="openAvatarModal">
                <i class="fas fa-camera"></i>
              </button>
            </div>
            <div class="profile-info">
              <h2>{{ authStore.fullName || authStore.username }}</h2>
              <p class="email">{{ authStore.email || 'No email set' }}</p>
              <div class="role-badges">
                <span v-for="role in authStore.roles" :key="role" class="role-badge">
                  {{ role.replace('ROLE_', '') }}
                </span>
              </div>
            </div>
            <button class="edit-profile-btn" @click="goToProfile">
              <i class="fas fa-edit"></i> Edit Profile
            </button>
          </div>

          <div class="profile-details">
            <div class="detail-item">
              <i class="fas fa-user"></i>
              <div>
                <label>Username</label>
                <p>{{ authStore.username }}</p>
              </div>
            </div>
            <div class="detail-item">
              <i class="fas fa-envelope"></i>
              <div>
                <label>Email</label>
                <p>{{ authStore.email || 'Not set' }}</p>
              </div>
            </div>
            <div class="detail-item">
              <i class="fas fa-phone"></i>
              <div>
                <label>Phone</label>
                <p>{{ authStore.profile?.phone || 'Not set' }}</p>
              </div>
            </div>
            <div class="detail-item">
              <i class="fas fa-map-marker-alt"></i>
              <div>
                <label>Address</label>
                <p>{{ authStore.profile?.address || 'Not set' }}</p>
              </div>
            </div>
            <div class="detail-item">
              <i class="fas fa-calendar"></i>
              <div>
                <label>Member Since</label>
                <p>{{ formatDate(authStore.profile?.createdAt) || 'Unknown' }}</p>
              </div>
            </div>
          </div>
        </div>

        <!-- Loading Profile -->
        <div v-else-if="authStore.profileLoading" class="loading-card">
          <div class="spinner"></div>
          <p>Loading profile...</p>
        </div>

        <!-- Recent Activity - เฉพาะ Admin -->
        <div class="activity-card" v-if="authStore.isAdmin && recentActivities.length > 0">
          <div class="card-header">
            <h3>Recent Activity</h3>
            <router-link to="/audit" class="view-all">View All</router-link>
          </div>
          <div class="activity-list">
            <div v-for="activity in recentActivities" :key="activity.id" class="activity-item">
              <div class="activity-icon" :class="getActivityClass(activity.action)">
                <i :class="getActivityIcon(activity.action)"></i>
              </div>
              <div class="activity-details">
                <p class="activity-action">{{ formatAction(activity.action) }}</p>
                <p class="activity-meta">
                  {{ activity.details || '-' }} • {{ formatTime(activity.timestamp) }}
                </p>
              </div>
              <div class="activity-ip">{{ activity.ipAddress }}</div>
            </div>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import api from '@/api/axios'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

// ==================== STATE ====================
const sidebarCollapsed = ref(false)
const showUserMenu = ref(false)
const showNotifications = ref(false)
const stats = ref({
  totalUsers: 0,
  onlineUsers: 0,
  todayLogins: 0,
  sessionTime: '0h'
})
const recentActivities = ref([])
const notifications = ref([
  { id: 1, icon: 'fas fa-check-circle', message: 'Login from new device', time: '2 minutes ago' },
  { id: 2, icon: 'fas fa-shield-alt', message: 'Security update available', time: '1 hour ago' }
])

// ==================== COMPUTED ====================
const currentRoute = computed(() => {
  const path = route.path
  if (path === '/dashboard') return 'dashboard'
  if (path === '/profile') return 'profile'
  if (path === '/change-password') return 'change-password'
  if (path === '/admin') return 'admin'
  if (path === '/audit') return 'audit'
  return 'dashboard'
})

const pageTitle = computed(() => {
  const titles = {
    dashboard: 'Dashboard',
    profile: 'My Profile',
    'change-password': 'Change Password',
    admin: 'User Management',
    audit: 'Audit Log'
  }
  return titles[currentRoute.value] || 'Dashboard'
})

// ==================== METHODS ====================
const toggleSidebar = () => {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

const handleLogout = async () => {
  await authStore.logout()
}

const goToProfile = () => {
  router.push('/profile')
}

const openAvatarModal = () => {
  alert('Avatar upload coming soon!')
}

const fetchStats = async () => {
  if (!authStore.isAdmin) return
  
  try {
    const sessionsRes = await api.get('/admin/sessions')
    stats.value.onlineUsers = sessionsRes.data?.length || 0
  } catch (error) {
    console.error('Failed to fetch stats:', error)
  }
}

const fetchRecentActivities = async () => {
  if (!authStore.isAdmin) return
  
  try {
    const response = await api.get('/audit/logs', {
      params: { page: 0, size: 5 }
    })
    recentActivities.value = response.data?.content || []
  } catch (error) {
    console.error('Failed to fetch activities:', error)
    recentActivities.value = []
  }
}

const formatDate = (date) => {
  if (!date) return '-'
  return new Date(date).toLocaleDateString()
}

const formatTime = (date) => {
  if (!date) return '-'
  const now = new Date()
  const then = new Date(date)
  const diff = Math.floor((now - then) / 1000 / 60)
  
  if (diff < 1) return 'Just now'
  if (diff < 60) return `${diff} minutes ago`
  if (diff < 1440) return `${Math.floor(diff / 60)} hours ago`
  return `${Math.floor(diff / 1440)} days ago`
}

const formatAction = (action) => {
  const actions = {
    LOGIN_SUCCESS: 'Login',
    LOGIN_FAILED: 'Login Failed',
    LOGOUT: 'Logout',
    PASSWORD_CHANGE: 'Password Changed',
    ADMIN_KICK_USER: 'Kicked by Admin',
    SESSION_EXPIRED: 'Session Expired'
  }
  return actions[action] || action
}

const getActivityIcon = (action) => {
  const icons = {
    LOGIN_SUCCESS: 'fas fa-sign-in-alt',
    LOGIN_FAILED: 'fas fa-exclamation-triangle',
    LOGOUT: 'fas fa-sign-out-alt',
    PASSWORD_CHANGE: 'fas fa-key',
    ADMIN_KICK_USER: 'fas fa-user-slash',
    SESSION_EXPIRED: 'fas fa-clock'
  }
  return icons[action] || 'fas fa-circle'
}

const getActivityClass = (action) => {
  const classes = {
    LOGIN_SUCCESS: 'success',
    LOGIN_FAILED: 'danger',
    LOGOUT: 'info',
    PASSWORD_CHANGE: 'warning',
    ADMIN_KICK_USER: 'danger'
  }
  return classes[action] || 'info'
}

const handleClickOutside = (event) => {
  if (!event.target.closest('.user-menu')) {
    showUserMenu.value = false
  }
  if (!event.target.closest('.notification-btn')) {
    showNotifications.value = false
  }
}

// ==================== LIFECYCLE ====================
onMounted(() => {
  // ดึง profile ถ้ายังไม่มี
  if (!authStore.profile && authStore.isAuthenticated) {
    authStore.fetchUserProfile()
  }
  
  // เฉพาะ Admin เท่านั้นที่เรียก API เพิ่มเติม
  if (authStore.isAdmin) {
    fetchStats()
    fetchRecentActivities()
  }
  
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>

<style scoped>
.dashboard {
  display: flex;
  min-height: 100vh;
  background: #f0f2f5;
}

/* Sidebar Styles */
.sidebar {
  width: 280px;
  background: linear-gradient(135deg, #1e2a3a 0%, #0f172a 100%);
  color: white;
  display: flex;
  flex-direction: column;
  transition: width 0.3s ease;
  position: fixed;
  height: 100vh;
  z-index: 100;
}

.sidebar.collapsed {
  width: 80px;
}

.sidebar-header {
  padding: 1.5rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid rgba(255,255,255,0.1);
}

.logo {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  cursor: pointer;
}

.logo-icon {
  font-size: 1.5rem;
}

.logo-text {
  font-size: 1.25rem;
  font-weight: bold;
}

.toggle-btn {
  background: none;
  border: none;
  color: white;
  cursor: pointer;
  font-size: 1rem;
}

.sidebar-nav {
  flex: 1;
  padding: 1.5rem 0;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0.75rem 1.5rem;
  color: rgba(255,255,255,0.8);
  text-decoration: none;
  transition: all 0.3s;
  cursor: pointer;
  width: 100%;
  background: none;
  border: none;
  font-size: 1rem;
}

.nav-item:hover {
  background: rgba(255,255,255,0.1);
  color: white;
}

.nav-item.active {
  background: rgba(99, 102, 241, 0.2);
  border-left: 3px solid #6366f1;
  color: white;
}

.nav-item i {
  width: 1.5rem;
  font-size: 1.1rem;
}

.nav-divider {
  height: 1px;
  background: rgba(255,255,255,0.1);
  margin: 1rem 1.5rem;
}

.logout-btn {
  color: #f87171;
}

.logout-btn:hover {
  background: rgba(248, 113, 113, 0.1);
  color: #f87171;
}

.sidebar-footer {
  padding: 1rem;
  border-top: 1px solid rgba(255,255,255,0.1);
}

.user-info-mini {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.user-avatar-mini {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  overflow: hidden;
}

.user-avatar-mini img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.user-details-mini {
  flex: 1;
}

.user-details-mini strong {
  display: block;
  font-size: 0.875rem;
}

.user-details-mini small {
  font-size: 0.75rem;
  opacity: 0.7;
}

/* Main Content */
.main-content {
  flex: 1;
  margin-left: 280px;
  transition: margin-left 0.3s ease;
}

.sidebar.collapsed ~ .main-content {
  margin-left: 80px;
}

/* Top Header */
.top-header {
  background: white;
  padding: 1rem 2rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
  position: sticky;
  top: 0;
  z-index: 99;
}

.header-left h1 {
  font-size: 1.5rem;
  margin-bottom: 0.25rem;
  color: #1e293b;
}

.welcome-text {
  color: #64748b;
  font-size: 0.875rem;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 1.5rem;
  position: relative;
}

.notification-btn {
  position: relative;
  cursor: pointer;
  font-size: 1.25rem;
  color: #64748b;
}

.notification-btn .badge {
  position: absolute;
  top: -8px;
  right: -8px;
  background: #ef4444;
  color: white;
  font-size: 0.7rem;
  padding: 0.125rem 0.375rem;
  border-radius: 10px;
}

.user-menu {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  cursor: pointer;
  padding: 0.5rem;
  border-radius: 0.5rem;
  transition: background 0.3s;
}

.user-menu:hover {
  background: #f1f5f9;
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

.user-name {
  font-weight: 500;
  color: #1e293b;
}

/* Dropdown Menus */
.dropdown-menu {
  position: absolute;
  top: 100%;
  right: 0;
  background: white;
  border-radius: 0.5rem;
  box-shadow: 0 10px 25px rgba(0,0,0,0.1);
  min-width: 200px;
  margin-top: 0.5rem;
  overflow: hidden;
  z-index: 1000;
}

.dropdown-menu.notifications {
  width: 300px;
}

.dropdown-header {
  padding: 0.75rem 1rem;
  font-weight: 600;
  border-bottom: 1px solid #e2e8f0;
  background: #f8fafc;
}

.dropdown-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem 1rem;
  color: #1e293b;
  text-decoration: none;
  transition: background 0.3s;
  width: 100%;
  background: none;
  border: none;
  cursor: pointer;
  font-size: 0.875rem;
}

.dropdown-item:hover {
  background: #f1f5f9;
}

.dropdown-item.logout {
  color: #ef4444;
}

.dropdown-divider {
  height: 1px;
  background: #e2e8f0;
  margin: 0.5rem 0;
}

.notification-item {
  padding: 0.75rem 1rem;
  border-bottom: 1px solid #e2e8f0;
  cursor: pointer;
  transition: background 0.3s;
}

.notification-item:hover {
  background: #f8fafc;
}

.notification-item i {
  margin-right: 0.75rem;
  color: #6366f1;
}

.notification-item small {
  display: block;
  font-size: 0.7rem;
  color: #94a3b8;
  margin-top: 0.25rem;
}

/* Content */
.content {
  padding: 2rem;
}

/* Stats Grid */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 1.5rem;
  margin-bottom: 2rem;
}

.stat-card {
  background: white;
  border-radius: 1rem;
  padding: 1.5rem;
  display: flex;
  align-items: center;
  gap: 1rem;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
  transition: transform 0.3s, box-shadow 0.3s;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 6px rgba(0,0,0,0.1);
}

.stat-icon {
  width: 50px;
  height: 50px;
  border-radius: 1rem;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.5rem;
}

.stat-icon.blue { background: #e0f2fe; color: #0284c7; }
.stat-icon.green { background: #dcfce7; color: #16a34a; }
.stat-icon.purple { background: #f3e8ff; color: #9333ea; }
.stat-icon.orange { background: #ffedd5; color: #ea580c; }

.stat-info h3 {
  font-size: 0.875rem;
  color: #64748b;
  margin-bottom: 0.25rem;
}

.stat-value {
  font-size: 1.5rem;
  font-weight: bold;
  color: #1e293b;
}

/* Welcome Card */
.welcome-card {
  background: white;
  border-radius: 1rem;
  padding: 2rem;
  text-align: center;
  margin-bottom: 2rem;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
}

.welcome-icon {
  font-size: 3rem;
  margin-bottom: 1rem;
}

.welcome-card h2 {
  margin-bottom: 0.5rem;
  color: #1e293b;
}

.welcome-card p {
  color: #64748b;
  margin-bottom: 1.5rem;
}

.quick-actions {
  display: flex;
  justify-content: center;
  gap: 1rem;
  flex-wrap: wrap;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem;
  background: #f1f5f9;
  color: #1e293b;
  text-decoration: none;
  border-radius: 0.5rem;
  transition: background 0.3s;
}

.action-btn:hover {
  background: #e2e8f0;
}

/* Profile Card */
.profile-card {
  background: white;
  border-radius: 1rem;
  padding: 1.5rem;
  margin-bottom: 2rem;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
}

.profile-header {
  display: flex;
  align-items: center;
  gap: 1.5rem;
  flex-wrap: wrap;
  margin-bottom: 1.5rem;
  padding-bottom: 1.5rem;
  border-bottom: 1px solid #e2e8f0;
}

.profile-avatar {
  position: relative;
  width: 80px;
  height: 80px;
  border-radius: 50%;
  overflow: hidden;
}

.profile-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.edit-avatar {
  position: absolute;
  bottom: 0;
  right: 0;
  background: #6366f1;
  color: white;
  border: none;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.75rem;
}

.profile-info h2 {
  font-size: 1.25rem;
  margin-bottom: 0.25rem;
}

.profile-info .email {
  color: #64748b;
  margin-bottom: 0.5rem;
}

.role-badges {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.role-badge {
  background: #e0f2fe;
  color: #0284c7;
  padding: 0.25rem 0.75rem;
  border-radius: 1rem;
  font-size: 0.75rem;
  font-weight: 500;
}

.edit-profile-btn {
  margin-left: auto;
  padding: 0.5rem 1rem;
  background: #f1f5f9;
  border: none;
  border-radius: 0.5rem;
  color: #1e293b;
  cursor: pointer;
  transition: background 0.3s;
}

.edit-profile-btn:hover {
  background: #e2e8f0;
}

.profile-details {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 1rem;
}

.detail-item {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0.75rem;
  background: #f8fafc;
  border-radius: 0.5rem;
}

.detail-item i {
  width: 30px;
  color: #6366f1;
  font-size: 1.1rem;
}

.detail-item label {
  font-size: 0.7rem;
  color: #64748b;
  display: block;
}

.detail-item p {
  font-size: 0.875rem;
  color: #1e293b;
  margin: 0;
}

/* Loading Card */
.loading-card {
  background: white;
  padding: 2rem;
  border-radius: 1rem;
  text-align: center;
  color: #64748b;
  margin-bottom: 2rem;
}

.spinner {
  width: 40px;
  height: 40px;
  border: 3px solid #e2e8f0;
  border-top-color: #6366f1;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin: 0 auto 1rem;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* Activity Card */
.activity-card {
  background: white;
  border-radius: 1rem;
  padding: 1.5rem;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
  padding-bottom: 0.75rem;
  border-bottom: 1px solid #e2e8f0;
}

.card-header h3 {
  font-size: 1rem;
  font-weight: 600;
}

.view-all {
  color: #6366f1;
  text-decoration: none;
  font-size: 0.875rem;
}

.activity-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.activity-item {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0.75rem;
  border-radius: 0.5rem;
  transition: background 0.3s;
}

.activity-item:hover {
  background: #f8fafc;
}

.activity-icon {
  width: 40px;
  height: 40px;
  border-radius: 0.5rem;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1rem;
}

.activity-icon.success { background: #dcfce7; color: #16a34a; }
.activity-icon.danger { background: #fee2e2; color: #dc2626; }
.activity-icon.info { background: #e0f2fe; color: #0284c7; }
.activity-icon.warning { background: #ffedd5; color: #ea580c; }

.activity-details {
  flex: 1;
}

.activity-action {
  font-weight: 500;
  margin-bottom: 0.25rem;
}

.activity-meta {
  font-size: 0.75rem;
  color: #64748b;
}

.activity-ip {
  font-size: 0.75rem;
  color: #94a3b8;
  font-family: monospace;
}

/* Responsive */
@media (max-width: 768px) {
  .sidebar {
    transform: translateX(-100%);
    position: fixed;
  }
  
  .sidebar.open {
    transform: translateX(0);
  }
  
  .main-content {
    margin-left: 0 !important;
  }
  
  .stats-grid {
    grid-template-columns: 1fr;
  }
  
  .profile-header {
    flex-direction: column;
    text-align: center;
  }
  
  .edit-profile-btn {
    margin-left: 0;
  }
  
  .profile-details {
    grid-template-columns: 1fr;
  }
}
</style>