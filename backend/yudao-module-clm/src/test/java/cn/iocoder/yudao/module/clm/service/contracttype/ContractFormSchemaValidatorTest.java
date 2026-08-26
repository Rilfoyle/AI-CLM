package cn.iocoder.yudao.module.clm.service.contracttype;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import org.junit.jupiter.api.Test;

import java.util.*;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.CONTRACT_CUSTOM_FIELD_REQUIRED;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.CONTRACT_CUSTOM_FIELD_UNKNOWN;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ContractFormSchemaValidator} 的纯单元测试（无 DB）
 */
public class ContractFormSchemaValidatorTest extends BaseMockitoUnitTest {

    private final ContractFormSchemaValidator validator = new ContractFormSchemaValidator();

    private static final String RULE_NAME_REQUIRED = "{\"type\":\"input\",\"field\":\"projectName\",\"title\":\"项目名称\",\"$required\":true}";
    private static final String RULE_AMOUNT_VALIDATE_REQUIRED = "{\"type\":\"inputNumber\",\"field\":\"budget\",\"title\":\"预算\",\"validate\":[{\"required\":true,\"message\":\"必填\"}]}";
    private static final String RULE_OPTIONAL = "{\"type\":\"input\",\"field\":\"remark\",\"title\":\"备注\"}";
    private static final String RULE_NESTED = "{\"type\":\"row\",\"children\":["
            + "{\"type\":\"col\",\"children\":[{\"type\":\"input\",\"field\":\"childField\",\"title\":\"子字段\",\"$required\":true}]}"
            + "]}";

    @Test
    public void testValidate_requiredMissing() {
        List<String> fields = Arrays.asList(RULE_NAME_REQUIRED, RULE_OPTIONAL);
        // 缺失
        Map<String, Object> data = new HashMap<>();
        data.put("remark", "x");
        assertServiceException(() -> validator.validate(fields, data), CONTRACT_CUSTOM_FIELD_REQUIRED, "项目名称");
        // 空字符串
        Map<String, Object> data2 = new HashMap<>();
        data2.put("projectName", "");
        assertServiceException(() -> validator.validate(fields, data2), CONTRACT_CUSTOM_FIELD_REQUIRED, "项目名称");
        // 空数组
        Map<String, Object> data3 = new HashMap<>();
        data3.put("projectName", new ArrayList<>());
        assertServiceException(() -> validator.validate(fields, data3), CONTRACT_CUSTOM_FIELD_REQUIRED, "项目名称");
        // validate[] 中 required
        Map<String, Object> data4 = new HashMap<>();
        data4.put("projectName", "ok");
        assertServiceException(() -> validator.validate(Arrays.asList(RULE_NAME_REQUIRED, RULE_AMOUNT_VALIDATE_REQUIRED), data4),
                CONTRACT_CUSTOM_FIELD_REQUIRED, "预算");
    }

    @Test
    public void testValidate_unknownKey() {
        List<String> fields = Arrays.asList(RULE_NAME_REQUIRED, RULE_OPTIONAL);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("projectName", "ok");
        data.put("notInSchema", 1);
        assertServiceException(() -> validator.validate(fields, data), CONTRACT_CUSTOM_FIELD_UNKNOWN, "notInSchema");
    }

    @Test
    public void testValidate_nestedChildren() {
        List<String> fields = Collections.singletonList(RULE_NESTED);
        Map<String, ContractFormSchemaValidator.FieldRule> rules = validator.parseRules(fields);
        assertEquals(1, rules.size());
        assertEquals("子字段", rules.get("childField").getTitle());
        // 嵌套必填缺失
        assertServiceException(() -> validator.validate(fields, new HashMap<>()), CONTRACT_CUSTOM_FIELD_REQUIRED, "子字段");
        // 嵌套字段已填
        Map<String, Object> data = new HashMap<>();
        data.put("childField", "v");
        assertDoesNotThrow(() -> validator.validate(fields, data));
    }

    @Test
    public void testValidate_emptySchemaWithData() {
        Map<String, Object> data = new HashMap<>();
        data.put("a", 1);
        assertServiceException(() -> validator.validate(null, data), CONTRACT_CUSTOM_FIELD_UNKNOWN, "a");
        assertServiceException(() -> validator.validate(new ArrayList<>(), data), CONTRACT_CUSTOM_FIELD_UNKNOWN, "a");
        // 空 schema + 空数据 OK
        assertDoesNotThrow(() -> validator.validate(null, null));
        assertDoesNotThrow(() -> validator.validate(new ArrayList<>(), new HashMap<>()));
    }

    @Test
    public void testValidate_success() {
        List<String> fields = Arrays.asList(RULE_NAME_REQUIRED, RULE_AMOUNT_VALIDATE_REQUIRED, RULE_OPTIONAL, RULE_NESTED);
        Map<String, Object> data = new HashMap<>();
        data.put("projectName", "CLM POC");
        data.put("budget", 100);
        data.put("childField", Arrays.asList("a", "b"));
        assertDoesNotThrow(() -> validator.validate(fields, data));
        // 非必填可缺省
        Map<String, Object> data2 = new HashMap<>();
        data2.put("projectName", "CLM POC");
        data2.put("budget", 0);
        data2.put("childField", "x");
        assertDoesNotThrow(() -> validator.validate(fields, data2));
    }

}
