/// <reference types="vite/client" />
/// <reference types="@dcloudio/types" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>
  export default component
}

interface ImportMetaEnv {
  /** 'true' 时全部走本地 mock，'false' 时打真实后端 /api/applet/** */
  readonly VITE_USE_MOCK: string
  /** 真实后端地址前缀，留空则用同源（H5 下由 vite proxy 转发） */
  readonly VITE_API_BASE_URL: string
  readonly VITE_APP_TITLE: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
