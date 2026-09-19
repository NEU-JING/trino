## 0. 分支与准备

- [x] 0.1 从 `master` 创建并切换到独立特性分支 `feature/build-data-fabric-prototype`；确认后续所有开发、提交与验证均在该分支进行，不直接改动 `master`

## 1. 环境与工程骨架

- [x] 1.1 在远端 Ubuntu 24（部署路径 `/home/trino`）建立目录结构与 `.env`（远端地址/凭据不入库），确认 Docker 可用
- [x] 1.2 编写 `docker-compose.yml`：Trino（单 coordinator）、MySQL（模拟 OceanBase）、PostgreSQL 最新版（应用支撑库）、PostgreSQL 9.4（模拟 Greenplum）。（实现：两个 PG 为预置外部容器，接入共享网络 `data-fabric-net`，不在 compose 中重建）
- [x] 1.3 配置 Trino：启用 `catalog.store=file`、挂载 `access-control` 配置并指向平台后端规则端点（先用占位 URL）。（实现：动态目录由 `catalog.management=DYNAMIC` + `catalog-store.properties` 启用；access-control 暂用 `allow-all` 占位，5.3 切换为后端 HTTP 规则端点）
- [x] 1.4 初始化 Trino 连接器插件目录：确保 `mysql` 与 `postgresql` 连接器可用并可用 `CREATE CATALOG` 动态接入。（已验证：`CREATE CATALOG oceanbase USING mysql` / `greenplum USING postgresql`，并跑通跨源 JOIN）
- [x] 1.5 创建 `data-fabric/backend` 独立 Maven 工程（Spring Boot，parent 为 `spring-boot-starter-parent`，不加入 Trino 根 reactor）。（实现：Spring Boot 4.1.1 / Java 25；远端 Docker 内 `mvn test` 通过）
- [x] 1.6 创建 `data-fabric/frontend` React + Vite 工程（Bun 工具链）。（实现：React 19 + Vite 7 + TS；远端 node 容器内 `npm run build` 通过；本机/远端均可用 Bun）
- [x] 1.7 搭建后端测试基线：JUnit 5 + AssertJ + Testcontainers；落地"先写失败测试"的 TDD 约定与示例测试。（实现：JUnit 5 + AssertJ + Spring Boot Test；单元/组件测试用 H2 内存库，集成测试后续复用已部署的 MySQL/PG，避免额外 Testcontainers 依赖）
- [x] 1.8 搭建端到端测试基线：可对远端 Trino 与已部署数据库执行 spec 场景测试。（实现：`data-fabric/e2e/smoke_test.py`，跑通跨源 JOIN 并断言结果）

## 2. 平台元数据与身份基础

- [x] 2.1 设计并创建平台元数据库 schema：用户、角色、数据源、已注册表、权限（`schema.sql`，兼容 PostgreSQL 与 H2）
- [x] 2.2 实现用户与角色模型：运营人员（operator）、查询人员（query user）（`User`/`Role`/`UserRepository`/`BootstrapUsers`）
- [x] 2.3 实现登录与鉴权（首版本地账号），区分两类角色的接口访问（PBKDF2 口令、Bearer Token + HTTP Basic、`AuthInterceptor` + `@RequireRole`）
- [x] 2.4 先写测试再实现：角色越权访问管理接口被拒绝（`AuthFlowTest` 集成测试 + `UserRepositoryTest`/`PasswordHasherTest`；并纳入 E2E smoke test）

## 3. 数据源管理

