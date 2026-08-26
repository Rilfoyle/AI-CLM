import request from '@/config/axios'

/** 合同签约方（保存入参项） */
export interface ContractPartyItemVO {
  partyId: number
  roleCode: string // OUR_SIDE / COUNTERPARTY / OTHER
  sort?: number
}

/** 合同签约方（详情出参） */
export interface ContractPartyVO {
  id?: number
  partyId: number
  partyName?: string
  roleCode: string
  sort?: number
  partySnapshot?: Record<string, any>
}

/** 文档版本 */
export interface DocumentVersionVO {
  id: number
  documentId: number
  contractId: number
  versionNo: number
  parentVersionId?: number
  fileName: string
  mimeType?: string
  fileSize: number
  checksumSha256: string
  sourceType: string
  frozen: boolean
  remark?: string
  creator?: string
  creatorName?: string
  createTime?: Date
}

/** 文档 */
export interface DocumentVO {
  id: number
  contractId: number
  roleCode: string // MAIN / ATTACHMENT
  name: string
  currentVersionId?: number
  status?: number
  createTime?: Date
  versions: DocumentVersionVO[]
}

/** 流程绑定（详情中内嵌） */
export interface ContractBindingVO {
  id: number
  contractId: number
  purpose?: string
  processDefinitionKey?: string
  processDefinitionId?: string
  processInstanceId?: string
  documentVersionId?: number
  documentVersionNo?: number
  contractTypeVersionId?: number
  checksumSha256?: string
  status: number
  resultReason?: string
  creator?: string
  creatorName?: string
  createTime?: Date
  finishedTime?: Date
}

/** 合同对象权限 */
export interface ContractPermissionsVO {
  canView: boolean
  canEdit: boolean
  canDownload: boolean
  canManage: boolean
  canSubmit: boolean
  canDelete: boolean
  canCancelApproval: boolean
  canArchive?: boolean
  canCopy?: boolean
}

/** 合同 */
export interface ContractVO {
  id?: number
  contractNo?: string
  title: string
  typeId?: number
  typeCode?: string
  typeName?: string
  typeVersionId?: number
  typeVersionNo?: number
  ownerUserId?: number
  ownerUserName?: string
  ownerDeptId?: number
  ownerDeptName?: string
  amount?: number
  currency?: string
  signDate?: string
  effectiveDate?: string
  expiryDate?: string
  description?: string
  customData?: Record<string, any>
  lifecycleStatus?: number
  approvalStatus?: number
  sourceContractId?: number
  sourceContractTitle?: string
  sourceContractNo?: string
  relationType?: 'COPY' | 'RENEWAL' | string
  currentDocumentVersionId?: number
  currentBindingId?: number
  createTime?: Date
  updateTime?: Date
  creator?: string
  parties?: ContractPartyVO[] | ContractPartyItemVO[]
  currentDocumentVersion?: DocumentVersionVO | null
  currentBinding?: ContractBindingVO | null
  permissions?: ContractPermissionsVO
}

/** 参与人 */
export interface ParticipantVO {
  id?: number
  contractId?: number
  principalType?: string
  principalId: number
  principalName?: string
  roleCode: string // OWNER / COLLABORATOR / VIEWER
  canView: boolean
  canEdit: boolean
  canDownload: boolean
  canManage: boolean
  createTime?: Date
}

/** 审计事件 */
export interface AuditEventVO {
  id: number
  aggregateType: string
  aggregateId: number
  contractId: number
  action: string
  actorUserId?: number
  actorName?: string
  detailJson?: string
  occurredAt: Date
}

/** 提交审批入参 */
export interface ContractSubmitReqVO {
  id: number
  startUserSelectAssignees?: Record<string, number[]>
  remark?: string
}

/** 审批预览出参 */
export interface ApprovalPreviewVO {
  processDefinitionKey?: string
  processDefinitionId?: string
  processDefinitionName?: string
}

// ========== 合同 ==========

// 查询合同分页
export const getContractPage = (params: PageParam & Record<string, any>) => {
  return request.get<PageResult<ContractVO[]>>({ url: '/clm/contract/page', params })
}

// 查询合同详情
export const getContract = (id: number | string) => {
  return request.get<ContractVO>({ url: '/clm/contract/get?id=' + id })
}

// 新增合同
export const createContract = (data: ContractVO) => {
  return request.post<number>({ url: '/clm/contract/create', data })
}

// 修改合同
export const updateContract = (data: ContractVO) => {
  return request.put<boolean>({ url: '/clm/contract/update', data })
}

// 删除合同
export const deleteContract = (id: number) => {
  return request.delete<boolean>({ url: '/clm/contract/delete?id=' + id })
}

// 提交审批
export const submitContract = (data: ContractSubmitReqVO) => {
  return request.post<number>({ url: '/clm/contract/submit', data })
}

// 审批预览（获得流程定义信息，用于审批人预测）
export const getApprovalPreview = (id: number) => {
  return request.get<ApprovalPreviewVO>({ url: '/clm/contract/approval-preview?id=' + id })
}

// 复制 / 续签合同，返回新合同编号
export const copyContract = (data: {
  sourceContractId: number
  relationType: 'COPY' | 'RENEWAL'
  title?: string
}) => {
  return request.post<number>({ url: '/clm/contract/copy', data })
}

// 上传盖章件归档定稿，返回 CommonResult（data 为新版本编号）
export const archiveContract = (formData: FormData) => {
  return request.upload<{ code: number; data: number; msg: string }>({
    url: '/clm/contract/archive',
    data: formData
  })
}

// ========== 文档 ==========

// 上传文档（正文新版本 / 附件），返回 CommonResult
export const uploadDocument = (formData: FormData) => {
  return request.upload<{ code: number; data: number; msg: string }>({
    url: '/clm/contract/document/upload',
    data: formData
  })
}

// 查询合同文档列表（含版本）
export const getDocumentList = (contractId: number) => {
  return request.get<DocumentVO[]>({ url: '/clm/contract/document/list', params: { contractId } })
}

// 查询文档版本
export const getDocumentVersion = (id: number) => {
  return request.get<DocumentVersionVO>({ url: '/clm/document/version/get?id=' + id })
}

// 获取文档版本文件 Blob（用于页面内预览）
export const previewVersionBlob = (id: number): Promise<Blob> => {
  return request.download<Blob>({ url: '/clm/document/version/download', params: { id } })
}

// 下载文档版本文件
export const downloadVersionFile = async (id: number, fileName: string) => {
  const data: Blob = await request.download({
    url: '/clm/document/version/download',
    params: { id }
  })
  const blob = new Blob([data])
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.style.display = 'none'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}

// ========== 参与人 ==========

// 查询参与人列表
export const getParticipantList = (contractId: number) => {
  return request.get<ParticipantVO[]>({
    url: '/clm/contract/participant/list',
    params: { contractId }
  })
}

// 保存参与人
export const saveParticipants = (data: { contractId: number; participants: ParticipantVO[] }) => {
  return request.put<boolean>({ url: '/clm/contract/participant/save', data })
}

// ========== 审计 ==========

// 查询审计事件分页
export const getAuditEventPage = (params: PageParam & { contractId: number }) => {
  return request.get<PageResult<AuditEventVO[]>>({ url: '/clm/contract/audit-event/page', params })
}
