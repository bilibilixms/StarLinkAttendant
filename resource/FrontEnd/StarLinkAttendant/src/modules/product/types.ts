/** 商品 */
export interface ProductItem {
  id: number
  categoryId: number | null
  categoryName: string | null
  productCode: string
  productName: string
  productType: number
  productTypeLabel: string
  unit: string
  costPrice: number
  retailPrice: number
  memberPrice: number | null
  imageUrl: string | null
  isVipOnly: number
  isActive: number
  isActiveLabel: string
  createdAt: string
  updatedAt: string
}

/** 分类 */
export interface CategoryItem {
  id: number
  parentId: number | null
  categoryName: string
  icon: string | null
  sortOrder: number
  level: number
  isActive: number
  isActiveLabel: string
  children?: CategoryItem[]
}

/** 套餐 */
export interface ComboItem {
  id: number
  comboName: string
  comboCode: string
  description: string | null
  originalPrice: number | null
  comboPrice: number
  imageUrl: string | null
  isActive: number
  isActiveLabel: string
  sortOrder: number
  items: ComboSubItem[]
  createdAt: string
  updatedAt: string
}

/** 套餐子项 */
export interface ComboSubItem {
  id: number
  comboId: number
  productId: number
  productName: string
  unitPrice: number
  quantity: number
  subtotal: number
}

/** 商品查询参数 */
export interface ProductQuery {
  productName?: string
  productCode?: string
  categoryId?: number | null
  productType?: number | null
  isActive?: number | null
}

/** 套餐查询参数 */
export interface ComboQuery {
  comboName?: string
  isActive?: number | null
}

/** 创建/更新商品请求 */
export interface ProductForm {
  categoryId?: number | null
  productCode: string
  productName: string
  productType: number
  unit: string
  costPrice: number
  retailPrice: number
  memberPrice?: number | null
  imageUrl?: string | null
  isVipOnly?: number
  isActive?: number
}

/** 创建/更新分类请求 */
export interface CategoryForm {
  parentId?: number | null
  categoryName: string
  icon?: string | null
  sortOrder?: number
  isActive?: number
}

/** 创建/更新套餐请求 */
export interface ComboForm {
  comboName: string
  comboCode: string
  description?: string | null
  comboPrice: number
  imageUrl?: string | null
  isActive?: number
  sortOrder?: number
  comboItems: ComboItemRequest[]
}

export interface ComboItemRequest {
  productId: number
  quantity: number
}
