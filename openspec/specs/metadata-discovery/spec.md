# metadata-discovery Specification

## Purpose

定义列元数据、样例行与跨对象建议候选的查询能力，为前端联想输入与表详情提供统一数据源，并按用户可见范围过滤。

## Requirements

### Requirement: 列元数据

系统 SHALL 提供已注册表的列元数据（列名、类型、可空、注释），数据来源于数据源的 `information_schema`。

#### Scenario: 查看列结构

- **WHEN** 用户查看某张已注册表的详情
- **THEN** 显示其列名与类型等结构信息

### Requirement: 样例数据

系统 SHALL 为已注册表提供有限行数的样例行，并受超时与行数上限约束。

#### Scenario: 查看样例

- **WHEN** 用户请求某表的样例数据
- **THEN** 返回少量行用于预览，且不会因大表导致长时间阻塞

### Requirement: 建议候选

系统 SHALL 提供跨 catalog/schema/table/column 的候选查询，且候选仅包含当前用户可见或可管理的对象。

#### Scenario: 候选过滤

- **WHEN** 查询人员请求表候选
- **THEN** 仅返回其已授权且已注册的表

#### Scenario: 运营候选

- **WHEN** 运营人员请求某数据源下的 schema/table 候选
- **THEN** 返回该数据源的可发现对象

### Requirement: 数据集与关系建议

系统 SHALL 在建议候选中支持数据集、字段语义角色与关系类型，且候选仅包含当前用户可见或可管理的对象。

#### Scenario: 数据集候选

- **WHEN** 用户请求数据集候选
- **THEN** 系统仅返回其可见的数据集

#### Scenario: 关系候选

- **WHEN** 用户为某数据集请求可关联对象
- **THEN** 系统返回存在声明或推断关系的数据集及其字段
