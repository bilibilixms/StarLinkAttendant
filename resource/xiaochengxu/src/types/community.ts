/**
 * 社区 / 找队友 类型（截图 4）。
 */

export type PostContentType = 'text' | 'image' | 'video'

export interface PostAuthor {
  id: number
  name: string
  avatar: string
  /** 认证标识，如「官方」「版主」 */
  badge?: string
  /** 等级，如 5 → 显示 V5 */
  level?: number
}

export interface Post {
  id: number
  author: PostAuthor
  /** 正文标题 */
  title: string
  /** 摘要/正文 */
  content: string
  /** 话题标签，如 #三角洲行动 */
  topics?: string[]
  contentType: PostContentType
  /** 图片列表（视频时第一张为封面） */
  images: string[]
  /** 视频地址 */
  videoUrl?: string
  /** 视频时长，如 03:15 */
  videoDuration?: string
  /** 关联门店 */
  storeName?: string
  storeId?: number
  shareCount: number
  commentCount: number
  likeCount: number
  liked: boolean
  collected: boolean
  createdAt: string
  /** 展示用时间，如 09-16 */
  timeText: string
}

export interface PostComment {
  id: number
  author: PostAuthor
  content: string
  likeCount: number
  liked: boolean
  createdAt: string
  timeText: string
  replies?: PostComment[]
}

/** 找队友招募 */
export interface TeamRecruit {
  id: number
  gameName: string
  gameIcon: string
  gameColor: string
  /** 段位，如「王者段位」 */
  rank: string
  /** 开局时间文案，如「今晚 20:00」 */
  startTimeText: string
  /** 还差几人 */
  needCount: number
  /** 当前已报名人数 */
  joinedCount: number
  /** 总人数 */
  totalCount: number
  /** 房主昵称 */
  ownerName: string
  ownerAvatar: string
  /** 备注要求，如「不开麦勿扰」 */
  remark: string
  /** 是否已加入 */
  joined: boolean
  /** 在线状态 */
  online: boolean
  createdAt: string
  timeText: string
}
