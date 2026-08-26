# yudao-module-clm 后端模块新建约定

Backend root: `D:\dev\clm\backend`. Paths below are relative to that root. **Actual `revision` = `2026.06-SNAPSHOT`** (root `pom.xml:39`), not 2026.07.

---

## 1. Module pom.xml template

**No separate `*-api` module.** API packages live *inside* the module: `yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/{user,dept,permission,dict,...}` (+ `api/*/dto/`). There is no `yudao-module-system-api` artifact. Only two exceptions in the repo: `yudao-module-mall/yudao-module-trade-api` and the `yudao-module-iot` / `yudao-module-mall` aggregator poms (packaging `pom` with sub-modules). A flat single-module layout (like infra/system/bpm/mes) is the norm.

`yudao-module-infra/pom.xml` (full, verbatim):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <parent>
        <groupId>cn.iocoder.boot</groupId>
        <artifactId>yudao</artifactId>
        <version>${revision}</version>
    </parent>
    <modelVersion>4.0.0</modelVersion>
    <artifactId>yudao-module-infra</artifactId>
    <packaging>jar</packaging>

    <name>${project.artifactId}</name>
    <description>...</description>

    <dependencies>
        <!-- 业务组件 -->
        <dependency><groupId>cn.iocoder.boot</groupId><artifactId>yudao-spring-boot-starter-biz-tenant</artifactId></dependency>
        <!-- Web 相关 -->
        <dependency><groupId>cn.iocoder.boot</groupId><artifactId>yudao-spring-boot-starter-security</artifactId></dependency>
        <dependency><groupId>cn.iocoder.boot</groupId><artifactId>yudao-spring-boot-starter-websocket</artifactId></dependency>
        <!-- DB 相关 -->
        <dependency><groupId>cn.iocoder.boot</groupId><artifactId>yudao-spring-boot-starter-mybatis</artifactId></dependency>
        <dependency><groupId>com.baomidou</groupId><artifactId>mybatis-plus-generator</artifactId></dependency>
        <dependency><groupId>cn.iocoder.boot</groupId><artifactId>yudao-spring-boot-starter-redis</artifactId></dependency>
        <!-- Job 定时任务相关 -->
        <dependency><groupId>cn.iocoder.boot</groupId><artifactId>yudao-spring-boot-starter-job</artifactId></dependency>
        <!-- 消息队列相关 -->
        <dependency><groupId>cn.iocoder.boot</groupId><artifactId>yudao-spring-boot-starter-mq</artifactId></dependency>
        <!-- Test 测试相关 -->
        <dependency><groupId>cn.iocoder.boot</groupId><artifactId>yudao-spring-boot-starter-test</artifactId><scope>test</scope></dependency>
        <!-- 工具类相关 -->
        <dependency><groupId>cn.iocoder.boot</groupId><artifactId>yudao-spring-boot-starter-excel</artifactId></dependency>
        <dependency><groupId>org.apache.velocity</groupId><artifactId>velocity-engine-core</artifactId></dependency>
        <!-- 监控相关 -->
        <dependency><groupId>cn.iocoder.boot</groupId><artifactId>yudao-spring-boot-starter-monitor</artifactId></dependency>
        <dependency><groupId>de.codecentric</groupId><artifactId>spring-boot-admin-starter-server</artifactId><optional>true</optional></dependency>
        <!-- 三方云服务相关 -->
        <dependency><groupId>commons-net</groupId><artifactId>commons-net</artifactId></dependency>
        <dependency><groupId>com.github.mwiede</groupId><artifactId>jsch</artifactId></dependency>
        <dependency><groupId>software.amazon.awssdk</groupId><artifactId>s3</artifactId></dependency>
        <dependency><groupId>org.apache.tika</groupId><artifactId>tika-core</artifactId></dependency>
    </dependencies>
</project>
```

Notes: no `<groupId>`/`<version>` on the module itself (inherited); no `<version>` on `cn.iocoder.boot` starters (managed by `yudao-dependencies`); `yudao-spring-boot-starter-web` is **not** declared by infra (it comes transitively from `-security`), but bpm/mes declare it explicitly.

**Recommended `yudao-module-clm/pom.xml` dependency set** — copy `yudao-module-mes/pom.xml` (the newest, minimal business module), which is exactly:
`yudao-module-system` (with `<version>${revision}</version>`), `yudao-spring-boot-starter-web`, `-security`, `-mybatis`, `-redis`, `-excel`, `-test`. Add `yudao-spring-boot-starter-biz-tenant` (infra/bpm have it) and `yudao-spring-boot-starter-biz-data-permission` (bpm/crm have it) if you need multi-tenant / data-permission.

`yudao-module-bpm/pom.xml:24-28` shows the intra-repo dependency form that requires an explicit version:
```xml
<dependency>
    <groupId>cn.iocoder.boot</groupId>
    <artifactId>yudao-module-system</artifactId>
    <version>${revision}</version>
