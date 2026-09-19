## Why

需要一个面向最终用户的**数据编织（Data Fabric）平台原型**，把多个异构数据库统一成一个可查询的逻辑视图。用户不直接接触底层数据库，而是通过平台浏览/搜索已注册的数据表、在线编写 SQL，由平台把查询下推到各数据源并汇聚结果。

原型的目标是验证核心可行性：**跨多个数据库的组合查询**、**表级权限管控**、以及**面向两类角色的自助操作流程**。当前仓库（Trino）已具备联邦查询与下推引擎，本变更在其之上构建平台层。

## What Changes

- 新增一个**平台后端服务**（面向用户的 REST API + 自有元数据库），负责用户/角色、数据源注册、表目录、权限规则、查询网关与结果导出。
- 新增一个**前端应用**，包含运营人员视图（数据源管理、表注册、权限配置）与查询人员视图（表目录、SQL 编辑器、结果表格、导出）。
- **数据源在线注册**：运营人员通过界面填写连接信息，平台后端以动态 Catalog 方式（`CREATE CATALOG` / `DROP CATALOG`）接入 Trino，重启后保留。
- **数据源展示别名**：原型阶段用 MySQL 连接器模拟 **OceanBase**、PostgreSQL 连接器模拟 **Greenplum**，在平台界面统一以 `OceanBase` / `Greenplum` 呈现，不向用户暴露底层连接器名称。
- **表目录**：运营人员从数据源发现结果中挑选并注册"可查询表"（含名称、备注），查询人员可浏览、按关键字搜索。
- **表级权限**：运营人员为角色/用户配置表级（及可选列级）可读权限，由 Trino 的 `SystemAccessControl` 在查询执行时强制执行。
- **SQL 查询**：查询人员在线提交 SQL，平台后端经 Trino 执行并流式返回结果，以表格呈现；支持跨库 JOIN 与谓词下推。
- **结果导出**：查询结果支持导出 CSV（首版）与 Excel（xlsx）。
- 建立**测试驱动开发（TDD）**与**远端环境部署验证**流程。

## Capabilities

### New Capabilities

- `data-source-management`: 运营人员在线注册/更新/停用数据源，并在界面上以业务别名（OceanBase、Greenplum）展示。
- `table-catalog`: 运营人员注册可查询表及其描述，查询人员浏览与搜索当前可用的数据表。
- `table-access-control`: 运营人员配置表级访问权限，查询执行时强制生效，且越权访问被拒绝。
- `sql-query-execution`: 查询人员在线编写并提交 SQL，支持跨数据源组合查询，结果以表格呈现。
- `query-result-export`: 查询结果可导出为 CSV 与 Excel 文件。

### Modified Capabilities

<!-- 无：本仓库当前 openspec/specs 为空，未修改既有能力。 -->

## Impact

- **分支策略**：本变更**不直接在 `master` 上开发**，而是从 `master` 创建独立特性分支（`feature/build-data-fabric-prototype`）进行延伸，全部提交与验证发生在该分支。
- **本仓库（Trino）**：原型以 Trino 作为查询引擎，主要通过配置与连接器使用，预计不修改引擎核心代码；若确需改动（如自定义连接器或访问控制），将作为本变更的受影响代码列出。
- **新增平台代码**：平台后端服务、前端应用、平台元数据库 schema、访问控制规则生成端点。这些代码将作为本仓库内的新模块/目录引入。
- **数据源连接器**：原型阶段使用 `trino-mysql`（模拟 OceanBase）与 `trino-postgresql`（模拟 Greenplum）。后续接入真实达梦/OceanBase/Greenplum 连接器时沿用同一平台层，无需改动平台交互模型。
- **部署环境**：开发验证部署到远端 Ubuntu 24 服务器（`/home/trino` 路径下），数据库实例通过该服务器上的 Docker 运行。远端地址与凭据**不写入仓库**，通过环境变量/本地私有配置提供。
  - 已有 PostgreSQL 实例：最新版本 → 平台应用支撑库；PostgreSQL 9.4 → 模拟 Greenplum 内核版本的数据源。
- **测试**：采用 TDD，测试与生产代码同等质量标准（遵循仓库 `.github/DEVELOPMENT.md`）。
- **不包含（Non-goals）**：真实达梦/OceanBase(MySQL/Oracle 双模式)/Greenplum 连接器实现、生产级高可用部署、SSO/企业级认证、数据血缘与治理、缓存/物化层。
