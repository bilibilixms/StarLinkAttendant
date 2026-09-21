/**
 * 图标库。
 *
 * 为什么不用 iconfont / PNG：
 * - 微信小程序 WXML 不支持内联 <svg> 标签，但支持 background-image 使用 data URI；
 * - 所以这里把每个图标写成 SVG 片段，运行时编码成 base64 data URI 挂到 background-image 上，
 *   H5 与微信小程序渲染结果完全一致，且不需要任何二进制资源文件。
 *
 * 约定：
 * - 统一 24×24 viewBox；
 * - `{c}` 是颜色占位符，渲染时替换成实际颜色；
 * - `filled: true` 的图标用 fill 上色、无描边；否则用 stroke 上色。
 */

export interface IconDef {
  /** <svg> 内部内容 */
  body: string
  /** 是否为实心图标（fill 上色，无描边） */
  filled?: boolean
  /** 描边宽度，默认 1.7 */
  strokeWidth?: number
}

/* 简写，减少重复 */
const S = (d: string) => `<path d="${d}"/>`

export const ICONS: Record<string, IconDef> = {
  /* ==================== TabBar ==================== */
  'tab-home': {
    body: `<path d="M3.6 10.4L12 3.6l8.4 6.8"/><path d="M5.6 9.4V19a1.6 1.6 0 0 0 1.6 1.6h9.6A1.6 1.6 0 0 0 18.4 19V9.4"/><path d="M10 20.6v-5.2h4v5.2"/>`,
  },
  'tab-home-active': {
    filled: true,
    body: `<path d="M11.05 2.9L3.3 9.2a1.6 1.6 0 0 0-.55 1.2V19a2.4 2.4 0 0 0 2.4 2.4h3.1v-5.6a1 1 0 0 1 1-1h4.7a1 1 0 0 1 1 1v5.6h3.1A2.4 2.4 0 0 0 21.25 19v-8.6a1.6 1.6 0 0 0-.55-1.2L12.95 2.9a1.5 1.5 0 0 0-1.9 0z" fill="{c}"/>`,
  },
  'tab-service': {
    body: `<rect x="4.8" y="3" width="14.4" height="18" rx="2.6"/><path d="M8.6 8h6.8M8.6 12h6.8M8.6 16h4.2"/>`,
  },
  'tab-service-active': {
    body: `<rect x="4.8" y="3" width="14.4" height="18" rx="2.6" fill="{c}" stroke="none"/><path d="M8.6 8h6.8M8.6 12h6.8M8.6 16h4.2" stroke="#ffffff" stroke-width="1.9"/>`,
  },
  'tab-game': {
    body: `<rect x="2.4" y="6.8" width="19.2" height="10.4" rx="4.6"/><path d="M7.4 9.8v4.4M5.2 12h4.4"/><circle cx="15.8" cy="10.9" r="1.15" fill="{c}" stroke="none"/><circle cx="18.4" cy="13.5" r="1.15" fill="{c}" stroke="none"/>`,
  },
  'tab-game-active': {
    body: `<rect x="2.4" y="6.8" width="19.2" height="10.4" rx="4.6" fill="{c}" stroke="none"/><path d="M7.4 9.8v4.4M5.2 12h4.4" stroke="#ffffff" stroke-width="1.9"/><circle cx="15.8" cy="10.9" r="1.15" fill="#ffffff"/><circle cx="18.4" cy="13.5" r="1.15" fill="#ffffff"/>`,
  },
  // 参考截图里「社区」是一个较实心的星球（粗描边 + 环），不是细线
  'tab-community': {
    strokeWidth: 2.1,
    body: `<circle cx="12" cy="11.6" r="6.4"/><path d="M2.8 11.6c0 2.1 4.1 3.6 9.2 3.6s9.2-1.5 9.2-3.6-4.1-3.6-9.2-3.6-9.2 1.5-9.2 3.6z"/>`,
  },
  'tab-community-active': {
    body: `<circle cx="12" cy="11.6" r="6.4" fill="{c}" stroke="none"/><path d="M2.8 11.6c0 2.1 4.1 3.6 9.2 3.6s9.2-1.5 9.2-3.6" fill="none" stroke="{c}" stroke-width="2.1" stroke-linecap="round"/>`,
  },
  'tab-mine': {
    body: `<circle cx="12" cy="8" r="4"/><path d="M4.6 20.4c0-4.1 3.3-6.6 7.4-6.6s7.4 2.5 7.4 6.6"/>`,
  },
  'tab-mine-active': {
    filled: true,
    body: `<circle cx="12" cy="7.8" r="4.2" fill="{c}"/><path d="M12 14.2c-4.4 0-8 2.7-8 6.6 0 .6.5 1.1 1.1 1.1h13.8c.6 0 1.1-.5 1.1-1.1 0-3.9-3.6-6.6-8-6.6z" fill="{c}"/>`,
  },

  /* ==================== 通用操作 ==================== */
  search: { body: `<circle cx="11" cy="11" r="7"/><path d="M20.2 20.2l-3.6-3.6"/>` },
  scan: {
    body: `<path d="M3.2 8.4V5.6A2.4 2.4 0 0 1 5.6 3.2h2.8M15.6 3.2h2.8a2.4 2.4 0 0 1 2.4 2.4v2.8M20.8 15.6v2.8a2.4 2.4 0 0 1-2.4 2.4h-2.8M8.4 20.8H5.6a2.4 2.4 0 0 1-2.4-2.4v-2.8"/><path d="M3.2 12h17.6"/>`,
  },
  more: {
    filled: true,
    body: `<circle cx="5" cy="12" r="1.7" fill="{c}"/><circle cx="12" cy="12" r="1.7" fill="{c}"/><circle cx="19" cy="12" r="1.7" fill="{c}"/>`,
  },
  menu: {
    body: `<rect x="3.2" y="3.2" width="7.2" height="7.2" rx="1.8"/><rect x="13.6" y="3.2" width="7.2" height="7.2" rx="1.8"/><rect x="3.2" y="13.6" width="7.2" height="7.2" rx="1.8"/><rect x="13.6" y="13.6" width="7.2" height="7.2" rx="1.8"/>`,
  },
  back: { body: S('M15.2 4.6L7.8 12l7.4 7.4') },
  'arrow-right': { body: S('M9 4.6l7.4 7.4L9 19.4') },
  'arrow-down': { body: S('M5 9l7 7 7-7') },
  'arrow-up': { body: S('M5 15l7-7 7 7') },
  close: { body: `<path d="M6 6l12 12M18 6L6 18"/>` },
  plus: { body: `<path d="M12 5.2v13.6M5.2 12h13.6"/>` },
  minus: { body: S('M5.2 12h13.6') },
  check: { body: S('M4.4 12.6l5 5L19.6 7') },
  question: {
    body: `<circle cx="12" cy="12" r="8.8"/><path d="M9.4 9.4a2.6 2.6 0 1 1 3.6 2.4c-.7.4-1 .9-1 1.7v.2"/><circle cx="12" cy="17" r="1" fill="{c}" stroke="none"/>`,
  },
  edit: { body: `<path d="M4.2 19.8h3.9L18.6 9.3a1.6 1.6 0 0 0 0-2.3l-1.6-1.6a1.6 1.6 0 0 0-2.3 0L4.2 15.9v3.9z"/><path d="M14 7.2l2.8 2.8"/>` },
  share: { body: `<path d="M4 19.4c0-7.1 4.7-10.7 11.6-10.7"/><path d="M14.2 4.2l6.4 4.6-6.4 4.6"/>` },
  comment: { body: `<path d="M20.4 11.6c0 4.1-3.8 7.4-8.4 7.4a9.4 9.4 0 0 1-2.7-.4L4.6 20.2l1.1-3.8a7 7 0 0 1-2.1-4.8c0-4.1 3.8-7.4 8.4-7.4s8.4 3.3 8.4 7.4z"/>` },
  like: {
    body: `<path d="M7.2 20.6V10.2l4.4-6.8c1.3 0 2.1.9 2.1 2.2v3.6h4.3c1.4 0 2.4 1.2 2.1 2.5l-1.7 7.1a2.2 2.2 0 0 1-2.1 1.8H7.2z"/><path d="M7.2 10.2H4.8c-.9 0-1.6.7-1.6 1.6v7.2c0 .9.7 1.6 1.6 1.6h2.4"/>`,
  },
  'like-filled': {
    filled: true,
    body: `<path d="M7.2 20.6V10.2l4.4-6.8c1.3 0 2.1.9 2.1 2.2v3.6h4.3c1.4 0 2.4 1.2 2.1 2.5l-1.7 7.1a2.2 2.2 0 0 1-2.1 1.8H7.2z" fill="{c}"/><rect x="3.2" y="10.2" width="4" height="10.4" rx="1.4" fill="{c}"/>`,
  },
  star: { body: S('M12 3.4l2.7 5.5 6 .8-4.4 4.2 1.1 6-5.4-2.9-5.4 2.9 1.1-6L3.3 9.7l6-.8z') },
  'star-filled': {
    filled: true,
    body: S('M12 3.4l2.7 5.5 6 .8-4.4 4.2 1.1 6-5.4-2.9-5.4 2.9 1.1-6L3.3 9.7l6-.8z'),
  },
  cart: {
    body: `<circle cx="9.6" cy="20" r="1.5"/><circle cx="18" cy="20" r="1.5"/><path d="M2.6 3.4h2.6l2.3 11.9a1.6 1.6 0 0 0 1.6 1.3h8.3a1.6 1.6 0 0 0 1.6-1.3L20.6 7.2H6"/>`,
  },
  location: { body: `<path d="M12 21.2s7-6.4 7-11.2a7 7 0 1 0-14 0c0 4.8 7 11.2 7 11.2z"/><circle cx="12" cy="10" r="2.6"/>` },
  phone: { body: S('M4.8 3.2h4l2 5-2.5 1.5a12.4 12.4 0 0 0 6 6L15.8 13l5 2v4a2 2 0 0 1-2.2 2A17.2 17.2 0 0 1 2.8 5.4a2 2 0 0 1 2-2.2z') },
  clock: { body: `<circle cx="12" cy="12" r="8.6"/><path d="M12 6.8v5.5l3.6 2.2"/>` },
  fire: {
    body: `<path d="M12 2.8s.9 3.1 3.4 4.8c2.7 1.8 3.8 3.9 3.8 6.1a7.2 7.2 0 0 1-14.4 0c0-2.1 1-3.8 2.5-5.6C9 5.9 12 2.8 12 2.8z"/><path d="M12 20.4a3.2 3.2 0 0 0 3.2-3.2c0-1.6-1.6-2.9-3.2-4.8-1.6 1.9-3.2 3.2-3.2 4.8a3.2 3.2 0 0 0 3.2 3.2z"/>`,
  },
  gift: {
    body: `<rect x="3.2" y="7.8" width="17.6" height="4.2" rx="1.4"/><path d="M5.2 12v7.4a1.4 1.4 0 0 0 1.4 1.4h10.8a1.4 1.4 0 0 0 1.4-1.4V12"/><path d="M12 7.8v13"/><path d="M12 7.8S10.6 3 8.3 3a2.4 2.4 0 0 0 0 4.8h3.7zM12 7.8S13.4 3 15.7 3a2.4 2.4 0 0 1 0 4.8h-3.7z"/>`,
  },
  trophy: {
    body: `<path d="M7.2 3.6h9.6v5.2a4.8 4.8 0 0 1-9.6 0V3.6z"/><path d="M7.2 5.6H4.6a2.6 2.6 0 0 0 2.6 5.2M16.8 5.6h2.6a2.6 2.6 0 0 1-2.6 5.2"/><path d="M12 13.6v4.2M8.4 21h7.2"/>`,
  },
  wallet: {
    body: `<path d="M3 7.6A2.4 2.4 0 0 1 5.4 5.2h12a1.4 1.4 0 0 1 1.4 1.4v1"/><rect x="3" y="7.6" width="18" height="12" rx="2.6"/><circle cx="16.6" cy="13.6" r="1.3" fill="{c}" stroke="none"/>`,
  },
  coupon: {
    body: `<path d="M3.2 8.6A2.4 2.4 0 0 1 5.6 6.2h12.8a2.4 2.4 0 0 1 2.4 2.4v1.6a2 2 0 0 0 0 3.6v1.6a2.4 2.4 0 0 1-2.4 2.4H5.6a2.4 2.4 0 0 1-2.4-2.4v-1.6a2 2 0 0 0 0-3.6V8.6z"/><path d="M14 6.4v11.2" stroke-dasharray="2.4 2.4"/>`,
  },
  coin: {
    body: `<circle cx="12" cy="12" r="8.6"/><path d="M8.8 8.6L12 12.8l3.2-4.2M12 12.8v4.6M9.4 14h5.2"/>`,
  },
  refresh: {
    body: `<path d="M3.8 12A8.2 8.2 0 0 1 17.9 6.2L20.4 8.4"/><path d="M20.4 3.8v4.6h-4.6"/><path d="M20.2 12a8.2 8.2 0 0 1-14.1 5.8L3.6 15.6"/><path d="M3.6 20.2v-4.6h4.6"/>`,
  },
  headset: {
    body: `<path d="M4 14.2v-2.4a8 8 0 0 1 16 0v2.4"/><rect x="2.4" y="13" width="4.2" height="6.2" rx="1.8"/><rect x="17.4" y="13" width="4.2" height="6.2" rx="1.8"/><path d="M19.5 19.2v.4a2.6 2.6 0 0 1-2.6 2.6h-3.1"/>`,
  },
  users: {
    body: `<circle cx="9.2" cy="8" r="3.6"/><path d="M2.6 20.2c0-3.7 3-6.2 6.6-6.2s6.6 2.5 6.6 6.2"/><path d="M16.2 5.2a3.6 3.6 0 0 1 0 6.6M18.4 14.6c2.1.8 3.4 2.6 3.4 5"/>`,
  },
  warning: {
    body: `<path d="M12 3.2l9 15.8H3l9-15.8z"/><path d="M12 9.4v4.6"/><circle cx="12" cy="16.6" r="1.05" fill="{c}" stroke="none"/>`,
  },
  settings: { body: `<path d="M12 2.8l7.6 4.4v8.8L12 20.4l-7.6-4.4V7.2L12 2.8z"/><circle cx="12" cy="11.6" r="3"/>` },
  calendar: {
    body: `<rect x="3.4" y="5" width="17.2" height="16" rx="2.6"/><path d="M3.4 10h17.2M8 3v4M16 3v4"/>`,
  },
  'calendar-check': {
    body: `<rect x="3.4" y="5" width="17.2" height="16" rx="2.6"/><path d="M3.4 10h17.2M8 3v4M16 3v4"/><path d="M8.8 14.6l2.2 2.2 4.2-4.2"/>`,
  },
  camera: {
    body: `<path d="M4 8h3.2l1.5-2.2h6.6L16.8 8H20a1.4 1.4 0 0 1 1.4 1.4v9.2A1.4 1.4 0 0 1 20 20H4a1.4 1.4 0 0 1-1.4-1.4V9.4A1.4 1.4 0 0 1 4 8z"/><circle cx="12" cy="13.4" r="3.4"/>`,
  },
  crown: {
    filled: true,
    body: S('M3.2 7.6l4.2 4.2L12 4.4l4.6 7.4 4.2-4.2-2 12.2H5.2L3.2 7.6z'),
  },
  /** 签到用的手势图标（参考图里签到按钮前是一个举手） */
  hand: {
    body: `<path d="M9 11V5.4a1.4 1.4 0 0 1 2.8 0V11"/><path d="M11.8 10.6V4.6a1.4 1.4 0 0 1 2.8 0v6"/><path d="M14.6 11V6.4a1.4 1.4 0 0 1 2.8 0V14a7 7 0 0 1-7 7h-.6a6 6 0 0 1-4.7-2.3L3.4 16a1.6 1.6 0 0 1 2.4-2.1L9 16.4V8.6a1.4 1.4 0 0 1 2.8 0"/>`,
  },
  /**
   * 机械键盘（首页「天天夺宝」横幅左侧的商品占位，替代实拍图）。
   * 小尺寸下画单颗键帽会糊，所以用「键帽排」宽条表达。
   */
  kbd: {
    filled: true,
    body:
      `<rect x="1.4" y="6.2" width="21.2" height="11.6" rx="2.4" fill="#1E1E32"/>` +
      `<rect x="3.2" y="8.0" width="17.6" height="1.6" rx="0.5" fill="#E8E8F4"/>` +
      `<rect x="3.2" y="10.2" width="17.6" height="1.6" rx="0.5" fill="#D4D4E8"/>` +
      `<rect x="3.2" y="12.4" width="12.4" height="1.6" rx="0.5" fill="#E8E8F4"/>` +
      `<rect x="16.2" y="12.4" width="4.6" height="1.6" rx="0.5" fill="#D4D4E8"/>` +
      `<rect x="1.4" y="16.4" width="21.2" height="1.9" rx="0.95" fill="#FF4D9D"/>`,
  },

  /* ==================== 首页功能 ==================== */
  'fn-seat': {
    body: `<path d="M5.6 11.4V6.2A2.2 2.2 0 0 1 7.8 4h8.4a2.2 2.2 0 0 1 2.2 2.2v5.2"/><rect x="3" y="11.4" width="18" height="5.4" rx="1.8"/><path d="M6.2 16.8v3M17.8 16.8v3"/>`,
  },
  'fn-recharge': {
    body: `<path d="M3 8.2A2.4 2.4 0 0 1 5.4 5.8h12.2a1.4 1.4 0 0 1 1.4 1.4v1"/><rect x="3" y="8.2" width="18" height="11.6" rx="2.6"/><circle cx="16.6" cy="14" r="1.3" fill="{c}" stroke="none"/><path d="M6.6 14h4.8"/>`,
  },
  'fn-food': {
    body: `<path d="M4.2 8.4h13.2v6.2a5 5 0 0 1-5 5H9.2a5 5 0 0 1-5-5V8.4z"/><path d="M17.4 9.8h1.4a2.6 2.6 0 0 1 0 5.2h-1.4"/><path d="M7.4 3.4v2.6M10.8 3.4v2.6M14.2 3.4v2.6"/>`,
  },
  'fn-welfare': {
    body: `<rect x="3.2" y="8" width="17.6" height="4" rx="1.4"/><path d="M5.2 12v7.2a1.6 1.6 0 0 0 1.6 1.6h10.4a1.6 1.6 0 0 0 1.6-1.6V12"/><path d="M12 8v12.8"/><path d="M12 8S10.6 3.2 8.4 3.2a2.4 2.4 0 0 0 0 4.8h3.6zM12 8s1.4-4.8 3.6-4.8a2.4 2.4 0 0 1 0 4.8h-3.6z"/>`,
  },
  'fn-card': {
    body: `<rect x="2.6" y="5.6" width="18.8" height="12.8" rx="2.6"/><path d="M2.6 10.2h18.8"/><path d="M6.2 14.4h4.4"/>`,
  },

  /* ==================== 服务页宫格 ==================== */
  'svc-seat': {
    body: `<path d="M5.6 11.4V6.2A2.2 2.2 0 0 1 7.8 4h8.4a2.2 2.2 0 0 1 2.2 2.2v5.2"/><rect x="3" y="11.4" width="18" height="5.4" rx="1.8"/><path d="M6.2 16.8v3M17.8 16.8v3"/>`,
  },
  'svc-power': {
    body: `<rect x="2.6" y="4.4" width="18.8" height="12.4" rx="2.2"/><path d="M8.6 20.6h6.8"/><path d="M12 16.8v3.8"/><path d="M12 13.8V8.6M9.6 11l2.4-2.4L14.4 11"/>`,
  },
  'svc-remote': {
    body: `<rect x="2.6" y="4.4" width="18.8" height="12.4" rx="2.2"/><path d="M8.6 20.6h6.8"/><path d="M12 16.8v3.8"/><path d="M12 8.6v5.2M9.6 11.4l2.4 2.4 2.4-2.4"/>`,
  },
  'svc-food': {
    body: `<path d="M4.2 8.4h13.2v6.2a5 5 0 0 1-5 5H9.2a5 5 0 0 1-5-5V8.4z"/><path d="M17.4 9.8h1.4a2.6 2.6 0 0 1 0 5.2h-1.4"/><path d="M7.4 3.4v2.6M10.8 3.4v2.6M14.2 3.4v2.6"/>`,
  },
  'svc-wallet': {
    body: `<rect x="2.8" y="5.4" width="18.4" height="13.4" rx="2.8"/><path d="M2.8 9.8h18.4"/><path d="M16.8 14.2h1.6"/><path d="M6.2 5.4V4.2a1.2 1.2 0 0 1 1.5-1.2l8.4 2.4"/>`,
  },
  'svc-chat': {
    body: `<path d="M20 11.4c0 4-3.6 7.2-8 7.2a9 9 0 0 1-2.6-.4L5 20l1-3.6A7 7 0 0 1 4 11.4c0-4 3.6-7.2 8-7.2s8 3.2 8 7.2z"/><path d="M8.6 11.2h6.8M8.6 14h4.4"/>`,
  },
  'svc-hotel': {
    body: `<path d="M2.6 18.6V6"/><path d="M2.6 11.6h18.8v7"/><circle cx="7.4" cy="8.6" r="2"/><path d="M11.4 11.6V9.2h6.4a3.6 3.6 0 0 1 3.6 3.6"/>`,
  },
  'svc-game': {
    body: `<rect x="2.4" y="6.8" width="19.2" height="10.4" rx="4.6"/><path d="M7.4 9.8v4.4M5.2 12h4.4"/><circle cx="15.8" cy="10.9" r="1.15" fill="{c}" stroke="none"/><circle cx="18.4" cy="13.5" r="1.15" fill="{c}" stroke="none"/>`,
  },
  'svc-bed': {
    body: `<path d="M3 18.6v-9M3 12.6h18v6"/><circle cx="8" cy="9.4" r="2.2"/><path d="M12.4 12.6V8.4h5.2a3.4 3.4 0 0 1 3.4 3.4v.8"/>`,
  },
  'svc-wifi': {
    body: `<path d="M3.6 9.2a12.4 12.4 0 0 1 16.8 0"/><path d="M6.8 12.8a7.8 7.8 0 0 1 10.4 0"/><path d="M9.8 16.2a3.6 3.6 0 0 1 4.4 0"/><circle cx="12" cy="19.4" r="1.2" fill="{c}" stroke="none"/>`,
  },
  'svc-invoice': {
    body: `<path d="M6 3.2h12v17.6l-3-2-3 2-3-2-3 2V3.2z"/><path d="M9 8h6M9 12h6M9 16h3.6"/>`,
  },

  /* ==================== 我的页面 ==================== */
  'mine-order-seat': {
    body: `<path d="M5.6 11.4V6.2A2.2 2.2 0 0 1 7.8 4h8.4a2.2 2.2 0 0 1 2.2 2.2v5.2"/><rect x="3" y="11.4" width="18" height="5.4" rx="1.8"/><path d="M6.2 16.8v3M17.8 16.8v3"/>`,
  },
  'mine-order-hotel': {
    body: `<path d="M5 20.2V4.6a1.4 1.4 0 0 1 1.4-1.4h11.2a1.4 1.4 0 0 1 1.4 1.4v15.6"/><path d="M3.2 20.2h17.6"/><path d="M9 7.6h6M9 11.4h6"/><path d="M10.4 20.2v-4.4h3.2v4.4"/>`,
  },
  'mine-order-food': {
    body: `<path d="M3.6 11.2h16.8a8.4 8.4 0 0 1-8.4 8.4 8.4 8.4 0 0 1-8.4-8.4z"/><path d="M2.4 11.2h19.2"/><path d="M12 11.2V6.6a2.4 2.4 0 0 1 3.6-2.1"/>`,
  },
  'mine-order-mall': {
    body: `<path d="M4.4 8h15.2l-1.3 11.4a1.6 1.6 0 0 1-1.6 1.4H7.3a1.6 1.6 0 0 1-1.6-1.4L4.4 8z"/><path d="M8.6 10V6.6a3.4 3.4 0 0 1 6.8 0V10"/>`,
  },
  'tool-favorite': { body: S('M12 3.4l2.7 5.5 6 .8-4.4 4.2 1.1 6-5.4-2.9-5.4 2.9 1.1-6L3.3 9.7l6-.8z') },
  'tool-service': {
    body: `<path d="M4 14.2v-2.4a8 8 0 0 1 16 0v2.4"/><rect x="2.4" y="13" width="4.2" height="6.2" rx="1.8"/><rect x="17.4" y="13" width="4.2" height="6.2" rx="1.8"/><path d="M19.5 19.2v.4a2.6 2.6 0 0 1-2.6 2.6h-3.1"/>`,
  },
  'tool-join': {
    body: `<circle cx="9.2" cy="8" r="3.6"/><path d="M2.6 20.2c0-3.7 3-6.2 6.6-6.2s6.6 2.5 6.6 6.2"/><path d="M16.2 5.2a3.6 3.6 0 0 1 0 6.6M18.4 14.6c2.1.8 3.4 2.6 3.4 5"/>`,
  },
  'tool-coin': {
    body: `<circle cx="12" cy="12" r="8.6"/><path d="M3.4 12A8.6 8.6 0 0 1 12 3.4M20.6 12A8.6 8.6 0 0 1 12 20.6"/><path d="M8.8 9.6L12 13.4l3.2-3.8M9.4 14.4h5.2"/>`,
  },
  'tool-report': {
    body: `<path d="M12 3.2l9 15.8H3l9-15.8z"/><path d="M12 9.4v4.6"/><circle cx="12" cy="16.6" r="1.05" fill="{c}" stroke="none"/>`,
  },
  'mine-wallet': {
    body: `<path d="M3 7.6A2.4 2.4 0 0 1 5.4 5.2h12a1.4 1.4 0 0 1 1.4 1.4v1"/><rect x="3" y="7.6" width="18" height="12" rx="2.6"/><circle cx="16.6" cy="13.6" r="1.3" fill="{c}" stroke="none"/>`,
  },
  'mine-scan': {
    body: `<path d="M3.2 8.4V5.6A2.4 2.4 0 0 1 5.6 3.2h2.8M15.6 3.2h2.8a2.4 2.4 0 0 1 2.4 2.4v2.8M20.8 15.6v2.8a2.4 2.4 0 0 1-2.4 2.4h-2.8M8.4 20.8H5.6a2.4 2.4 0 0 1-2.4-2.4v-2.8"/><path d="M3.2 12h17.6"/>`,
  },

  /* ==================== 支付 ==================== */
  'pay-wechat': {
    filled: true,
    body: `<path d="M9.2 3.2C5 3.2 1.6 6.1 1.6 9.6c0 2 1 3.7 2.7 4.9l-.7 2.3 2.6-1.4c.9.3 1.9.4 2.9.4h.6a5.6 5.6 0 0 1-.2-1.6c0-3.4 3.3-6.1 7.3-6.1h.7c-.6-2.9-3.7-5-8.3-5z" fill="{c}"/><path d="M22.4 14.2c0-2.8-2.8-5.1-6.2-5.1s-6.2 2.3-6.2 5.1 2.8 5.1 6.2 5.1c.8 0 1.5-.1 2.2-.3l2.1 1.1-.5-1.8c1.5-.9 2.4-2.4 2.4-4.1z" fill="{c}"/>`,
  },
  'pay-balance': {
    body: `<rect x="2.8" y="5.4" width="18.4" height="13.4" rx="2.8"/><path d="M2.8 9.8h18.4"/><path d="M16.8 14.2h1.6"/>`,
  },
  'pay-alipay': {
    filled: true,
    body: `<rect x="2.4" y="2.4" width="19.2" height="19.2" rx="5" fill="{c}"/><path d="M6.6 8.6h10.8M12 5.6v6.4M7.4 15.2c1.6.6 3.2.4 4.6-.5 1.4-.9 2.4-2.2 3.2-3.9M12.6 17.6c1.6-1.2 2.8-2.6 3.6-4.2" stroke="#ffffff" stroke-width="1.6" fill="none"/>`,
  },

  /* ==================== 空状态 / 错误 ==================== */
  'empty-box': {
    body: `<path d="M3 8.4l9-4.4 9 4.4-9 4.4-9-4.4z"/><path d="M3 8.4v7.2l9 4.4 9-4.4V8.4"/><path d="M12 12.8v7.2"/>`,
  },
  'empty-order': {
    body: `<rect x="4.6" y="3" width="14.8" height="18" rx="2.6"/><path d="M8.6 8.4h6.8M8.6 12.4h6.8M8.6 16.4h4"/>`,
  },
  'empty-coupon': {
    body: `<path d="M3.2 8.6A2.4 2.4 0 0 1 5.6 6.2h12.8a2.4 2.4 0 0 1 2.4 2.4v1.6a2 2 0 0 0 0 3.6v1.6a2.4 2.4 0 0 1-2.4 2.4H5.6a2.4 2.4 0 0 1-2.4-2.4v-1.6a2 2 0 0 0 0-3.6V8.6z"/><path d="M14 6.4v11.2" stroke-dasharray="2.4 2.4"/>`,
  },
  'empty-network': {
    body: `<path d="M7 18.4h10.2a4.2 4.2 0 0 0 .6-8.4A6.2 6.2 0 0 0 6.2 9.2 4.6 4.6 0 0 0 7 18.4z"/><path d="M12 11.2v3.4"/><circle cx="12" cy="16.4" r=".95" fill="{c}" stroke="none"/>`,
  },
  'empty-search': {
    body: `<circle cx="11" cy="11" r="7"/><path d="M20.2 20.2l-3.6-3.6"/><path d="M8.6 8.6l4.8 4.8M13.4 8.6l-4.8 4.8"/>`,
  },
}

