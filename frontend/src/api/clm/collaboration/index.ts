import request from '@/config/axios'
import type { ContractVO } from '@/api/clm/contract'

export type CollaborationView = 'TODO' | 'STARTED' | 'COMPLETED'

export interface CollaborationCommentVO {
  id: string
  userId?: string
  userName?: string
  revisionId: string
  content: string
  createTime?: string
}

interface CollaborationCommentCompatVO {
  id: string | number
  userId?: string | number
  actorUserId?: string | number
  userName?: string
  actorUserName?: string
  revisionId?: string | number
  content?: string
  createTime?: string
}

export interface CollaborationCaseVO {
  id: string
  contractId: string
  contractName?: string
  contractNo?: string
  revisionId: string
  completedRevisionId?: string
  currentRevisionId?: string
  legalUserId?: string
  legalUserName?: string
  starterUserName?: string
  reason?: string
  conclusion?: string
  status: string
  conclusionExpired?: boolean
  waitingMinutes?: number
  availableActions?: string[]
  comments?: CollaborationCommentVO[]
  contract?: ContractVO
  createTime?: string
  updateTime?: string
}

type CollaborationCaseCompatVO = Omit<
  CollaborationCaseVO,
  'id' | 'contractId' | 'revisionId' | 'completedRevisionId' | 'currentRevisionId' | 'comments'
> & {
  id: string | number
  contractId: string | number
  revisionId?: string | number
  requestedRevisionId?: string | number
  completedRevisionId?: string | number
  currentRevisionId?: string | number
  comments?: CollaborationCommentCompatVO[]
  events?: CollaborationCommentCompatVO[]
}

export interface CollaborationPageReqVO extends PageParam {
  view: CollaborationView
  contractId?: string
}

export interface CollaborationStartReqVO {
  contractId: string
  revisionId: string
  legalUserId: string
  reason: string
}

export interface CollaborationReplyReqVO {
  caseId: string
  revisionId: string
  content: string
}

const optionalId = (value?: string | number) =>
  value === undefined || value === null || value === '' ? undefined : String(value)

const normalizeComment = (item: CollaborationCommentCompatVO): CollaborationCommentVO => ({
  id: String(item.id),
  userId: optionalId(item.userId ?? item.actorUserId),
  userName: item.userName || item.actorUserName,
  revisionId: String(item.revisionId || ''),
  content: item.content || '',
  createTime: item.createTime
})

const normalizeCase = (item: CollaborationCaseCompatVO): CollaborationCaseVO => ({
  ...item,
  id: String(item.id),
  contractId: String(item.contractId),
  revisionId: String(item.revisionId ?? item.requestedRevisionId ?? ''),
  completedRevisionId: optionalId(item.completedRevisionId),
  currentRevisionId: optionalId(item.currentRevisionId),
  comments: (item.comments || item.events)?.map(normalizeComment)
})

export const getCollaborationPage = (params: CollaborationPageReqVO) => {
  return request
    .get<PageResult<CollaborationCaseCompatVO[]>>({
      url: '/clm/collaboration/page',
      params
    })
    .then((data) => ({ ...data, list: (data?.list || []).map(normalizeCase) }))
}

export const getCollaboration = (id: string) => {
  return request
    .get<CollaborationCaseCompatVO>({ url: '/clm/collaboration/get', params: { id } })
    .then(normalizeCase)
}

export const startCollaboration = (data: CollaborationStartReqVO) => {
  return request.post<string>({ url: '/clm/collaboration/start', data })
}

export const commentCollaboration = (data: CollaborationReplyReqVO) => {
  return request.post<string>({ url: '/clm/collaboration/comment', data })
}

export const requestCollaborationChange = (data: CollaborationReplyReqVO) => {
  return request.put<boolean>({ url: '/clm/collaboration/request-change', data })
}

export const completeCollaboration = (data: CollaborationReplyReqVO) => {
  return request.put<boolean>({
    url: '/clm/collaboration/complete',
    data: { caseId: data.caseId, revisionId: data.revisionId, conclusion: data.content }
  })
}

export const cancelCollaboration = (data: { caseId: string; reason: string }) => {
  return request.put<boolean>({ url: '/clm/collaboration/cancel', data })
}
