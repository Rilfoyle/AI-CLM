package cn.iocoder.yudao.module.clm.controller.admin.contract;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.clm.controller.admin.contract.vo.ContractParticipantRespVO;
import cn.iocoder.yudao.module.clm.controller.admin.contract.vo.ContractParticipantSaveReqVO;
import cn.iocoder.yudao.module.clm.service.contract.ContractParticipantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - CLM 合同参与人")
@RestController
@RequestMapping("/clm/contract/participant")
@Validated
public class ContractParticipantController {

    @Resource
    private ContractParticipantService contractParticipantService;

    @GetMapping("/list")
    @Operation(summary = "获得合同参与人列表")
    @Parameter(name = "contractId", description = "合同编号", required = true)
    @PreAuthorize("@ss.hasPermission('clm:contract:query')")
    public CommonResult<List<ContractParticipantRespVO>> getParticipantList(@RequestParam("contractId") Long contractId) {
        return success(contractParticipantService.getParticipantList(contractId));
    }

    @PutMapping("/save")
    @Operation(summary = "保存合同参与人（差异更新，不含 OWNER）")
    @PreAuthorize("@ss.hasPermission('clm:contract:manage-member')")
    public CommonResult<Boolean> saveParticipants(@Valid @RequestBody ContractParticipantSaveReqVO reqVO) {
        contractParticipantService.saveParticipants(reqVO);
        return success(true);
    }

}
