import { ref, reactive } from 'vue'
import type { PageResult } from '@/common/api/types'

export function usePagination<T>(fetchFn: (params: any) => Promise<any>) {
  const loading = ref(false)
  const tableData = ref<T[]>([]) as any
  const pagination = reactive({
    page: 1,
    size: 10,
    total: 0,
  })

  const fetchData = async (extraParams: Record<string, any> = {}) => {
    loading.value = true
    try {
      const res = await fetchFn({
        page: pagination.page,
        size: pagination.size,
        ...extraParams,
      })
      const data = (res as any).data as PageResult<T>
      tableData.value = data.records || []
      pagination.total = data.total || 0
    } catch (e) {
      tableData.value = []
      pagination.total = 0
    } finally {
      loading.value = false
    }
  }

  const handlePageChange = (page: number) => {
    pagination.page = page
  }

  const handleSizeChange = (size: number) => {
    pagination.size = size
    pagination.page = 1
  }

  const reset = () => {
    pagination.page = 1
    pagination.total = 0
    tableData.value = []
  }

  return {
    loading,
    tableData,
    pagination,
    fetchData,
    handlePageChange,
    handleSizeChange,
    reset,
  }
}
