## Why

原型以 MySQL 连接器模拟 OceanBase、PostgreSQL 连接器模拟 Greenplum，但**没有真实达梦（DM）数据源**。为体现国产数据库适配能力与跨 4 源联邦查询，需要接入真实达梦。

技术前提（已验证）：Trino **没有通用 `jdbc` 连接器**，`trino-base-jdbc` 是库而非可加载插件；达梦官方虽有 Trino 连接器插件但仅适配 **Trino 435**，与本平台 483 的 SPI 严格不匹配；本仓库也不含达梦连接器。因此必须**自研 `trino-dameng` 连接器**。

## What Changes

- 新增独立工程 **`data-fabric/trino-dameng`**，继承 `trino-base-jdbc`，实现达梦连接器（插件、工厂、JdbcClient、模块与类型映射）。
- 连接器按 **Trino 483 SPI** 编译，作为**插件**加载，**不修改 Trino 内核**。
- 部署：Trino 镜像固定 `trinodb/trino:483`，将插件目录**挂载**进 `/usr/lib/trino/plugin/trino-dameng`（无需重建镜像）；新增 `dm8` 服务（端口 5236）接入 `data-fabric-net`。
- 平台接入：新增 `BusinessType.DAMENG`，`DataSourceService` 增加 `jdbc:dm://` 连接串分支。
- 端到端验证：跨 4 源（OceanBase/Greenplum/达梦/…）JOIN。

## Capabilities

### New Capabilities

- `dameng-connector`: 通过自研插件连接器以联邦方式访问真实达梦数据库。

### Modified Capabilities

- `data-source-management`: 新增「达梦」业务类型与连接串构造。

## Impact

- **Trino 模块**：`data-fabric/trino-dameng`（不进入 Trino reactor，依赖 Maven Central 的 `io.trino:*:483`）。
- **部署**：`data-fabric/deploy/docker-compose.yml`（镜像锁定 + 插件挂载 + `dm8` 服务）；`DmJdbcDriver18.jar` 通过 `install-file` 进入构建 `.m2`。
- **后端**：`BusinessType`、`DataSourceService`。
- **前置依赖**：`align-trino-483-baseline`（版本一致）。
- **风险**：达梦 `information_schema` 行为、标识符大小写、类型映射、授权（`dm.key`）。
