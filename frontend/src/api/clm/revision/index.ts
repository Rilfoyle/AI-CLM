import request from '@/config/axios'

export interface ContractRevisionVO {
  id: string
  contractId: string
  revisionNo: number
  baseRevisionId?: string
  documentVersionId?: string
  name?: string
  amount?: number
  currency?: string
  startDate?: string
  endDate?: string
  ourPartyId?: string
  counterpartyIds?: string[]
  changeReason?: string
  sourceType?: string
  creatorName?: string
  createTime?: string
}

export interface RevisionFieldChangeVO {
  field?: string
  fieldLabel?: string
  before?: unknown
  after?: unknown
}

export interface RevisionCompareVO {
  fromRevision?: ContractRevisionVO
  toRevision?: ContractRevisionVO
  summary?: string
  fieldChanges?: RevisionFieldChangeVO[]
  partyChanges?: RevisionFieldChangeVO[]
  commitmentChanges?: RevisionFieldChangeVO[]
  documentChanges?: RevisionFieldChangeVO[]
}

export interface SaveContractRevisionReqVO {
  contractId: string
  baseRevisionId: string
  name: string
  amount?: number
  currency?: string
  startDate?: string
  endDate?: string
  ourPartyId?: string
  counterpartyIds: string[]
  changeReason: string
  approvalTaskId?: string
}

export interface SaveContractRevisionRespVO {
  revisionId: string
  revisionNo: number
}

export const getContractRevisionList = (contractId: string | number) => {
  return request.get<ContractRevisionVO[]>({
    url: '/clm/contract/revision/list',
    params: { contractId }
  })
}

export const compareContractRevisions = (fromRevisionId: string, toRevisionId: string) => {
  return request.get<RevisionCompareVO>({
    url: '/clm/contract/revision/compare',
    params: { fromRevisionId, toRevisionId }
  })
}

export const saveContractRevision = (data: SaveContractRevisionReqVO) => {
  return request.put<SaveContractRevisionRespVO>({
    url: '/clm/contract/revision/save',
    data
  })
}
