package cn.iocoder.yudao.module.clm.service.contract;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.clm.controller.admin.contract.vo.ContractCopyReqVO;
import cn.iocoder.yudao.module.clm.controller.admin.contract.vo.ContractPageReqVO;
import cn.iocoder.yudao.module.clm.controller.admin.contract.vo.ContractRespVO;
import cn.iocoder.yudao.module.clm.controller.admin.contract.vo.ContractSaveReqVO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractPartyDO;
import jakarta.validation.Valid;

import java.time.LocalDate;
import java.util.List;

/**
 * CLM 合同 Service 接口
 */
public interface ContractService {

    /**
     * 创建合同（草稿）
     */
    Long createContract(@Valid ContractSaveReqVO createReqVO);

    /**
     * 更新合同；要求可编辑
     */
    void updateContract(@Valid ContractSaveReqVO updateReqVO);

    /**
     * 删除合同；要求可管理且未提交/已取消且草稿
     */
    void deleteContract(Long id);

    /**
     * 复制/续签合同：源合同可见即可；按源合同类型的当前发布版重建，
     * customData 只按新版本 schema 过滤未知字段（不校验必填），签约方快照重建，
     * 源合同有当前正文版本时复制其 blob 为新合同 MAIN v1
     *
     * @return 新合同编号
     */
    Long copyContract(@Valid ContractCopyReqVO reqVO);

    /**
     * 归档定稿：上传盖章扫描件生成 MAIN 冻结版本（MANUAL_FINAL），
     * 更新传入的日期字段并推进 lifecycle=EFFECTIVE；
     * 要求编辑主体条件且 approvalStatus=APPROVED、lifecycle=APPROVED，否则 CONTRACT_ARCHIVE_NOT_ALLOWED
     *
     * @return 新的正文版本编号
     */
    Long archiveContract(Long contractId, LocalDate signDate, LocalDate effectiveDate, LocalDate expiryDate,
                         String fileName, String contentType, byte[] content);

    ContractDO getContract(Long id);

    /**
     * 获得合同；不存在抛 CONTRACT_NOT_EXISTS
     */
    ContractDO getRequiredContract(Long id);

    /**
     * 获得合同详情（含签约方、当前正文版本、当前绑定、权限）；要求可见
     */
    ContractRespVO getContractDetail(Long id);

    /**
     * 分页（Mapper 层按对象权限过滤）
     */
    PageResult<ContractRespVO> getContractPage(ContractPageReqVO pageReqVO);

    /**
     * 构建合同详情 VO（不做权限断言，权限字段按 userId 计算）
     */
    ContractRespVO buildContractRespVO(ContractDO contract, Long userId);

    List<ContractPartyDO> getContractPartyList(Long contractId);

    /**
     * 校验合同签约方、标题等提交必填项
     */
    void validateContractForSubmit(ContractDO contract, List<ContractPartyDO> parties);

}
