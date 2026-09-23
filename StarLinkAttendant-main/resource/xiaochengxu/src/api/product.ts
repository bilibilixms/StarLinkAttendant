/**
 * 商品 / 套餐 接口（自助点餐）。
 */
import { get } from './request'
import { API_PREFIX } from '@/config'
import type { Product, ProductCategory } from '@/types/product'

/** 商品分类 */
export function getCategories(): Promise<ProductCategory[]> {
  return get<ProductCategory[]>(`${API_PREFIX}/products/categories`)
}

/** 商品列表，可按分类筛选 */
export function getProducts(params?: {
  categoryId?: number
  keyword?: string
}): Promise<Product[]> {
  return get<Product[]>(`${API_PREFIX}/products`, params as Record<string, unknown>)
}

/** 商品详情 */
export function getProductDetail(id: number): Promise<Product> {
  return get<Product>(`${API_PREFIX}/products/${id}`)
}

/** 热销推荐（首页热门商品区） */
export function getHotProducts(limit = 6): Promise<Product[]> {
  return get<Product[]>(`${API_PREFIX}/products/hot`, { limit })
}
