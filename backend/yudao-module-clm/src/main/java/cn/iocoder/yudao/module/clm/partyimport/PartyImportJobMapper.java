package cn.iocoder.yudao.module.clm.partyimport;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PartyImportJobMapper extends BaseMapperX<PartyImportJobDO> {
    default PartyImportJobDO selectByJobKey(String jobKey) {
        return selectOne(PartyImportJobDO::getJobKey, jobKey);
    }

    default PageResult<PartyImportJobDO> selectPage(PartyImportPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<PartyImportJobDO>()
                .likeIfPresent(PartyImportJobDO::getFileName, reqVO.getFileName())
                .eqIfPresent(PartyImportJobDO::getStatus, reqVO.getStatus())
                .orderByDesc(PartyImportJobDO::getId));
    }

    @Select("SELECT * FROM clm_party_import_job WHERE id = #{id} AND deleted = FALSE FOR UPDATE")
    PartyImportJobDO selectByIdForUpdate(Long id);

    @Update("UPDATE clm_party_import_job SET status = 'IMPORTING', updater = #{updater}, "
            + "update_time = CURRENT_TIMESTAMP WHERE id = #{id} AND status = #{expectedStatus} AND deleted = FALSE")
    int compareAndSetImporting(@Param("id") Long id, @Param("expectedStatus") String expectedStatus,
                               @Param("updater") String updater);
}
