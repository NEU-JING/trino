## Context

Trino 是一个成熟的联邦查询引擎：跨 Catalog 查询、谓词下推、动态 Catalog、`SystemAccessControl` 等能力已内置。本原型不改造成熟的引擎，而是**在其之上构建一个面向最终用户的平台层**，把运营治理能力与查询自助能力产品化。

约束与既有条件：

- 原型必须支持**跨多个数据库的组合查询**。模拟映射：MySQL 连接器 → 业务名 **OceanBase**；PostgreSQL 连接器 → 业务名 **Greenplum**。
- 远端 Ubuntu 24 服务器用于开发期部署验证，部署路径 `/home/trino`；数据库通过该服务器上的 Docker 运行。
- 已有两个 PostgreSQL 实例：最新版本作为**平台应用支撑库**；PostgreSQL 9.4（对应 Greenplum 内核版本）作为**模拟 Greenplum 的数据源**。
- 采用**测试驱动开发（TDD）**，测试与生产代码同等质量。
- 远端地址与凭据**不写入仓库**，通过环境变量/本地私有配置注入。

已确认的技术栈决策：后端 **Java + Spring Boot**，前端 **React + Vite**，部署形态为**同机独立进程 + docker-compose**。

## Goals / Non-Goals

**Goals:**

- 打通「运营配置数据源 → 注册表 → 授权 → 查询人员检索/写 SQL → 跨源查询 → 表格展示 → 导出」的完整闭环。
- 权限在 Trino 查询执行层强制生效，能拦截任何形式的越权 SQL（JOIN/子查询/CTE）。
- 数据源对外只暴露业务别名 OceanBase / Greenplum。
- 以 TDD 建立可回归的测试基线，并可部署到远端服务器验证。

**Non-Goals:**

- 实现真实的达梦 / OceanBase / Greenplum 连接器（后续独立变更）。
- 生产级高可用、多 coordinator/worker 集群、SSO/企业级认证。
- 缓存 / 物化 / 数据血缘 / 数据治理。
- 列级权限（首版仅表级，接口预留列级扩展）。

## Decisions

### D1. 以 Trino 作为联邦查询内核，平台层不自行拆解 SQL

平台不解析用户 SQL 去"分库改写"，而是把 SQL 交给 Trino。Trino 负责逻辑计划、跨 Catalog JOIN、谓词下推与执行。平台只负责治理、身份、展示与导出。

- **理由**：自行实现多方言 SQL 拆解与下推的复杂度与正确性风险极高；Trino 已具备所需优化器与连接器下推框架。
- **备选**：自研 SQL 拆解器（否决，重复造轮子且难以保证正确性）；用多个引擎分别查询再由后端合并（否决，无法下推过滤、性能差）。

### D2. 架构：独立平台后端 + React 前端 + 独立 Trino，docker-compose 编排

```
┌──────── React 前端 (查询人员 / 运营人员) ────────┐
│ 数据源管理 │ 表注册 │ 权限配置                   │
│ 表目录/搜索 │ SQL 编辑器 │ 结果表格 │ CSV/Excel  │
└───────────────────────┬─────────────────────────┘
                        │ REST/JSON
┌───────────────────────▼─────────────────────────┐
│ 平台后端 (Spring Boot)                            │
│  · 用户/角色            · 表注册表                  │
│  · 数据源注册 → CREATE CATALOG                     │
│  · 权限规则 → 生成 JSON                            │
│  · 查询网关 (io.trino:trino-client)               │
│  · 导出 CSV/XLSX (流式)                            │
└───┬───────────────────────────────┬─────────────┘
    │ StatementClient               │ 规则 JSON (HTTP)
┌───▼───────────────────────────────▼─────────────┐
│ Trino Server (单 coordinator)                     │
│  · 动态 Catalog (mysql→OceanBase, pg→Greenplum)   │
│  · access-control.config-file = http://backend/...│
│  · 谓词下推 + 跨库 JOIN                            │
└───────────────────────────────────────────────────┘
        │                    │
   [MySQL 容器]        [PostgreSQL 容器]
   (OceanBase 模拟)     (Greenplum 模拟, PG 9.4)
```

- **理由**：Trino 与平台进程边界清晰；Spring Boot 可直接复用官方 `io.trino:trino-client`；后续可加 worker 扩展而不改平台。
- **端口约定**：可用端口段 `8001-8004`；平台后端监听 `8001`，其余（`8002-8004`）预留。Trino 监听 `8080`，前端开发服务器 `5173`（`/api` 代理到 `8001`）。
- **备选**：嵌入式 Trino（否决，Trino 非为嵌入式设计）；平台做成 Trino 插件（否决，自定义 UX 与服务不便）。

### D3. 平台代码位置：`data-fabric/` 独立 Maven 工程，不进入 Trino 根 reactor

在仓库根新增 `data-fabric/` 目录，包含 `backend/`（Spring Boot，独立 `pom.xml`，parent 为 `spring-boot-starter-parent`）与 `frontend/`（React + Vite）。**不加入** Trino 根 `pom.xml` 的 `<modules>`。

