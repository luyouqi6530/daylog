<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { logout as logoutApi } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const isDark = ref(false)
const sidebarCollapsed = ref(false)
const isMobile = ref(false)

function toggleDark() {
  isDark.value = !isDark.value
  document.documentElement.setAttribute('data-theme', isDark.value ? 'dark' : 'light')
  localStorage.setItem('daylog-theme', isDark.value ? 'dark' : 'light')
}

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

function closeSidebar() {
  if (isMobile.value) {
    sidebarCollapsed.value = true
  }
}

function handleResize() {
  isMobile.value = window.innerWidth < 768
  if (isMobile.value) {
    sidebarCollapsed.value = true
  }
}

async function handleLogout() {
  try {
    await logoutApi()
  } catch (e) {
    // ignore
  }
  userStore.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}

onMounted(() => {
  const saved = localStorage.getItem('daylog-theme')
  if (saved === 'dark') {
    isDark.value = true
    document.documentElement.setAttribute('data-theme', 'dark')
  }
  handleResize()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
})
</script>

<template>
  <el-container class="layout">
    <!-- 移动端遮罩 -->
    <div v-if="isMobile && !sidebarCollapsed" class="sidebar-overlay" @click="closeSidebar" />

    <el-aside :width="sidebarCollapsed && isMobile ? '0px' : '190px'" class="layout-aside" :class="{ collapsed: sidebarCollapsed && isMobile }">
      <div class="logo">Daylog<span class="logo-dot" /></div>
      <el-menu router :default-active="$route.path" class="menu" @select="closeSidebar">
        <el-menu-item index="/dashboard">首页</el-menu-item>
        <el-menu-item index="/diary">我的日记</el-menu-item>
        <el-menu-item index="/calendar">日历</el-menu-item>
        <el-menu-item index="/tags">标签</el-menu-item>
        <el-menu-item index="/stats">统计</el-menu-item>
        <el-menu-item index="/report">AI 周报</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="layout-header">
        <div class="header-left">
          <button class="hamburger" @click="toggleSidebar">
            <span></span>
            <span></span>
            <span></span>
          </button>
          <span class="slogan">每日记录，看见情绪的变化</span>
        </div>
        <div class="header-right">
          <button class="theme-toggle" @click="toggleDark" :title="isDark ? '切换亮色模式' : '切换暗色模式'">
            {{ isDark ? '☀️' : '🌙' }}
          </button>
          <span class="nickname">{{ userStore.userInfo?.nickname || userStore.userInfo?.username || '' }}</span>
          <el-button link class="logout-btn" @click="handleLogout">退出</el-button>
        </div>
      </el-header>
      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100vh;
}

.layout-aside {
  border-right: 1px solid var(--border-hairline);
  background: var(--bg-card);
  transition: width 0.3s ease;
  overflow: hidden;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.5px;
  color: var(--text-main);
}

.logo-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--text-main);
  opacity: 0.85;
}

.menu {
  border-right: none;
  padding: 8px;
}

.menu :deep(.el-menu-item) {
  height: 42px;
  line-height: 42px;
  border-radius: 8px;
  margin-bottom: 2px;
  font-size: 14px;
  color: var(--text-sub);
}

.menu :deep(.el-menu-item:hover) {
  background: #f4f4f5;
  color: var(--text-main);
}

.menu :deep(.el-menu-item.is-active) {
  background: #f4f4f5;
  color: var(--text-main);
  font-weight: 600;
}

[data-theme="dark"] .menu :deep(.el-menu-item:hover),
[data-theme="dark"] .menu :deep(.el-menu-item.is-active) {
  background: #27272a;
}

.layout-header {
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--border-hairline);
  background: var(--bg-card);
  padding: 0 16px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.hamburger {
  display: none;
  flex-direction: column;
  gap: 4px;
  background: none;
  border: none;
  cursor: pointer;
  padding: 4px;
}

.hamburger span {
  width: 18px;
  height: 2px;
  background: var(--text-sub);
  border-radius: 1px;
  transition: all 0.2s;
}

.slogan {
  color: var(--text-faint);
  font-size: 13px;
  letter-spacing: 1px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.theme-toggle {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 18px;
  padding: 4px;
  line-height: 1;
  transition: transform 0.2s;
}

.theme-toggle:hover {
  transform: scale(1.1);
}

.nickname {
  font-size: 13px;
  color: var(--text-sub);
}

.logout-btn {
  font-size: 13px;
  color: var(--text-faint);
}

.logout-btn:hover {
  color: var(--text-main);
}

.layout-main {
  background: var(--bg-page);
  padding: 24px 32px;
}

/* 移动端适配 */
@media (max-width: 767px) {
  .hamburger {
    display: flex;
  }

  .slogan {
    display: none;
  }

  .layout-aside {
    position: fixed;
    left: 0;
    top: 0;
    bottom: 0;
    z-index: 1000;
    width: 190px;
    box-shadow: 2px 0 8px rgba(0, 0, 0, 0.1);
  }

  .layout-aside.collapsed {
    width: 0;
    box-shadow: none;
  }

  .sidebar-overlay {
    position: fixed;
    left: 0;
    top: 0;
    right: 0;
    bottom: 0;
    background: rgba(0, 0, 0, 0.3);
    z-index: 999;
  }

  .layout-main {
    padding: 16px;
  }
}
</style>
