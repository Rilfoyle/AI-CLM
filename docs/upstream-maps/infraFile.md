# Infra File Storage API & Security Boundary

## 1. `FileApi` / `FileApiImpl`

`yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/api/file/FileApi.java`

```java
public interface FileApi {
    default String createFile(byte[] content) { return createFile(content, null, null, null); }
    default String createFile(byte[] content, String name) { return createFile(content, name, null, null); }
    String createFile(@NotEmpty(message = "文件内容不能为空") byte[] content,
                      String name, String directory, String type);
    String presignGetUrl(@NotEmpty(message = "URL 不能为空") String url, Integer expirationSeconds);
}
```

- All `createFile` variants return the **访问 URL (String)**, NOT the id.
- `FileApiImpl.java`: `@Service @Validated`, injects `FileService`, pure delegation (`fileService.createFile(...)`, `fileService.presignGetUrl(...)`). No other methods (no `getFile`, no delete on the API layer).
- No caller of `presignGetUrl`/`presignPutUrl` exists outside `yudao-module-infra`.

## 2. `FileService` / `FileServiceImpl`

`.../service/file/FileService.java` — full interface:

```java
PageResult<FileDO> getFilePage(FilePageReqVO pageReqVO);
String createFile(@NotEmpty(...) byte[] content, String name, String directory, String type);
FilePresignedUrlRespVO presignPutUrl(@NotEmpty(...) String name, String directory);
String presignGetUrl(String url, Integer expirationSeconds);
Long createFile(FileCreateReqVO createReqVO);
FileDO getFile(Long id);
void deleteFile(Long id) throws Exception;
void deleteFileList(List<Long> ids) throws Exception;
byte[] getFileContent(Long configId, String path) throws Exception;
FileDO getFileByConfigIdAndPath(Long configId, String path);
```

`.../service/file/FileServiceImpl.java`:

- `createFile(byte[], name, directory, type)` (L71-105):
  1. `name = FilePathUtils.validateFileName(name)` (rejects any `/`, traversal).
  2. type empty → `FileTypeUtils.getMineType(content, name)` (Apache Tika).
  3. name empty → `name = DigestUtil.sha256Hex(content)` (hutool); missing extension → appended from `FileTypeUtils.getExtension(type)`.
  4. `String path = generateUploadPath(name, directory)` → `directory/yyyyMMdd/name.ext` (flags `PATH_PREFIX_DATE_ENABLE=true`, `PATH_SUFFIX_TIMESTAMP_ENABLE=false`, `PATH_SUFFIX_AS_DIRECTORY=true`).
  5. `FileClient client = fileConfigService.getMasterFileClient(); String url = client.upload(content, path, type);`
  6. Persist: `fileMapper.insert(new FileDO().setConfigId(client.getId()).setName(name).setPath(path).setUrl(url).setType(type).setSize((long) content.length));`
  7. **returns `url`** (the client-formed URL, not the id).
- Master config choice: `FileConfigServiceImpl.getMasterFileClient()` → `clientCache.getUnchecked(0L)` where `CACHE_MASTER_ID = 0L`; loader does `fileConfigMapper.selectByMaster()` (i.e. `infra_file_config.master = TRUE`), builds/refreshes via `fileClientFactory.createOrUpdateFileClient(id, storage, config)`. Guava `buildAsyncReloadingCache(Duration.ofSeconds(10L), ...)` → master switch takes effect ≤10s.
- URL formation: delegated to the client. `AbstractFileClient.formatFileUrl` (used by db/local/ftp/sftp):
  ```java
  return StrUtil.format("{}/admin-api/infra/file/{}/get/{}", domain, getId(), HttpUtils.encodeUrlPath(path));
  ```
  S3 returns `presignGetUrl(path, null)` → public: `domain + "/" + encodeUrlPath(path)`; private (`enablePublicAccess == false`): a real presigned GET URL (default 24h).
- `createFile(FileCreateReqVO)` → returns `Long id`; strips signature params via `HttpUtils.removeUrlQuery(url)` before persisting.
- `getFile(Long id)` exists → `validateFileExists(id)` → `fileMapper.selectById`, throws `FILE_NOT_EXISTS` if absent.
- **No `getFileByUrl(...)` anywhere.** Only lookups: by id (`getFile`) and by `(configId, path)` (`getFileByConfigIdAndPath` → `FileMapper.selectLatestByConfigIdAndPath`).
- `deleteFile(Long id)`: load DO → `FilePathUtils.validatePath(file.getPath())` → `fileConfigService.getFileClient(file.getConfigId()).delete(path)` → `fileMapper.deleteById(id)`. `deleteFileList(List<Long>)` is the batch equivalent.
- `getFileContent(Long configId, String path) throws Exception`: `FilePathUtils.validatePath(path)` then `fileConfigService.getFileClient(configId).getContent(path)`.

