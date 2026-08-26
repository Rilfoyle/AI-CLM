# Menu / Permission / Role / Tenant Seeding Reference

## 1. `system_menu`

**DDL** — `sql/mysql/ruoyi-vue-pro.sql:1866-1887`

| column | type | default / note |
|---|---|---|
| `id` | bigint | `AUTO_INCREMENT` (table `AUTO_INCREMENT = 6735`) |
| `name` | varchar(50) | NOT NULL, 菜单名称 |
| `permission` | varchar(100) | NOT NULL DEFAULT `''` |
| `type` | tinyint | NOT NULL — 1 dir / 2 menu / 3 button |
| `sort` | int | NOT NULL DEFAULT 0 |
| `parent_id` | bigint | NOT NULL DEFAULT 0 |
| `path` | varchar(200) | DEFAULT `''` |
| `icon` | varchar(100) | DEFAULT `'#'` |
| `component` | varchar(255) | DEFAULT NULL |
| `component_name` | varchar(255) | DEFAULT NULL |
| `status` | tinyint | NOT NULL DEFAULT 0 (0 正常 / 1 停用) |
| `visible` | bit(1) | DEFAULT `b'1'` |
| `keep_alive` | bit(1) | DEFAULT `b'1'` |
| `always_show` | bit(1) | DEFAULT `b'1'` |
| `creator` / `create_time` / `updater` / `update_time` | varchar(64)/datetime | audit |
| `deleted` | bit(1) | DEFAULT `b'0'` |

**No `tenant_id` column** — menus are global across tenants.

`type` enum: `yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/enums/permission/MenuTypeEnum.java` — `DIR(1)`, `MENU(2)`, `BUTTON(3)`.

**Conventions**
- Top-level dir (`type=1, parent_id=0`): `path` starts with `/` (`/wms`, `/bpm`); `component` and `component_name` empty/NULL.
- Sub-dir (`type=1, parent_id=<dir>`): `path` is a **relative** segment (`md`, `manager`), no component.
- Menu (`type=2`): relative `path` (may contain `/`, e.g. `item/brand`); `component` = path under `src/views/` **without** `@/views/` prefix and **without** `.vue` (`wms/md/item/brand/index`); `component_name` = the Vue route name / `defineOptions({name})`, PascalCase (`WmsItemBrand`). Resolution: `frontend/src/utils/routerHelper.ts:13` `import.meta.glob('../views/**/*.{vue,tsx}')` matched by `modulesRoutesKeys.findIndex((ev) => ev.includes(route.component))` (`routerHelper.ts:186-189`); if `component` is empty it falls back to matching `route.path`.
- Button (`type=3`): `path`/`icon`/`component`/`component_name` all `''`; only `permission` matters.
- `permission` format: `module:entity:action`, entity kebab-case — `wms:item-brand:query|create|update|delete|export`, `bpm:form:query`, `system:user:list`.
- `visible=b'0'` → `meta.hidden=true` (`routerHelper.ts:92`). `keep_alive` → `meta.noCache = !keepAlive` (line 93). `always_show` only applies when the node has children (`routerHelper.ts:94-97`).

**Representative seed rows** (values only; column list is the full 19-column one above)

