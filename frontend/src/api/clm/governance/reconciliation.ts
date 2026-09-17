import request from '@/config/axios'

export type ReconciliationStatus = 'RUNNING' | 'SUCCEEDED' | 'PARTIAL' | 'FAILED'

export interface ReconciliationRunVO {
  id: string
  runKey: string
  triggerType: 'MANUAL' | 'SCHEDULED' | 'REPLAY'
  status: ReconciliationStatus
  scannedCount: number
  issueCount: number
  repairedCount: number
  reportJson?: string
  finishedTime?: string
  createTime?: string
}

export const getReconciliationPage = (params: PageParam) => {
  return request.get<PageResult<ReconciliationRunVO[]>>({
    url: '/clm/reconciliation/page',
    params
  })
}

export const runReconciliation = (data: {
  runKey: string
  triggerType: 'MANUAL' | 'SCHEDULED' | 'REPLAY'
}) => {
  return request.post<string>({ url: '/clm/reconciliation/run', data })
}

export const confirmReconciliation = (data: {
  runId: string
  bindingId: string | number
  opinion: string
}) => {
  return request.put<boolean>({ url: '/clm/reconciliation/confirm', data })
}