## 3. `FileController` — endpoints & security boundary

`.../controller/admin/file/FileController.java` — class: `@RestController @RequestMapping("/infra/file") @Validated @Slf4j` (effective base `/admin-api/infra/file`).

| Method | Path | Security | Returns |
|---|---|---|---|
| `uploadFile(@Valid FileUploadReqVO)` | `POST /upload` | none (requires login token) | `CommonResult<String>` = URL |
| `getFilePresignedUrl(name, directory)` | `GET /presigned-url` | none | `CommonResult<FilePresignedUrlRespVO>` |
| `createFile(@Valid @RequestBody FileCreateReqVO)` | `POST /create` | none | `CommonResult<Long>` id |
| `getFile(id)` | `GET /get` | `@PreAuthorize("@ss.hasPermission('infra:file:query')")` | `CommonResult<FileRespVO>` |
| `deleteFile(id)` | `DELETE /delete` | `@PreAuthorize("@ss.hasPermission('infra:file:delete')")` | `CommonResult<Boolean>` |
| `deleteFileList(ids)` | `DELETE /delete-list` | `@PreAuthorize("@ss.hasPermission('infra:file:delete')")` | `CommonResult<Boolean>` |
| `getFileContent(request, response, configId)` | `GET /{configId}/get/**` | **`@PermitAll` + `@TenantIgnore`** | raw bytes (no CommonResult) |
| `getFilePage(@Valid FilePageReqVO)` | `GET /page` | `@PreAuthorize("@ss.hasPermission('infra:file:query')")` | `CommonResult<PageResult<FileRespVO>>` |

The public read route, verbatim:

```java
@GetMapping("/{configId}/get/**")
@PermitAll
@TenantIgnore
@Operation(summary = "下载文件")
@Parameter(name = "configId", description = "配置编号", required = true)
public void getFileContent(HttpServletRequest request,
                           HttpServletResponse response,
                           @PathVariable("configId") Long configId) throws Exception {
    String path = StrUtil.subAfter(request.getRequestURI(), "/get/", false);
    if (StrUtil.isEmpty(path)) {
        throw new IllegalArgumentException("结尾的 path 路径必须传递");
    }
    path = HttpUtils.decodeUrlPath(path);
    byte[] content = fileService.getFileContent(configId, path);
    if (content == null) {
        log.warn("[getFileContent][configId({}) path({}) 文件不存在]", configId, path);
        response.setStatus(HttpStatus.NOT_FOUND.value());
        return;
    }
    FileDO file = fileService.getFileByConfigIdAndPath(configId, path);
    String filename = file != null && StrUtil.isNotEmpty(file.getName()) ? file.getName() : FileUtil.getName(path);
    writeAttachment(response, filename, content);
}
```

URL → (configId, path) mapping: the stored URL is `{domain}/admin-api/infra/file/{configId}/get/{encodeUrlPath(path)}`. `configId` comes from the path variable; `path` is everything after the **first** `/get/` in the raw `request.getRequestURI()` (`subAfter(..., false)`), then URL-decoded. It is **not** tenant-scoped and **not** authenticated — any anonymous caller who knows `configId + path` gets the bytes. Confirmed by `.../framework/security/config/SecurityConfiguration.java` L32:

```java
registry.requestMatchers(buildAdminApi("/infra/file/*/get/**")).permitAll();
```

Guardrails are path-validation only (`FilePathUtils.validatePath` in `getFileContent`, plus `LocalFileClient.getFilePath` re-normalizing against `basePath`). `FileDO` itself is `@TenantIgnore`.

Upload endpoint: `POST /admin-api/infra/file/upload`, `multipart/form-data`, field `file` (+ optional `directory`, validated by `FileUploadReqVO.isDirectoryValid()`), reads bytes with `IoUtil.readBytes`, calls `fileService.createFile(content, file.getOriginalFilename(), directory, file.getContentType())`, returns `CommonResult<String>` whose `data` is the access URL.

