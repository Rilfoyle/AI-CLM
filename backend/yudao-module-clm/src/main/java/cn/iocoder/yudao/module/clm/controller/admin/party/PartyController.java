package cn.iocoder.yudao.module.clm.controller.admin.party;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.clm.controller.admin.party.vo.PartyPageReqVO;
import cn.iocoder.yudao.module.clm.controller.admin.party.vo.PartyRespVO;
import cn.iocoder.yudao.module.clm.controller.admin.party.vo.PartySaveReqVO;
import cn.iocoder.yudao.module.clm.controller.admin.party.vo.PartySimpleRespVO;
import cn.iocoder.yudao.module.clm.dal.dataobject.party.PartyDO;
import cn.iocoder.yudao.module.clm.service.party.PartyService;
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

@Tag(name = "管理后台 - CLM 签约方")
@RestController
@RequestMapping("/clm/party")
@Validated
public class PartyController {

    @Resource
    private PartyService partyService;

    @PostMapping("/create")
    @Operation(summary = "创建签约方")
    @PreAuthorize("@ss.hasPermission('clm:party:create')")
    public CommonResult<Long> createParty(@Valid @RequestBody PartySaveReqVO createReqVO) {
        return success(partyService.createParty(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新签约方")
    @PreAuthorize("@ss.hasPermission('clm:party:update')")
    public CommonResult<Boolean> updateParty(@Valid @RequestBody PartySaveReqVO updateReqVO) {
        partyService.updateParty(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除签约方")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('clm:party:delete')")
    public CommonResult<Boolean> deleteParty(@RequestParam("id") Long id) {
        partyService.deleteParty(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得签约方")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('clm:party:query')")
    public CommonResult<PartyRespVO> getParty(@RequestParam("id") Long id) {
        PartyDO party = partyService.getRequiredParty(id);
        return success(BeanUtils.toBean(party, PartyRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得签约方分页")
    @PreAuthorize("@ss.hasPermission('clm:party:query')")
    public CommonResult<PageResult<PartyRespVO>> getPartyPage(@Valid PartyPageReqVO pageReqVO) {
        PageResult<PartyDO> pageResult = partyService.getPartyPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, PartyRespVO.class));
    }

    @GetMapping("/simple-list")
    @Operation(summary = "获得签约方精简列表（仅开启）")
    @Parameter(name = "internalFlag", description = "是否我方主体，可空")
    public CommonResult<List<PartySimpleRespVO>> getPartySimpleList(
            @RequestParam(value = "internalFlag", required = false) Boolean internalFlag) {
        List<PartyDO> list = partyService.getPartySimpleList(internalFlag);
        return success(BeanUtils.toBean(list, PartySimpleRespVO.class));
    }

}
