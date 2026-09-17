import request from '@/config/axios'

export interface ContractCommitmentVO {
  id?: string
  contractId: string
  baseRevisionId: string
  category: string
  content: string
  ownerUserId: string
  ownerUserName?: string
  dueDate?: string
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH' | string
  createTime?: string
  updateTime?: string
}

export const getContractCommitments = (contractId: string | number) => {
  return request.get<ContractCommitmentVO[]>({
    url: '/clm/contract/commitment',
    params: { contractId }
  })
}

export const createContractCommitment = (data: ContractCommitmentVO) => {
  return request.post<string>({ url: '/clm/contract/commitment', data })
}

export const updateContractCommitment = (data: ContractCommitmentVO) => {
  return request.put<boolean>({ url: '/clm/contract/commitment', data })
}

export const deleteContractCommitment = (id: string, baseRevisionId: string) => {
  return request.delete<boolean>({
    url: '/clm/contract/commitment',
    params: { id, baseRevisionId }
  })
}

export const confirmNoContractCommitment = (
  contractId: string | number,
  baseRevisionId: string
) => {
  return request.put<boolean>({
    url: '/clm/contract/commitment/confirm-none',
    params: { contractId, baseRevisionId }
  })
}
