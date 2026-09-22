# query-history Specification

## Purpose

定义查询历史与收藏查询的持久化能力：记录每次查询的 SQL、用户、状态、耗时与返回行数，允许用户回看与重跑历史查询、将常用查询保存为具名收藏并一键执行，且各用户仅能访问本人的历史与收藏。

## Requirements

### Requirement: 查询历史

系统 SHALL 持久化每次查询的记录（SQL、执行用户、状态、耗时、返回行数），并允许用户查看与重跑历史查询。

#### Scenario: 记录历史

- **WHEN** 用户执行一次查询
- **THEN** 该查询出现在该用户的历史列表中

#### Scenario: 重跑历史

- **WHEN** 用户对某条历史记录选择重跑
- **THEN** 以其 SQL 重新提交执行

### Requirement: 收藏查询

系统 SHALL 允许用户将查询保存为具名收藏，并一键执行。

#### Scenario: 保存收藏

- **WHEN** 用户将当前 SQL 保存并命名
- **THEN** 该查询出现在收藏列表中

#### Scenario: 执行收藏

- **WHEN** 用户点击某条收藏
- **THEN** 以其保存的 SQL 执行查询

### Requirement: 历史隔离

系统 SHALL 仅向用户展示其本人的查询历史与收藏。

#### Scenario: 用户隔离

- **WHEN** 用户查看历史列表
- **THEN** 只能看到自己执行的查询记录
