package cn.iocoder.yudao.module.clm.dal.mysql.document;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.clm.dal.dataobject.document.DocumentDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * CLM 合同文档 Mapper
 */
@Mapper
public interface DocumentMapper extends BaseMapperX<DocumentDO> {

    default List<DocumentDO> selectListByContractId(Long contractId) {
        return selectList(new LambdaQueryWrapperX<DocumentDO>()
                .eq(DocumentDO::getContractId, contractId)
                .orderByAsc(DocumentDO::getId));
    }

    default DocumentDO selectByContractIdAndRoleCode(Long contractId, String roleCode) {
        return selectFirstOne(DocumentDO::getContractId, contractId, DocumentDO::getRoleCode, roleCode);
    }

}