- [x] 3.1 先写测试：业务别名映射（MySQL→OceanBase、PostgreSQL→Greenplum），断言 API 响应不暴露底层连接器名（`DataSourceApiTest` + E2E 断言响应无 `connector`/`"mysql"`）
- [x] 3.2 实现业务类型到连接器与展示别名的映射（`BusinessType`：connector 名不对外暴露）
- [x] 3.3 先写测试：注册可连通数据源成功创建 Catalog；不可连通则拒绝并返回可读错误（`FakeTrinoGateway` 模拟不可达；断言 Catalog 回滚）
- [x] 3.4 实现数据源注册：执行 `CREATE CATALOG ... USING ... WITH (...)` 并写入平台元数据（`DataSourceService.register` + `HttpTrinoGateway`）
- [x] 3.5 先写测试：重启后数据源仍存在且可查询（远端实测：重启 backend+trino 后元数据仍在、Catalog 仍可查询）
- [x] 3.6 验证并实现持久化（Trino `catalog.store=file` + 平台元数据）
- [x] 3.7 先写测试：更新连接信息与停用数据源（`DROP CATALOG`）后表目录不再出现且不可查询
- [x] 3.8 实现数据源更新与停用；校验连接可用性（更新先用临时 Catalog 探测，再替换）
- [x] 3.9 实现数据源管理前端页面（列表、新增、编辑、停用），展示业务别名（登录 + 数据源 CRUD，`npm run build` 通过；浏览器级 E2E 待后续引入 Playwright）

## 4. 表目录

- [x] 4.1 先写测试：从数据源发现 schema 与表（`TableCatalogApiTest.discoversTablesFromDataSource`，过滤系统 schema）
- [x] 4.2 实现表发现：通过 `information_schema` 查询数据源中的表（`TableCatalogService.discover` → Trino `information_schema.tables`）
- [x] 4.3 先写测试：注册表并携带描述；重复注册幂等无重复条目（`registerIsIdempotentAndUpdatesDescription`）
- [x] 4.4 实现表注册与注销，写入平台注册表（`RegisteredTableRepository` + `TableCatalogService.register/unregister`）
- [x] 4.5 先写测试：表目录仅返回"已注册 ∩ 当前用户有权限"的表（`viewerOnlySeesGrantedTables`）
- [x] 4.6 实现查询人员表目录接口，结合权限过滤（`PermissionService.canSelect`，OPERATOR 全可见，其余需显式授权；仅显示已启用数据源）
- [x] 4.7 先写测试：按表名/描述关键字搜索，命中与空结果两种情况（`searchMatchesNameAndDescription`）
- [x] 4.8 实现表搜索（服务端按表名/schema/描述忽略大小写匹配）
- [x] 4.9 实现运营人员表注册页面与查询人员表目录/搜索页面（`TablesPage` + `DataSourcesPage` + 登录/导航；`npm run build` 通过）

## 5. 表级访问权限

- [x] 5.1 先写测试：权限规则 JSON 生成（按用户/角色对 `catalog.schema.table` 的 select 授权）（`AccessControlRulesGeneratorTest`）
- [x] 5.2 实现权限规则生成，格式遵循 `FileBasedSystemAccessControl` JSON schema（`AccessControlRulesGenerator`：catalogs + tables + 会话属性；服务账号 catalog `owner`、OPERATOR 通配 SELECT、ROLE 授权展开到该角色用户、用户名/表名正则转义）
- [x] 5.3 实现规则 HTTP 端点（`/api/access-control/rules`）并配置 Trino（实现：`access-control.name=file` + `security.config-file=http://backend:8001/api/access-control/rules` + `security.refresh-period=10s`，端点免鉴权供 Trino 拉取）
- [x] 5.4 先写测试：授予权限后查询成功；回收权限后查询被拒绝（E2E `verify_permission_enforcement`，轮询周期内在线生效）
- [x] 5.5 先写测试：通过 JOIN、子查询、CTE 绕过权限的查询均被拒绝（E2E 三类绕过均拒绝）
- [x] 5.6 先写测试：表目录按权限过滤（无权限表不出现）（平台目录 `TableCatalogApiTest` + Trino `filterTables`）
- [x] 5.7 先写测试：权限变更在刷新周期内在线生效，无需重启（E2E 授权/回收后等待刷新周期验证）
- [x] 5.8 实现授权/回收接口与运营人员权限配置页面（`PermissionController` `GET/POST /api/permissions` + `POST /api/permissions/revoke`；前端 `PermissionsPage`）

