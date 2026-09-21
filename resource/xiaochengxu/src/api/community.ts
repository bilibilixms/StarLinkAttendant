/**
 * 社区 / 找队友 接口（截图 4）。
 */
import { get, post } from './request'
import { API_PREFIX } from '@/config'
import type { Post, PostComment, TeamRecruit } from '@/types/community'
import type { PageResult } from '@/types/api'

/** 推荐信息流 */
export function getPosts(params: {
  current?: number
  size?: number
  keyword?: string
}): Promise<PageResult<Post>> {
  return get<PageResult<Post>>(`${API_PREFIX}/community/posts`, params as Record<string, unknown>)
}

/** 帖子详情 */
export function getPostDetail(id: number): Promise<Post> {
  return get<Post>(`${API_PREFIX}/community/posts/${id}`)
}

/** 帖子评论 */
export function getPostComments(postId: number): Promise<PostComment[]> {
  return get<PostComment[]>(`${API_PREFIX}/community/posts/${postId}/comments`)
}

/** 点赞 / 取消点赞 */
export function togglePostLike(id: number): Promise<{ liked: boolean; likeCount: number }> {
  return post<{ liked: boolean; likeCount: number }>(`${API_PREFIX}/community/posts/${id}/like`)
}

/** 收藏 / 取消收藏 */
export function togglePostCollect(id: number): Promise<{ collected: boolean }> {
  return post<{ collected: boolean }>(`${API_PREFIX}/community/posts/${id}/collect`)
}

/** 发表评论 */
export function addComment(postId: number, content: string): Promise<PostComment> {
  return post<PostComment>(`${API_PREFIX}/community/posts/${postId}/comments`, { content })
}

/** 找队友列表 */
export function getTeamRecruits(params?: {
  current?: number
  size?: number
  gameId?: number
}): Promise<PageResult<TeamRecruit>> {
  return get<PageResult<TeamRecruit>>(`${API_PREFIX}/community/teams`, params as Record<string, unknown>)
}

/** 加入队伍 */
export function joinTeam(id: number): Promise<{ joined: boolean; joinedCount: number }> {
  return post<{ joined: boolean; joinedCount: number }>(`${API_PREFIX}/community/teams/${id}/join`)
}
