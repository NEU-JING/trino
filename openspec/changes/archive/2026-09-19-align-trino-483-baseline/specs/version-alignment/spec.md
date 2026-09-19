## ADDED Requirements

### Requirement: 版本基线一致性

平台各层的 Trino 版本 MUST 一致为 483 正式版：仓库源码基线、运行时镜像、后端 `io.trino:trino-client` 依赖，以及任何自研连接器编译所用的 SPI 版本。

#### Scenario: 源码基线

- **WHEN** 查看根 `pom.xml` 的版本
- **THEN** 其基线对应 Trino 483，不含 484-SNAPSHOT

#### Scenario: 运行时镜像

- **WHEN** 查看 `data-fabric/deploy/docker-compose.yml`
- **THEN** Trino 服务镜像固定为 `trinodb/trino:483`，不使用 `latest`

#### Scenario: 后端客户端

- **WHEN** 查看 `data-fabric/backend/pom.xml`
- **THEN** `trino.version` 为 `483`

#### Scenario: 连接器 SPI

- **WHEN** 构建并加载自研连接器
- **THEN** 其编译 SPI 版本与运行时 483 一致，加载无 SPI 版本不匹配错误
