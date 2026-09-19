import { ref } from 'vue'

export function useLoading(initialState = false) {
  const loading = ref(initialState)

  const startLoading = () => {
    loading.value = true
  }

  const stopLoading = () => {
    loading.value = false
  }

  const withLoading = async <T>(fn: () => Promise<T>): Promise<T> => {
    loading.value = true
    try {
      return await fn()
    } finally {
      loading.value = false
    }
  }

  return { loading, startLoading, stopLoading, withLoading }
}
