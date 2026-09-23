import request from '@/common/api/request'
import type { PageRequest, PageResult } from '@/common/api/types'
import type {
  ProductItem, CategoryItem, ComboItem,
  ProductQuery, ComboQuery,
  ProductForm, CategoryForm, ComboForm,
} from '../types'

// ========== 商品管理 ==========
export function getProductList(params: PageRequest & ProductQuery) {
  return request.get<any, { data: PageResult<ProductItem> }>('/api/product/list', { params })
}

export function getProduct(id: number) {
  return request.get<any, { data: ProductItem }>(`/api/product/${id}`)
}

export function createProduct(data: ProductForm) {
  return request.post<any, { data: ProductItem }>('/api/product', data)
}

export function updateProduct(id: number, data: ProductForm) {
  return request.put<any, { data: ProductItem }>(`/api/product/${id}`, data)
}

export function deleteProduct(id: number) {
  return request.delete(`/api/product/${id}`)
}

export function updateProductStatus(id: number, isActive: number) {
  return request.patch(`/api/product/${id}/status`, { isActive })
}

// ========== 分类管理 ==========
export function getCategoryTree() {
  return request.get<any, { data: CategoryItem[] }>('/api/product/categories')
}

export function createCategory(data: CategoryForm) {
  return request.post<any, { data: CategoryItem }>('/api/product/categories', data)
}

export function updateCategory(id: number, data: CategoryForm) {
  return request.put<any, { data: CategoryItem }>(`/api/product/categories/${id}`, data)
}

export function deleteCategory(id: number) {
  return request.delete(`/api/product/categories/${id}`)
}

// ========== 套餐管理 ==========
export function getComboList(params: PageRequest & ComboQuery) {
  return request.get<any, { data: PageResult<ComboItem> }>('/api/product/combos', { params })
}

export function getCombo(id: number) {
  return request.get<any, { data: ComboItem }>(`/api/product/combos/${id}`)
}

export function createCombo(data: ComboForm) {
  return request.post<any, { data: ComboItem }>('/api/product/combos', data)
}

export function updateCombo(id: number, data: ComboForm) {
  return request.put<any, { data: ComboItem }>(`/api/product/combos/${id}`, data)
}

export function deleteCombo(id: number) {
  return request.delete(`/api/product/combos/${id}`)
}

export function updateComboStatus(id: number, isActive: number) {
  return request.patch(`/api/product/combos/${id}/status`, { isActive })
}

// ========== 文件上传 ==========
export function uploadFile(file: File, bizType = 'product') {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('bizType', bizType)
  return request.post<any, { data: { url: string; name: string } }>('/api/file/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
