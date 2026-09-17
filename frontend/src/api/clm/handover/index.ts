import request from '@/config/axios'

export type HandoverCaseStatus = 'OPEN' | 'PROCESSING' | 'COMPLETED' | 'CANCELED'
export type HandoverItemType = 'CONTRACT' | 'ACTIVE_TASK'
export type HandoverItemStatus = 'PENDING' | 'TRANSFERRED' | 'SKIPPED' | 'FAILED'

export interface HandoverCaseSummaryVO {
  id: string
  sourceUserId: string
  targetUserId?: string
  status: HandoverCaseStatus
  reason?: string
  handledBy?: string
  itemCount: number
  pendingCount: number
  finishedTime?: string
  createTime?: string
  updateTime?: string
}

export interface HandoverItemVO {
  id: string
  itemType: HandoverItemType
  contractId: string
  contractNo?: string
  contractName?: string
  taskId?: string
  originalAssignee: string
  targetUserId?: string
  status: HandoverItemStatus
  resultMessage?: string
}

export interface HandoverDetailVO {
  id: string
  sourceUserId: string
  targetUserId?: string
  status: HandoverCaseStatus
  reason?: string
  handledBy?: string
  finishedTime?: string
  items: HandoverItemVO[]
}

export interface HandoverReassignRespVO {
  caseId: string
  status: HandoverCaseStatus
  pendingCount: number
  items: Array<{
    itemId: string
    itemType: HandoverItemType
    status: HandoverItemStatus
    resultMessage?: string
  }>
}

export const refreshHandoverCase = (sourceUserId: string | number) => {
  return request.post<{ caseId: string; status: string; itemCount: number; pendingCount: number }>({
    url: '/clm/handover/open/refresh',
    data: { sourceUserId }
  })
}

export const getHandoverPage = (
  params: PageParam & { sourceUserId?: string | number; status?: string }
) => {
  return request.get<PageResult<HandoverCaseSummaryVO[]>>({ url: '/clm/handover/page', params })
}

export const getHandoverDetail = (id: string | number) => {
  return request.get<HandoverDetailVO>({ url: '/clm/handover/get', params: { id } })
}

export const reassignHandover = (data: {
  caseId: string | number
  targetUserId: string | number
  reason: string
  itemIds?: Array<string | number>
}) => {
  return request.put<HandoverReassignRespVO>({ url: '/clm/handover/reassign', data })
}
