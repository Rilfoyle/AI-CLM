import request from '@/config/axios'

export type WorkbenchItemType =
  | 'DRAFT'
  | 'COLLABORATION'
  | 'APPROVAL'
  | 'APPROVAL_DONE'
  | 'STARTED'
  | 'COPIED'
  | 'GOVERNANCE'

export interface WorkbenchSummaryVO {
  draftCount: number
  collaborationTodoCount: number
  approvalTodoCount: number
  startedRunningCount: number
  governanceIssueCount: number
}

export interface WorkbenchItemVO {
  id: string
  type?: WorkbenchItemType
  contractId: string
  contractName: string
  contractNo?: string
  revisionId?: string
  taskId?: string
  status?: string
  handlerName?: string
  waitingMinutes?: number
  updatedTime?: string
}

export interface WorkbenchItemPageReqVO extends PageParam {
  type: WorkbenchItemType
}

export const getWorkbenchSummary = () => {
  return request.get<WorkbenchSummaryVO>({ url: '/clm/workbench/summary' })
}

export const getWorkbenchItems = (params: WorkbenchItemPageReqVO) => {
  return request.get<PageResult<WorkbenchItemVO[]>>({ url: '/clm/workbench/items', params })
}
