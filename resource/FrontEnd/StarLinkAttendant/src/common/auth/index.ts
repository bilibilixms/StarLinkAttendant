const TOKEN_KEY = 'starlink_access_token'
const REFRESH_TOKEN_KEY = 'starlink_refresh_token'

export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function removeToken(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
}

export function getRefreshToken(): string {
  return localStorage.getItem(REFRESH_TOKEN_KEY) || ''
}

export function setRefreshToken(token: string): void {
  localStorage.setItem(REFRESH_TOKEN_KEY, token)
}

export function isAuthenticated(): boolean {
  return !!getToken()
}
