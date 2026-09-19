## ADDED Requirements

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
