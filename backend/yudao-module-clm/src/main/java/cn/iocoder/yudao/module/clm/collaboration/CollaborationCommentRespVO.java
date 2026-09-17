package cn.iocoder.yudao.module.clm.collaboration;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CollaborationCommentRespVO {

    private Long id;
    private Long userId;
    private String userName;
    private Long revisionId;
    private String content;
    private String eventType;
    private LocalDateTime createTime;

}
