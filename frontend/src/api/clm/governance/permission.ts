import request from '@/config/axios'

export type PermissionPolicyStatus = 'DRAFT' | 'PUBLISHED' | 'INACTIVE'
export type ClmProductRole =
  | 'clm_business'
  | 'clm_legal'
  | 'clm_contract_admin'
  | 'clm_system_admin'

export interface PermissionPolicyVO {
  id: string
  versionNo: number
  status: PermissionPolicyStatus
  roleCapabilitiesJson: string
  scopeLimitsJson: string
  nodeEditPolicyJson: string
  remark?: string
  publishedBy?: string
  publishedTime?: string
  createTime?: string
}

export interface PermissionPolicySaveReqVO {
  id?: string | number
  roleCapabilitiesJson: string
  scopeLimitsJson: string
  nodeEditPolicyJson: string
  remark?: string
}

export interface UserScopeVO {
  id: string
  userId: string
  roleCode: ClmProductRole
  orgScopeJson: string
  typeScopeJson: string
  policyVersionId: string
  effectiveFrom?: string
  effectiveTo?: string
  status: 'ACTIVE' | 'INACTIVE'
  createTime?: string
}

export interface UserScopeSaveReqVO {
  userId: string | number
  roleCode: ClmProductRole
  policyVersionId: string | number
  orgIds: Array<string | number>
  contractTypeIds: Array<string | number>
  effectiveFrom?: string
  effectiveTo?: string
  status: 'ACTIVE' | 'INACTIVE'
}

export const getPermissionPolicyList = () => {
  return request.get<PermissionPolicyVO[]>({ url: '/clm/permission/policy/list' })
}

export const savePermissionPolicy = (data: PermissionPolicySaveReqVO) => {
  return request.post<string>({ url: '/clm/permission/policy/save', data })
}

export const publishPermissionPolicy = (id: string | number) => {
  return request.put<boolean>({ url: '/clm/permission/policy/publish', params: { id } })
}

export const getUserScopeList = (userId: string | number) => {
  return request.get<UserScopeVO[]>({ url: '/clm/permission/user-scope/list', params: { userId } })
}

export const saveUserScope = (data: UserScopeSaveReqVO) => {
  return request.post<string>({ url: '/clm/permission/user-scope/save', data })
}

export const deleteUserScope = (id: string | number) => {
  return request.delete<boolean>({ url: '/clm/permission/user-scope/delete', params: { id } })
}