</dependency>
```
`yudao-module-bpm/pom.xml:58-61` declares `yudao-spring-boot-starter-test` **without** `<scope>test</scope>`; infra/mes use `<scope>test</scope>`. Prefer `test` scope.

---

## 2. Wiring the module

1. **`pom.xml` (root), `<modules>`** — add `<module>yudao-module-clm</module>` among lines 16-31. Most non-core modules are commented out by default; add yours uncommented.
2. **`yudao-server/pom.xml`** — add:
   ```xml
   <dependency>
       <groupId>cn.iocoder.boot</groupId>
       <artifactId>yudao-module-clm</artifactId>
       <version>${revision}</version>
   </dependency>
   ```
3. **Component scan — nothing to change.** `yudao-server/src/main/java/cn/iocoder/yudao/server/YudaoServerApplication.java:16`:
   ```java
   @SpringBootApplication(scanBasePackages = {"${yudao.info.base-package}.server", "${yudao.info.base-package}.module"})
   ```
   `yudao.info.base-package: cn.iocoder.yudao` (`application.yaml:263`). So `cn.iocoder.yudao.module.clm` is scanned automatically.
4. **MyBatis aliases — nothing to change.** `yudao-server/src/main/resources/application.yaml:79`:
   `type-aliases-package: ${yudao.info.base-package}.module.*.dal.dataobject` (wildcard covers `clm`). Same line exists in each module's `src/test/resources/application-unit-test.yaml:33`.
5. **Swagger group — module-owned, not in yaml.** There is no `springdoc.group-configs` in application.yaml. Each module registers its own group bean. Create `clm/framework/web/config/ClmWebConfiguration.java` mirroring `yudao-module-infra/.../framework/web/config/InfraWebConfiguration.java`:
   ```java
   @Configuration(proxyBeanMethods = false)
   public class ClmWebConfiguration {
       @Bean
       public GroupedOpenApi clmGroupedOpenApi() {
           return YudaoSwaggerAutoConfiguration.buildGroupedOpenApi("clm");
       }
   }
   ```
   `buildGroupedOpenApi(group)` → `pathsToMatch("/admin-api/" + group + "/**", "/app-api/" + group + "/**")` (`yudao-framework/yudao-spring-boot-starter-web/src/main/java/cn/iocoder/yudao/framework/swagger/config/YudaoSwaggerAutoConfiguration.java:121-134`).
6. **`yudao.security.permit-all-urls`** — property exists: `@ConfigurationProperties(prefix = "yudao.security")`, field `private List<String> permitAllUrls = Collections.emptyList();` (`yudao-framework/yudao-spring-boot-starter-security/src/main/java/cn/iocoder/yudao/framework/security/config/SecurityProperties.java:12,45`). Note `application.yaml:273` writes it as `permit-all_urls` (with underscore) — relaxed binding still maps it. Preferred house style for per-module public URLs is a `SecurityConfiguration` bean (see §3).
7. **`yudao.tenant.ignore-tables`** (`application.yaml:318`) is currently empty. Add CLM table names there only if a CLM DO is non-tenant while extending `TenantBaseDO`; otherwise just extend `BaseDO`, or annotate the DO class with `@TenantIgnore`.
8. **`spring.messages`** — not present anywhere in `application.yaml`; no i18n message bundle convention. `mybatis` (non-plus) keys: not present; only `mybatis-plus` / `mybatis-plus-join`.
9. **Local profile**: `yudao-server/src/main/resources/application-clm-local.yaml` already exists (DB/Redis overrides from env vars, activated with `--spring.profiles.active=local,clm-local`).

---

## 3. Package / file layout inside a module

```
yudao-module-clm/
├── pom.xml
└── src
    ├── main/java/cn/iocoder/yudao/module/clm/
    │   ├── api/<res>/            (XxxApi.java + XxxApiImpl.java, api/<res>/dto/)   ← only if other modules call you
    │   ├── controller/admin/<res>/  XxxController.java + vo/{XxxPageReqVO,XxxRespVO,XxxSaveReqVO}.java
    │   ├── controller/app/<res>/    (optional, /app-api)
    │   ├── convert/<res>/        (MapStruct, legacy — new code uses BeanUtils.toBean instead)
    │   ├── dal/dataobject/<res>/ XxxDO.java
    │   ├── dal/mysql/<res>/      XxxMapper.java  (extends BaseMapperX<XxxDO>)
    │   ├── dal/redis/            (optional)
    │   ├── enums/                ErrorCodeConstants.java, DictTypeConstants.java, <res>/XxxEnum.java
    │   ├── framework/            package-info.java + web/config/ClmWebConfiguration.java
    │   │                                          + security/config/SecurityConfiguration.java (optional)
    │   ├── job/  mq/  service/<res>/ (XxxService.java + XxxServiceImpl.java)
    │   └── ...
    ├── main/resources/           (mapper XML only if needed; none in infra/system)
    └── test/java/... + test/resources/{application-unit-test.yaml, logback.xml, sql/create_tables.sql, sql/clean.sql}