Mirror App controller: `.../controller/app/file/AppFileController.java`, `@RequestMapping("/infra/file")` → `/app-api/...`, with `POST /upload` and `POST /create` marked `@PermitAll` (anonymous upload!), `GET /presigned-url` not annotated.

## 4. `FileClient` and implementations

Location is **inside the infra module**, not a starter: `yudao-module-infra/.../infra/framework/file/core/client/`.

```java
public interface FileClient {
    Long getId();
    String upload(byte[] content, String path, String type) throws Exception;
    void delete(String path) throws Exception;
    byte[] getContent(String path) throws Exception;
    default String presignPutUrl(String path) { throw new UnsupportedOperationException("不支持的操作"); }
    default String presignGetUrl(String url, Integer expirationSeconds) { throw new UnsupportedOperationException("不支持的操作"); }
}
```

- `AbstractFileClient<Config extends FileClientConfig>`: holds `id`, `config`, `originalConfig`; `init()`/`doInit()`/`refresh(Config)`; `protected String formatFileUrl(String domain, String path)` (snippet above).
- `db/DBFileClient`: `upload` inserts `FileContentDO(configId, path, content)` into `infra_file_content` and returns `formatFileUrl(config.getDomain(), path)`; `getContent` selects by `(configId, path)`, sorts by id, returns the last; `delete` deletes by `(configId, path)`. Does **not** support presign.
- `local/LocalFileClient`: `FileUtil.writeBytes` under `config.getBasePath()`; `getFilePath` normalizes and throws `FILE_PATH_INVALID` if the resolved path escapes `basePath`; `getContent` returns `null` on "File not exist:". No presign.
- `s3/S3FileClient`: AWS SDK v2 `S3Client` + `S3Presigner`, `EXPIRATION_DEFAULT = Duration.ofHours(24)`. `upload` → `putObject` then returns `presignGetUrl(path, null)`. `presignGetUrl` returns an unsigned `domain/path` when `enablePublicAccess` is not `false`, otherwise a signed GET URL. `presignPutUrl` = 24h PUT presign.
- Others: `ftp/FtpFileClient`, `sftp/SftpFileClient`. Registry: `FileStorageEnum` — `DB(1)`, `LOCAL(10)`, `FTP(11)`, `SFTP(12)`, `S3(20)`.

Seeded `infra_file_config` rows in `sql/mysql/ruoyi-vue-pro.sql` (L287-297) — `(id, name, storage, master)`:

| id | name | storage | master | config `@class` (key fields) |
|---|---|---|---|---|
| 4 | 数据库（示例） | 1 | b'0' | `db.DBFileClientConfig` `domain=http://127.0.0.1:48080` |
| 22 | 七牛存储器（示例） | 20 | b'0' | `s3.S3FileClientConfig` bucket `ruoyi-vue-pro`, `enablePublicAccess:true` |
| 24 | 腾讯云存储（示例） | 20 | b'0' | S3, `enablePublicAccess:true` |
| 25 | 阿里云存储（示例） | 20 | b'0' | S3, `enablePublicAccess:true` |
| 26 | 火山云存储（示例） | 20 | b'0' | S3, `domain:null` |
| 27 | 华为云存储（示例） | 20 | b'0' | S3 |
| 28 | MinIO 存储（示例） | 20 | b'0' | S3, `domain=http://127.0.0.1:9000/yudao` |
| 29 | 本地存储（示例） | 10 | b'0' | `local.LocalFileClientConfig` `basePath=/Users/yunai/tmp/file`, `domain=http://127.0.0.1:48080` |
| 30 | SFTP 存储（示例） | 12 | b'0' | `sftp.SftpFileClientConfig` |
| 34 | 七牛云存储【私有】（示例） | 20 | b'0' | S3, `enablePublicAccess:false` (private bucket demo) |
| 35 | `1` | 20 | **b'1'** | S3, `endpoint=http://www.baidu.com`, `domain=http://www.xxx.com`, bucket `1`, ak `2`/sk `3`, `enablePublicAccess:false` |

The seeded **master is id 35** — a junk/placeholder S3 config with fake credentials. Uploads will fail out-of-the-box until master is switched (e.g. to 4 or 29).

## 5. `FileDO` columns, mapper, private-file support

