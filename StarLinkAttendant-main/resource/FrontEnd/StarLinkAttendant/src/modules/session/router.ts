export default [
  {
    path: 'session/seat-map',
    name: 'session-seat-map',
    component: () => import('./views/SeatMap.vue'),
    meta: { title: '座位图', icon: 'monitor', group: '上机管理' },
  },
  {
    path: 'session/monitor',
    name: 'session-monitor',
    component: () => import('./views/SessionMonitor.vue'),
    meta: { title: '会话监控', icon: 'view', group: '上机管理' },
  },
  {
    path: 'session/start',
    name: 'session-start',
    component: () => import('./views/SessionStart.vue'),
    meta: { title: '上机操作', icon: 'power', group: '上机管理' },
  },
  {
    path: 'session/reservations',
    name: 'session-reservations',
    component: () => import('./views/ReservationManage.vue'),
    meta: { title: '预约管理', icon: 'calendar', group: '上机管理' },
  },
  {
    path: 'session/transfer',
    name: 'session-transfer',
    component: () => import('./views/SessionTransfer.vue'),
    meta: { title: '换机操作', icon: 'switch', group: '上机管理', hidden: true },
  },
]
