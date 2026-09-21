/**
 * 商品 / 套餐 / 购物车 类型。
 * 对齐数据库表 product / product_category / product_combo。
 */

/** 商品类型：1-实物 2-虚拟（网费/时长/道具） 3-套餐 */
export type ProductType = 1 | 2 | 3

/** 上架状态：1-上架 0-下架 */
export type ProductStatus = 0 | 1

export interface ProductCategory {
  id: number
  name: string
  /** 排序 */
  sort?: number
  /** 图标 key */
  icon?: string
  /** 该分类下商品数 */
  count?: number
}

export interface Product {
  id: number
  name: string
  categoryId: number
  categoryName?: string
  type: ProductType
  /** 售价（元） */
  price: number
  /** 会员价（元），无则为 null */
  memberPrice?: number | null
  /** 原价/划线价 */
  originalPrice?: number
  /** 规格，如「500ml」 */
  spec?: string
  /** 单位，如「瓶」 */
  unit?: string
  /** 库存 */
  stock: number
  /** 销量 */
  sales: number
  /** 商品图（本地资源 key 或 url） */
  cover: string
  images?: string[]
  description?: string
  tags?: string[]
  status: ProductStatus
  /** 是否热销推荐 */
  hot?: boolean
}

/** 购物车项（纯前端状态，持久化到 storage） */
export interface CartItem {
  productId: number
  name: string
  /** 加购时的单价（会员价优先） */
  price: number
  cover: string
  spec?: string
  quantity: number
  /** 库存上限，用于加号拦截 */
  stock: number
}