```

`package-info.java` is a one-line doc file, e.g. `yudao-module-infra/.../framework/web/package-info.java`:
```java
/**
 * infra 模块的 web 配置
 */
package cn.iocoder.yudao.module.infra.framework.web;
```

Optional per-module security config (`yudao-module-infra/.../framework/security/config/SecurityConfiguration.java`), note the `value=` bean name to avoid collision across modules:
```java
@Configuration(proxyBeanMethods = false, value = "infraSecurityConfiguration")
public class SecurityConfiguration {
    @Bean("infraAuthorizeRequestsCustomizer")
    public AuthorizeRequestsCustomizer authorizeRequestsCustomizer() {
        return new AuthorizeRequestsCustomizer() {
            @Override
            public void customize(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry) {
                registry.requestMatchers(buildAdminApi("/infra/file/*/get/**")).permitAll();
            }
        };
    }
}
```

**There is no `enums/ApiConstants.java` anywhere in this repo** (no file by that name) — that class exists only in the Cloud/微服务 fork. Do not create one.

### Error-code numbering scheme
`cn.iocoder.yudao.framework.common.exception.ErrorCode(Integer code, String msg)`. Codes are `1-XXX-YYY-ZZZ`: `1` = business error, `XXX` = module segment, `YYY` = resource group inside module, `ZZZ` = sequence. Written with `_` separators.

`yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/enums/ErrorCodeConstants.java:1-20`:
```java
package cn.iocoder.yudao.module.infra.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * Infra 错误码枚举类
 *
 * infra 系统，使用 1-001-000-000 段
 */
public interface ErrorCodeConstants {

    // ========== 参数配置 1-001-000-000 ==========
    ErrorCode CONFIG_NOT_EXISTS = new ErrorCode(1_001_000_001, "参数配置不存在");
    ErrorCode CONFIG_KEY_DUPLICATE = new ErrorCode(1_001_000_002, "参数配置 key 重复");

    // ========== 定时任务 1-001-001-000 ==========
    ErrorCode JOB_NOT_EXISTS = new ErrorCode(1_001_001_000, "定时任务不存在");
```

Segments in use (from each module's `enums/ErrorCodeConstants.java` header comment):

| Segment | Module |
|---|---|
| 1-001 | infra |
| 1-002 | system |
| 1-003 | report |
| 1-004 | member |
| 1-006 | mp |
| 1-007 | pay |
| 1-008 | product (mall) |
| 1-009 | bpm |
| 1-011 | trade (mall) |
| 1-013 | promotion (mall) |
| 1-020 | crm |
| 1-030 | erp |
| 1-040 | ai **and** im **and** mes (三者冲突，已重复占用) |
| 1-050 | iot-biz |
| 1-051 | iot-gateway |
| 1-060 | wms |

**Free segments**: 1-005, 1-010, 1-012, 1-014…1-019, 1-021…1-029, 1-031…1-039, 1-041…1-049, 1-052…1-059, 1-061+. Recommend **`1-070-000-000` for clm** (clearly unused, avoids the 1-040 collision). There is no root README table of these ranges — the only source is the per-module header comments.

---

## 4. Exemplary code (copy verbatim, rename)

### DO — `yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/dal/dataobject/demo/demo03/Demo03StudentDO.java`
```java
@TableName("yudao_demo03_student")
@KeySequence("yudao_demo03_student_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Demo03StudentDO extends BaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 名字
     */
    private String name;
    ...
}
```
Multi-tenant + JSON column variant — `yudao-module-system/.../dal/dataobject/permission/RoleDO.java:22-31` (note `autoResultMap = true` is **required** when any `@TableField(typeHandler=...)` is used):
```java
@TableName(value = "system_role", autoResultMap = true)
@KeySequence("system_role_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class RoleDO extends TenantBaseDO {
    @TableId
    private Long id;
    ...
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Set<Long> dataScopeDeptIds;
}
```
Base classes: `BaseDO` (`yudao-framework/yudao-spring-boot-starter-mybatis/.../core/dataobject/BaseDO.java`) supplies `createTime`, `updateTime`, `creator`, `updater` (all `@TableField(fill=...)`, creator/updater are `String` with `jdbcType = JdbcType.VARCHAR`) and `@TableLogic private Boolean deleted`. `TenantBaseDO` (`yudao-spring-boot-starter-biz-tenant/.../core/db/TenantBaseDO.java`) adds `private Long tenantId`.

Available type handlers (`yudao-framework/yudao-spring-boot-starter-mybatis/src/main/java/cn/iocoder/yudao/framework/mybatis/core/type/`): `EncryptTypeHandler`, `IntegerListTypeHandler`, `LongListTypeHandler`, `LongSetTypeHandler`, `StringListTypeHandler`, plus MyBatis-Plus's `com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler`. **`JsonLongSetTypeHandler` does not exist in this repo** — use `LongSetTypeHandler` or `JacksonTypeHandler`.

### Mapper — `yudao-module-infra/.../dal/mysql/demo/demo03/normal/Demo03StudentNormalMapper.java`
```java
@Mapper
public interface Demo03StudentNormalMapper extends BaseMapperX<Demo03StudentDO> {

