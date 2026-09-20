import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import sessionRoutes from '@/modules/session/router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/modules/system/views/LoginView.vue'),
      meta: { title: '登录', layout: 'blank' },
    },
    {
      path: '/',
      name: 'main',
      component: () => import('@/layouts/MainLayout.vue'),
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/modules/system/views/DashboardView.vue'),
          meta: { title: '首页', icon: 'home' },
        },
        // ========== System 模块 ==========
        {
          path: 'system/users',
          name: 'system-users',
          component: () => import('@/modules/system/views/UserList.vue'),
          meta: { title: '用户管理', icon: 'users', group: '系统管理' },
        },
        {
          path: 'system/menus',
          name: 'system-menus',
          component: () => import('@/modules/system/views/MenuList.vue'),
          meta: { title: '菜单管理', icon: 'menu', group: '系统管理' },
        },
        {
          path: 'system/logs',
          name: 'system-logs',
          component: () => import('@/modules/system/views/LogList.vue'),
          meta: { title: '操作日志', icon: 'file-text', group: '系统管理' },
        },
        {
          path: 'system/notices',
          name: 'system-notices',
          component: () => import('@/modules/system/views/NoticeList.vue'),
          meta: { title: '通知公告', icon: 'bell', group: '系统管理' },
        },
        // ========== Member 模块 ==========
        {
          path: 'member/list',
          name: 'member-list',
          component: () => import('@/modules/member/views/MemberList.vue'),
          meta: { title: '会员列表', icon: 'user', group: '会员管理' },
        },
        {
          path: 'member/detail/:id',
          name: 'member-detail',
          component: () => import('@/modules/member/views/MemberDetail.vue'),
          meta: { title: '会员详情', group: '会员管理', hidden: true },
        },
        {
          path: 'member/register',
          name: 'member-register',
          component: () => import('@/modules/member/views/MemberRegister.vue'),
          meta: { title: '会员注册', icon: 'user-plus', group: '会员管理' },
        },
        {
          path: 'member/recharge',
          name: 'member-recharge',
          component: () => import('@/modules/member/views/RechargeManage.vue'),
          meta: { title: '充值管理', icon: 'wallet', group: '会员管理' },
        },
        {
          path: 'member/levels',
          name: 'member-levels',
          component: () => import('@/modules/member/views/LevelRules.vue'),
          meta: { title: '等级规则', icon: 'trophy', group: '会员管理' },
        },
        {
          path: 'member/points',
          name: 'member-points',
          component: () => import('@/modules/member/views/PointsManage.vue'),
          meta: { title: '积分管理', icon: 'star', group: '会员管理' },
        },
        {
          path: 'member/blacklist',
          name: 'member-blacklist',
          component: () => import('@/modules/member/views/BlacklistManage.vue'),
          meta: { title: '黑名单管理', icon: 'ban', group: '会员管理' },
        },
        // ========== Session 模块 ==========
        {
          path: 'session',
          redirect: '/session/seat-map',
        },
        ...sessionRoutes,
        // ========== 其他模块 ==========
        {
          path: 'cashier',
          name: 'cashier',
          component: () => import('@/modules/cashier/views/CashierPOS.vue'),
          meta: { title: '收银管理', icon: 'receipt' },
        },
        {
          path: 'product',
          name: 'product',
          component: () => import('@/modules/product/views/ProductList.vue'),
          meta: { title: '商品销售', icon: 'package' },
        },
        {
          path: 'product/edit/:id',
          name: 'product-edit',
          component: () => import('@/modules/product/views/ProductEdit.vue'),
          meta: { title: '商品编辑', hidden: true },
        },
        {
          path: 'report',
          name: 'report',
          component: () => import('@/modules/report/views/ReportDashboard.vue'),
          meta: { title: '经营报表', icon: 'pie-chart' },
        },
        {
          path: 'marketing',
          name: 'marketing',
          component: () => import('@/modules/marketing/views/CampaignManage.vue'),
          meta: { title: '营销活动', icon: 'ticket' },
        },
        {
          path: 'integration',
          name: 'integration',
          component: () => import('@/modules/marketing/views/IntegrationConfig.vue'),
          meta: { title: '第三方集成', icon: 'settings' },
        },
      ],
    },
  ],
})

router.beforeEach(async (to, _from, next) => {
  const userStore = useUserStore()
  userStore.loadFromStorage()

  document.title = (to.meta.title as string) || '星络灵侍馆'

  if (to.path === '/login') {
    if (userStore.isLoggedIn) {
      next('/dashboard')
    } else {
      next()
    }
  } else {
    if (userStore.isLoggedIn) {
      next()
    } else {
      next('/login')
    }
  }
})

export default router
