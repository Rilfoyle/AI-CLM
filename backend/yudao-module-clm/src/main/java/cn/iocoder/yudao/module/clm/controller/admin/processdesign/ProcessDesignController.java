package cn.iocoder.yudao.module.clm.controller.admin.processdesign;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.clm.controller.admin.processdesign.vo.ProcessDesignDetailRespVO;
import cn.iocoder.yudao.module.clm.controller.admin.processdesign.vo.ProcessDesignSaveReqVO;
import cn.iocoder.yudao.module.clm.processdesign.ProcessDesignListItemRespVO;
import cn.iocoder.yudao.module.clm.processdesign.ProcessDesignService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - CLM 流程定义")
@RestController
@RequestMapping("/clm/governance/process")
@Validated
public class ProcessDesignController {

    @Resource
    private ProcessDesignService service;

    @GetMapping("/list")
    @Operation(summary = "获得 CLM 合同流程定义列表")
    @PreAuthorize("@ss.hasPermission('clm:governance:process:query')")
    public CommonResult<List<ProcessDesignListItemRespVO>> list(
            @RequestParam(value = "name", required = false) String name) {
        return success(service.list(name));
    }

    @GetMapping("/get")
    @Operation(summary = "获得可编辑的 CLM 合同流程定义")
    @Parameter(name = "id", description = "Flowable 模型编号", required = true)
    @PreAuthorize("@ss.hasPermission('clm:governance:process:query')")
    public CommonResult<ProcessDesignDetailRespVO> get(@RequestParam("id") String id) {
        return success(service.get(id));
    }

    @PostMapping("/create")
    @Operation(summary = "创建 CLM 合同流程定义")
    @PreAuthorize("@ss.hasPermission('clm:governance:process:update')")
    public CommonResult<String> create(@Valid @RequestBody ProcessDesignSaveReqVO reqVO) {
        return success(service.create(getLoginUserId(), reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "保存 CLM 合同流程定义")
    @PreAuthorize("@ss.hasPermission('clm:governance:process:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody ProcessDesignSaveReqVO reqVO) {
        service.update(getLoginUserId(), reqVO);
        return success(true);
    }

    @PostMapping("/deploy")
    @Operation(summary = "发布 CLM 合同流程定义")
    @Parameter(name = "id", description = "Flowable 模型编号", required = true)
    @PreAuthorize("@ss.hasPermission('clm:governance:process:publish')")
    public CommonResult<Boolean> deploy(@RequestParam("id") String id) {
        service.deploy(getLoginUserId(), id);
        return success(true);
    }

}