- **理由**：避免平台工程继承 Trino 的 airbase/enforcer/长构建与发布约束；保持解耦但同仓便于协作。
- **备选**：作为 Trino 子模块（否决，工具链冲突）；独立仓库（暂不采用，增加协作成本）。

### D4. 数据源注册：动态 Catalog + 平台元数据

运营人员提交连接信息后，平台后端执行 `CREATE CATALOG <id> USING <connector> WITH (...)` 接入 Trino；连接器选择由业务类型映射决定（OceanBase→`mysql`，Greenplum→`postgresql`）。`DROP CATALOG` 用于停用。Trino 侧启用 `catalog.store=file` 持久化，重启保留。平台元数据库记录数据源的业务展示名、类型、创建人等元数据。

- **理由**：动态 Catalog 是 Trino 内置能力，无需重启即可接入数据源。
- **备选**：静态 `etc/catalog/*.properties`（否决，无法在线配置）。

### D5. 业务别名在平台层映射，不重命名底层连接器

平台元数据保存 `business_type`（OceanBase/Greenplum）与底层 `connector_name` 的映射，所有面向用户的界面/API 只返回 `business_type`。

- **理由**：底层连接器仍需按真实数据库类型选择；别名是展示层概念，与连接器解耦，后续接入真实达梦/OceanBase/Greenplum 连接器时只改映射。

### D6. 表注册：平台策展目录 + 引擎发现

运营人员从 `information_schema`（或 `SHOW TABLES`）发现结果中选择表，写入平台注册表并附描述。查询人员看到的"可用表" = 已注册 ∩ 有权限。

- **理由**：Trino 自动发现源库表，但"可查询范围"需要平台策展；描述与搜索是平台增值能力。
- **备选**：直接暴露源库所有表（否决，无法管控与检索）；用 Trino View 承载（暂不采用，原型无需）。

### D7. 权限在 Trino 执行层强制，规则经 HTTP 在线下发

实现方式：平台后端将权限规则生成 JSON，通过 HTTP 端点提供；Trino 配置 `access-control.config-file=http://<backend>/api/access-control/rules` 并设置 `access-control.refresh-period`。规则形如按用户/角色对 `catalog.schema.table` 的 `select` 授权；未授权即拒绝。表目录使用 `filterTables/filterCatalogs` 自动过滤。

- **理由**：由引擎强制鉴权能拦截任何构造方式的越权 SQL；后端"先检查再提交"无法可靠解析任意 SQL 访问了哪些表。HTTP 规则源 + 定时刷新实现**不重启在线生效**，且无需编写 Java 插件。
- **备选**：后端拦截（否决，不可靠）；自定义 `SystemAccessControl` 插件（暂不采用，增加部署复杂度）。
- **说明**：规则文件格式遵循 `FileBasedSystemAccessControl` 的 JSON schema。

### D8. 身份模型：平台用户 → Trino 服务账号 + 审计头

平台后端以单一 Trino 服务账号提交所有查询（connect 时使用服务身份），同时在 `X-Trino-User` 头传递真实平台用户名用于审计与权限判定。平台自身负责用户登录与角色（运营/查询）。

- **理由**：原型阶段避免引入完整的企业认证链；权限判定按规则文件中的平台用户名进行，足够验证核心能力。
- **备选**：接入 `PasswordAuthenticator` + 身份透传（后续增强）。

### D9. 查询执行：官方客户端流式拉取

后端使用 `io.trino:trino-client` 的 `StatementClient` 逐页消费结果（`ResultRows`），向前端流式推送；支持取消（关闭 StatementClient）与超时。

- **理由**：官方客户端与协议同源，支持分页、取消、类型信息。
- **备选**：JDBC 驱动（可行但流式与取消控制不如原生客户端）。

### D10. 导出：CSV 先行（UTF-8 BOM），Excel 用流式 xlsx

首版导出 CSV 并写 UTF-8 BOM（保证 Excel 中文不乱码）；Excel 导出用 Apache POI 的 SXSSF 流式写 `.xlsx`。导出与查询共用同一结果流，避免二次查询与整表入内存。

- **理由**：CSV-BOM 零依赖即可满足大多数场景；流式写避免大结果集 OOM。

### D11. TDD 与测试分层

- 后端单元/集成测试用 JUnit 5 + AssertJ（遵循仓库约定，禁用 mock 库，手写测试替身）。
- 测试分层：单元测试（权限规则生成、别名映射、导出格式）→ 组件测试（Testcontainers 起 MySQL/PG）→ 端到端（对远端 Trino + 已部署数据库验证 spec 中的场景）。
- 先写失败测试（红），再实现（绿），再重构。spec 中的每个 Scenario 对应至少一个测试。

### D12. 远端部署与凭据管理

