package cn.iocoder.yudao.module.clm.handover;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface HandoverCaseMapper extends BaseMapperX<HandoverCaseDO> {

    @Select("SELECT * FROM clm_handover_case "
            + "WHERE source_user_id = #{sourceUserId} AND status <> 'CANCELED' AND deleted = FALSE "
            + "ORDER BY id DESC LIMIT 1 FOR UPDATE")
    HandoverCaseDO selectLatestBySourceForUpdate(@Param("sourceUserId") Long sourceUserId);

    default PageResult<HandoverCaseDO> selectPage(HandoverPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<HandoverCaseDO>()
                .eqIfPresent(HandoverCaseDO::getSourceUserId, reqVO.getSourceUserId())
                .eqIfPresent(HandoverCaseDO::getStatus, reqVO.getStatus())
                .orderByDesc(HandoverCaseDO::getId));
    }

    default void updateState(HandoverCaseDO handoverCase) {
        update(null, new LambdaUpdateWrapper<HandoverCaseDO>()
                .eq(HandoverCaseDO::getId, handoverCase.getId())
                .set(HandoverCaseDO::getTargetUserId, handoverCase.getTargetUserId())
                .set(HandoverCaseDO::getStatus, handoverCase.getStatus())
                .set(HandoverCaseDO::getReason, handoverCase.getReason())
                .set(HandoverCaseDO::getHandledBy, handoverCase.getHandledBy())
                .set(HandoverCaseDO::getFinishedTime, handoverCase.getFinishedTime()));
    }
}
