import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { CartItem } from '../types'

export const useCartStore = defineStore('cashier-cart', () => {
  const items = ref<CartItem[]>([])
  const memberId = ref<number | null>(null)
  const memberName = ref<string>('')
  const remark = ref<string>('')

  const totalCount = computed(() => items.value.reduce((sum, i) => sum + i.quantity, 0))
  const totalAmount = computed(() => items.value.reduce((sum, i) => sum + i.subtotal, 0))

  function addItem(product: { productId: number; productName: string; unitPrice: number }) {
    const existing = items.value.find(i => i.productId === product.productId)
    if (existing) {
      existing.quantity += 1
      existing.subtotal = Math.round(existing.quantity * existing.unitPrice * 100) / 100
    } else {
      items.value.push({
        productId: product.productId,
        productName: product.productName,
        unitPrice: product.unitPrice,
        quantity: 1,
        subtotal: product.unitPrice,
      })
    }
  }

  function removeItem(productId: number) {
    items.value = items.value.filter(i => i.productId !== productId)
  }

  function updateQuantity(productId: number, quantity: number) {
    const item = items.value.find(i => i.productId === productId)
    if (item) {
      if (quantity <= 0) {
        removeItem(productId)
      } else {
        item.quantity = quantity
        item.subtotal = Math.round(item.quantity * item.unitPrice * 100) / 100
      }
    }
  }

  function clearCart() {
    items.value = []
    memberId.value = null
    memberName.value = ''
    remark.value = ''
  }

  function setMember(id: number | null, name: string) {
    memberId.value = id
    memberName.value = name
  }

  return {
    items, memberId, memberName, remark,
    totalCount, totalAmount,
    addItem, removeItem, updateQuantity, clearCart, setMember, recalculatePrices,
  }
})
