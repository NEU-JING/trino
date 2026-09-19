# sql-query-execution Specification

## Purpose

定义在线 SQL 提交与执行能力，包括跨数据源组合查询、结果表格展示、错误反馈、查询取消与超时，以及将过滤谓词下推到数据源执行。

## Requirements

### Requirement: 在线 SQL 提交

系统 SHALL 允许查询人员在前端编写 SQL 并提交执行。

#### Scenario: 提交单表查询

- **WHEN** 查询人员对一张有权限的表提交 SELECT 查询
- **THEN** 系统执行查询并返回结果集

### Requirement: 跨数据源组合查询

系统 SHALL 支持在单条 SQL 中查询多个数据源的表（含 JOIN），并返回合并后的结果。

#### Scenario: 跨数据源 JOIN

- **WHEN** 查询人员对有权限的 OceanBase 表与 Greenplum 表提交 JOIN 查询
- **THEN** 系统返回跨数据源合并后的结果集

### Requirement: 结果表格展示

系统 SHALL 按查询的 SELECT 结构以表格形式展示结果，包含列名与行数据。

#### Scenario: 结果列与 SELECT 一致

- **WHEN** 查询人员提交带别名与表达式的 SELECT
- **THEN** 结果表格的列名与查询结果集列名一致

#### Scenario: 空结果集

- **WHEN** 查询成功但没有匹配行
- **THEN** 系统展示空结果表格并显示列名，且不报错

### Requirement: 查询错误反馈

系统 SHALL 在 SQL 语法或语义错误时返回可读的错误信息，且不影响后续查询。

#### Scenario: 语法错误

- **WHEN** 查询人员提交语法错误的 SQL
- **THEN** 系统返回可读错误信息，且允许继续提交新查询

### Requirement: 查询取消

系统 SHALL 允许查询人员取消正在执行的查询。

#### Scenario: 取消执行中的查询

- **WHEN** 查询人员对正在执行的查询点击取消
- **THEN** 系统停止该查询，且不将其标记为成功

### Requirement: 查询超时

系统 SHALL 对执行时间超过配置上限的查询进行终止并返回超时错误。

#### Scenario: 超时终止

- **WHEN** 查询执行时间超过配置的超时上限
- **THEN** 系统终止该查询并返回超时错误

### Requirement: 谓词下推

系统 SHALL 将 SQL 中的过滤谓词尽可能下推到数据源执行，以控制跨数据源查询的数据传输量。

#### Scenario: 过滤条件下推

- **WHEN** 查询对某数据源的表带有过滤条件
- **THEN** 系统生成的远端查询包含该过滤条件（可通过查询计划或数据源日志验证）
