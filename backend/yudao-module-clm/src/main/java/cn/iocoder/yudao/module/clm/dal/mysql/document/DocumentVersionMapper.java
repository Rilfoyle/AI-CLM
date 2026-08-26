package cn.iocoder.yudao.module.clm.dal.mysql.document;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.clm.dal.dataobject.document.DocumentVersionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

/**
 * CLM 文档版本 Mapper
 */
@Mapper
public interface DocumentVersionMapper extends BaseMapperX<DocumentVersionDO> {

    default List<DocumentVersionDO> selectListByContractId(Long contractId) {
        return selectList(new LambdaQueryWrapperX<DocumentVersionDO>()
                .eq(DocumentVersionDO::getContractId, contractId)
                .orderByDesc(DocumentVersionDO::getVersionNo)
                .orderByDesc(DocumentVersionDO::getId));
    }

    default List<DocumentVersionDO> selectListByDocumentIds(Collection<Long> documentIds) {
        return selectList(new LambdaQueryWrapperX<DocumentVersionDO>()
                .in(DocumentVersionDO::getDocumentId, documentIds)
                .orderByDesc(DocumentVersionDO::getVersionNo)
                .orderByDesc(DocumentVersionDO::getId));
    }

    default DocumentVersionDO selectLatestByDocumentId(Long documentId) {
        return CollUtil.getFirst(selectList(new LambdaQueryWrapperX<DocumentVersionDO>()
                .eq(DocumentVersionDO::getDocumentId, documentId)
                .orderByDesc(DocumentVersionDO::getVersionNo)));
    }

}
