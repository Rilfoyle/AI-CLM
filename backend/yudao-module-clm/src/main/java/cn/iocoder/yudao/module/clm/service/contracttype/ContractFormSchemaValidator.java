package cn.iocoder.yudao.module.clm.service.contracttype;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.CONTRACT_CUSTOM_FIELD_REQUIRED;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.CONTRACT_CUSTOM_FIELD_UNKNOWN;

/**
 * 合同扩展字段（customData）按合同类型版本的 FormCreate 字段规则（formFields）校验
 *
 * 纯逻辑类，不依赖数据库
 */
@Component
public class ContractFormSchemaValidator {

    /**
     * 校验扩展字段
     *
     * @param formFields 类型版本的字段规则数组，每项为 FormCreate rule 的 JSON 串
     * @param customData 合同扩展字段
     */
    public void validate(List<String> formFields, Map<String, Object> customData) {
        // 1. 解析 schema：field -> 描述（含 required / title）
        Map<String, FieldRule> rules = parseRules(formFields);
        // 2. schema 为空：customData 必须为空
        if (rules.isEmpty()) {
            if (MapUtil.isNotEmpty(customData)) {
                throw exception(CONTRACT_CUSTOM_FIELD_UNKNOWN, CollUtil.getFirst(customData.keySet()));
            }
            return;
        }
        // 3. 不允许 schema 外的 key
        if (customData != null) {
            for (String key : customData.keySet()) {
                if (!rules.containsKey(key)) {
                    throw exception(CONTRACT_CUSTOM_FIELD_UNKNOWN, key);
                }
            }
        }
        // 4. 必填字段校验
        for (FieldRule rule : rules.values()) {
            if (!rule.required) {
                continue;
            }
            Object value = customData == null ? null : customData.get(rule.field);
            if (isEmptyValue(value)) {
                throw exception(CONTRACT_CUSTOM_FIELD_REQUIRED, StrUtil.blankToDefault(rule.title, rule.field));
            }
        }
    }

    /**
     * 解析规则数组，递归收集所有 field（含 children[]）
     */
    public Map<String, FieldRule> parseRules(List<String> formFields) {
        Map<String, FieldRule> rules = new LinkedHashMap<>();
        if (CollUtil.isEmpty(formFields)) {
            return rules;
        }
        for (String ruleJson : formFields) {
            if (StrUtil.isBlank(ruleJson)) {
                continue;
            }
            JsonNode node = JsonUtils.parseTree(ruleJson);
            collectRule(node, rules);
        }
        return rules;
    }

    private void collectRule(JsonNode node, Map<String, FieldRule> rules) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectRule(child, rules);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        JsonNode fieldNode = node.get("field");
        if (fieldNode != null && fieldNode.isTextual() && StrUtil.isNotBlank(fieldNode.asText())) {
            String field = fieldNode.asText();
            JsonNode titleNode = node.get("title");
            String title = titleNode != null && titleNode.isTextual() ? titleNode.asText() : null;
            boolean required = isRequired(node);
            FieldRule existing = rules.get(field);
            if (existing == null) {
                rules.put(field, new FieldRule(field, title, required));
            } else if (required) {
                existing.required = true;
            }
        }
        JsonNode children = node.get("children");
        if (children != null) {
            collectRule(children, rules);
        }
    }

    /**
     * required = $required == true 或 validate[] 中任一 required == true
     */
    private boolean isRequired(JsonNode node) {
        JsonNode requiredNode = node.get("$required");
        if (requiredNode != null && isTrue(requiredNode)) {
            return true;
        }
        JsonNode validate = node.get("validate");
        if (validate != null && validate.isArray()) {
            for (JsonNode item : validate) {
                JsonNode r = item.get("required");
                if (r != null && isTrue(r)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isTrue(JsonNode node) {
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isTextual()) {
            return "true".equalsIgnoreCase(node.asText());
        }
        return false;
    }

    private boolean isEmptyValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof CharSequence) {
            return StrUtil.isEmpty((CharSequence) value);
        }
        if (value instanceof Collection) {
            return ((Collection<?>) value).isEmpty();
        }
        if (value instanceof Object[]) {
            return ((Object[]) value).length == 0;
        }
        return false;
    }

    /**
     * 字段规则摘要
     */
    public static class FieldRule {

        private final String field;
        private final String title;
        private boolean required;

        public FieldRule(String field, String title, boolean required) {
            this.field = field;
            this.title = title;
            this.required = required;
        }

        public String getField() {
            return field;
        }

        public String getTitle() {
            return title;
        }

        public boolean isRequired() {
            return required;
        }

    }

}
