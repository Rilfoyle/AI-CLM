package cn.iocoder.yudao.module.clm.controller.admin.revision;

import cn.iocoder.yudao.module.clm.revision.*;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - CLM 合同修订")
@RestController
@RequestMapping("/clm/contract/revision")
@Validated
public class ContractRevisionController {
    @Resource private ContractRevisionService revisionService;

    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermission('clm:revision:query')")
    public CommonResult<List<ContractRevisionRespVO>> getList(@RequestParam("contractId") Long contractId) {
        return success(revisionService.getList(contractId));
    }

    @GetMapping("/compare")
    @PreAuthorize("@ss.hasPermission('clm:revision:query')")
    public CommonResult<ContractRevisionCompareRespVO> compare(@RequestParam("fromRevisionId") Long fromRevisionId,
                                                               @RequestParam("toRevisionId") Long toRevisionId) {
        return success(revisionService.compare(fromRevisionId, toRevisionId));
    }

    @PutMapping("/save")
    @PreAuthorize("@ss.hasPermission('clm:revision:update')")
    public CommonResult<ContractRevisionSaveRespVO> save(@Valid @RequestBody ContractRevisionSaveReqVO reqVO) {
        return success(revisionService.save(reqVO));
    }
}
