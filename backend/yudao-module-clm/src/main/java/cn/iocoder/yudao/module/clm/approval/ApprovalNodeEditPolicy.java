package cn.iocoder.yudao.module.clm.approval;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

record ApprovalNodeEditPolicy(boolean enabled, Set<String> editableFields,
                              Set<String> majorFields, String reason) {

    static ApprovalNodeEditPolicy denied(String reason) {
        return new ApprovalNodeEditPolicy(false, Collections.emptySet(), Collections.emptySet(), reason);
    }

    @SuppressWarnings("unchecked")
    static ApprovalNodeEditPolicy parse(String json, String taskDefinitionKey) {
        try {
            Map<String, Object> root = JsonUtils.parseMap(json);
            if (root == null) return denied("未发布审批节点编辑政策");
            Map<String, Object> config = root;
            Object nodesValue = root.get("nodes");
            if (nodesValue instanceof Map<?, ?> nodes && taskDefinitionKey != null
                    && nodes.get(taskDefinitionKey) instanceof Map<?, ?> node) {
                config = (Map<String, Object>) node;
            } else if (root.get("default") instanceof Map<?, ?> defaultNode) {
                config = (Map<String, Object>) defaultNode;
            }
            boolean enabled = Boolean.TRUE.equals(config.get("enabled"));
            return new ApprovalNodeEditPolicy(enabled, strings(config.get("editableFields")),
                    strings(config.get("majorFields")), enabled ? "" : "当前节点政策未开启合同编辑");
        } catch (RuntimeException ex) {
            return denied("审批节点编辑政策格式错误");
        }
    }

    private static Set<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return Collections.emptySet();
        Set<String> result = new LinkedHashSet<>();
        for (Object item : list) if (item != null) result.add(String.valueOf(item));
        return result;
    }

    boolean allows(String field) {
        return editableFields.contains("*") || editableFields.contains(field);
    }

    boolean isMajor(String field) {
        return majorFields.contains("*") || majorFields.contains(field);
    }
}