- 远端 Ubuntu 24，部署路径 `/home/trino`，使用 docker-compose 编排 Trino、平台后端、MySQL、PostgreSQL。
- 远端地址与凭据通过环境变量 / 本地未跟踪的私有配置提供；**不入库**。
- 应用支撑库使用远端最新版 PostgreSQL；数据源使用 PG 9.4 实例模拟 Greenplum。
- 实现澄清：两个 PostgreSQL 实例为**预置外部容器**（`pg-latest`、`postgres94`），已接入自定义网络 `data-fabric-net`；compose 仅管理 Trino、MySQL（OceanBase 模拟）与平台后端，不重建现有 PG 容器。

### D13. 独立特性分支开发

本次调整从 `master` 创建并切换到独立特性分支（建议命名 `feature/build-data-fabric-prototype`），后续所有开发、提交与验证均在该分支进行，不直接基于/改动 `master`。

- **理由**：与 Trino 的贡献流程一致（改动经特性分支提交，`master` 作为稳定集成分支）；便于隔离原型开发，且 CI/本地可用 `-P gib` 针对分支与 `master` 的差异做增量构建与测试（见根 `AGENTS.md`）。
- **备选**：直接在 `master` 开发（否决，违反仓库工作流且污染稳定分支）。
- **备注**：分支仅承载平台与配套配置；不修改 Trino 引擎核心代码，降低与上游合并的冲突面。

## Risks / Trade-offs

- **[动态 Catalog 中数据源密码明文落盘]**（FileCatalogStore）→ 原型接受；通过限制文件权限、后续引入密钥管理缓解。
- **[权限规则刷新延迟窗口]** → 采用较短的 `refresh-period`；在设计与文档中明确最终一致性窗口。
- **[跨源 JOIN 性能]** → 依赖谓词下推与动态过滤；对大表 JOIN 在文档中给出提示，必要时提示用户加过滤条件。
- **[模拟不保真]** MySQL≈OceanBase、PG9.4≈Greenplum 仅覆盖常见 SQL/类型；个别语法与类型差异会推迟到接入真实连接器时暴露 → 用平台层隔离，连接器替换时不改平台交互。
- **[任意 SQL 的安全面]** → 原型仅允许只读语义、限制语句类型、设置超时；服务账号权限最小化。
- **[在 Windows 上开发、Linux 上部署]** → 以远端 Linux 为验证基线；本地以模块级测试为主（参见根 `AGENTS.md` 的环境约束）。

## Migration Plan

新建能力，无存量迁移。部署步骤：

1. 在远端 `/home/trino` 准备 `docker-compose.yml` 与 `.env`（凭据）。
2. 启动 Trino（配置 `catalog.store=file`、`access-control.config-file` 指向后端）。
3. 启动平台后端（连接应用支撑库，初始化 schema）。
4. 启动前端（或由后端托管静态资源）。
5. 通过运营界面注册数据源、注册表、配置权限；查询界面验证闭环。

回滚：停止并移除 `data-fabric` 相关容器；Trino 侧删除对应动态 Catalog 与访问控制配置即可，不影响 Trino 引擎本身。

## Open Questions

- 权限规则刷新周期的取值（建议 5–30s），以及目录过滤的一致性要求。
- 数据源密码的存储方式（明文/加密/外部密钥），首版接受明文但需记录为技术债。
- 是否限制仅允许 `SELECT` 类语句，以及并发查询数、单查询超时/行数上限。
- 平台用户认证方式（首版本地账号？）与审计留存要求。
- 表级权限是否需要在首版即支持"按角色"批量授权（否则仅按用户）。

## Resolved Decisions & Technical Debt（实现后回填）

**已定稿**
- 刷新周期：`security.refresh-period=10s`，权限变更在线生效（E2E 已验证）。
- 认证：首版本地账号，口令用 PBKDF2 加盐哈希；登录返回内存态 Bearer Token，并支持 HTTP Basic。
- 角色授权：支持 `principal_type=ROLE`，生成规则时展开为该平台角色的所有启用用户。
- 查询限制：`max-rows=10000`（超出截断并提示）、`timeout=30s`（超时终止）、线程数 `4`。
- 数据源别名：MySQL→OceanBase、PostgreSQL→Greenplum，API 不暴露底层连接器名。
- 权限执行点：Trino `FileBasedSystemAccessControl`（HTTP 规则源），非平台层拦截。
- 服务账号：`trino_service` 拥有 catalog `owner` 与表全权限（用于建/删 Catalog、探测、E2E 建数据）。

**技术债（原型接受，后续处理）**
- 数据源密码在平台元数据库与 Trino catalog store 中**明文存储**；应引入密钥管理/加密。
- Token 为内存态，服务重启即失效；无刷新/撤销列表，应改为持久化会话或 JWT。
- 规则端点免鉴权（供 Trino 拉取），需限制为仅集群网络可达。
- 未限制仅 `SELECT` 语句类型；服务账号具备写权限，应细化。
- 查询结果保存在后端内存中，供导出复用；大结果集需改为真正的流式直传。
- 前端无鉴权路由守卫与浏览器级 E2E（可引入 Playwright）。
- `admin` 等账号默认口令为弱口令，生产需强制修改。

