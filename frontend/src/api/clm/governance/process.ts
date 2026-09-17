import request from '@/config/axios'

export interface ContractProcessSummaryVO {
  id: string
  key: string
  name: string
  description?: string
  deployed?: boolean
  deployedVersion?: number
  updateTime?: string
}

export interface ContractProcessModelVO {
  id?: string
  key: string
  name: string
  description?: string
  category?: string
  type: number
  formType: number
  formId?: number
  formCustomCreatePath?: string
  formCustomViewPath?: string
  visible: boolean
  startUserIds?: number[]
  startDeptIds?: number[]
  managerUserIds: number[]
  allowCancelRunningProcess?: boolean
  allowWithdrawTask?: boolean
  modelVersion?: number
  simpleModel?: Record<string, any>
  processDefinition?: {
    id?: string
    version?: number
    suspensionState?: number
  }
}

export type ContractProcessSaveVO = Omit<ContractProcessModelVO, 'modelVersion' | 'processDefinition'> & {
  /** 读取详情时的模型版本；更新时用于防止多个管理员互相覆盖。 */
  expectedModelVersion?: number
}

export const getContractProcessList = (name?: string) => {
  return request.get<ContractProcessSummaryVO[]>({
    url: '/clm/governance/process/list',
    params: { name }
  })
}

export const getContractProcess = (id: string) => {
  return request.get<ContractProcessModelVO>({
    url: '/clm/governance/process/get',
    params: { id }
  })
}

export const createContractProcess = (data: ContractProcessSaveVO) => {
  return request.post<string>({ url: '/clm/governance/process/create', data })
}

export const updateContractProcess = (data: ContractProcessSaveVO) => {
  return request.put<boolean>({ url: '/clm/governance/process/update', data })
}

export const deployContractProcess = (id: string) => {
  return request.post<boolean>({
    url: '/clm/governance/process/deploy',
    params: { id }
  })
}
