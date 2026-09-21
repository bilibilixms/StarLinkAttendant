/**
 * 购物车状态（自助点餐）。
 * 持久化到 storage，退出小程序再进来购物车还在。
 */
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { STORAGE_KEYS } from '@/config'
import { getStorage, setStorage } from '@/utils/storage'
import type { CartItem, Product } from '@/types/product'

export const useCartStore = defineStore('cart', () => {
  const items = ref<CartItem[]>([])

  const count = computed(() => items.value.reduce((sum, i) => sum + i.quantity, 0))
  const totalAmount = computed(() =>
    Math.round(items.value.reduce((sum, i) => sum + i.price * i.quantity, 0) * 100) / 100
  )
  const isEmpty = computed(() => items.value.length === 0)

  function persist(): void {
    setStorage(STORAGE_KEYS.CART, items.value)
  }

  /** 从本地缓存恢复（App onLaunch / 购物车页 onLoad） */
  function restore(): void {
    items.value = getStorage<CartItem[]>(STORAGE_KEYS.CART, [])
  }

  /** 加入购物车；已存在则累加数量，并受库存上限约束 */
  function add(product: Product, quantity = 1): boolean {
    const price = product.memberPrice ?? product.price
    const exist = items.value.find((i) => i.productId === product.id)
    if (exist) {
      if (exist.quantity + quantity > product.stock) return false
      exist.quantity += quantity
      exist.price = price
    } else {
      if (quantity > product.stock) return false
      items.value.push({
        productId: product.id,
        name: product.name,
        price,
        cover: product.cover,
        spec: product.spec,
        quantity,
        stock: product.stock,
      })
    }
    persist()
    return true
  }

  /** 设置数量，传 0 视为删除 */
  function setQuantity(productId: number, quantity: number): void {
    const idx = items.value.findIndex((i) => i.productId === productId)
    if (idx < 0) return
    const item = items.value[idx]
    if (quantity <= 0) {
      items.value.splice(idx, 1)
    } else {
      item.quantity = Math.min(quantity, item.stock)
    }
    persist()
  }

  function increment(productId: number): boolean {
    const item = items.value.find((i) => i.productId === productId)
    if (!item) return false
    if (item.quantity >= item.stock) return false
    item.quantity += 1
    persist()
    return true
  }

  function decrement(productId: number): void {
    const item = items.value.find((i) => i.productId === productId)
    if (!item) return
    item.quantity -= 1
    if (item.quantity <= 0) remove(productId)
    else persist()
  }

  function remove(productId: number): void {
    items.value = items.value.filter((i) => i.productId !== productId)
    persist()
  }

  function clear(): void {
    items.value = []
    persist()
  }

  function quantityOf(productId: number): number {
    return items.value.find((i) => i.productId === productId)?.quantity ?? 0
  }

  return {
    items,
    count,
    totalAmount,
    isEmpty,
    restore,
    add,
    setQuantity,
    increment,
    decrement,
    remove,
    clear,
    quantityOf,
  }
})
