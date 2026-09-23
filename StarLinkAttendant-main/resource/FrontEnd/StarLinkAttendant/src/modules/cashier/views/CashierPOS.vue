<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Minus, Delete, ShoppingCart, User, Money, Goods } from '@element-plus/icons-vue'
import { getProductList, getCategoryTree } from '@/modules/product/api'
import { createOrder, payOrder } from '../api'
import { useCartStore } from '../stores'
import { PAYMENT_METHOD_MAP } from '@/common/constants'
import { formatMoney } from '@/common/utils/money'
import type { ProductItem, CategoryItem } from '@/modules/product/types'

const cart = useCartStore()
const productLoading = ref(false)
const productList = ref<ProductItem[]>([])
const categories = ref<CategoryItem[]>([])
const activeCategoryId = ref<number | null>(null)
const searchKeyword = ref('')
const payDialogVisible = ref(false)
const selectedPayMethod = ref(1)
const memberSearchVisible = ref(false)
const memberPhone = ref('')
const paying = ref(false)

const productQuery = reactive({
  page: 1, size: 200, productName: '', isActive: 1,
})

const filteredProducts = computed(() => {
  let list = productList.value
  if (activeCategoryId.value) {
    list = list.filter(p => p.categoryId === activeCategoryId.value)
  }
  if (searchKeyword.value) {
    const kw = searchKeyword.value.toLowerCase()
    list = list.filter(p =>
      p.productName.toLowerCase().includes(kw) || p.productCode.toLowerCase().includes(kw)
    )
  }
  return list
})

const categoryProductCount = computed(() => {
  const map: Record<number, number> = {}
  for (const p of productList.value) {
    const cid = p.categoryId || 0
    map[cid] = (map[cid] || 0) + 1
  }
  return map
})

const fetchProducts = async () => {
  productLoading.value = true
  try {
    const res = await getProductList(productQuery)
    productList.value = res.data.records || []
  } catch { productList.value = [] }
  finally { productLoading.value = false }
}

const fetchCategories = async () => {
  try {
    const res = await getCategoryTree()
    categories.value = res.data || []
  } catch { categories.value = [] }
}

const selectCategory = (id: number | null) => {
  activeCategoryId.value = id
}

const addToCart = (product: ProductItem) => {
  cart.addItem({
    productId: product.id,
    productName: product.productName,
    unitPrice: product.retailPrice,
  })
}

const handleQuantityChange = (productId: number, val: number) => {
  cart.updateQuantity(productId, val)
}

const getCartQuantity = (productId: number) => {
  const item = cart.items.find(i => i.productId === productId)
  return item ? item.quantity : 0
}

const handlePay = () => {
  if (cart.items.length === 0) {
    ElMessage.warning('购物车为空')
    return
  }
  selectedPayMethod.value = 1
  payDialogVisible.value = true
}

const confirmPay = async () => {
  if (paying.value) return
  paying.value = true
  try {
    const orderRes = await createOrder({
      memberId: cart.memberId || null,
      orderType: 1,
      remark: cart.remark || undefined,
      items: cart.items.map(i => ({ productId: i.productId, quantity: i.quantity })),
    })
    await payOrder(orderRes.data.id, { paymentMethod: selectedPayMethod.value })
    ElMessage.success({ message: '支付成功！', duration: 2000 })
    cart.clearCart()
    payDialogVisible.value = false
  } catch { /* handled */ }
  finally { paying.value = false }
}

const handleClearCart = () => {
  if (cart.items.length === 0) return
  ElMessageBox.confirm('确定清空购物车？', '提示', { type: 'warning' })
    .then(() => cart.clearCart())
    .catch(() => {})
}

const handleBindMember = () => {
  memberSearchVisible.value = true
  memberPhone.value = ''
}

const handleUnbindMember = () => {
  cart.setMember(null, '')
}

