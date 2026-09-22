# guided-input Specification

## Purpose

定义全局联想输入、数据源连接测试与可诊断错误处理，使运营人员与查询人员无需凭记忆手输 catalog、schema、table 等数据对象标识符，从而降低拼写错误与无效授权。

## Requirements

### Requirement: 全局联想输入

系统 SHALL 在所有需要输入数据对象标识符的位置提供联想/候选，避免用户凭记忆手输。

#### Scenario: 权限配置级联选择

- **WHEN** 运营人员在权限页选择 catalog 后
- **THEN** schema 候选随之联动，选择 schema 后 table 候选随之联动，且不可提交无效对象

#### Scenario: 注册表现

- **WHEN** 运营人员注册表时输入关键字
- **THEN** 显示匹配的候选表，并标记已注册项

### Requirement: 数据源连接测试

系统 SHALL 允许运营人员在注册或编辑数据源前测试连接可用性。

#### Scenario: 测试成功

- **WHEN** 运营人员填写连接信息并点击测试且连接可用
- **THEN** 界面提示连接成功

#### Scenario: 测试失败

- **WHEN** 连接不可用
- **THEN** 界面返回可读的失败原因

### Requirement: 错误可诊断

系统 SHALL 在用户输入不存在的对象时，提示失败原因与可用候选，而非仅返回通用错误。

#### Scenario: 无效对象提示

- **WHEN** 用户提交了一个不存在的表标识
- **THEN** 返回错误并提示可用候选或相近对象