    default PageResult<Demo03StudentDO> selectPage(Demo03StudentNormalPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<Demo03StudentDO>()
                .likeIfPresent(Demo03StudentDO::getName, reqVO.getName())
                .eqIfPresent(Demo03StudentDO::getSex, reqVO.getSex())
                .betweenIfPresent(Demo03StudentDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(Demo03StudentDO::getId));
    }
}
```
`BaseMapperX<T> extends MPJBaseMapper<T>`, key defaults: `selectPage(PageParam, Wrapper<T>)`, `selectPage(SortablePageParam, Wrapper<T>)`, `selectJoinPage(...)`, `selectOne(SFunction, Object)` (up to 3 field/value pairs), `selectOneForUpdate(...)`, `selectFirstOne/selectLastOne`, plus MP's `insert/updateById/deleteById/deleteByIds/selectById/selectByIds/selectList`.
`LambdaQueryWrapperX<T>` methods: `likeIfPresent`, `likeRightIfPresent`, `inIfPresent(Collection)` / `inIfPresent(Object...)`, `eqIfPresent`, `neIfPresent`, `gtIfPresent`, `geIfPresent`, `ltIfPresent`, `leIfPresent`, `betweenIfPresent(col, val1, val2)` / `betweenIfPresent(col, Object[])`, plus covariant `eq/orderByDesc/last/in`.

### ServiceImpl — `yudao-module-infra/.../service/demo/demo01/Demo01ContactServiceImpl.java`
```java
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.DEMO01_CONTACT_NOT_EXISTS;

@Service
@Validated
public class Demo01ContactServiceImpl implements Demo01ContactService {

    @Resource
    private Demo01ContactMapper demo01ContactMapper;

    @Override
    public Long createDemo01Contact(Demo01ContactSaveReqVO createReqVO) {
        // 插入
        Demo01ContactDO demo01Contact = BeanUtils.toBean(createReqVO, Demo01ContactDO.class);
        demo01ContactMapper.insert(demo01Contact);
        // 返回
        return demo01Contact.getId();
    }

    @Override
    public void updateDemo01Contact(Demo01ContactSaveReqVO updateReqVO) {
        // 校验存在
        validateDemo01ContactExists(updateReqVO.getId());
        // 更新
        Demo01ContactDO updateObj = BeanUtils.toBean(updateReqVO, Demo01ContactDO.class);
        demo01ContactMapper.updateById(updateObj);
    }

    private void validateDemo01ContactExists(Long id) {
        if (demo01ContactMapper.selectById(id) == null) {
            throw exception(DEMO01_CONTACT_NOT_EXISTS);
        }
    }

    private void validateDemo01ContactExists(List<Long> ids) {
        List<Demo01ContactDO> list = demo01ContactMapper.selectByIds(ids);
        if (CollUtil.isEmpty(list) || list.size() != ids.size()) {
            throw exception(DEMO01_CONTACT_NOT_EXISTS);
        }
    }

    @Override
    public PageResult<Demo01ContactDO> getDemo01ContactPage(Demo01ContactPageReqVO pageReqVO) {
        return demo01ContactMapper.selectPage(pageReqVO);
    }
}
```
`jakarta.annotation.Resource` (not `@Autowired`); imports are `jakarta.*`, not `javax.*`. Use `@Transactional(rollbackFor = Exception.class)` on multi-write methods; `@DSTransactional` when multiple data sources are involved.

### Controller — `yudao-module-infra/.../controller/admin/demo/demo01/Demo01ContactController.java`
```java
@Tag(name = "管理后台 - 示例联系人")
@RestController
@RequestMapping("/infra/demo01-contact")
@Validated
public class Demo01ContactController {

    @Resource
    private Demo01ContactService demo01ContactService;

