import request from '@/config/axios'

/** 签约方 VO */
export interface PartyVO {
  id?: number
  partyType: number
  name: string
  unifiedCreditCode?: string
  internalFlag: boolean
  contactName?: string
  contactPhone?: string
  address?: string
  status: number
  remark?: string
  createTime?: Date
}

/** 签约方精简 VO */
export interface PartySimpleVO {
  id: number
  name: string
  partyType: number
  internalFlag: boolean
  unifiedCreditCode?: string
}

/** 签约方分页查询参数 */
export interface PartyPageReqVO extends PageParam {
  name?: string
  partyType?: number
  internalFlag?: boolean
  status?: number
}

// 查询签约方分页
export const getPartyPage = (params: PartyPageReqVO) => {
  return request.get<PageResult<PartyVO[]>>({ url: '/clm/party/page', params })
}

// 查询签约方详情
export const getParty = (id: number) => {
  return request.get<PartyVO>({ url: '/clm/party/get?id=' + id })
}

// 新增签约方
export const createParty = (data: PartyVO) => {
  return request.post<number>({ url: '/clm/party/create', data })
}

// 修改签约方
export const updateParty = (data: PartyVO) => {
  return request.put<boolean>({ url: '/clm/party/update', data })
}

// 删除签约方
export const deleteParty = (id: number) => {
  return request.delete<boolean>({ url: '/clm/party/delete?id=' + id })
}

// 查询签约方精简列表（仅启用）；internalFlag 可空：true 我方主体 / false 相对方
export const getPartySimpleList = (internalFlag?: boolean) => {
  return request.get<PartySimpleVO[]>({ url: '/clm/party/simple-list', params: { internalFlag } })
}
