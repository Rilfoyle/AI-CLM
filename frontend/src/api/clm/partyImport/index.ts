import request from '@/config/axios'

export type PartyImportJobStatus =
  | 'VALIDATING'
  | 'PREVIEW_READY'
  | 'IMPORTING'
  | 'PARTIAL'
  | 'SUCCEEDED'
  | 'FAILED'
export type PartyImportItemStatus = 'VALID' | 'INVALID' | 'SKIPPED' | 'IMPORTED' | 'FAILED'

export interface PartyImportItemVO {
  id: string
  jobId: string
  rowNo: number
  name: string
  unifiedCreditCode: string
  contactName?: string
  contactPhone?: string
  duplicatePartyId?: string
  status: PartyImportItemStatus
  errorMessage?: string
  partyId?: string
  updateTime?: string
}

export interface PartyImportJobVO {
  id: string
  jobKey: string
  fileName: string
  status: PartyImportJobStatus
  totalCount: number
  validCount: number
  invalidCount: number
  skippedCount: number
  successCount: number
  failedCount: number
  finishedTime?: string
  creator?: string
  createTime?: string
  updateTime?: string
  items?: PartyImportItemVO[]
}

export interface PartyImportItemUpdateReqVO {
  id: string | number
  name: string
  unifiedCreditCode: string
  contactName?: string
  contactPhone?: string
}

export const downloadPartyImportTemplate = () => {
  return request.download<Blob>({ url: '/clm/party-import/template' })
}

export const uploadPartyImport = async (file: File, jobKey: string) => {
  const data = new FormData()
  data.append('file', file)
  data.append('jobKey', jobKey)
  const response = await request.upload<{ code: number; data: string; msg: string }>({
    url: '/clm/party-import/upload',
    data
  })
  return response.data
}

export const getPartyImportPage = (params: PageParam & { fileName?: string; status?: string }) => {
  return request.get<PageResult<PartyImportJobVO[]>>({ url: '/clm/party-import/page', params })
}

export const getPartyImportJob = (id: string | number) => {
  return request.get<PartyImportJobVO>({ url: '/clm/party-import/get', params: { id } })
}

export const getPartyImportItemPage = (
  params: PageParam & { jobId: string | number; status?: string }
) => {
  return request.get<PageResult<PartyImportItemVO[]>>({
    url: '/clm/party-import/item/page',
    params
  })
}

export const updatePartyImportItem = (data: PartyImportItemUpdateReqVO) => {
  return request.put<PartyImportItemVO>({ url: '/clm/party-import/item/update', data })
}

export const confirmPartyImport = (id: string | number, requestId: string) => {
  return request.put<PartyImportJobVO>({
    url: '/clm/party-import/confirm',
    params: { id, requestId }
  })
}

export const retryFailedPartyImport = (id: string | number, requestId: string) => {
  return request.put<PartyImportJobVO>({
    url: '/clm/party-import/retry-failed',
    params: { id, requestId }
  })
}

export const downloadPartyImportFailedFile = (id: string | number) => {
  return request.download<Blob>({ url: '/clm/party-import/failed-file', params: { id } })
}
