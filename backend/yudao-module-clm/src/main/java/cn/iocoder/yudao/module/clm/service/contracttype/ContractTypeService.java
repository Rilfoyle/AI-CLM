package cn.iocoder.yudao.module.clm.service.contracttype;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.clm.controller.admin.contracttype.vo.*;
import cn.iocoder.yudao.module.clm.dal.dataobject.contracttype.ContractTypeDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contracttype.ContractTypeVersionDO;
import jakarta.validation.Valid;

import java.util.List;

/**
 * CLM 合同类型 Service 接口
 */
public interface ContractTypeService {

    /**
     * 创建合同类型，同时创建版本 1（草稿）
     */
    Long createContractType(@Valid ContractTypeSaveReqVO createReqVO);

    /**
     * 更新合同类型（code 不可改）
     */
    void updateContractType(@Valid ContractTypeSaveReqVO updateReqVO);

    /**
     * 删除合同类型；存在引用合同时抛 CONTRACT_TYPE_IN_USE
     */
    void deleteContractType(Long id);

    ContractTypeDO getContractType(Long id);

    /**
     * 获得合同类型；不存在抛 CONTRACT_TYPE_NOT_EXISTS
     */
    ContractTypeDO getRequiredContractType(Long id);

    ContractTypeRespVO getContractTypeDetail(Long id);

    PageResult<ContractTypeRespVO> getContractTypePage(ContractTypePageReqVO pageReqVO);

    /**
     * 精简列表：仅 status=0 且已发布（含 hasTemplate / description）
     */
    List<ContractTypeSimpleRespVO> getContractTypeSimpleList();

    // ========== 范本 ==========

    /**
     * 上传/替换类型范本文件（.docx/.doc/.pdf），存 ClmDocumentStorage 并回写 templateFileKey / templateFileName
     *
     * @param typeId   合同类型编号
     * @param fileName 原始文件名
     * @param content  文件内容
     */
    void uploadContractTypeTemplate(Long typeId, String fileName, byte[] content);

    /**
     * 下载类型范本文件；未配置范本抛 CONTRACT_TYPE_TEMPLATE_NOT_EXISTS
     *
     * @param typeId 合同类型编号
     * @return 文件名 + 内容
     */
    TypeTemplateDownloadResult downloadContractTypeTemplate(Long typeId);

    // ========== 版本 ==========

    List<ContractTypeVersionDO> getContractTypeVersionList(Long typeId);

    ContractTypeVersionDO getContractTypeVersion(Long id);

    /**
     * 获得版本；不存在抛 CONTRACT_TYPE_VERSION_NOT_EXISTS
     */
    ContractTypeVersionDO getRequiredContractTypeVersion(Long id);

    /**
     * 更新草稿版本；非草稿抛 CONTRACT_TYPE_VERSION_NOT_DRAFT
     */
    void updateContractTypeVersion(@Valid ContractTypeVersionSaveReqVO updateReqVO);

    /**
     * 发布版本：DRAFT→PUBLISHED，type.currentVersionId = id，审计 TYPE_PUBLISH
     */
    void publishContractTypeVersion(Long id);

    /**
     * 新建草稿版本（复制当前发布版本）；已存在草稿抛 CONTRACT_TYPE_DRAFT_EXISTS
     */
    Long createDraftVersion(Long typeId);

}
