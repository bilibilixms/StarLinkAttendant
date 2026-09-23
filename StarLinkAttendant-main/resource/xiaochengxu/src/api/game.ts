/**
 * 游戏福利 / 游戏任务 接口（截图 3）。
 */
import { get, post } from './request'
import { API_PREFIX } from '@/config'
import type { Game, GameTask, GameTaskDetail } from '@/types/game'

/** 游戏分类（顶部横向滚动的那一排） */
export function getGames(): Promise<Game[]> {
  return get<Game[]>(`${API_PREFIX}/games`)
}

/** 热门任务列表；gameId 不传返回全部游戏的任务 */
export function getGameTasks(params?: { gameId?: number; current?: number; size?: number }): Promise<GameTask[]> {
  return get<GameTask[]>(`${API_PREFIX}/games/tasks`, params as Record<string, unknown>)
}

/** 任务详情 */
export function getGameTaskDetail(id: number): Promise<GameTaskDetail> {
  return get<GameTaskDetail>(`${API_PREFIX}/games/tasks/${id}`)
}

/** 参与任务 / 领取奖励 */
export function joinGameTask(id: number): Promise<{ status: number; message: string }> {
  return post<{ status: number; message: string }>(`${API_PREFIX}/games/tasks/${id}/join`)
}
