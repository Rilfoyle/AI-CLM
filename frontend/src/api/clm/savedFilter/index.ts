import request from '@/config/axios'

export type SavedFilterScene = 'CONTRACT_LEDGER' | 'APPROVAL_INBOX' | 'COLLABORATION_CENTER'

export interface SavedFilterVO {
  id: string
  userId: string
  sceneCode: SavedFilterScene
  name: string
  filterJson: string
  defaultFlag: boolean
  createTime?: string
}

export interface SavedFilterSaveReqVO {
  id?: string | number
  sceneCode: SavedFilterScene
  name: string
  filterJson: string
  defaultFlag?: boolean
}

export const getSavedFilterList = (sceneCode: SavedFilterScene) => {
  return request.get<SavedFilterVO[]>({ url: '/clm/saved-filter/list', params: { sceneCode } })
}

export const saveSavedFilter = (data: SavedFilterSaveReqVO) => {
  return request.post<string>({ url: '/clm/saved-filter/save', data })
}

export const deleteSavedFilter = (id: string | number) => {
  return request.delete<boolean>({ url: '/clm/saved-filter/delete', params: { id } })
}