const confirmMemberSearch = async () => {
  if (!memberPhone.value) return
  try {
    const res = await import('@/modules/member/api').then(m => m.getMemberList({ page: 1, size: 1, phone: memberPhone.value }))
    const members = res.data.records || []
    const member = members[0]
    if (member) {
      cart.setMember(member.id, member.realName || member.phone)
      ElMessage.success(`已绑定会员: ${member.realName || member.phone}`)
      memberSearchVisible.value = false
    } else {
      ElMessage.warning('未找到该手机号对应的会员')
    }
  } catch { /* handled */ }
}

const getProductTypeColor = (type: number) => {
  const map: Record<number, string> = { 1: '#f59e0b', 2: '#3b82f6', 3: '#10b981', 4: '#6b7280' }
  return map[type] || '#6b7280'
}

const getProductTypeLabel = (type: number) => {
  const map: Record<number, string> = { 1: '食品', 2: '饮料', 3: '日用', 4: '其他' }
  return map[type] || '其他'
}

onMounted(() => { fetchProducts(); fetchCategories() })
</script>

<template>
  <div class="pos-container">
    <div class="pos-body">
      <!-- 左侧: 商品区域 -->
      <div class="product-panel">
        <!-- 搜索栏 -->
        <div class="product-toolbar">
          <el-input v-model="searchKeyword" placeholder="搜索商品名称 / 编码" :prefix-icon="Search" clearable class="search-input" />
          <div class="category-tabs">
            <div
              class="tab-item"
              :class="{ active: activeCategoryId === null }"
              @click="selectCategory(null)"
            >
              全部
              <span class="tab-count">{{ productList.length }}</span>
            </div>
            <div
              v-for="cat in categories"
              :key="cat.id"
              class="tab-item"
              :class="{ active: activeCategoryId === cat.id }"
              @click="selectCategory(cat.id)"
            >
              {{ cat.categoryName }}
              <span class="tab-count">{{ categoryProductCount[cat.id] || 0 }}</span>
            </div>
          </div>
        </div>

        <!-- 商品网格 -->
        <div v-loading="productLoading" class="product-grid">
          <div
            v-for="product in filteredProducts"
            :key="product.id"
            class="product-card"
            @click="addToCart(product)"
          >
            <div class="product-card-header">
              <span class="product-type-badge" :style="{ background: getProductTypeColor(product.productType) }">
                {{ getProductTypeLabel(product.productType) }}
              </span>
              <span v-if="product.isVipOnly" class="vip-badge">VIP</span>
              <span v-if="getCartQuantity(product.id) > 0" class="cart-badge">{{ getCartQuantity(product.id) }}</span>
            </div>
            <div class="product-thumb">
              <img v-if="product.imageUrl" :src="product.imageUrl" :alt="product.productName" loading="lazy" />
              <div v-else class="thumb-placeholder">{{ product.productName.slice(0, 1) }}</div>
            </div>
            <div class="product-name" :title="product.productName">{{ product.productName }}</div>
            <div class="product-meta">
              <span class="product-price">¥{{ formatMoney(product.retailPrice) }}</span>
              <span class="product-unit">/{{ product.unit }}</span>
            </div>
            <div v-if="product.memberPrice" class="member-price">会员价 ¥{{ formatMoney(product.memberPrice) }}</div>
          </div>
          <div v-if="filteredProducts.length === 0 && !productLoading" class="empty-tip">
            <el-icon :size="48" color="#cbd5e1"><Goods /></el-icon>
            <p>暂无商品</p>
          </div>
        </div>
      </div>

      <!-- 右侧: 购物车 -->
      <div class="cart-panel">
        <!-- 会员区域 -->
        <div class="member-section">
          <div v-if="cart.memberId" class="member-bound">
            <el-icon :size="18" color="#3b82f6"><User /></el-icon>
            <span class="member-name">{{ cart.memberName }}</span>
            <el-button type="danger" text size="small" @click="handleUnbindMember">取消</el-button>
          </div>
          <el-button v-else text type="primary" size="small" @click="handleBindMember">
            <el-icon><User /></el-icon> 绑定会员
          </el-button>
        </div>

        <!-- 购物车头部 -->
        <div class="cart-header">
          <div class="cart-title">
            <el-icon :size="18"><ShoppingCart /></el-icon>
            <span>购物车</span>
            <el-tag v-if="cart.totalCount > 0" size="small" type="primary" round>{{ cart.totalCount }}</el-tag>
          </div>
          <el-button type="danger" text size="small" :disabled="cart.items.length === 0" @click="handleClearCart">清空</el-button>
        </div>

        <!-- 购物车列表 -->
        <div class="cart-items">
          <div v-if="cart.items.length === 0" class="cart-empty">
            <el-icon :size="56" color="#e2e8f0"><ShoppingCart /></el-icon>
            <p>点击左侧商品添加</p>
          </div>
          <transition-group name="cart-item">
            <div v-for="item in cart.items" :key="item.productId" class="cart-item">
              <div class="cart-item-top">
                <span class="cart-item-name">{{ item.productName }}</span>
                <span class="cart-item-unit-price">¥{{ formatMoney(item.unitPrice) }}</span>
              </div>
              <div class="cart-item-bottom">
                <el-input-number
                  :model-value="item.quantity"
                  :min="1"
                  :max="99"
                  size="small"
                  controls-position="right"
                  @update:model-value="(v: number) => handleQuantityChange(item.productId, v || 1)"
                  style="width: 110px"
                />
                <span class="cart-item-subtotal">¥{{ formatMoney(item.subtotal) }}</span>
                <el-button type="danger" text size="small" :icon="Delete" @click="cart.removeItem(item.productId)" />
              </div>
            </div>
          </transition-group>
        </div>

        <!-- 备注 -->
        <div class="cart-remark">
          <el-input v-model="cart.remark" placeholder="备注（可选）" size="small" clearable />
        </div>

        <!-- 结算区域 -->
        <div class="cart-footer">
          <div class="footer-stats">
            <div class="stat-row">
              <span class="stat-label">商品数量</span>
              <span class="stat-value">{{ cart.totalCount }} 件</span>
            </div>
            <div class="stat-row total-row">
              <span class="stat-label">应收金额</span>
              <span class="stat-value total-amount">¥{{ formatMoney(cart.totalAmount) }}</span>
            </div>
          </div>
          <el-button
            type="primary"
            size="large"
            class="pay-btn"
            :disabled="cart.items.length === 0"
            @click="handlePay"
          >
            <el-icon :size="18"><Money /></el-icon>
            结 算
          </el-button>
        </div>
      </div>
    </div>

    <!-- 支付弹窗 -->
    <el-dialog v-model="payDialogVisible" title="确认支付" width="460px" :close-on-click-modal="false">
      <div class="pay-dialog">
        <div class="pay-order-summary">
          <div class="pay-order-items">
            <div v-for="item in cart.items" :key="item.productId" class="pay-order-item">
              <span class="poi-name">{{ item.productName }}</span>
              <span class="poi-qty">x{{ item.quantity }}</span>
              <span class="poi-subtotal">¥{{ formatMoney(item.subtotal) }}</span>
            </div>
          </div>
          <el-divider />
          <div class="pay-order-total">
            <span>合计</span>
            <span class="pay-total-amount">¥{{ formatMoney(cart.totalAmount) }}</span>
          </div>
        </div>

        <div class="pay-method-section">
          <div class="pay-method-label">支付方式</div>
          <div class="pay-method-options">
            <div
              v-for="(label, val) in PAYMENT_METHOD_MAP"
              :key="val"
              class="pay-method-card"
              :class="{ active: selectedPayMethod === Number(val) }"
              @click="selectedPayMethod = Number(val)"
            >
              <el-icon :size="20">
                <Money v-if="Number(val) === 1" />
                <User v-else />
              </el-icon>
              <span>{{ label }}</span>
            </div>
          </div>
        </div>

        <div v-if="cart.memberId" class="pay-member-info">
          <el-icon><User /></el-icon>
          <span>会员: {{ cart.memberName }}</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="payDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="paying" @click="confirmPay">确认支付 ¥{{ formatMoney(cart.totalAmount) }}</el-button>
      </template>
    </el-dialog>

    <!-- 会员搜索弹窗 -->
    <el-dialog v-model="memberSearchVisible" title="绑定会员" width="400px">
      <el-form @submit.prevent="confirmMemberSearch">
        <el-form-item label="会员手机号">
          <el-input v-model="memberPhone" placeholder="输入会员手机号查询" :prefix-icon="Search" clearable />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="memberSearchVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmMemberSearch">查询绑定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.pos-container { width: 100%; }