```sql
-- top-level dir
(6400, 'WMS 系统', '', 1, 310, 0, '/wms', 'ep:box', '', '', 0, b'1', b'1', b'1', '1', '2026-05-09 16:11:01', '1', '2026-05-09 16:11:01', b'0');
-- top-level dir (bpm)
(1185, '工作流程', '', 1, 50, 0, '/bpm', 'fa:medium', NULL, NULL, 0, b'1', b'1', b'1', '1', '2021-12-30 20:26:36', '1', '2024-02-29 12:43:43', b'0');
-- sub dir
(6401, '基础数据', '', 1, 6, 6400, 'md', 'ep:files', '', '', 0, b'1', b'1', b'1', '1', '2026-05-09 16:11:01', '1', '2026-05-12 17:48:35', b'0');
-- menu with component
(6411, '商品品牌', '', 2, 3, 6401, 'item/brand', 'ep:price-tag', 'wms/md/item/brand/index', 'WmsItemBrand', 0, b'1', b'1', b'1', '1', '2026-05-10 01:43:12', '1', '2026-05-10 02:12:51', b'0');
-- buttons
(6412, '品牌查询', 'wms:item-brand:query',  3, 1, 6411, '', '', '', '', 0, b'1', b'1', b'1', '1', '2026-05-10 01:43:12', '1', '2026-05-10 01:43:12', b'0');
(6413, '品牌创建', 'wms:item-brand:create', 3, 2, 6411, '', '', '', '', 0, b'1', b'1', b'1', '1', '2026-05-10 01:43:12', '1', '2026-05-10 01:43:12', b'0');
-- hidden menu (visible = b'0')
(5906, '条码配置', '', 2, 15, 5780, 'barcode/config', 'fa:barcode', 'mes/wm/barcode/config/index', 'MesWmBarcodeConfig', 0, b'0', b'1', b'1', '1', '2026-03-05 14:37:20', '1', '2026-03-06 21:05:20', b'0');
-- menu with keep_alive = 0 (bpm 发起流程)
(2720, '发起流程', '', 2, 0, 1200, 'create', 'fa-solid:grin-stars', 'bpm/processInstance/create/index', 'BpmProcessInstanceCreate', 0, b'1', b'0', b'1', '1', '2024-03-19 19:46:05', '1', '2024-03-23 19:03:42', b'0');
```

**Important — detail pages are NOT in `system_menu`.** The seed contains **zero** rows with `:id` path params or `?query` in `path`, and only **2** rows with `visible=b'0'` (ids 4037, 5906). BPM process-instance detail, OA leave detail and all CRM `*/detail/:id` pages are **static frontend routes** in `frontend/src/router/modules/remaining.ts` (no permission check, no DB row):

```ts
// remaining.ts:278-294  (query params)
{ path: 'process-instance/detail',
  component: () => import('@/views/bpm/processInstance/detail/index.vue'),
  name: 'BpmProcessInstanceDetail',
  meta: { noCache: true, hidden: true, canTo: true, title: '流程详情', activeMenu: '/bpm/task/my' },
  props: (route) => ({ id: route.query.id, taskId: route.query.taskId, activityId: route.query.activityId }) }

// remaining.ts:546-556  (path param — closest analogue for a contract detail page)
{ path: 'contract/detail/:id', name: 'CrmContractDetail',
  meta: { title: '合同详情', noCache: true, hidden: true, activeMenu: '/crm/contract' },
  component: () => import('@/views/crm/contract/detail/index.vue') }
```
Both live under a parent `{ path: '/crm', component: Layout, name: 'CrmCenter', meta: { hidden: true }, children: [...] }` (`remaining.ts:507-512`).

**Max id / collision**: highest seeded `system_menu.id` = **6734** (tail: 6722, 6723, 6724, 6730–6734); `AUTO_INCREMENT = 6735`. Ids are auto-increment and **may be omitted**, but every seed row inserts them explicitly (needed for `parent_id` wiring). For a new tree, use an explicit reserved block — e.g. **7000+** — which is safely above 6734.
Other maxima: `system_role_menu` max id 6691 (`AUTO_INCREMENT=6692`), `system_role` max id 155 (`AUTO_INCREMENT=160`), `system_dict_type` max 1061098, `system_dict_data` max 1061136, `system_user_role` max 53 (`AUTO_INCREMENT=55`).

---

## 2. Roles & role→menu

**`system_role`** DDL at `sql/mysql/ruoyi-vue-pro.sql:3660-3677`: `id, name, code, sort, data_scope, data_scope_dept_ids, status, type, remark, creator, create_time, updater, update_time, deleted, tenant_id`. Note: **`name` comes before `code`**.

Seed rows (id, name, code, type, tenant_id):
```
(1,   '超级管理员', 'super_admin',  type=1, tenant_id=1)
(2,   '普通角色',   'common',       type=1, tenant_id=1)
(3,   'CRM 管理员', 'crm_admin',    type=1, tenant_id=1)
(109, '租户管理员', 'tenant_admin', type=1, tenant_id=121)
(111, '租户管理员', 'tenant_admin', type=1, tenant_id=122)
(155, '测试数据权限12', 'test-dp',  type=2, tenant_id=1)
```
`type`: 1 = 内置, 2 = 自定义 (`RoleTypeEnum`).

