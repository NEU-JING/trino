# demo-datasets Specification

## Purpose

定义可重放、彼此关联、覆盖多办公业务域的样例数据集，使演示环境可从空环境重建，并天然支撑跨数据源联邦查询的演示。

## Requirements

### Requirement: 可重放的样例数据

系统部署 MUST 通过随仓库版本化的 seed 脚本提供样例数据，可在空环境重复执行且结果一致。

#### Scenario: 空环境重建

- **WHEN** 在无样例数据的空环境执行 seed 脚本
- **THEN** 建表并导入数据，且重复执行不产生重复或脏数据

### Requirement: 多业务域覆盖

样例数据 SHALL 覆盖至少办公 OA、人力资源、财务三个业务域，并分布在不同数据源。

#### Scenario: 跨源分布

- **WHEN** 查看各数据源
- **THEN** 不同业务域的表分别位于不同数据源

### Requirement: 跨源关联

样例数据 SHALL 通过共用关联键（如 employee_id / dept_id / cost_center_id / 会计期间）支持跨数据源 JOIN。

#### Scenario: 跨源 JOIN

- **WHEN** 执行跨两个及以上数据源的 JOIN 查询
- **THEN** 能按共用键正确关联并返回结果
