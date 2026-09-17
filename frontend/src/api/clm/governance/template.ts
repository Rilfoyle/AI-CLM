import request from '@/config/axios'

export type GovernanceTemplateStatus = 0 | 1
export type GovernanceTemplateVersionStatus = 'DRAFT' | 'PUBLISHED' | 'INACTIVE'

export interface GovernanceTemplateVersionVO {
  id: string
  templateId: string
  versionNo: number
  status: GovernanceTemplateVersionStatus
  fileName: string
  mimeType?: string
  fileSize?: number
  checksumSha256?: string
  publishedTime?: string
  remark?: string
  creator?: string
  createTime?: string
}

export interface GovernanceTemplateVO {
  id: string
  code: string
  name: string
  contractTypeId: string
  contractTypeName?: string
  currentVersionId?: string
  draftVersionId?: string
  currentVersionNo?: number
  currentFileName?: string
  status: GovernanceTemplateStatus
  description?: string
  createTime?: string
  versions?: GovernanceTemplateVersionVO[]
}

export interface GovernanceTemplatePageReqVO extends PageParam {
  code?: string
  name?: string
  contractTypeId?: string | number
  status?: GovernanceTemplateStatus
}

export interface GovernanceTemplateDraftForm {
  templateId?: string | number
  versionId?: string | number
  code: string
  name: string
  contractTypeId: string | number
  description?: string
  remark?: string
  file?: File
}

export const getGovernanceTemplatePage = (params: GovernanceTemplatePageReqVO) => {
  return request.get<PageResult<GovernanceTemplateVO[]>>({
    url: '/clm/governance/template/page',
    params
  })
}

export const getGovernanceTemplate = (id: string | number) => {
  return request.get<GovernanceTemplateVO>({
    url: '/clm/governance/template/get',
    params: { id }
  })
}

export const saveGovernanceTemplateDraft = (data: GovernanceTemplateDraftForm) => {
  const formData = new FormData()
  Object.entries(data).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      formData.append(key, value instanceof File ? value : String(value))
    }
  })
  return request.post<string>({
    url: '/clm/governance/template/save-draft',
    data: formData,
    headersType: 'multipart/form-data'
  })
}

export const publishGovernanceTemplateVersion = (id: string | number) => {
  return request.put<boolean>({ url: '/clm/governance/template/publish', params: { id } })
}

export const disableGovernanceTemplate = (id: string | number) => {
  return request.put<boolean>({ url: '/clm/governance/template/disable', params: { id } })
}

export const getGovernanceTemplateFile = (versionId: string | number) => {
  return request.download<Blob>({
    url: '/clm/governance/template/file',
    params: { versionId }
  })
}