**super_admin handling** — `yudao-module-system/.../service/permission/RoleServiceImpl.java:234-243`:
```java
@Override
public boolean hasAnySuperAdmin(Collection<Long> ids) {
    if (CollectionUtil.isEmpty(ids)) { return false; }
    RoleServiceImpl self = getSelf();
    return ids.stream().anyMatch(id -> {
        RoleDO role = self.getRoleFromCache(id);
        return role != null && RoleCodeEnum.isSuperAdmin(role.getCode());
    });
}
```
`RoleCodeEnum` (`.../enums/permission/RoleCodeEnum.java`): `SUPER_ADMIN("super_admin")`, `TENANT_ADMIN("tenant_admin")`, `CRM_ADMIN("crm_admin")`; `isSuperAdmin(code)` compares only against `super_admin`.

`PermissionServiceImpl.java:184-195`:
```java
public Set<Long> getRoleMenuListByRoleId(Collection<Long> roleIds) {
    if (CollUtil.isEmpty(roleIds)) { return Collections.emptySet(); }
    // 如果是管理员的情况下，获取全部菜单编号
    if (roleService.hasAnySuperAdmin(roleIds)) {
        return convertSet(menuService.getMenuList(), MenuDO::getId);
    }
    return convertSet(roleMenuMapper.selectListByRoleId(roleIds), RoleMenuDO::getMenuId);
}
```
and `PermissionServiceImpl.java:82-83` in `hasAnyPermissions`: `return roleService.hasAnySuperAdmin(convertSet(roles, RoleDO::getId));`

**⇒ Super admin (role 1) needs NO `system_role_menu` rows** — new menus are visible to `admin` immediately. (The seed does contain 8 stray rows for role_id=1, but they are not required.) `AuthController.getPermissionInfo()` (`.../controller/admin/auth/AuthController.java:112-114`) uses this same `getRoleMenuListByRoleId`, so the menu tree for super admin is the full table.

**`system_role_menu`** columns: `id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id`. Seed distribution by role: role 1 → 8 rows, role 2 → 264, role 3 → 1, role 109 → 292, role 110 → 1, role 111 → 257, role 155 → 90.

**`system_user_role`** DDL at `:4965-4977` — `id, user_id, role_id, creator, create_time, updater, update_time, deleted, tenant_id`. Tenant-1 seed:
```
(1,  user 1   -> role 1)    (18, user 1   -> role 2)
(2,  user 2   -> role 2)    (5, 6: user 100 -> roles 1 and 2)
(10, user 103 -> role 1)    (47, user 104 -> role 2)
(48, user 100 -> role 155)  (35, user 112 -> role 1)  (36, user 118 -> role 1)
(46, user 117 -> role 1)    (49/50, user 142 -> roles 1,2)  (51/52, user 139 -> roles 1,2)
(22, user 115 -> role 2)    (53, user 114 -> role 2)
-- other tenants: (14, user 110 -> role 109, tenant 121); (16, user 113 -> role 111, tenant 122)
```

---

## 3. Tenants

**`system_tenant`** DDL `:4873-4890` — `id, name, contact_user_id, contact_name, contact_mobile, status, websites, package_id, expire_time, account_count, creator, create_time, updater, update_time, deleted` (**no `tenant_id`**, `AUTO_INCREMENT=162`).

```sql
(1,   '芋道源码', NULL, '芋艿', '17321315478', 0, 'www.iocoder.cn,127.0.0.1:3000,wxc4598c446f8a9cb3', 0,   '2099-02-19 17:14:16', 9999, ...);
(121, '小租户',   110,  '小王2','15601691300', 0, 'zsxq.iocoder.cn,123321', 111, '2026-07-10 00:00:00', 30, ...);
(122, '测试租户', 113,  '芋道', '15601691300', 0, 'test.iocoder.cn,222,333', 111, '2023-04-29 00:00:00', 50, ...);
```
**Tenant 1 has `package_id = 0`** = `TenantDO.PACKAGE_ID_SYSTEM` (`.../dal/dataobject/tenant/TenantDO.java:35`) → it is the *system tenant* with the full menu set.

