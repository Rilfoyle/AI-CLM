package cn.iocoder.yudao.module.clm.collaboration;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@TableName("clm_collaboration_event")
@KeySequence("clm_collaboration_event_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CollaborationEventDO extends TenantBaseDO {

    public static final String TYPE_START = "START";
    public static final String TYPE_COMMENT = "COMMENT";
    public static final String TYPE_REQUEST_CHANGE = "REQUEST_CHANGE";
    public static final String TYPE_COMPLETE = "COMPLETE";
    public static final String TYPE_CANCEL = "CANCEL";

    @TableId
    private Long id;
    private Long caseId;
    private Long contractId;
    private Long revisionId;
    private String eventType;
    private Long actorUserId;
    private String content;

}