.pos-body { display: flex; gap: 16px; height: calc(100vh - 130px); }

/* ===== 左侧商品区 ===== */
.product-panel {
  flex: 1; background: white; border-radius: 12px; padding: 16px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.05); display: flex; flex-direction: column; overflow: hidden;
}
.product-toolbar { margin-bottom: 12px; }
.search-input { margin-bottom: 10px; }
.category-tabs { display: flex; gap: 6px; overflow-x: auto; padding-bottom: 4px; }
.category-tabs::-webkit-scrollbar { height: 3px; }
.category-tabs::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 3px; }
.tab-item {
  display: flex; align-items: center; gap: 4px; padding: 6px 14px; border-radius: 20px;
  font-size: 13px; color: #64748b; cursor: pointer; white-space: nowrap;
  background: #f8fafc; border: 1px solid transparent; transition: all 0.2s;
}
.tab-item:hover { background: #eff6ff; color: #3b82f6; }
.tab-item.active { background: #3b82f6; color: white; border-color: #3b82f6; }
.tab-item.active .tab-count { background: rgba(255,255,255,0.25); color: white; }
.tab-count {
  font-size: 11px; background: #e2e8f0; color: #64748b; padding: 1px 6px;
  border-radius: 10px; min-width: 18px; text-align: center;
}

.product-grid {
  flex: 1; overflow-y: auto; display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 10px; align-content: start; padding-right: 4px;
}
.product-grid::-webkit-scrollbar { width: 4px; }
.product-grid::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 4px; }
.product-card {
  border: 1px solid #e2e8f0; border-radius: 10px; padding: 12px; cursor: pointer;
  transition: all 0.2s; position: relative; background: #fafbfc;
}
.product-card:hover { border-color: #93c5fd; box-shadow: 0 3px 12px rgba(59,130,246,0.12); transform: translateY(-2px); background: white; }
.product-card:active { transform: translateY(0); }
.product-card-header { display: flex; gap: 4px; margin-bottom: 8px; align-items: center; }
.product-thumb { height: 64px; border-radius: 6px; overflow: hidden; margin-bottom: 8px; background: #f1f5f9; }
.product-thumb img { width: 100%; height: 100%; object-fit: cover; display: block; }
.thumb-placeholder {
  width: 100%; height: 100%; display: flex; align-items: center; justify-content: center;
  font-size: 22px; font-weight: 600; color: #94a3b8; background: #f1f5f9;
}
.product-type-badge {
  font-size: 10px; color: white; padding: 1px 6px; border-radius: 4px; font-weight: 500;
}
.vip-badge {
  font-size: 10px; color: #d97706; background: #fef3c7; padding: 1px 5px; border-radius: 4px; font-weight: 600;
}
.cart-badge {
  position: absolute; top: -6px; right: -6px; background: #ef4444; color: white;
  font-size: 11px; font-weight: 600; width: 20px; height: 20px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
}
.product-name {
  font-size: 13px; font-weight: 600; color: #1e293b; margin-bottom: 6px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.product-meta { display: flex; align-items: baseline; gap: 2px; }
.product-price { font-size: 16px; font-weight: 700; color: #ef4444; }
.product-unit { font-size: 11px; color: #94a3b8; }
.member-price { font-size: 11px; color: #d97706; margin-top: 4px; }
.empty-tip { grid-column: 1 / -1; text-align: center; color: #94a3b8; padding: 60px 0; }

/* ===== 右侧购物车 ===== */
.cart-panel {
  width: 400px; background: white; border-radius: 12px; padding: 0;
  box-shadow: 0 1px 3px rgba(0,0,0,0.05); display: flex; flex-direction: column; overflow: hidden;
}
.member-section {
  padding: 10px 16px; border-bottom: 1px solid #f1f5f9; background: #f8fafc; min-height: 44px;
  display: flex; align-items: center;
}
.member-bound { display: flex; align-items: center; gap: 8px; flex: 1; }
.member-name { font-size: 13px; font-weight: 500; color: #1e293b; }

.cart-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 12px 16px; border-bottom: 1px solid #f1f5f9;
}
.cart-title { display: flex; align-items: center; gap: 6px; font-size: 15px; font-weight: 600; color: #1e293b; }

.cart-items { flex: 1; overflow-y: auto; padding: 0 16px; }
.cart-items::-webkit-scrollbar { width: 4px; }
.cart-items::-webkit-scrollbar-thumb { background: #e2e8f0; border-radius: 4px; }
.cart-empty { text-align: center; color: #94a3b8; padding: 60px 0; }
.cart-empty p { margin-top: 8px; font-size: 13px; }

.cart-item {
  padding: 10px 0; border-bottom: 1px solid #f8fafc;
}
.cart-item-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.cart-item-name { font-size: 13px; font-weight: 500; color: #334155; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cart-item-unit-price { font-size: 12px; color: #94a3b8; margin-left: 8px; }
.cart-item-bottom { display: flex; align-items: center; gap: 8px; }
.cart-item-subtotal { font-size: 15px; font-weight: 700; color: #ef4444; flex: 1; text-align: right; margin-right: 4px; }

.cart-item-enter-active { transition: all 0.3s ease; }
.cart-item-leave-active { transition: all 0.2s ease; }
.cart-item-enter-from { opacity: 0; transform: translateX(20px); }
.cart-item-leave-to { opacity: 0; transform: translateX(-20px); }

.cart-remark { padding: 8px 16px; border-top: 1px solid #f1f5f9; }

.cart-footer { padding: 12px 16px 16px; border-top: 1px solid #e2e8f0; background: #fafbfc; }
.footer-stats { margin-bottom: 12px; }
.stat-row { display: flex; justify-content: space-between; padding: 3px 0; font-size: 13px; color: #64748b; }
.total-row { padding-top: 6px; border-top: 1px dashed #e2e8f0; margin-top: 4px; }
.total-row .stat-label { font-weight: 600; color: #334155; font-size: 14px; }
.total-amount { font-size: 22px; font-weight: 800; color: #ef4444; }
.pay-btn { width: 100%; height: 44px; font-size: 16px; font-weight: 600; letter-spacing: 2px; }

/* ===== 支付弹窗 ===== */
.pay-dialog { }
.pay-order-summary { background: #f8fafc; border-radius: 8px; padding: 12px 16px; }
.pay-order-items { max-height: 150px; overflow-y: auto; }
.pay-order-item { display: flex; justify-content: space-between; padding: 4px 0; font-size: 13px; color: #475569; }
.poi-name { flex: 1; }
.poi-qty { color: #94a3b8; margin: 0 12px; }
.poi-subtotal { font-weight: 500; }
.pay-order-total { display: flex; justify-content: space-between; font-size: 16px; font-weight: 700; color: #1e293b; }
.pay-total-amount { color: #ef4444; font-size: 20px; }

.pay-method-section { margin-top: 16px; }
.pay-method-label { font-size: 14px; font-weight: 500; color: #334155; margin-bottom: 10px; }
.pay-method-options { display: flex; gap: 12px; }
.pay-method-card {
  flex: 1; display: flex; flex-direction: column; align-items: center; gap: 6px;
  padding: 16px 12px; border: 2px solid #e2e8f0; border-radius: 10px; cursor: pointer;
  transition: all 0.2s; font-size: 13px; color: #64748b;
}
.pay-method-card:hover { border-color: #93c5fd; background: #eff6ff; }
.pay-method-card.active { border-color: #3b82f6; background: #eff6ff; color: #1e40af; font-weight: 600; }

.pay-member-info {
  margin-top: 12px; padding: 8px 12px; background: #eff6ff; border-radius: 6px;
  display: flex; align-items: center; gap: 6px; font-size: 13px; color: #1e40af;
}
</style>