**`system_tenant_package`** DDL `:4905-4917` — `id, name, status, remark, menu_ids varchar(4096) (JSON array string), creator, create_time, updater, update_time, deleted`. Seed: id 111 `'普通套餐'` (large `[1,2,5,1031,...]` array), id 113 `'测试套餐（啥都没有）'` `'[2160,1254,2159]'`. **There is no package row for tenant 1.**

**Package → menu filtering** — `.../service/tenant/TenantServiceImpl.java:294-314`:
```java
public void handleTenantMenu(TenantMenuHandler handler) {
    if (isTenantDisable()) { return; }
    TenantDO tenant = getTenant(TenantContextHolder.getRequiredTenantId());
    Set<Long> menuIds;
    if (isSystemTenant(tenant)) { // 系统租户，菜单是全量的
        menuIds = CollectionUtils.convertSet(menuService.getMenuList(), MenuDO::getId);
    } else {
        menuIds = tenantPackageService.getTenantPackage(tenant.getPackageId()).getMenuIds();
    }
    handler.handle(menuIds);
}
private static boolean isSystemTenant(TenantDO tenant) {
    return Objects.equals(tenant.getPackageId(), TenantDO.PACKAGE_ID_SYSTEM);
}
```
Callers (only 2):
- `MenuServiceImpl.java:131-137` `getMenuListByTenant` — `tenantService.handleTenantMenu(menuIds -> menus.removeIf(menu -> !CollUtil.contains(menuIds, menu.getId())));` (used by the 菜单管理 list screen)
- `PermissionController.java:46` — trims `assignRoleMenu` request menu ids to the package.

`TenantServiceImpl.updateTenantRoleMenu(Long tenantId, Set<Long> menuIds)` (`:192-215`) reassigns `tenant_admin` role to the package menus and intersects other roles' menus with the package.

**⇒ For tenant 1 nothing needs to be added to any package** — it is the system tenant and gets all menus. New menus only need adding to `system_tenant_package.menu_ids` for non-system tenants (121/122 use package 111).

**Tenant-aware tables**: `yudao-server/src/main/resources/application.yaml:311-318` —
```yaml
yudao:
  tenant:
    enable: true
    ignore-urls:
      - /jmreport/*
    ignore-visit-urls:
      - /admin-api/system/user/profile/**
      - /admin-api/system/auth/**
    ignore-tables:      # <-- EMPTY, no entries
```
Tenant awareness is decided **per-DO, not per-table list** — `yudao-framework/yudao-spring-boot-starter-biz-tenant/.../core/db/TenantDatabaseInterceptor.java:68-81`:
```java
private boolean computeIgnoreTable(String tableName) {
    TableInfo tableInfo = TableInfoHelper.getTableInfo(tableName);
    if (tableInfo == null) { return true; }                       // not a yudao table -> ignore
    if (TenantBaseDO.class.isAssignableFrom(tableInfo.getEntityType())) { return false; } // tenant-aware
    TenantIgnore tenantIgnore = tableInfo.getEntityType().getAnnotation(TenantIgnore.class);
    return tenantIgnore != null;
}
```
`TenantBaseDO` (`.../core/db/TenantBaseDO.java`) simply adds `private Long tenantId;` on top of `BaseDO`.

