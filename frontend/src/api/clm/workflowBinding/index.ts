import request from '@/config/axios'
import type { ContractVO, DocumentVersionVO } from '@/api/clm/contract'

/** 流程绑定 */
export interface WorkflowBindingVO {
  id: number
  contractId: number
  purpose?: string
  processDefinitionKey?: string
  processDefinitionId?: string
  processInstanceId?: string
  documentVersionId?: number
  documentVersionNo?: number
  contractTypeVersionId?: number
  checksumSha256?: string
  status: number // 0 PREPARING / 1 RUNNING / 2 APPROVED / 3 REJECTED / 4 CANCELED
  resultReason?: string
  creator?: string
  creatorName?: string
  createTime?: Date
  finishedTime?: Date
}

/** 流程绑定详情（BPM 业务表单查看用） */
export interface WorkflowBindingDetailVO {
  binding: WorkflowBindingVO
  contract: ContractVO
  formSnapshot: {
    contract?: Record<string, any>
    parties?: Record<string, any>[]
    documentVersion?: Record<string, any>
    submitRemark?: string
  }
  documentVersion?: DocumentVersionVO
  formConf?: string
  formFields?: string[]
}

// 查询合同的流程绑定列表
export const getWorkflowBindingList = (contractId: number) => {
  return request.get<WorkflowBindingVO[]>({
    url: '/clm/workflow-binding/list',
    params: { contractId }
  })
}

// 查询流程绑定详情
export const getWorkflowBinding = (id: number | string) => {
  return request.get<WorkflowBindingDetailVO>({ url: '/clm/workflow-binding/get?id=' + id })
}
