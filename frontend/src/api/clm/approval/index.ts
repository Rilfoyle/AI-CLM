import request from '@/config/axios'
import type { ContractVO, DocumentVO } from '@/api/clm/contract'
import type { ContractCommitmentVO } from '@/api/clm/commitment'
import type { ContractRevisionVO } from '@/api/clm/revision'
import type { SaveContractRevisionReqVO } from '@/api/clm/revision'

export interface ApprovalOpinionVO {
  taskId?: string
  nodeName?: string
  userName?: string
  action?: string
  reason?: string
  revisionId?: string
  createTime?: string
}

export interface ApprovalReturnTargetVO {
  activityId: string
  name: string
}

export interface ApprovalTaskDetailVO {
  taskId: string
  taskName?: string
  taskStatus?: string
  approvalCaseId: string
  submittedRevisionId: string
  currentRevisionId: string
  contractId: string
  contract?: ContractVO
  documents?: DocumentVO[]
  revisions?: ContractRevisionVO[]
  parties?: Record<string, unknown>[]
  commitments?: ContractCommitmentVO[]
  opinions?: ApprovalOpinionVO[]
  availableActions?: string[]
  returnTargets?: ApprovalReturnTargetVO[]
  nodeEditPolicy?: {
    canEdit: boolean
    editableFields?: string[]
    reason?: string
  }
  editPolicy?: {
    canEdit: boolean
    editableFields?: string[]
    reason?: string
  }
}

export interface ApprovalActionReqVO {
  taskId: string
  revisionId: string
  reason?: string
  requestId: string
  targetActivityId?: string
}

export interface ApprovalHistoryCaseVO {
  id: string
  status: string
  cancelReason?: string
  submittedRevisionId: string
  currentRevisionId?: string
  approvedRevisionId?: string
  supersedesCaseId?: string
  processInstanceId?: string
  opinions?: ApprovalOpinionVO[]
  startTime?: string
  endTime?: string
}

export interface ApprovalHistoryVO {
  contractId: string
  cases: ApprovalHistoryCaseVO[]
}

export interface ApprovalEditReqVO extends Omit<SaveContractRevisionReqVO, 'approvalTaskId'> {
  taskId: string
  requestId: string
  description?: string
  customData?: Record<string, unknown>
}

export interface ApprovalEditRespVO {
  revisionId: string
  revisionNo: number
  majorChange: boolean
  approvalCaseId: string
  processInstanceId?: string
}

export const getApprovalTask = (taskId: string) => {
  return request.get<ApprovalTaskDetailVO>({
    url: '/clm/approval/task/get',
    params: { taskId }
  })
}

export const approveApprovalTask = (data: ApprovalActionReqVO) => {
  return request.put<boolean>({ url: '/clm/approval/task/approve', data })
}

export const rejectApprovalTask = (data: ApprovalActionReqVO) => {
  return request.put<boolean>({ url: '/clm/approval/task/reject', data })
}

export const returnApprovalTask = (data: ApprovalActionReqVO) => {
  return request.put<boolean>({ url: '/clm/approval/task/return', data })
}

export const editApprovalTask = (data: ApprovalEditReqVO) => {
  return request.put<ApprovalEditRespVO>({ url: '/clm/approval/task/edit', data })
}

export const editApprovalTaskDocument = (data: FormData) => {
  return request.put<ApprovalEditRespVO>({
    url: '/clm/approval/task/edit-document',
    data,
    headersType: 'multipart/form-data'
  })
}

export const transferApprovalTask = (data: {
  id: string
  assigneeUserId: string | number
  reason: string
}) => {
  return request.put<boolean>({ url: '/clm/approval/task/transfer', data })
}

export const copyApprovalTask = (data: {
  id: string
  copyUserIds: Array<string | number>
  reason?: string
}) => {
  return request.put<boolean>({ url: '/clm/approval/task/copy', data })
}

export const addSignApprovalTask = (data: {
  id: string
  userIds: Array<string | number>
  type: 'before' | 'after'
  reason: string
}) => {
  return request.put<boolean>({ url: '/clm/approval/task/add-sign', data })
}

export const withdrawApprovalTask = (taskId: string) => {
  return request.put<boolean>({ url: '/clm/approval/task/withdraw', params: { taskId } })
}

export const withdrawApprovalCase = (data: { approvalCaseId: string | number; reason: string }) => {
  return request.put<boolean>({ url: '/clm/approval/case/withdraw', data })
}

export const getApprovalHistory = (contractId: string | number) => {
  return request.get<ApprovalHistoryVO>({
    url: '/clm/approval/history',
    params: { contractId }
  })
}
