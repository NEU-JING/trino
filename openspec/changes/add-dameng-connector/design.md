## Context

- Trino 是插件架构：`PluginManager` 启动时扫描 `<trino>/plugin/*`，经 `ServiceLoader` 读取 `META-INF/services/io.trino.spi.Plugin`。连接器无需改动引擎核心。
- `Versions.checkStrictSpiVersionMatch`（`lib/trino-plugin-toolkit/.../Versions.java:33`）是**可选项**，注释说明「designed only for plugins distributed with Trino」；第三方连接器仍应按目标版本编译以避免 SPI API 漂移。
- 达梦官方插件仅 435；本平台目标 483（见 `align-trino-483-baseline`）。

## Goals / Non-Goals

**Goals:**

- 以插件方式接入真实达梦，跨 4 源可查询。
- 版本对齐 483，部署简单（官方镜像 + 插件挂载）。
- 类型映射可维护、可扩展。

**Non-Goals:**

- 不修改 Trino 内核。
- 不支持达梦写入/DDL 下推（首版只读）。
- 不构建自定义 Trino 镜像（挂载即可）。

## Decisions

### D1. 独立插件工程 `data-fabric/trino-dameng`（而非放入 Trino 树）

依赖 Maven Central 的 `io.trino:trino-spi:483`、`trino-base-jdbc:483`、`trino-plugin-toolkit:483`。
优点：与 `data-fabric` 独立定位一致；无需构建 Trino 源码；SPI 精确等于 483。
- **备选**：放入 `plugin/trino-dameng`（复用 Trino parent pom，但需 483 源码树与 reactor 构建）。

### D2. 插件加载与部署：挂载，不重建镜像

```
trino:
  image: trinodb/trino:483
  volumes:
    - ./trino/plugin/trino-dameng:/usr/lib/trino/plugin/trino-dameng:ro
```
插件为自包含目录（模块 jar + `trino-base-jdbc` 等依赖 + `DmJdbcDriver18.jar` + service 文件）。

### D3. 达梦 JDBC 驱动依赖

`DmJdbcDriver18.jar` 不在 Central，用 `mvn install:install-file` 装入构建用 `.m2`，作为普通依赖；不提交 jar、不使用 `system` scope。

### D4. 类型映射按需扩展

首版覆盖常用类型（数值/字符串/日期/时间戳/CLOB→VARCHAR 等）；对不支持类型先以视图或字符串降级，避免列丢失。

### D5. 达梦部署

官方 Docker 镜像（官网下载后 `docker load`），`PAGE_SIZE=16`、`INSTANCE_NAME`，端口 5236；确认授权（`dm.key`）可长期运行。

## Risks / Trade-offs

- [信息模式差异] → 先做 spike 验证 `information_schema.tables/columns` 与大小写，必要时调整 `TableCatalogService`。
- [类型映射不完整] → 影响列可读性；按 D4 降级并记录。
- [授权/许可] → 演示环境需确认达梦授权有效期。
- [构建环境] → 首次需安装驱动到 `.m2`；在远端 Maven 容器内完成。
