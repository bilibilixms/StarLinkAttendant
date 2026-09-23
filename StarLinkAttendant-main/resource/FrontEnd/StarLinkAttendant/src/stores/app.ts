import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAppStore = defineStore('app', () => {
  const collapsed = ref(false)

  const toggleCollapse = () => {
    collapsed.value = !collapsed.value
  }

  const setCollapse = (value: boolean) => {
    collapsed.value = value
  }

  return {
    collapsed,
    toggleCollapse,
    setCollapse
  }
})