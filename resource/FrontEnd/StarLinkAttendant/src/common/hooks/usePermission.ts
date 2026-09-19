import { usePermissionStore } from '@/stores/permission'

export function usePermission() {
  const permissionStore = usePermissionStore()

  const hasPermission = (code: string): boolean => {
    return permissionStore.permissions.includes(code) || permissionStore.permissions.length === 0
  }

  return { hasPermission }
}