**For a new tenant-aware table**: DO extends `TenantBaseDO`, and the table needs `` `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号' `` (MP auto-injects the `tenant_id = ?` predicate and fills it on insert). `system_menu` deliberately has none.

---

## 4. Dicts

**`system_dict_type`** DDL `:1503-1516` — `id, name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time` (`AUTO_INCREMENT=1061099`).
```sql
INSERT INTO `system_dict_type` (`id`,`name`,`type`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`deleted_time`)
VALUES (1, '用户性别', 'system_user_sex', 0, NULL, 'admin', '2021-01-05 17:03:48', '1', '2022-05-16 20:29:32', b'0', NULL);
```

**`system_dict_data`** DDL `:449-465` — `id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted` (`AUTO_INCREMENT=1061137`). `color_type` ∈ Element Plus tag types (`primary|success|info|warning|danger|''`).
```sql
INSERT INTO `system_dict_data` (`id`,`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES (1, 1, '男', '1', 'system_user_sex', 0, 'primary', 'A', '性别男', 'admin', '2021-01-05 17:03:48', '1', '2025-12-10 13:19:26', b'0');
```
Neither dict table has `tenant_id`.

**Frontend consumption** — `frontend/src/utils/dict.ts`:
- `export enum DICT_TYPE { ... }` at line **114** (file is 360 lines); it is a **hard-coded TS string enum** listing every dict type, grouped by module (`// ========== SYSTEM 模块 ==========`, CRM block at lines 209-219, etc.).
- **Technically optional**: all accessors take a plain `string` — `export const getDictOptions = (dictType: string)`, `getIntDictOptions(dictType: string): NumberDictDataType[]`, `getStrDictOptions(dictType: string)`; `DictTag.vue` prop is `type: { type: String as PropType<string> }`. So a raw string works, but **the convention is to extend the enum** (add a `// ========== CLM 模块 ==========` block) — every existing view uses `DICT_TYPE.XXX`.
- Data comes from the dict store (`@/store/modules/dict`, loaded once at login), so new dict rows require no code change beyond the enum entry.

---

## 5. Seeded users

**`system_users`** DDL `:5010-5036` — `id, username, password, nickname, remark, dept_id, post_ids, email, mobile, sex, avatar, status, login_ip, login_date, creator, create_time, updater, update_time, deleted, tenant_id` (`AUTO_INCREMENT=225`).

| id | username | nickname | dept_id | tenant_id |
|---|---|---|---|---|
| 1 | `admin` | 芋道源码 | 103 | 1 |
| 100 | `yudao` | 芋道 | 104 | 1 |
| 103 | `yuanma` | 源码 | 106 | 1 |
| 104 | `test` | 测试号 | 107 | 1 |
| 107 | `admin107` | 芋艿 | NULL | 118 |
| 108 | `admin108` | 芋艿 | NULL | 119 |
| 109 | `admin109` | 芋艿 | NULL | 120 |
| 110 | `admin110` | 小王 | NULL | 121 |
| 111 | `test` | 测试用户 | NULL | 121 |
| 112 | `newobject` | 新对象 | 100 | 1 |
| 113 | `aoteman` | 芋道1 | NULL | 122 |
| 114 | `hrmgr` | hr 小姐姐 | NULL | 1 |
| 115 | `aotemane` | 阿呆 | 102 | 1 |
| 117 | `admin123` | 测试号02 | 100 | 1 |

**Password**: BCrypt hashes, e.g. admin = `$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG` (same hash as user 100). The plaintext default per `frontend/.env` is **`admin` / `admin123`** (`VITE_APP_DEFAULT_LOGIN_USERNAME = admin`, `VITE_APP_DEFAULT_LOGIN_PASSWORD = admin123`) — **not** `123456`. I found no `123456` default in the seed or env files.

---

## 6. Local-dev login config

- **Captcha**: `backend/yudao-server/src/main/resources/application-local.yaml:237-238`
  ```yaml
  yudao:
    captcha:
      enable: false # 本地环境，暂时关闭图片验证码，方便登录等接口的测试；
  ```
  Frontend counterpart `frontend/.env`: `VITE_APP_CAPTCHA_ENABLE=true` — **must be flipped to `false`** to match the backend, otherwise `LoginForm.vue:78` renders the slider. (`LoginForm.vue:199` `if (loginData.captchaEnable === 'false') { await handleLogin({}) }`.)
- Also in `application-local.yaml:239-240`: `yudao.security.mock-enable: true`.
- **Tenant**: backend `application.yaml:311-312` `yudao.tenant.enable: true`; frontend `.env:11` `VITE_APP_TENANT_ENABLE=true`. Header injected in `frontend/src/config/axios/service.ts:20,61`.
- **Default login tenant name**: `frontend/.env:23` `VITE_APP_DEFAULT_LOGIN_TENANT = 芋道源码`, consumed at `LoginForm.vue:181` (`tenantName: import.meta.env.VITE_APP_DEFAULT_LOGIN_TENANT || ''`); also hard-coded in `src/views/Login/components/MobileForm.vue:129` and `src/views/Login/SocialLogin.vue:203`.

---

## 7. Ready-to-use INSERT template for 合同管理 (clm)

Explicit ids in a reserved block **7000+** (max seeded menu id = 6734).

```sql
SET @CLM_ROOT   = 7000;
SET @CLM_LIST   = 7010;
SET @CLM_DETAIL = 7020;

-- 1) 顶级目录
INSERT INTO `system_menu`
  (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES
  (@CLM_ROOT, '合同管理', '', 1, 210, 0, '/clm', 'ep:document', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- 2) 列表菜单（type=2，component 不带 @/views/ 前缀、不带 .vue）
INSERT INTO `system_menu`
  (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES
  (@CLM_LIST, '合同列表', '', 2, 1, @CLM_ROOT, 'contract', 'ep:tickets',
   'clm/contract/index', 'ClmContract', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- 3) 按钮（type=3，path/icon/component/component_name 全为 ''）
INSERT INTO `system_menu`
  (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES
  (@CLM_LIST+1, '合同查询', 'clm:contract:query',  3, 1, @CLM_LIST, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (@CLM_LIST+2, '合同创建', 'clm:contract:create', 3, 2, @CLM_LIST, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (@CLM_LIST+3, '合同更新', 'clm:contract:update', 3, 3, @CLM_LIST, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (@CLM_LIST+4, '合同删除', 'clm:contract:delete', 3, 4, @CLM_LIST, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (@CLM_LIST+5, '合同导出', 'clm:contract:export', 3, 5, @CLM_LIST, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- 4) 隐藏详情页 (visible=b'0', keep_alive=b'0')
--    ⚠ 若详情页需要 /:id 动态参数，请勿用 system_menu，改在
--      frontend/src/router/modules/remaining.ts 里加静态隐藏路由（见 §1）。
INSERT INTO `system_menu`
  (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES
  (@CLM_DETAIL, '合同详情', '', 2, 99, @CLM_ROOT, 'contract/detail', '',
   'clm/contract/detail/index', 'ClmContractDetail', 0, b'0', b'0', b'1', '1', NOW(), '1', NOW(), b'0');
```

Ids can also be omitted (`id` is AUTO_INCREMENT) — but then you must resolve `parent_id` via `LAST_INSERT_ID()` / a follow-up `SELECT`, which is why every seed row hard-codes them.

**Grant all new menus to a role** (unnecessary for role 1 / any `super_admin` role — see §2):

```sql
SET @ROLE_ID = 2;
SET @TENANT_ID = 1;
INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT @ROLE_ID, m.id, '1', NOW(), '1', NOW(), b'0', @TENANT_ID
FROM `system_menu` m
WHERE m.id BETWEEN 7000 AND 7999 AND m.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` rm
                  WHERE rm.role_id = @ROLE_ID AND rm.menu_id = m.id AND rm.deleted = b'0');
