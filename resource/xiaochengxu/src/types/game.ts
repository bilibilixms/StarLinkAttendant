/**
 * 游戏福利 / 游戏任务 类型（截图 3）。
 */

export interface Game {
  id: number
  /** 游戏名，如「英雄联盟」 */
  name: string
  /** 图标上的短名（1~2 字），用于无原图时生成品牌色方块 */
  short: string
  /** 图标资源 key */
  icon: string
  /** 图标底色 */
  color: string
  /**
   * 官方 logo 图片路径。
   * 留空则用「品牌色块 + 短名」兜底；
   * 拿到真实 logo 后填上即可，无需改页面。
   */
  logo?: string
  /** 该游戏下任务数 */
  taskCount: number
}

export interface GameTask {
  id: number
  gameId: number
  gameName: string
  title: string
  /** 任务说明（点击 ? 展开） */
  description: string
  /** 已完成进度 */
  progress: number
  /** 目标进度 */
  target: number
  /** 进度单位文案，如「分钟」「局」 */
  unit: string
  /** 奖励文案，如「永久皮肤」 */
  reward: string
  /** 奖励图（本地资源 key） */
  rewardIcon?: string
  /** 四宫格缩略图 */
  thumbs: string[]
  /**
   * 剩余时间展示文案，如「11天后结束」「12:31:43后结束」。
   * 定时任务型（有 endTimestamp）时由前端实时倒计时，否则直接用 endText。
   */
  endText: string
  endTimestamp?: number
  /** 剩余份数，0 表示不限量 */
  remainCount: number
  /** 任务状态：0-未完成 1-已领取 2-已完成待领取 */
  status: 0 | 1 | 2
}

export interface GameTaskDetail extends GameTask {
  /** 领取步骤说明 */
  steps: string[]
  /** 规则说明 */
  rules: string[]
}
