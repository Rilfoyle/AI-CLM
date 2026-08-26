package cn.iocoder.yudao.module.clm.controller.admin.onlineedit;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.clm.controller.admin.onlineedit.vo.OnlineEditConfigRespVO;
import cn.iocoder.yudao.module.clm.service.onlineedit.OnlineEditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - CLM 在线编辑（ONLYOFFICE）")
@RestController
@RequestMapping("/clm/online-edit")
@Validated
public class OnlineEditController {

    @Resource
    private OnlineEditService onlineEditService;

    @GetMapping("/config")
    @Operation(summary = "获得 ONLYOFFICE 编辑器配置（edit 需 clm:contract:update，view 需 clm:contract:query；服务层按 mode 校验）")
    @Parameter(name = "versionId", description = "文档版本编号", required = true)
    @Parameter(name = "mode", description = "edit / view", example = "edit")
    @PreAuthorize("@ss.hasAnyPermissions('clm:contract:update','clm:contract:query')")
    public CommonResult<OnlineEditConfigRespVO> getConfig(@RequestParam("versionId") Long versionId,
                                                          @RequestParam(value = "mode", defaultValue = "view") String mode) {
        return success(onlineEditService.buildConfig(versionId, mode, SecurityFrameworkUtils.getLoginUserId()));
    }

}
