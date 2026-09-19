/** 员工状态 */
export const EMPLOYEE_STATUS = {
  ACTIVE: 1,
  INACTIVE: 0,
} as const

export const EMPLOYEE_STATUS_MAP: Record<number, string> = {
  1: '在职',
  0: '离职',
}

export const EMPLOYMENT_TYPE_MAP: Record<number, string> = {
  1: '全职',
  2: '兼职',
  3: '实习',
}

/** 会员状态 */
export const MEMBER_STATUS_MAP: Record<number, string> = {
  1: '正常',
  0: '禁用',
}

export const GENDER_MAP: Record<number, string> = {
  0: '未知',
  1: '男',
  2: '女',
}

export const REGISTER_SOURCE_MAP: Record<number, string> = {
  0: '门店注册',
  1: '线上注册',
  2: '导入',
}

/** 支付方式 */
export const PAYMENT_METHOD_MAP: Record<number, string> = {
  1: '现金',
  2: '微信',
  3: '支付宝',
  4: '银行卡',
  5: '余额',
}

/** 充值状态 */
export const RECHARGE_STATUS_MAP: Record<number, string> = {
  0: '待支付',
  1: '已完成',
  2: '已取消',
  3: '已退款',
}

/** 积分业务类型 */
export const POINTS_BIZ_TYPE_MAP: Record<number, string> = {
  1: '消费获取',
  2: '充值赠送',
  3: '活动奖励',
  4: '兑换扣除',
  5: '过期清零',
  6: '管理员调整',
}

/** 通知类型 */
export const NOTIFY_TYPE_MAP: Record<number, string> = {
  1: '系统公告',
  2: '活动通知',
  3: '维护通知',
}

/** 权限类型 */
export const PERM_TYPE_MAP: Record<number, string> = {
  1: '菜单',
  2: '按钮',
  3: '接口',
}

/** 机位运营状态 */
export const COMPUTER_STATUS = {
  IDLE: 0,
  IN_USE: 1,
  LOCKED: 2,
  MAINTENANCE: 3,
  OFF: 4,
} as const

export const COMPUTER_STATUS_MAP: Record<number, string> = {
  [COMPUTER_STATUS.IDLE]: '空闲',
  [COMPUTER_STATUS.IN_USE]: '使用中',
  [COMPUTER_STATUS.LOCKED]: '锁定',
  [COMPUTER_STATUS.MAINTENANCE]: '维修中',
  [COMPUTER_STATUS.OFF]: '关机',
}

export const COMPUTER_STATUS_COLOR: Record<number, string> = {
  [COMPUTER_STATUS.IDLE]: '#67c23a',
  [COMPUTER_STATUS.IN_USE]: '#409eff',
  [COMPUTER_STATUS.LOCKED]: '#e6a23c',
  [COMPUTER_STATUS.MAINTENANCE]: '#f56c6c',
  [COMPUTER_STATUS.OFF]: '#909399',
}

/** 设备类型 */
export const DEVICE_TYPE_MAP: Record<number, string> = {
  1: '普通 PC',
  2: '电竞 PC',
  3: '包间',
  4: 'PS5/主机',
}

/** 会话状态 */
export const SESSION_STATUS = {
  ACTIVE: 0,
  PAUSED: 1,
  ENDED: 2,
  FORCE_ENDED: 3,
  ERROR: 4,
} as const

export const SESSION_STATUS_MAP: Record<number, string> = {
  [SESSION_STATUS.ACTIVE]: '上机中',
  [SESSION_STATUS.PAUSED]: '已暂停',
  [SESSION_STATUS.ENDED]: '已下机',
  [SESSION_STATUS.FORCE_ENDED]: '强制下机',
  [SESSION_STATUS.ERROR]: '异常中断',
}

/** 认证方式 */
export const AUTH_METHOD_MAP: Record<number, string> = {
  1: '刷卡',
  2: '扫码',
  3: '人脸',
  4: '临时密码',
}

/** 商品类型 */
export const PRODUCT_TYPE_MAP: Record<number, string> = {
  1: '食品',
  2: '饮料',
  3: '虚拟商品',
  4: '日用品',
  5: '网游点卡',
}

/** 商品上下架状态 */
export const PRODUCT_STATUS_MAP: Record<number, string> = {
  1: '上架',
  0: '下架',
}

/** 预约状态 */
export const RESERVATION_STATUS = {
  PENDING: 0,
  CONFIRMED: 1,
  CHECKED_IN: 2,
  CANCELLED: 3,
  NO_SHOW: 4,
} as const

export const RESERVATION_STATUS_MAP: Record<number, string> = {
  [RESERVATION_STATUS.PENDING]: '待确认',
  [RESERVATION_STATUS.CONFIRMED]: '已确认',
  [RESERVATION_STATUS.CHECKED_IN]: '已上机',
  [RESERVATION_STATUS.CANCELLED]: '已取消',
  [RESERVATION_STATUS.NO_SHOW]: '超时未到',
}
