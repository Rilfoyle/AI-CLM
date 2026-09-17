package cn.iocoder.yudao.module.clm.partyimport;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface PartyImportItemMapper extends BaseMapperX<PartyImportItemDO> {
    default List<PartyImportItemDO> selectListByJobId(Long jobId) {
        return selectList(new LambdaQueryWrapperX<PartyImportItemDO>()
                .eq(PartyImportItemDO::getJobId, jobId)
                .orderByAsc(PartyImportItemDO::getRowNo));
    }

    default List<PartyImportItemDO> selectListByJobIdAndStatuses(Long jobId, Collection<String> statuses) {
        return selectList(new LambdaQueryWrapperX<PartyImportItemDO>()
                .eq(PartyImportItemDO::getJobId, jobId)
                .in(PartyImportItemDO::getStatus, statuses)
                .orderByAsc(PartyImportItemDO::getRowNo));
    }

    default PageResult<PartyImportItemDO> selectPage(PartyImportItemPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<PartyImportItemDO>()
                .eq(PartyImportItemDO::getJobId, reqVO.getJobId())
                .eqIfPresent(PartyImportItemDO::getStatus, reqVO.getStatus())
                .orderByAsc(PartyImportItemDO::getRowNo));
    }

    @Select("SELECT * FROM clm_party_import_item WHERE id = #{id} AND deleted = FALSE FOR UPDATE")
    PartyImportItemDO selectByIdForUpdate(Long id);
}