`.../dal/dataobject/file/FileDO.java`: `@TableName("infra_file")`, `@KeySequence("infra_file_seq")`, `@TenantIgnore`, extends `BaseDO`.
Fields: `Long id`, `Long configId`, `String name`, `String path`, `String url`, `String type`, `Long size` (+ `BaseDO`: `creator`, `createTime`, `updater`, `updateTime`, `deleted`).

DDL `sql/mysql/ruoyi-vue-pro.sql` L241-256: `id bigint AI`, `config_id bigint NULL`, `name varchar(256) NULL`, `path varchar(512) NOT NULL`, `url varchar(1024) NOT NULL`, `type varchar(128) NULL`, `size int NOT NULL`, `creator varchar(64)`, `create_time datetime`, `updater varchar(64)`, `update_time datetime`, `deleted bit(1)`. **No tenant_id column.** No index on `path`/`url`.

`infra_file_content`: `id`, `config_id bigint NOT NULL`, `path varchar(512) NOT NULL`, `content mediumblob NOT NULL`, audit cols, `INDEX idx_config_id_path(config_id, path)`.

`FileMapper extends BaseMapperX<FileDO>` — two default methods: `selectPage(FilePageReqVO)` (like on `path`/`type`, between `createTime`, order by id desc) and `selectLatestByConfigIdAndPath(Long configId, String path)` (`selectLastOne`, ordered asc by id).

`FileService.getFile(Long id)` exists (→ `GET /infra/file/get`, permission `infra:file:query`).

Private-file / signed-url feature: **only** the S3-level one — `S3FileClientConfig.enablePublicAccess=false` + `presignGetUrl`/`presignPutUrl`, surfaced as `FileApi.presignGetUrl(url, expirationSeconds)`. There is **no** per-file ACL, no owner column, no token-based access check on `/{configId}/get/**`, and no signed-url mechanism for db/local/ftp/sftp clients. No other module currently calls `presignGetUrl`.

## 6. `FileUtils`, digests, hutool

