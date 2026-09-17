import request from '@/config/axios'

export type NumberingRuleStatus = 'DRAFT' | 'PUBLISHED' | 'INACTIVE'
export type NumberingResetPeriod = 'NONE' | 'YEAR' | 'MONTH'

export interface NumberingRuleDraftReqVO {
  id?: string | number
  ruleCode: string
  contractTypeId?: string | number
  prefix: string
  datePattern?: string
  separator: string
  sequenceLength: number
  resetPeriod: NumberingResetPeriod
  includePartyShortName: boolean
}

export interface NumberingRulePreviewReqVO extends NumberingRuleDraftReqVO {
  ourPartyShortName: string
}

export interface NumberingRuleVO extends NumberingRuleDraftReqVO {
  id: string
  versionNo: number
  status: NumberingRuleStatus
  effectiveTime?: string
  sample?: string
  creator?: string
  createTime?: string
}

export interface NumberingRulePageReqVO extends PageParam {
  ruleCode?: string
  contractTypeId?: string | number
  status?: NumberingRuleStatus
}

export const getNumberingRulePage = (params: NumberingRulePageReqVO) => {
  return request.get<PageResult<NumberingRuleVO[]>>({
    url: '/clm/governance/numbering/page',
    params
  })
}

export const getNumberingRule = (id: string | number) => {
  return request.get<NumberingRuleVO>({
    url: '/clm/governance/numbering/get',
    params: { id }
  })
}

export const saveNumberingRuleDraft = (data: NumberingRuleDraftReqVO) => {
  return request.post<string>({ url: '/clm/governance/numbering/save-draft', data })
}

export const previewNumberingRule = (data: NumberingRulePreviewReqVO) => {
  return request.post<{ sample: string }>({ url: '/clm/governance/numbering/preview', data })
}

export const publishNumberingRule = (id: string | number) => {
  return request.put<boolean>({ url: '/clm/governance/numbering/publish', params: { id } })
}

export const disableNumberingRule = (id: string | number) => {
  return request.put<boolean>({ url: '/clm/governance/numbering/disable', params: { id } })
}
