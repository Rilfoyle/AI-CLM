import request from '@/config/axios'

export interface PublishedTemplateVO {
  id: string
  code: string
  name: string
  contractTypeId: string
  contractTypeName?: string
  currentVersionId: string
  currentVersionNo: number
  fileName?: string
  description?: string
}

export interface PublishedTemplatePageReqVO extends PageParam {
  name?: string
  contractTypeId?: string | number
}

export interface TemplateUpgradePreviewVO {
  contractId: string
  upgradeAvailable: boolean
  oldTemplateVersionId?: string
  oldTemplateVersionNo?: number
  oldChecksumSha256?: string
  newTemplateVersionId?: string
  newTemplateVersionNo?: number
  newChecksumSha256?: string
}

export interface TemplateUpgradeRespVO {
  contractId: string
  revisionId: string
  documentVersionId: string
  templateVersionId: string
}

export const getPublishedTemplatePage = (params: PublishedTemplatePageReqVO) => {
  return request.get<PageResult<PublishedTemplateVO[]>>({
    url: '/clm/template/published-page',
    params
  })
}

export const getTemplateUpgradePreview = (contractId: string | number) => {
  return request.get<TemplateUpgradePreviewVO>({
    url: '/clm/template/upgrade-preview',
    params: { contractId }
  })
}

export const upgradeContractTemplate = (data: {
  contractId: string | number
  baseRevisionId: string | number
  targetTemplateVersionId: string | number
}) => {
  return request.put<TemplateUpgradeRespVO>({ url: '/clm/template/upgrade', data })
}