```

**Append to a tenant package** (only for non-system tenants; tenant 1 has `package_id = 0` and needs nothing):

```sql
-- menu_ids is a JSON array string in varchar(4096) -> use JSON_MERGE_PRESERVE (MySQL 8)
UPDATE `system_tenant_package`
SET `menu_ids` = JSON_MERGE_PRESERVE(
      `menu_ids`,
      (SELECT CAST(CONCAT('[', GROUP_CONCAT(id), ']') AS JSON)
       FROM `system_menu` WHERE id BETWEEN 7000 AND 7999 AND deleted = b'0')),
    `update_time` = NOW(), `updater` = '1'
WHERE `id` = 111;
-- 注意 varchar(4096) 长度上限；套餐改动后需调用 TenantServiceImpl#updateTenantRoleMenu
-- （或后台“租户套餐”界面保存一次）才会同步到各租户角色的 system_role_menu。
```

**Also required for a working CLM module** (not SQL):
- add `CLM_*` entries to the `DICT_TYPE` enum in `frontend/src/utils/dict.ts` (line 114+) for any new dict types;
- if the CLM business tables are tenant-aware, give each `` `tenant_id` bigint NOT NULL DEFAULT 0 `` and have the DO extend `TenantBaseDO`;
- register hidden `/clm/**/detail/:id` routes in `frontend/src/router/modules/remaining.ts`.