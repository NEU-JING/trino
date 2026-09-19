## Why

本仓库当前源码基线为 **484-SNAPSHOT（未发布）**，而平台实际运行与依赖版本为 **Trino 483 正式版**：`data-fabric/backend/pom.xml` 已固定 `trino.version=483`，部署镜像 `trinodb/trino:latest` 当前也解析为 483。源码树、运行时镜像、后端客户端与计划自研的达梦连接器 SPI 版本不一致，存在「本地能编译、部署报 SPI 不匹配」的漂移风险，并可能在 484 发布后被 `latest` 意外升级。

## What Changes

- 将 Trino 引擎源码基线从 484-SNAPSHOT 迁移到 **483 正式版 tag**，保留 `data-fabric/` 与 `openspec/` 的既有成果。
- 将 `data-fabric/deploy/docker-compose.yml` 的 Trino 镜像从 `trinodb/trino:latest` **固定为 `trinodb/trino:483`**。
- 建立「源码基线 / 运行时镜像 / 后端 `trino-client` / 连接器 SPI」四者一致的版本基线。

## Capabilities

### New Capabilities

- `version-alignment`: 平台各层的 Trino 版本统一为 483 正式版并保持可验证的一致性。

### Modified Capabilities

<!-- 无 requirement 级行为变更，仅为基线/工程一致性。 -->

## Impact

- **本仓库**：根 `pom.xml` 基线及各模块源码回退到 483；`data-fabric/`、`openspec/` 保留。
- **部署**：`data-fabric/deploy/docker-compose.yml` 镜像锁定 483。
- **后续变更**：`add-dameng-connector` 的连接器必须按 483 编译，依赖本变更先完成。
- **不涉及**：平台功能行为不变。
