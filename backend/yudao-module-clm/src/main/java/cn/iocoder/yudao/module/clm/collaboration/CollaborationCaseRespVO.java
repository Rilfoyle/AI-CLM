package cn.iocoder.yudao.module.clm.collaboration;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CollaborationCaseRespVO {
    private Long id;
    private Long contractId;
    private String contractName;
    private String contractNo;
    /** 当前协同所请求的修订；保留 requestedRevisionId 兼容旧接口。 */
    private Long revisionId;
    private Long requestedRevisionId;
    private Long completedRevisionId;
    private Long currentRevisionId;
    private Boolean conclusionExpired;
    private Long initiatorUserId;
    private String starterUserName;
    private Long legalUserId;
    private String legalUserName;
    private String status;
    private String reason;
    private String conclusion;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime finishedTime;
    private List<String> availableActions;
    private List<CollaborationCommentRespVO> comments;
    private List<CollaborationEventDO> events;
}