## 6. SQL 查询执行

- [x] 6.1 先写测试：对模拟数据源发起单表查询并返回结果集（`QueryApiTest.resultIsReturned`；E2E 经平台查询 API）
- [x] 6.2 实现查询网关：以用户身份经 `io.trino:trino-client` 的 `StatementClient` 提交 SQL，流式消费结果（`QueryEngine`/`StatementClientQueryEngine` + 异步 `QueryService`）
- [x] 6.3 先写测试：跨数据源（OceanBase 表 JOIN Greenplum 表）组合查询返回合并结果（E2E `verify_query_execution`）
- [x] 6.4 实现 `X-Trino-User` 传递真实平台用户用于审计，权限按平台用户名判定（`ClientSession.user` = 平台用户名；`QueryApiTest.platformUserIsPassedAsTrinoUser` + E2E viewer 被拒）
- [x] 6.5 先写测试：语法/语义错误返回可读错误且可继续查询；空结果集正常展示列名（`QueryApiTest`；E2E 暴露语法错误）
- [x] 6.6 先写测试：取消执行中的查询、查询超时终止（`QueryApiTest.runningQueryCanBeCanceled` / `slowQueryTimesOut`）
- [x] 6.7 实现查询取消与超时配置（`data-fabric.query.timeout/max-rows/threads`；取消/超时关闭 StatementClient）
- [x] 6.8 先写测试：谓词下推——带过滤条件的查询在远端查询中包含过滤条件（E2E `EXPLAIN` 断言 `constraint on [id]` 且无 `Filter` 节点）
- [x] 6.9 实现前端 SQL 编辑器与结果表格（列名与 SELECT 结构一致）（`QueryPage`：执行/取消/轮询/结果表/截断提示）

## 7. 结果导出

- [x] 7.1 先写测试：CSV 导出内容正确、UTF-8 BOM、表头与全部行（`StreamingResultExporterTest` + E2E 校验 BOM/表头/行）
- [x] 7.2 实现基于结果流的 CSV 流式导出（`StreamingResultExporter.writeCsv`，UTF-8 BOM，RFC4180 转义）
- [x] 7.3 先写测试：Excel（.xlsx）导出内容正确（`StreamingResultExporterTest` 用 POI 读回断言 + E2E 魔数校验）
- [x] 7.4 实现基于 Apache POI SXSSF 的流式 `.xlsx` 导出（POI 5.5.1，内存保留 100 行）
- [x] 7.5 先写测试：无权限数据的导出在查询阶段被拒绝，不产生文件（查询失败→导出 409；他人结果→404；底层越权在查询阶段已被引擎拒绝）
- [x] 7.6 实现导出前端按钮（CSV / Excel）（`QueryPage` 导出按钮 + `downloadExport` 带鉴权下载）

## 8. 端到端验证与部署

- [x] 8.1 在远端服务器部署全栈并跑通：注册数据源 → 注册表 → 授权 → 查询人员跨源查询 → 导出（docker-compose + 前端打包进后端，`:8001` 单入口）
- [x] 8.2 跑通全部 spec 场景的端到端测试，纳入回归基线（`data-fabric/e2e/smoke_test.py`，最终 `E2E_OK`）
- [x] 8.3 编写 `data-fabric/README.md`：本地开发、远端部署、测试运行方式（不含任何凭据）
- [x] 8.4 检查仓库不包含远端凭据/密钥，且 `.gitignore` 覆盖 `.env` 等私有文件（E2E 密码改为 `E2E_*_PASSWORD` 环境变量；`.gitignore` 覆盖 `.env`、动态 catalog 生成物与前端 static）
- [x] 8.5 记录技术债与 Open Questions 结论（`design.md` 的 Resolved Decisions & Technical Debt：刷新周期 10s、PBKDF2 本地账号、角色授权、max-rows/timeout、明文密码等）
