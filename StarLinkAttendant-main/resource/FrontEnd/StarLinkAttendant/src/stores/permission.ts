import { defineStore } from 'pinia'
import { ref } from 'vue'

export const usePermissionStore = defineStore('permission', () => {
  const routes = ref<string[]>([])
  const permissions = ref<string[]>([])

  const setRoutes = (routeList: string[]) => {
    routes.value = routeList
  }

  const setPermissions = (permList: string[]) => {
    permissions.value = permList
  }

  const hasPermission = (code: string): boolean => {
    return permissions.value.includes(code) || permissions.value.length === 0
  }

  const hasRoute = (path: string): boolean => {
    return routes.value.includes(path) || routes.value.length === 0
  }

  return {
    routes,
    permissions,
    setRoutes,
    setPermissions,
    hasPermission,
    hasRoute,
  }
})