/* ==================== 编码 ==================== */

const B64_CHARS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/'

/** 字符串 → UTF-8 字节数组 */
function toUtf8Bytes(str: string): number[] {
  const bytes: number[] = []
  for (let i = 0; i < str.length; i += 1) {
    const c = str.charCodeAt(i)
    if (c < 0x80) {
      bytes.push(c)
    } else if (c < 0x800) {
      bytes.push(0xc0 | (c >> 6), 0x80 | (c & 0x3f))
    } else if (c >= 0xd800 && c <= 0xdbff) {
      const c2 = str.charCodeAt((i += 1))
      const cp = 0x10000 + ((c & 0x3ff) << 10) + (c2 & 0x3ff)
      bytes.push(
        0xf0 | (cp >> 18),
        0x80 | ((cp >> 12) & 0x3f),
        0x80 | ((cp >> 6) & 0x3f),
        0x80 | (cp & 0x3f)
      )
    } else {
      bytes.push(0xe0 | (c >> 12), 0x80 | ((c >> 6) & 0x3f), 0x80 | (c & 0x3f))
    }
  }
  return bytes
}

/** 纯 JS base64 编码（微信小程序没有 btoa） */
function base64(input: string): string {
  const bytes = toUtf8Bytes(input)
  let out = ''
  for (let i = 0; i < bytes.length; i += 3) {
    const b0 = bytes[i]
    const b1 = bytes[i + 1]
    const b2 = bytes[i + 2]
    out += B64_CHARS[b0 >> 2]
    out += B64_CHARS[((b0 & 0x03) << 4) | ((b1 ?? 0) >> 4)]
    out += b1 === undefined ? '=' : B64_CHARS[((b1 & 0x0f) << 2) | ((b2 ?? 0) >> 6)]
    out += b2 === undefined ? '=' : B64_CHARS[b2 & 0x3f]
  }
  return out
}