    @PostMapping("/create")
    @Operation(summary = "创建示例联系人")
    @PreAuthorize("@ss.hasPermission('infra:demo01-contact:create')")
    public CommonResult<Long> createDemo01Contact(@Valid @RequestBody Demo01ContactSaveReqVO createReqVO) {
        return success(demo01ContactService.createDemo01Contact(createReqVO));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除示例联系人")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('infra:demo01-contact:delete')")
    public CommonResult<Boolean> deleteDemo01Contact(@RequestParam("id") Long id) {
        demo01ContactService.deleteDemo01Contact(id);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "获得示例联系人分页")
    @PreAuthorize("@ss.hasPermission('infra:demo01-contact:query')")
    public CommonResult<PageResult<Demo01ContactRespVO>> getDemo01ContactPage(@Valid Demo01ContactPageReqVO pageReqVO) {
        PageResult<Demo01ContactDO> pageResult = demo01ContactService.getDemo01ContactPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, Demo01ContactRespVO.class));
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出示例联系人 Excel")
    @PreAuthorize("@ss.hasPermission('infra:demo01-contact:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportDemo01ContactExcel(@Valid Demo01ContactPageReqVO pageReqVO,
                                         HttpServletResponse response) throws IOException {
        pageReqVO.setPageSize(PageParam.PAGE_SIZE_NONE);
        List<Demo01ContactDO> list = demo01ContactService.getDemo01ContactPage(pageReqVO).getList();
        // 导出 Excel
        ExcelUtils.write(response, "示例联系人.xls", "数据", Demo01ContactRespVO.class,
                BeanUtils.toBean(list, Demo01ContactRespVO.class));
    }
}
```
Static import: `import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;`. `@RequestMapping` path has **no** `/admin-api` prefix — the prefix is added by the framework's path-prefix config. Permission string = `<module>:<resource>:<action>` → for CLM: `clm:contract:create` etc. Standard endpoints: `/create` (POST→`CommonResult<Long>`), `/update` (PUT→Boolean), `/delete` (DELETE, `@RequestParam("id")`), `/delete-list` (DELETE, `@RequestParam("ids") List<Long>`), `/get` (GET), `/page` (GET), `/export-excel` (GET, void + `HttpServletResponse`).

### VOs
`Demo01ContactPageReqVO` (`.../controller/admin/demo/demo01/vo/`):
```java
@Schema(description = "管理后台 - 示例联系人分页 Request VO")
@Data
public class Demo01ContactPageReqVO extends PageParam {

