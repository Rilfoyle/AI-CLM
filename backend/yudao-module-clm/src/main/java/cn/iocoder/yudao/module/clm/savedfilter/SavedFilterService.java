package cn.iocoder.yudao.module.clm.savedfilter;

import java.util.List;

public interface SavedFilterService {
    List<SavedFilterDO> getList(Long userId, String sceneCode);
    Long save(SavedFilterSaveReqVO reqVO, Long userId);
    void delete(Long id, Long userId);
}
