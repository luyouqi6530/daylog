import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/layout/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '首页', requiresAuth: true }
      },
      {
        path: 'diary',
        name: 'Diary',
        component: () => import('@/views/diary/index.vue'),
        meta: { title: '我的日记', requiresAuth: true }
      },
      {
        path: 'diary/view',
        name: 'DiaryView',
        component: () => import('@/views/diary/view.vue'),
        meta: { title: '日记详情', requiresAuth: true }
      },
      {
        path: 'diary/edit',
        name: 'DiaryEdit',
        component: () => import('@/views/diary/edit.vue'),
        meta: { title: '写日记', requiresAuth: true }
      },
      {
        path: 'calendar',
        name: 'Calendar',
        component: () => import('@/views/calendar/index.vue'),
        meta: { title: '日历视图', requiresAuth: true }
      },
      {
        path: 'tags',
        name: 'Tags',
        component: () => import('@/views/tags/index.vue'),
        meta: { title: '标签管理', requiresAuth: true }
      },
      {
        path: 'stats',
        name: 'Stats',
        component: () => import('@/views/stats/index.vue'),
        meta: { title: '统计分析', requiresAuth: true }
      },
      {
        path: 'report',
        name: 'Report',
        component: () => import('@/views/report/index.vue'),
        meta: { title: 'AI 周报', requiresAuth: true }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局前置守卫：登录校验 + 页面标题
router.beforeEach((to) => {
  const userStore = useUserStore()
  document.title = to.meta.title ? `${to.meta.title} - Daylog` : 'Daylog'
  if (to.meta.requiresAuth && !userStore.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
})

export default router