    @Schema(description = "名字", example = "张三")
    private String name;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;
}
```
`Demo01ContactRespVO`:
```java
@Schema(description = "管理后台 - 示例联系人 Response VO")
@Data
@ExcelIgnoreUnannotated
public class Demo01ContactRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "21555")
    @ExcelProperty("编号")
    private Long id;

    @Schema(description = "性别", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @ExcelProperty(value = "性别", converter = DictConvert.class)
    @DictFormat("system_user_sex")
    private Integer sex;
}
```
Excel imports are **`cn.idev.excel.annotation.*`** (FastExcel), not `com.alibaba.excel`.
`Demo01ContactSaveReqVO`: `@Schema(description = "管理后台 - 示例联系人新增/修改 Request VO")` + `@Data`, one VO for create & update, `@NotEmpty(message="名字不能为空")` / `@NotNull(...)` per field, `id` present but unvalidated.
`PageParam` (`yudao-framework/yudao-common/.../pojo/PageParam.java`): `pageNo` (default 1, `@Min(1)`), `pageSize` (default 10, `@Min(1) @Max(200)`), constant `PAGE_SIZE_NONE = -1`.
`BeanUtils.toBean` overloads used: `toBean(Object, Class<T>)`, `toBean(List<T>, Class<U>)`, `toBean(PageResult<T>, Class<U>)` — from `cn.iocoder.yudao.framework.common.util.object.BeanUtils`.

---

## 5. Security / tenant utils

`cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils` (`yudao-spring-boot-starter-security/.../core/util/SecurityFrameworkUtils.java`), all static:
- `@Nullable LoginUser getLoginUser()`
- `@Nullable Long getLoginUserId()`
- `@Nullable String getLoginUserNickname()`
- `@Nullable Long getLoginUserDeptId()`
- `Authentication getAuthentication()`
- `void setLoginUser(LoginUser, HttpServletRequest)`
- `String obtainAuthorization(HttpServletRequest, String headerName, String parameterName)`
- `boolean skipPermissionCheck()` — true when cross-tenant visiting

`LoginUser` fields (`yudao-spring-boot-starter-security/.../core/LoginUser.java`): `Long id`, `Integer userType` (→`UserTypeEnum`), `Map<String,String> info` (keys `INFO_KEY_NICKNAME="nickname"`, `INFO_KEY_DEPT_ID="deptId"`), `Long tenantId`, `List<String> scopes`, `LocalDateTime expiresTime`, `@JsonIgnore Map<String,Object> context`, `Long visitTenantId`; helpers `setContext(String,Object)` / `<T> T getContext(String, Class<T>)`.

`cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder` statics: `Long getTenantId()`, `Long getRequiredTenantId()` (throws if absent), `void setTenantId(Long)`, `void setIgnore(Boolean)`, `boolean isIgnore()`, `void clear()`.

`cn.iocoder.yudao.framework.tenant.core.util.TenantUtils`: `execute(Long tenantId, Runnable)`, `<V> V execute(Long tenantId, Callable<V>)`, `executeIgnore(Runnable)`, `<V> V executeIgnore(Callable<V>)`, `addTenantHeader(Map<String,String>, Long)`.

`@TenantIgnore` (`yudao-spring-boot-starter-biz-tenant/.../core/aop/TenantIgnore.java`): `@Target({METHOD, TYPE}) @Retention(RUNTIME) @Inherited`, single attribute `String enable() default "true"` (Spring EL). On a Controller class → the URL is auto-added to `TenantProperties.ignoreUrls`; on a DO class → the table is auto-treated as in `ignoreTables`. Only affects DB filtering, not Redis/MQ.

`@DataPermission` (`yudao-spring-boot-starter-biz-data-permission/.../core/annotation/DataPermission.java`): `@Target({TYPE, METHOD})`, attributes `boolean enable() default true`, `Class<? extends DataPermissionRule>[] includeRules() default {}`, `Class<? extends DataPermissionRule>[] excludeRules() default {}`. Data permission is on by default; `@DataPermission(enable = false)` disables it.

---

## 6. `admin:*` / super-admin permission check

`@PreAuthorize("@ss.hasPermission('clm:xxx:create')")` resolves `@ss` to `SecurityFrameworkServiceImpl` (`yudao-spring-boot-starter-security/.../core/service/SecurityFrameworkServiceImpl.java`), which delegates to `PermissionCommonApi`:
```java
@Override
public boolean hasAnyPermissions(String... permissions) {
    if (skipPermissionCheck()) { return true; }          // 跨租户访问
    Long userId = getLoginUserId();
    if (userId == null) { return false; }
    return permissionApi.hasAnyPermissions(userId, permissions);
}
```
Also available on `@ss`: `hasPermission(String)`, `hasRole(String)`, `hasAnyRoles(String...)`, `hasScope(String)`, `hasAnyScopes(String...)`.

Implementation — `yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/service/permission/PermissionServiceImpl.java:62-84`:
```java
@Override
public boolean hasAnyPermissions(Long userId, String... permissions) {
    if (ArrayUtil.isEmpty(permissions)) { return true; }          // 空 => 有权限
    List<RoleDO> roles = getEnableUserRoleListByUserIdFromCache(userId);
    if (CollUtil.isEmpty(roles)) { return false; }
    // 情况一：遍历判断每个权限，如果有一满足，说明有权限
    for (String permission : permissions) {
        if (hasAnyPermission(roles, permission)) { return true; }
    }
    // 情况二：如果是超管，也说明有权限
    return roleService.hasAnySuperAdmin(convertSet(roles, RoleDO::getId));
}
```
Note `hasAnyPermission(roles, permission)` uses **strict mode**: if no menu row carries that permission string, the check fails — so **every new `clm:*:*` permission must have a corresponding `system_menu` row** (insert menu SQL alongside the module).

Super-admin role code: `RoleCodeEnum.SUPER_ADMIN("super_admin", "超级管理员")` (also `TENANT_ADMIN("tenant_admin")`, `CRM_ADMIN("crm_admin")`), with `static boolean isSuperAdmin(String code)` — `yudao-module-system/.../enums/permission/RoleCodeEnum.java`.

Programmatic check from a CLM service: inject `cn.iocoder.yudao.module.system.api.permission.PermissionApi` (extends `PermissionCommonApi`) and call `hasAnyPermissions(Long userId, String... permissions)` / `hasAnyRoles(Long userId, String... roles)`; or inject `SecurityFrameworkService` (bean name `ss`) for the current-user variants.

---

## 7. Utilities

- **JSON** — `cn.iocoder.yudao.framework.common.util.json.JsonUtils`: `String toJsonString(Object)`, `byte[] toJsonByte(Object)`, `String toJsonPrettyString(Object)`, `<T> T parseObject(String, Class<T>)`, `parseObject(String, String path, Class<T>)`, `parseObject(String, Type)`, `parseObject(byte[], Class<T>)`, `parseObject(String, TypeReference<T>)`, `parseObjectQuietly(...)`, `Map<String,Object> parseMap(String)`, `<T> List<T> parseArray(String, Class<T>)`, `parseArray(String, String path, Class<T>)`, `JsonNode parseTree(String)`, `boolean isJson(String)`, `<T> T convertObject(Object, Class<T>)`, `<T> List<T> convertList(Object, Class<T>)`.
- **Exceptions** — `cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil`: `ServiceException exception(ErrorCode)`, `exception(ErrorCode, Object... params)` (params fill `{}` placeholders), `exception0(Integer code, String messagePattern, Object...)`, `invalidParamException(String, Object...)`. Always `throw exception(XXX_NOT_EXISTS);` with a static import.
- **Collections** — `cn.iocoder.yudao.framework.common.util.collection.CollectionUtils`: `convertList`, `convertSet`, `convertLinkedSet`, `convertMap` (5 overloads), `convertMultiMap`, `convertMultiMap2`, `convertImmutableMap`, `convertMapByFilter`, `convertListByFlatMap`, `convertSetByFlatMap`, `convertPage(PageResult<T>, Function)`, `filterList`, `distinct`, `diffList(oldList, newList, sameFunc)`, `getFirst`, `findFirst`, `getMaxValue`/`getMinValue`/`getMinObject`/`getSumValue`, `containsAny`, `anyMatch`, `addIfNotNull`. Hutool's `CollUtil` is also used freely alongside it.
- **Dates** — `cn.iocoder.yudao.framework.common.util.date.DateUtils`: constants `TIME_ZONE_DEFAULT="GMT+8"`, `SECOND_MILLIS`, `FORMAT_YEAR_MONTH_DAY="yyyy-MM-dd"`, `FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND="yyyy-MM-dd HH:mm:ss"`; methods `of(LocalDateTime)`, `of(Date)`, `addTime(Duration)`, `isExpired(LocalDateTime)`, `buildTime(...)`, `max(...)`, `isToday`, `isYesterday`. And `LocalDateTimeUtils`: `parse`, `addTime`, `minusTime`, `beforeNow`, `afterNow`, `ofEpochSecond`, `buildTime(y,m,d)`, `buildBetweenTime(y1,m1,d1,y2,m2,d2)`, `isBetween`, `isOverlap`, `beginOfMonth`, `endOfMonth`, `getQuarterOfYear`, `between`, `getToday/getYesterday/getMonth/getYear`, `getLatestDays(int)`.
- **ID generation** — DB auto-increment. `application.yaml:72`: `mybatis-plus.global-config.db-config.id-type: NONE` — "智能"模式, `IdTypeEnvironmentPostProcessor` picks `AUTO` (MySQL) or `INPUT` (Oracle/PG/Kingbase/DB2/H2) from the datasource type. Therefore every DO gets `@TableId private Long id;` (no `IdType`) plus `@KeySequence("<table>_seq")` on the class **only** so non-MySQL DBs work; on MySQL it is inert. Keep `@KeySequence` — H2 unit tests rely on it.

---

## 8. Unit-test infrastructure

`yudao-framework/yudao-spring-boot-starter-test/src/main/java/cn/iocoder/yudao/framework/test/core/ut/BaseDbUnitTest.java` (full):
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = BaseDbUnitTest.Application.class)
@ActiveProfiles("unit-test") // 设置使用 application-unit-test 配置文件
@Sql(scripts = "/sql/clean.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class BaseDbUnitTest {

    @Import({
            YudaoDataSourceAutoConfiguration.class,
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            DruidDataSourceAutoConfigure.class,
            SqlInitializationTestConfiguration.class,
            YudaoMybatisAutoConfiguration.class,
            MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class,
            SpringUtil.class
    })
    public static class Application {
    }
}
```
Siblings in the same package: `BaseMockitoUnitTest`, `BaseRedisUnitTest`, `BaseDbAndRedisUnitTest`.

