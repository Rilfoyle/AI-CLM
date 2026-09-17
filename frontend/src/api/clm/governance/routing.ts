import request from '@/config/axios'

export type RoutingRuleStatus = 'DRAFT' | 'PUBLISHED' | 'INACTIVE'
export type RoutingPrecheckResult = 'UNIQUE' | 'ZERO' | 'MULTIPLE'

export interface RoutingConditionVO {
  currency?: string
  ownerDeptId?: string | number
  minAmount?: number
  maxAmount?: number
}

export interface RoutingRuleDraftReqVO {
  id?: string | number
  ruleCode: string
  name: string
  contractTypeId?: string | number
  priority: number
  condition: RoutingConditionVO
  processDefinitionKey: string
}

export interface RoutingRuleVO extends RoutingRuleDraftReqVO {
  id: string
  versionNo: number
  status: RoutingRuleStatus
  effectiveTime?: string
  creator?: string
  createTime?: string
}

export interface RoutingRulePageReqVO extends PageParam {
  ruleCode?: string
  name?: string
  contractTypeId?: string | number
  status?: RoutingRuleStatus
}

export interface RoutingPrecheckReqVO {
  candidateVersionId?: string | number
  contractTypeId: string | number
  ownerDeptId?: string | number
  amount?: number
  currency?: string
}

export interface RoutingPrecheckRespVO {
  result: RoutingPrecheckResult
  matchedCount: number
  matchedRuleVersionIds: string[]
  processDefinitionKeys: string[]
}

export const getRoutingRulePage = (params: RoutingRulePageReqVO) => {
  return request.get<PageResult<RoutingRuleVO[]>>({
    url: '/clm/governance/routing/page',
    params
  })
}

export const getRoutingRule = (id: string | number) => {
  return request.get<RoutingRuleVO>({
    url: '/clm/governance/routing/get',
    params: { id }
  })
}

export const saveRoutingRuleDraft = (data: RoutingRuleDraftReqVO) => {
  return request.post<string>({ url: '/clm/governance/routing/save-draft', data })
}

export const precheckRoutingRule = (data: RoutingPrecheckReqVO) => {
  return request.post<RoutingPrecheckRespVO>({
    url: '/clm/governance/routing/precheck',
    data
  })
}

export const publishRoutingRule = (id: string | number) => {
  return request.put<boolean>({ url: '/clm/governance/routing/publish', params: { id } })
}

export const disableRoutingRule = (id: string | number) => {
  return request.put<boolean>({ url: '/clm/governance/routing/disable', params: { id } })
}
