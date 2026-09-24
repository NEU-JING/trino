## ADDED Requirements

### Requirement: 逻辑数据集定义

系统 SHALL 允许运营人员将数据定义为逻辑数据集（Dataset），并区分三类：`BASE`（指向已注册物理表的直通数据集）、`DERIVED`（对其它数据集进行 join、投影与过滤的组合数据集）、`AGGREGATE`（对输入数据集按维度分组并计算度量的聚合数据集）。每个数据集 MUST 具有稳定标识、名称、描述、所属域与负责人。

#### Scenario: 定义 BASE 数据集

- **WHEN** 运营人员选择一张已注册表并将其注册为 BASE 数据集
- **THEN** 系统创建该数据集，其字段来源于该表的列元数据

#### Scenario: 定义 DERIVED 数据集

- **WHEN** 运营人员选择多个数据集、指定 join 关系并投影所需字段
- **THEN** 系统创建 DERIVED 数据集，其定义包含关系、投影与过滤

#### Scenario: 定义 AGGREGATE 数据集

- **WHEN** 运营人员选择一个输入数据集、指定分组维度、度量表达式与过滤条件
- **THEN** 系统创建 AGGREGATE 数据集，其字段包含维度与度量

### Requirement: 字段语义角色

系统 SHALL 为数据集字段记录语义角色，取值包括 `id`、`dimension`、`measure`、`time`、`geo`、`computed`。度量字段 SHALL 可记录默认聚合方式，时间字段 SHALL 可记录时间粒度。字段语义角色 MUST 随数据集定义持久化并通过模型接口对外提供。

#### Scenario: 标注维度与度量

- **WHEN** 运营人员为聚合数据集的字段标注 `dimension` 与 `measure`
- **THEN** 系统持久化其角色，并在模型接口返回中体现

#### Scenario: 度量默认聚合

- **WHEN** 某度量字段设置了默认聚合方式
- **THEN** 下游消费方读取该字段时获得其默认聚合方式

### Requirement: 数据集版本与生命周期

系统 SHALL 为数据集维护递增版本与生命周期状态（`DRAFT`、`PUBLISHED`、`DEPRECATED`）。已发布版本 MUST 不可变；删除字段或修改字段类型等破坏性变更 MUST 通过发布新版本完成。消费方 MUST 能按稳定标识绑定数据集，并 MAY 指定版本。

#### Scenario: 发布数据集

- **WHEN** 运营人员发布草稿数据集
- **THEN** 系统生成一个不可变的已发布版本

#### Scenario: 破坏性变更需新版本

- **WHEN** 运营人员删除已发布版本的字段或修改其类型
- **THEN** 系统要求发布新版本，且旧版本保持可用

#### Scenario: 弃用数据集

- **WHEN** 运营人员弃用某数据集
- **THEN** 系统将其标记为 DEPRECATED，且不再出现在面向下游的默认可消费列表中

### Requirement: 数据集物化策略

系统 SHALL 为数据集记录物化模式（`virtual` 或 `materialized`）。对物化数据集，系统 SHALL 支持手动触发刷新。数据集 MUST 可记录新鲜度/陈旧度信息。

#### Scenario: 手动物化刷新

- **WHEN** 运营人员对物化数据集触发刷新
- **THEN** 系统重新计算并更新其物化结果

#### Scenario: 虚拟数据集按需查询

- **WHEN** 消费方查询虚拟数据集
- **THEN** 系统按定义实时计算并返回结果

### Requirement: 数据集管理权限

系统 SHALL 仅允许运营人员创建、修改、发布与弃用数据集；查询用户 MUST NOT 执行数据集管理操作。

#### Scenario: 查询用户无权管理数据集

- **WHEN** 查询用户尝试创建或发布数据集
- **THEN** 系统拒绝该操作并返回权限不足