Required test resources under `yudao-module-clm/src/test/resources/`:
- `application-unit-test.yaml` — copy `yudao-module-infra/src/test/resources/application-unit-test.yaml` verbatim; key lines:
  ```yaml
  spring:
    datasource:
      name: ruoyi-vue-pro
      url: jdbc:h2:mem:testdb;MODE=MYSQL;DATABASE_TO_UPPER=false;NON_KEYWORDS=value;
      driver-class-name: org.h2.Driver
      username: sa
      password:
    sql:
      init:
        schema-locations: classpath:/sql/create_tables.sql
        encoding: UTF-8
  mybatis-plus:
    lazy-initialization: true
    type-aliases-package: ${yudao.info.base-package}.module.*.dal.dataobject
  yudao:
    info:
      base-package: cn.iocoder.yudao
  ```
- `sql/create_tables.sql` — H2/MySQL-mode DDL, **all identifiers double-quoted lowercase**:
  ```sql
  CREATE TABLE IF NOT EXISTS "infra_config" (
      "id" bigint(20) NOT NULL GENERATED BY DEFAULT AS IDENTITY COMMENT '编号',
      "name" varchar(100) NOT NULL DEFAULT '' COMMENT '名字',
      "creator" varchar(64) DEFAULT '',
      "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
      "updater" varchar(64) DEFAULT '',
      "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
      "deleted" bit NOT NULL DEFAULT FALSE,
      PRIMARY KEY ("id")
  ) COMMENT '参数配置表';
  ```
- `sql/clean.sql` — one `DELETE FROM "clm_xxx";` per table.
- `logback.xml` (copy from infra).

