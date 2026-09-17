import request from '@/config/axios'

export interface IntegrationStatusVO {
  configured: boolean
  mode: string
  message: string
}

export interface IntegrationRunVO {
  id: string
  integrationType: 'DINGTALK_ORG_SYNC' | 'DINGTALK_SSO_CHECK'
  runKey: string
  mode: string
  status: string
  summaryJson?: string
  errorMessage?: string
  finishedTime?: string
  createTime?: string
}

export interface IntegrationDeliveryVO {
  id: string
  deliveryKey: string
  channel: string
  messageType: string
  recipientUserId?: string
  contractId?: string
  taskId?: string
  deepLinkPath?: string
  status: string
  attemptCount: number
  nextRetryTime?: string
  lastError?: string
  createTime?: string
}

export const getDingtalkStatus = () => {
  return request.get<IntegrationStatusVO>({ url: '/clm/integration/dingtalk/status' })
}

export const runDingtalkSandbox = (data: {
  integrationType: 'DINGTALK_ORG_SYNC' | 'DINGTALK_SSO_CHECK'
  runKey: string
}) => {
  return request.post<string>({ url: '/clm/integration/dingtalk/run-sandbox', data })
}

export const getIntegrationRunPage = (params: PageParam & { type?: string }) => {
  return request.get<PageResult<IntegrationRunVO[]>>({ url: '/clm/integration/run/page', params })
}

export const getIntegrationDeliveryPage = (params: PageParam & { status?: string }) => {
  return request.get<PageResult<IntegrationDeliveryVO[]>>({
    url: '/clm/integration/delivery/page',
    params
  })
}

export const retryIntegrationDelivery = (id: string | number) => {
  return request.put<boolean>({ url: '/clm/integration/delivery/retry', params: { id } })
}
