package cn.iocoder.yudao.module.clm.savedfilter;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.*;

@Service
public class SavedFilterServiceImpl implements SavedFilterService {

    private static final Set<String> ALLOWED_KEYS = Set.of("title", "contractNo", "typeId", "approvalStatus",
            "lifecycleStatus", "stageCode", "ownerUserId", "orgId", "counterpartyName",
            "scope", "viewMode", "status", "view");

    @Resource private SavedFilterMapper filterMapper;

    @Override
    public List<SavedFilterDO> getList(Long userId, String sceneCode) {
        return filterMapper.selectListByUserAndScene(userId, sceneCode);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(SavedFilterSaveReqVO reqVO, Long userId) {
        validateFilter(reqVO.getFilterJson());
        SavedFilterDO filter;
        if (reqVO.getId() == null) {
            filter = new SavedFilterDO().setUserId(userId).setSceneCode(reqVO.getSceneCode());
        } else {
            filter = filterMapper.selectById(reqVO.getId());
            if (filter == null) throw exception(SAVED_FILTER_NOT_EXISTS);
            if (!userId.equals(filter.getUserId())) throw exception(SAVED_FILTER_ACCESS_DENIED);
            if (!reqVO.getSceneCode().equals(filter.getSceneCode())) throw exception(SAVED_FILTER_ACCESS_DENIED);
        }
        if (Boolean.TRUE.equals(reqVO.getDefaultFlag())) {
            filterMapper.clearDefault(userId, reqVO.getSceneCode());
        }
        filter.setName(reqVO.getName()).setFilterJson(reqVO.getFilterJson())
                .setDefaultFlag(Boolean.TRUE.equals(reqVO.getDefaultFlag()));
        if (filter.getId() == null) filterMapper.insert(filter); else filterMapper.updateById(filter);
        return filter.getId();
    }

    @Override
    public void delete(Long id, Long userId) {
        SavedFilterDO filter = filterMapper.selectById(id);
        if (filter == null) throw exception(SAVED_FILTER_NOT_EXISTS);
        if (!userId.equals(filter.getUserId())) throw exception(SAVED_FILTER_ACCESS_DENIED);
        filterMapper.deleteById(id);
    }

    private void validateFilter(String json) {
        Map<String, Object> filter;
        try {
            filter = JsonUtils.parseMap(json);
        } catch (RuntimeException ex) {
            throw exception(SAVED_FILTER_INVALID);
        }
        if (filter == null || filter.keySet().stream().anyMatch(key -> !ALLOWED_KEYS.contains(key))) {
            throw exception(SAVED_FILTER_INVALID);
        }
    }
}
