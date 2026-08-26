import request from '@/config/axios'

/** 合同类型 VO */
export interface ContractTypeVO {
  id?: number
  code: string
  name: string
  description?: string
  status: number
  sort?: number
  processDefinitionKey?: string
  currentVersionId?: number
  currentVersionNo?: number
  draftVersionId?: number
  templateFileKey?: string
  templateFileName?: string
  createTime?: Date
}

/** 合同类型精简 VO */
export interface ContractTypeSimpleVO {
  id: number
  code: string
  name: string
  currentVersionId: number
  hasTemplate?: boolean
  description?: string
}

/** 合同类型版本 VO */
export interface ContractTypeVersionVO {
  id?: number
  typeId?: number
  versionNo?: number
  status?: number
  formConf?: string
  formFields?: string[]
  processDefinitionKey: string
  remark?: string
  publishedTime?: Date
  createTime?: Date
}

/** 合同类型分页查询参数 */
export interface ContractTypePageReqVO extends PageParam {
  code?: string
  name?: string
  status?: number
}

// 查询合同类型分页
export const getContractTypePage = (params: ContractTypePageReqVO) => {
  return request.get<PageResult<ContractTypeVO[]>>({ url: '/clm/contract-type/page', params })
}

// 查询合同类型详情
export const getContractType = (id: number) => {
  return request.get<ContractTypeVO>({ url: '/clm/contract-type/get?id=' + id })
}

// 新增合同类型
export const createContractType = (data: ContractTypeVO) => {
  return request.post<number>({ url: '/clm/contract-type/create', data })
}

// 修改合同类型
export const updateContractType = (data: ContractTypeVO) => {
  return request.put<boolean>({ url: '/clm/contract-type/update', data })
}

// 删除合同类型
export const deleteContractType = (id: number) => {
  return request.delete<boolean>({ url: '/clm/contract-type/delete?id=' + id })
}

// 查询合同类型精简列表（仅启用且已发布的类型）
export const getContractTypeSimpleList = () => {
  return request.get<ContractTypeSimpleVO[]>({ url: '/clm/contract-type/simple-list' })
}

// 查询合同类型的版本列表
export const getContractTypeVersionList = (typeId: number) => {
  return request.get<ContractTypeVersionVO[]>({
    url: '/clm/contract-type/version/list',
    params: { typeId }
  })
}

// 查询合同类型版本详情
export const getContractTypeVersion = (id: number) => {
  return request.get<ContractTypeVersionVO>({ url: '/clm/contract-type/version/get?id=' + id })
}

// 修改合同类型版本（仅草稿）
export const updateContractTypeVersion = (data: ContractTypeVersionVO) => {
  return request.put<boolean>({ url: '/clm/contract-type/version/update', data })
}

// 发布合同类型版本
export const publishContractTypeVersion = (id: number) => {
  return request.post<boolean>({ url: '/clm/contract-type/version/publish?id=' + id })
}

// 新建草稿版本
export const createContractTypeDraft = (typeId: number) => {
  return request.post<number>({ url: '/clm/contract-type/version/create-draft?typeId=' + typeId })
}

// 上传合同类型范本文件
export const uploadTypeTemplate = (formData: FormData) => {
  return request.upload<{ code: number; data: boolean; msg: string }>({
    url: '/clm/contract-type/template/upload',
    data: formData
  })
}

// 下载合同类型范本文件（blob 另存）
export const downloadTypeTemplate = async (typeId: number, fileName: string) => {
  const data: Blob = await request.download({
    url: '/clm/contract-type/template/download?typeId=' + typeId
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
