/**
 * 商品 / 套餐 接口（自助点餐）。
 */
import { get } from './request'
import { API_PREFIX } from '@/config'
import { registerMockProducts } from '@/mock/db'
import type { Product, ProductCategory, ProductType } from '@/types/product'

/** 后端「热门商品」接口返回的字段（真实 product 表按销量聚合） */
export interface HotProductItem {
  id: number
  name: string
  type: number
  categoryId: number
  unit?: string
  price: number
  memberPrice?: number | null
  sales: number
  cover?: string
  status: number
}

/** 后端「热门商品详情」接口返回的字段（真实 product 表） */
export interface HotProductDetailItem extends HotProductItem {
  stock: number
  images?: string[]
  description?: string
  spec?: string | null
  tags?: string[]
}

/** 后端「商品分类」接口返回的字段（真实 product_category 表） */
export interface MemberCategoryItem {
  id: number
  name: string
  sort?: number
}

/** 商品分类（真实后端） */
export async function getCategories(): Promise<ProductCategory[]> {
  const list = await get<MemberCategoryItem[]>(`${API_PREFIX}/products/categories`)
  return list.map((c) => ({ id: c.id, name: c.name, sort: c.sort }))
}

/** 商品列表，可按分类筛选（真实后端） */
export async function getProducts(params?: {
  categoryId?: number
  keyword?: string
}): Promise<Product[]> {
  const list = await get<HotProductDetailItem[]>(`${API_PREFIX}/products`, params as Record<string, unknown>)
  const products = list.map((item): Product => toProduct(item))
  registerMockProducts(products)
  return products
}

/** 商品详情（真实后端） */
export async function getProductDetail(id: number): Promise<Product> {
  const item = await get<HotProductDetailItem>(`${API_PREFIX}/products/${id}`)
  const product = toProduct(item)
  registerMockProducts([product])
  return product
}

/**
 * 热门商品「详情」（真实后端）。
 */
export async function getHotProductDetail(id: number): Promise<Product> {
  const item = await get<HotProductDetailItem>(`${API_PREFIX}/products/hot/${id}`)
  const product = toProduct(item)
  // 真实商品注册进 mock 下单池，保证下单(mock 流程)能查到该商品
  registerMockProducts([product])
  return product
}

/** 后端真实商品字段 → 前端 Product 形状 */
function toProduct(item: HotProductDetailItem): Product {
  return {
    id: item.id,
    name: item.name,
    categoryId: item.categoryId,
    type: item.type as ProductType,
    price: item.price,
    memberPrice: item.memberPrice ?? null,
    spec: item.spec ?? null,
    unit: item.unit,
    stock: item.stock ?? 0,
    sales: item.sales,
    cover: item.cover ?? '',
    images: item.images?.length ? item.images : item.cover ? [item.cover] : [],
    description: item.description,
    tags: item.tags ?? [],
    status: item.status === 1 ? 1 : 0,
    hot: true,
  }
}

/** 热销推荐（首页热门商品区，真实后端） */
export async function getHotProducts(limit = 6): Promise<Product[]> {
  const list = await get<HotProductDetailItem[]>(`${API_PREFIX}/products/hot`, { limit })
  // 真实后端字段映射为前端 Product 形状，缺失字段补默认值
  const products = list.map((item): Product => toProduct(item))
  // 预注册进 mock 下单池：首页点击进详情/下单时，mock 流程能校验到真实商品
  registerMockProducts(products)
  return products
}
