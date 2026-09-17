package cn.iocoder.yudao.module.clm.controller.admin.commitment;

import cn.iocoder.yudao.module.clm.commitment.*;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/clm/contract/commitment")
@Validated
public class CommitmentController {
    @Resource private CommitmentService commitmentService;

    @GetMapping
    @PreAuthorize("@ss.hasPermission('clm:commitment:query')")
    public CommonResult<List<CommitmentRespVO>> getList(@RequestParam("contractId") Long contractId) {
        return success(commitmentService.getList(contractId));
    }
    @PostMapping
    @PreAuthorize("@ss.hasPermission('clm:commitment:create')")
    public CommonResult<CommitmentMutationRespVO> create(@Valid @RequestBody CommitmentSaveReqVO reqVO) {
        return success(commitmentService.create(reqVO));
    }
    @PutMapping
    @PreAuthorize("@ss.hasPermission('clm:commitment:update')")
    public CommonResult<CommitmentMutationRespVO> update(@Valid @RequestBody CommitmentSaveReqVO reqVO) {
        return success(commitmentService.update(reqVO));
    }
    @DeleteMapping
    @PreAuthorize("@ss.hasPermission('clm:commitment:delete')")
    public CommonResult<Long> delete(@RequestParam("id") Long id,
                                     @RequestParam(value = "baseRevisionId", required = false) Long baseRevisionId) {
        return success(commitmentService.delete(id, baseRevisionId));
    }
    @PutMapping("/confirm-none")
    @PreAuthorize("@ss.hasPermission('clm:commitment:confirm')")
    public CommonResult<Long> confirmNone(@RequestParam("contractId") Long contractId,
                                          @RequestParam(value = "baseRevisionId", required = false) Long baseRevisionId) {
        return success(commitmentService.confirmNone(contractId, baseRevisionId));
    }
}
