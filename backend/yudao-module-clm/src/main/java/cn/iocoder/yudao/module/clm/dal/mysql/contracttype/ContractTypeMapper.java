package cn.iocoder.yudao.module.clm.dal.mysql.contracttype;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.clm.controller.admin.contracttype.vo.ContractTypePageReqVO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contracttype.ContractTypeDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * CLM 合同类型 Mapper
 */
@Mapper
public interface ContractTypeMapper extends BaseMapperX<ContractTypeDO> {

    default PageResult<ContractTypeDO> selectPage(ContractTypePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ContractTypeDO>()
                .likeIfPresent(ContractTypeDO::getCode, reqVO.getCode())
                .likeIfPresent(ContractTypeDO::getName, reqVO.getName())
                .eqIfPresent(ContractTypeDO::getStatus, reqVO.getStatus())
                .orderByAsc(ContractTypeDO::getSort)
                .orderByDesc(ContractTypeDO::getId));
    }

    default ContractTypeDO selectByCode(String code) {
        return selectOne(ContractTypeDO::getCode, code);
    }

    default List<ContractTypeDO> selectListByStatusAndPublished(Integer status) {
        return selectList(new LambdaQueryWrapperX<ContractTypeDO>()
                .eq(ContractTypeDO::getStatus, status)
                .isNotNull(ContractTypeDO::getCurrentVersionId)
                .orderByAsc(ContractTypeDO::getSort)
                .orderByDesc(ContractTypeDO::getId));
    }

}
