# dataset-relations Specification

## Purpose

定义数据集之间关系（join 边）的声明与推断、关系图与血缘追溯，使跨数据集整合知识只在平台内维护一份并可被复用。

## Requirements

### Requirement: 关系声明

系统 SHALL 允许运营人员声明数据集之间的关系（join 边），每条关系包含来源字段、目标字段、join 类型与基数。关系 MUST 可被多个数据集复用。

#### Scenario: 声明 join 关系

- **WHEN** 运营人员指定两个数据集之间的来源字段、目标字段、join 类型与基数
- **THEN** 系统持久化该关系并在关系图中展示

#### Scenario: 关系复用

- **WHEN** 运营人员在 DERIVED 数据集中引用一条已声明关系
- **THEN** 该数据集使用该关系进行 join，且不产生重复的关系定义

### Requirement: 关系推断

系统 SHALL 基于字段命名约定（如 `*_id`、`*_no`）与样例取值匹配，为数据集推断候选关系。推断得到的关系 MUST 标记为 `inferred`，在运营人员确认前 MUST NOT 用于生产查询建议。

#### Scenario: 推断候选关系

- **WHEN** 系统发现两个数据集的字段符合命名约定或样例取值高度匹配
- **THEN** 系统给出标记为 inferred 的候选关系

#### Scenario: 确认候选关系

- **WHEN** 运营人员确认某条推断关系
- **THEN** 该关系被标记为 declared 并可用于查询建议

### Requirement: 关系图

系统 SHALL 以图的形式展示数据集及其之间的关系，并支持从节点跳转到对应数据集详情。

#### Scenario: 查看关系图

- **WHEN** 运营人员打开关系图
- **THEN** 系统展示数据集节点及其 join 连线

### Requirement: 血缘追溯

系统 SHALL 记录并展示数据集的血缘，包括其上游数据集/物理表与下游消费方。

#### Scenario: 查看上游血缘

- **WHEN** 用户查看某数据集的血缘
- **THEN** 系统展示其依赖的上游数据集与物理表

#### Scenario: 查看下游消费

- **WHEN** 某数据集被其它数据集或应用消费
- **THEN** 系统在其血缘中展示下游依赖
