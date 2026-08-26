package cn.iocoder.yudao.module.clm.controller.admin.contract;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.clm.controller.admin.contract.vo.AuditEventPageReqVO;
import cn.iocoder.yudao.module.clm.controller.admin.contract.vo.AuditEventRespVO;
import cn.iocoder.yudao.module.clm.dal.dataobject.audit.AuditEventDO;
import cn.iocoder.yudao.module.clm.service.audit.ClmAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - CLM 合同审计事件")
@RestController
@RequestMapping("/clm/contract/audit-event")
@Validated
public class ContractAuditEventController {

    @Resource
    private ClmAuditService clmAuditService;

    @GetMapping("/page")
    @Operation(summary = "获得合同审计事件分页（按发生时间降序）")
    @PreAuthorize("@ss.hasPermission('clm:contract:query')")
    public CommonResult<PageResult<AuditEventRespVO>> getAuditEventPage(@Valid AuditEventPageReqVO pageReqVO) {
        PageResult<AuditEventDO> pageResult = clmAuditService.getAuditEventPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, AuditEventRespVO.class));
    }

}
