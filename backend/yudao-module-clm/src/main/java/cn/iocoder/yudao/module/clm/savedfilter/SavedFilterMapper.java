package cn.iocoder.yudao.module.clm.savedfilter;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SavedFilterMapper extends BaseMapperX<SavedFilterDO> {
    default List<SavedFilterDO> selectListByUserAndScene(Long userId, String sceneCode) {
        return selectList(new LambdaQueryWrapperX<SavedFilterDO>()
                .eq(SavedFilterDO::getUserId, userId).eq(SavedFilterDO::getSceneCode, sceneCode)
                .orderByDesc(SavedFilterDO::getDefaultFlag).orderByDesc(SavedFilterDO::getId));
    }

    default void clearDefault(Long userId, String sceneCode) {
        update(new SavedFilterDO().setDefaultFlag(false), new LambdaQueryWrapperX<SavedFilterDO>()
                .eq(SavedFilterDO::getUserId, userId).eq(SavedFilterDO::getSceneCode, sceneCode)
                .eq(SavedFilterDO::getDefaultFlag, true));
    }
}