const cache = new Map<string, string>()

/**
 * 取图标的 data URI。
 *
 * @param color  单色，或 `linear-gradient(...)` 形式的渐变字符串（用于 TabBar 激活态等高亮场景）
 * @param strokeWidth 描边宽度（仅对描边型图标生效）
 */
export function iconDataUri(name: string, color: string, strokeWidth = 1.7): string {
  const key = `${name}|${color}|${strokeWidth}`
  const hit = cache.get(key)
  if (hit) return hit

  const def = ICONS[name]
  if (!def) {
    console.warn(`[icons] 未定义的图标: ${name}`)
    return ''
  }

  const isStops = color.startsWith('linear-gradient(')
  const stops = isStops ? parseGradient(color) : null
  const paint = stops ? 'url(#ig)' : color

  const sw = def.strokeWidth ?? strokeWidth
  const body = def.body.split('{c}').join(paint)
  const attrs = def.filled
    ? `fill="${paint}" stroke="none"`
    : `fill="none" stroke="${paint}" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round"`

  // 渐变需要 <defs>，且 stroke="url(#ig)" 在部分渲染器上不稳定，
  // 因此渐变模式下统一走 fill，描边型图标就退化为“用渐变填充内部”。
  const defs = stops
    ? `<defs><linearGradient id="ig" x1="0" y1="0" x2="1" y2="1">` +
      stops.map(([offset, c]) => `<stop offset="${offset}" stop-color="${c}"/>`).join('') +
      `</linearGradient></defs>`
    : ''

  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24" ${attrs}>` +
    `${defs}${body}</svg>`

  const uri = `data:image/svg+xml;base64,${base64(svg)}`
  cache.set(key, uri)
  return uri
}

/**
 * 解析 `linear-gradient(135deg, #8E7BFF 0%, #5B5BD6 100%)` 为 [[offset, color], ...]
 */
function parseGradient(input: string): Array<[string, string]> {
  const inner = input.slice(input.indexOf('(') + 1, input.lastIndexOf(')'))
  const parts = inner.split(',')
  // 第一段是角度，跳过
  const stops: Array<[string, string]> = []
  for (let i = 1; i < parts.length; i += 1) {
    const seg = parts[i].trim()
    const m = seg.match(/^(#[0-9a-fA-F]{3,8}|rgba?\([^)]*\))\s*(\d+%)?$/)
    if (m) {
      stops.push([m[2] ?? '', m[1]])
    } else if (seg) {
      // 形如 "#8E7BFF 0%" 之外的情况，尝试拆分
      const sp = seg.split(/\s+/)
      if (sp.length >= 2) stops.push([sp[1], sp[0]])
    }
  }
  if (!stops.length) return [['0%', '#5B5BD6'], ['100%', '#7C7CF0']]
  if (stops.length === 1) stops.push(['100%', stops[0][1]])
  return stops
}

/** 图标名是否存在于图标库 */
export function hasIcon(name: string): boolean {
  return !!ICONS[name]
}
