/**
 * 幂等键生成（管理端）。
 * <p>
 * 与小程序端 `utils/format.ts` 的 `genIdempotentKey` 保持<b>同一实现</b>：
 * 时间戳 + 随机串，前缀区分业务。后端据此生成确定性充值单号，
 * 由 `member_recharge.uk_recharge_no` 唯一索引保证「同一请求只入账一次」。
 * <p>
 * 注意：本函数只负责「生成」，不负责「复用」。同一笔请求的重试必须复用同一个键，
 * 新的一笔请求才生成新键 —— 该策略由调用方（充值页）按请求指纹维护。
 */
export function genIdempotentKey(prefix = ''): string {
  const rand = Math.random().toString(36).slice(2, 10)
  return `${prefix}${Date.now()}${rand}`.toUpperCase()
}
