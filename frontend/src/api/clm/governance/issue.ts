import request from '@/config/axios'

export interface GovernanceIssueVO {
  id: string
  issueType: string
  status: 'OPEN' | 'RESOLVED'
  contractId?: string
  approvalBindingId?: string
  sourceRef?: string
  summary: string
  detailJson?: string
  ownerUserId?: string
  resolvedBy?: string
  resolvedTime?: string
  createTime?: string
}

export interface GovernanceIssuePageReqVO extends PageParam {
  issueType?: string
  status?: 'OPEN' | 'RESOLVED'
  contractId?: string | number
  ownerUserId?: string | number
}

export const getGovernanceIssuePage = (params: GovernanceIssuePageReqVO) => {
  return request.get<PageResult<GovernanceIssueVO[]>>({
    url: '/clm/governance-issue/page',
    params
  })
}

export const resolveGovernanceIssue = (id: string | number) => {
  return request.put<boolean>({ url: '/clm/governance-issue/resolve', params: { id } })
}