- `cn.iocoder.yudao.framework.common.util.io.FileUtils` — `yudao-framework/yudao-common/src/main/java/cn/iocoder/yudao/framework/common/util/io/FileUtils.java`. Only three methods: `static File createTempFile(String data)`, `static File createTempFile(byte[] data)`, `static File createTempFile()` (UUID name, `deleteOnExit()`). **No `getMimeType` here.**
- MIME lives in `cn.iocoder.yudao.module.infra.framework.file.core.utils.FileTypeUtils`: `getMineType(byte[])`, `getMineType(String name)`, `getMineType(byte[] data, String name)` (Apache Tika `TIKA.detect`), `getExtension(String mineType)`, `isImage(String mineType)`, `writeAttachment(HttpServletResponse, String filename, byte[] content)` (sets `Content-Disposition: inline` for images, `attachment` otherwise, RFC 5987 `filename*=UTF-8''`).
- Path safety: `cn.iocoder.yudao.module.infra.framework.file.core.utils.FilePathUtils` — `validateFileName(String)`, `isDirectoryValid(String)`, `validateDirectory(String)`, `validatePath(String)`; blocks leading `/`/`\`, backslashes, NUL, Windows drive letters, absolute paths, and `.`/`..`/empty segments.
- Digests: hutool `cn.hutool.crypto.digest.DigestUtil`. sha256 of content used as filename fallback in `FileServiceImpl` L83 (`DigestUtil.sha256Hex(content)`); other uses: `ApiSignatureAspect` (`sha256Hex`), `IotOtaFirmwareServiceImpl` (`DigestUtil.digester(alg).digestHex(...)`), `Kd100ExpressClient` (`DigestUtil.md5`), SMS clients (`sha256Hex`, `DigestUtil.hmac(HmacAlgorithm.HmacSHA256, ...)`). No custom digest util in the framework.
- Hutool versions (`yudao-dependencies/pom.xml` L57-58): `hutool-5.version = 5.8.46` (`cn.hutool:hutool-all`) and `hutool-6.version = 6.0.0-M22` (`org.dromara.hutool:hutool-extra` only).

## 7. Frontend upload & download

`src/components/UploadFile/src/UploadFile.vue` props:

```ts
modelValue: propTypes.oneOfType<string | string[]>([String, Array<String>]).isRequired,
fileType:   propTypes.array.def(['doc','xls','ppt','txt','pdf']),
fileSize:   propTypes.number.def(5),      // MB
limit:      propTypes.number.def(5),
autoUpload: propTypes.bool.def(true),
drag:       propTypes.bool.def(false),
isShowTip:  propTypes.bool.def(true),
disabled:   propTypes.bool.def(false),
directory:  propTypes.string.def(undefined)
```

- Emits `update:modelValue`. `el-upload` uses `:http-request="httpRequest"` from `useUpload(props.directory)` (`src/components/UploadFile/src/useUpload.ts`), `name="file"`, `:multiple="props.limit > 1"`.
- `useUpload(directory?)` returns `{ uploadUrl, httpRequest }`. `getUploadUrl()` = `import.meta.env.VITE_BASE_URL + import.meta.env.VITE_API_URL + '/infra/file/upload'`.
  - Mode `VITE_UPLOAD_TYPE === 'client'`: `FileApi.getFilePresignedUrl(fileName, directory)` → raw `axios.put(presignedInfo.uploadUrl, file, {headers:{'Content-Type': file.type || 'application/octet-stream'}})` → then fire-and-forget `FileApi.createFile({configId, url, path, name, type, size})`, and resolves `{ data: presignedInfo.url }` to mimic the server response shape.
  - Mode `server` (default): `FileApi.updateFile({ file: options.file, directory }, uploadProgressHandler)`, resolves when `res.code === 0`.
- `handleFileSuccess(res)` reads `res.data` as the **URL string**, pushes `{ name: response.data, url: response.data }`; `emitUpdateModelValue()` emits a `string[]`, or a comma-joined string when `limit === 1` or `modelValue` is a string. The watch on `modelValue` splits on `,` and derives display names via `url.substring(url.lastIndexOf('/') + 1)`. Download in the list is a plain `<el-link :href="file.url" download target="_blank">` — i.e. the browser hits the `@PermitAll` `/{configId}/get/**` URL directly with no auth header.

`src/api/infra/file/index.ts`:

```ts
export interface FilePresignedUrlRespVO { configId: number; uploadUrl: string; url: string; path: string }
export const getFilePage      = (params: PageParam) => request.get({ url: '/infra/file/page', params })
export const deleteFile       = (id: number) => request.delete({ url: '/infra/file/delete?id=' + id })
export const deleteFileList   = (ids: number[]) => request.delete({ url: '/infra/file/delete-list', params: { ids: ids.join(',') } })
export const getFilePresignedUrl = (name: string, directory?: string) => request.get<FilePresignedUrlRespVO>({ url: '/infra/file/presigned-url', params: { name, directory } })
export const createFile       = (data: any) => request.post({ url: '/infra/file/create', data })
export const updateFile       = (data: any, onUploadProgress?: Function) => request.upload({ url: '/infra/file/upload', data, onUploadProgress })
```

Note `updateFile` (sic) is the upload helper; there is no separate `upload` export.

`src/config/axios/index.ts`:

```ts
download: async <T = any>(option: any) => {
  const res = await request({ method: 'GET', responseType: 'blob', ...option })
  return res as unknown as Promise<T>
},
upload: async <T = any>(option: any) => {
  option.headersType = 'multipart/form-data'
  const res = await request({ method: 'POST', ...option })
  return res as unknown as Promise<T>
}
```

`get/post/put/delete` unwrap `res.data`; `download`/`upload` return the full response object (hence `res.code === 0` checks in `useUpload`). `src/config/axios/service.ts` L137-138 short-circuits the response interceptor when `responseType` is `blob`/`arraybuffer`, so the raw Blob reaches the caller.

`src/utils/download.ts` — default-exported object, no named exports:

```ts
excel(data: Blob, fileName: string)          // application/vnd.ms-excel
word(data: Blob, fileName: string)           // application/msword
zip(data: Blob, fileName: string)            // application/zip
html(data: Blob, fileName: string)           // text/html
markdown(data: Blob, fileName: string)       // text/markdown
json(data: Blob, fileName: string)           // application/json
image({ url, canvasWidth?, canvasHeight?, drawWithImageSize? })  // canvas re-encode to PNG
base64Image(base64: string, fileName: string)
base64ToFile(base64: any, fileName: string): File
```

Private `download0(data, fileName, mineType)` builds a Blob → `URL.createObjectURL` → synthetic `<a download>` click → `revokeObjectURL`. Standard call site pattern (e.g. `src/views/crm/product/index.vue` L213-214):

```ts
const data = await ProductApi.exportProduct(queryParams)   // request.download(...) -> Blob
download.excel(data, '产品.xls')
```