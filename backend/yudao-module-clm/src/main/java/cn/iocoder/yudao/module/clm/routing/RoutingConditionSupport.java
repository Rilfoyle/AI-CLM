package cn.iocoder.yudao.module.clm.routing;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.clm.routing.RoutingGovernanceErrors.CONDITION_INVALID;

final class RoutingConditionSupport {
    private static final Set<String> KEYS = Set.of("currency", "ownerDeptId", "minAmount", "maxAmount");

    private RoutingConditionSupport() {
    }

    static Map<String, Object> normalize(Map<String, Object> input) {
        Map<String, Object> source = input == null ? Map.of() : input;
        for (String key : source.keySet()) {
            if (!KEYS.contains(key)) throw exception(CONDITION_INVALID, "不支持条件 " + key);
        }
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        if (source.get("currency") != null) {
            String currency = String.valueOf(source.get("currency")).trim().toUpperCase();
            if (!currency.matches("[A-Z]{3}")) throw exception(CONDITION_INVALID, "币种必须为 3 位大写代码");
            normalized.put("currency", currency);
        }
        if (source.get("ownerDeptId") != null) {
            Long deptId = toLong(source.get("ownerDeptId"), "经办部门");
            if (deptId <= 0) throw exception(CONDITION_INVALID, "经办部门必须为正整数");
            normalized.put("ownerDeptId", deptId);
        }
        BigDecimal min = toAmount(source.get("minAmount"), "最小金额");
        BigDecimal max = toAmount(source.get("maxAmount"), "最大金额");
        if (min != null) normalized.put("minAmount", min.stripTrailingZeros());
        if (max != null) normalized.put("maxAmount", max.stripTrailingZeros());
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw exception(CONDITION_INVALID, "最小金额不能大于最大金额");
        }
        return normalized;
    }

    static Map<String, Object> parse(String json) {
        if (StrUtil.isBlank(json)) return new LinkedHashMap<>();
        try {
            Map<String, Object> parsed = JsonUtils.parseMap(json);
            if (parsed == null) throw exception(CONDITION_INVALID, "条件 JSON 不能为空");
            return normalize(parsed);
        } catch (cn.iocoder.yudao.framework.common.exception.ServiceException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw exception(CONDITION_INVALID, "条件 JSON 无法解析");
        }
    }

    static String toJson(Map<String, Object> input) {
        return JsonUtils.toJsonString(normalize(input));
    }

    static boolean matches(String json, ContractDO contract) {
        Map<String, Object> condition = parse(json);
        if (condition.get("currency") != null
                && !condition.get("currency").equals(StrUtil.nullToEmpty(contract.getCurrency()).toUpperCase())) return false;
        if (condition.get("ownerDeptId") != null
                && !condition.get("ownerDeptId").equals(contract.getOwnerDeptId())) return false;
        BigDecimal amount = contract.getAmount() == null ? BigDecimal.ZERO : contract.getAmount();
        if (condition.get("minAmount") != null
                && amount.compareTo((BigDecimal) condition.get("minAmount")) < 0) return false;
        return condition.get("maxAmount") == null
                || amount.compareTo((BigDecimal) condition.get("maxAmount")) <= 0;
    }

    private static Long toLong(Object value, String label) {
        try {
            if (value instanceof Number number) return number.longValue();
            return Long.valueOf(String.valueOf(value));
        } catch (RuntimeException ex) {
            throw exception(CONDITION_INVALID, label + "必须为整数");
        }
    }

    private static BigDecimal toAmount(Object value, String label) {
        if (value == null || StrUtil.isBlank(String.valueOf(value))) return null;
        try {
            BigDecimal amount = new BigDecimal(String.valueOf(value));
            if (amount.signum() < 0) throw exception(CONDITION_INVALID, label + "不能为负数");
            return amount;
        } catch (cn.iocoder.yudao.framework.common.exception.ServiceException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw exception(CONDITION_INVALID, label + "必须为数字");
        }
    }
}
