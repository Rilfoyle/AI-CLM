package cn.iocoder.yudao.module.clm.collaboration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CollaborationCancelReqVO {

    @NotNull
    private Long caseId;

    private Long revisionId;

    @NotBlank
    @Size(max = 4000)
    private String reason;

    public CollaborationActionReqVO toActionReqVO() {
        CollaborationActionReqVO reqVO = new CollaborationActionReqVO();
        reqVO.setCaseId(caseId);
        reqVO.setRevisionId(revisionId);
        reqVO.setContent(reason);
        return reqVO;
    }

}