Example test — `yudao-module-infra/src/test/java/cn/iocoder/yudao/module/infra/service/config/ConfigServiceImplTest.java` (compact):
```java
@Import(ConfigServiceImpl.class)
public class ConfigServiceImplTest extends BaseDbUnitTest {

    @Resource
    private ConfigServiceImpl configService;
    @Resource
    private ConfigMapper configMapper;

    @Test
    public void testCreateConfig_success() {
        // 准备参数
        ConfigSaveReqVO reqVO = randomPojo(ConfigSaveReqVO.class).setId(null);
        // 调用
        Long configId = configService.createConfig(reqVO);
        // 断言
        assertNotNull(configId);
        ConfigDO config = configMapper.selectById(configId);
        assertPojoEquals(reqVO, config, "id");
    }

    @Test
    public void testDeleteConfig_canNotDeleteSystemType() {
        ConfigDO dbConfig = randomConfigDO(o -> o.setType(ConfigTypeEnum.SYSTEM.getType()));
        configMapper.insert(dbConfig);
        // 调用, 并断言异常
        assertServiceException(() -> configService.deleteConfig(dbConfig.getId()), CONFIG_CAN_NOT_DELETE_SYSTEM_TYPE);
    }

    @Test
    public void testGetConfigPage() {
        ConfigDO dbConfig = randomConfigDO(o -> { o.setName("芋艿"); o.setCreateTime(buildTime(2021, 2, 1)); });
        configMapper.insert(dbConfig);
        configMapper.insert(cloneIgnoreId(dbConfig, o -> o.setName("土豆")));   // 不匹配
        ConfigPageReqVO reqVO = new ConfigPageReqVO();
        reqVO.setName("艿");
        reqVO.setCreateTime(buildBetweenTime(2021, 1, 15, 2021, 2, 15));
        PageResult<ConfigDO> pageResult = configService.getConfigPage(reqVO);
        assertEquals(1, pageResult.getTotal());
        assertPojoEquals(dbConfig, pageResult.getList().get(0));
    }

    // ========== 随机对象 ==========
    @SafeVarargs
    private static ConfigDO randomConfigDO(Consumer<ConfigDO>... consumers) {
        Consumer<ConfigDO> consumer = (o) -> o.setType(randomEle(ConfigTypeEnum.values()).getType());
        return RandomUtils.randomPojo(ConfigDO.class, ArrayUtils.append(consumer, consumers));
    }
}
```
Test helper static imports: `cn.iocoder.yudao.framework.test.core.util.RandomUtils.*` (`randomPojo`, `randomLongId`, `randomString`, …), `AssertUtils.assertPojoEquals` / `assertServiceException`, `ObjectUtils.cloneIgnoreId`, `LocalDateTimeUtils.buildTime` / `buildBetweenTime`.
Pom test dependency: `cn.iocoder.boot:yudao-spring-boot-starter-test` with `<scope>test</scope>`.

---

## 9. system-api interfaces usable from yudao-module-clm

All under `yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/`. Requires the `yudao-module-system` pom dependency (§1).

**`user/AdminUserApi.java`**
```java
AdminUserRespDTO getUser(Long id);
List<AdminUserRespDTO> getUserListBySubordinate(Long id);
List<AdminUserRespDTO> getUserList(Collection<Long> ids);
List<AdminUserRespDTO> getUserListByDeptIds(Collection<Long> deptIds);
List<AdminUserRespDTO> getUserListByPostIds(Collection<Long> postIds);
List<AdminUserRespDTO> getUserListByNickname(String nickname);
default Map<Long, AdminUserRespDTO> getUserMap(Collection<Long> ids);
default void validateUser(Long id);
void validateUserList(Collection<Long> ids);
```
**`user/dto/AdminUserRespDTO`** fields: `Long id`, `String nickname`, `Integer status` (→`CommonStatusEnum`), `Long deptId`, `Set<Long> postIds`, `String mobile`, `String avatar`.

**`dept/DeptApi.java`**
```java
DeptRespDTO getDept(Long id);
List<DeptRespDTO> getDeptList(Collection<Long> ids);
void validateDeptList(Collection<Long> ids);
default Map<Long, DeptRespDTO> getDeptMap(Collection<Long> ids);
List<DeptRespDTO> getChildDeptList(Long id);
```

**`permission/PermissionApi.java`** — `extends PermissionCommonApi` (framework), adds:
```java
Set<Long> getUserRoleIdListByRoleIds(Collection<Long> roleIds);
```
Inherited from `PermissionCommonApi`: `boolean hasAnyPermissions(Long userId, String... permissions)`, `boolean hasAnyRoles(Long userId, String... roles)`, plus data-permission helpers.

**`permission/RoleApi.java`**
```java
void validRoleList(Collection<Long> ids);
RoleRespDTO getRole(Long id);
List<RoleRespDTO> getRoleList(Collection<Long> ids);
default Map<Long, RoleRespDTO> getRoleMap(Collection<Long> ids);
```

**`dict/DictDataApi.java`** — `extends DictDataCommonApi`, adds:
```java
void validateDictDataList(String dictType, Collection<String> values);
```

Other available APIs in the same tree: `logger/OperateLogApi`, `mail/MailSendApi`, `message/user/*`, `notify/NotifySendApi`, `oauth2/OAuth2TokenApi`, `sms/SmsSendApi`, `social/SocialUserApi` / `SocialClientApi`, `tenant/TenantApi`.