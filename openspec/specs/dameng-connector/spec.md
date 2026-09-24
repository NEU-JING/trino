# dameng-connector Specification

## Purpose

定义以插件方式加载的达梦连接器能力，使 Trino 能够把达梦数据库作为 Catalog 查询，支持常用类型映射与跨源联邦查询。

## Requirements

### Requirement: 达梦连接器

平台 SHALL 提供一个以插件方式加载的达梦连接器，使 Trino 能够把达梦数据库作为 Catalog 查询，且不修改 Trino 引擎核心。

#### Scenario: 插件加载

- **WHEN** Trino 以包含 `trino-dameng` 插件的配置启动
- **THEN** 连接器被加载，无 SPI 版本不匹配错误

#### Scenario: 创建并查询达梦 Catalog

- **WHEN** 平台注册达梦数据源并执行 `CREATE CATALOG ... USING dameng`
- **THEN** 可执行 `SHOW SCHEMAS` 与 `SELECT` 并返回数据

### Requirement: 类型映射

连接器 SHALL 将达梦常用数据类型映射为 Trino 类型；对暂不支持的类型 MUST 以可读方式降级而非导致列缺失。

#### Scenario: 常用类型

- **WHEN** 查询包含数值、字符串、日期/时间戳与 CLOB 的列
- **THEN** 各列以合理类型返回

### Requirement: 跨源联邦

达梦 Catalog SHALL 能与其它数据源 Catalog 在同一查询中 JOIN。

#### Scenario: 跨源 JOIN

- **WHEN** 执行达梦表与 OceanBase/Greenplum 表的 JOIN
- **THEN** 返回正确关联结果
