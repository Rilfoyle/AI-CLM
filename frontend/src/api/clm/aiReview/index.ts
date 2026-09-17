import request from '@/config/axios'

export type AiReviewRunType = 'SUMMARY' | 'EXTRACTION' | 'RISK_REVIEW'
export type AiFindingResolution = 'ACCEPTED' | 'IGNORED'

export interface AiReviewRunVO {
  id: string
  contractId: string
  revisionId: string
  runType: AiReviewRunType
  providerCode: 'LOCAL_DETERMINISTIC' | string
  status: string
  inputFingerprint?: string
  resultJson?: string
  errorMessage?: string
  finishedTime?: string
  createTime?: string
}

export interface AiReviewFindingVO {
  id: string
  runId: string
  contractId: string
  revisionId: string
  findingType: string
  severity: 'HIGH' | 'MEDIUM' | 'LOW' | string
  title: string
  detail: string
  locatorJson?: string
  resolution?: AiFindingResolution
  resolvedBy?: string
  resolvedTime?: string
}

export interface AiReviewRunResultVO {
  run: AiReviewRunVO
  stale: boolean
  findings: AiReviewFindingVO[]
}

export const runAiReview = (data: {
  contractId: string | number
  revisionId: string | number
  runType: AiReviewRunType
}) => {
  return request.post<string>({ url: '/clm/ai-review/run', data })
}

export const getAiReviewList = (contractId: string | number) => {
  return request.get<AiReviewRunResultVO[]>({ url: '/clm/ai-review/list', params: { contractId } })
}

export const resolveAiFinding = (id: string | number, resolution: AiFindingResolution) => {
  return request.put<boolean>({
    url: '/clm/ai-review/finding/resolve',
    params: { id, resolution }
  })
}
