import request from '@/config/axios'

/** 在线编辑模式 */
export type OnlineEditMode = 'edit' | 'view'

/** ONLYOFFICE 文档配置（透传给 DocsAPI.DocEditor） */
export interface OnlineEditDocumentConfig {
  fileType: string
  key: string
  title: string
  url: string
  permissions?: {
    edit?: boolean
    download?: boolean
    print?: boolean
    review?: boolean
    comment?: boolean
  }
}

/** ONLYOFFICE 编辑器配置 */
export interface OnlineEditEditorConfig {
  mode: OnlineEditMode
  lang?: string
  callbackUrl?: string
  user?: { id: string; name: string }
  customization?: Record<string, any>
}

/** ONLYOFFICE 完整配置 */
export interface OnlineEditConfig {
  documentType: string // word / cell / slide
  document: OnlineEditDocumentConfig
  editorConfig: OnlineEditEditorConfig
}

/** 在线编辑配置响应 */
export interface OnlineEditConfigRespVO {
  enabled: boolean
  documentServerUrl?: string
  config?: OnlineEditConfig
  token?: string
}

// 获取在线编辑 / 预览配置
export const getOnlineEditConfig = (versionId: number | string, mode: OnlineEditMode) => {
  return request.get<OnlineEditConfigRespVO>({
    url: '/clm/online-edit/config',
    params: { versionId, mode }
  })
}

// ========== Document Server 脚本加载 ==========

const DOCS_API_PATH = '/web-apps/apps/api/documents/api.js'
const DOCS_API_PROMISE_KEY = '__clmOnlyOfficeDocsApiPromise__'

/** 取 window.DocsAPI（由 Document Server 的 api.js 注入） */
export const getDocsApi = (): any => (window as any).DocsAPI

/**
 * 注入 `{documentServerUrl}/web-apps/apps/api/documents/api.js`（只注入一次，Promise 缓存在 window 上）。
 * Document Server 不可达时 reject，调用方需自行降级展示，不会抛出未处理异常。
 */
export const loadDocsApi = (documentServerUrl: string): Promise<any> => {
  const w = window as any
  if (w.DocsAPI) {
    return Promise.resolve(w.DocsAPI)
  }
  if (w[DOCS_API_PROMISE_KEY]) {
    return w[DOCS_API_PROMISE_KEY]
  }
  const src = documentServerUrl.replace(/\/+$/, '') + DOCS_API_PATH
  const promise = new Promise<any>((resolve, reject) => {
    const script = document.createElement('script')
    script.src = src
    script.async = true
    script.onload = () => {
      if (w.DocsAPI) {
        resolve(w.DocsAPI)
      } else {
        reject(new Error('api.js 已加载，但未找到 window.DocsAPI'))
      }
    }
    script.onerror = () => {
      script.remove()
      reject(new Error('无法加载 ' + src))
    }
    document.head.appendChild(script)
  }).catch((e) => {
    // 失败后清理缓存，允许下次重试
    delete w[DOCS_API_PROMISE_KEY]
    throw e
  })
  w[DOCS_API_PROMISE_KEY] = promise
  return promise
}
