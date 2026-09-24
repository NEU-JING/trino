# data-source-management Specification

## Purpose

定义数据源的注册、业务别名、持久化、变更停用与访问控制，允许运营人员注册可查询的数据源并以业务名称展示，同时保证数据源在服务重启后仍然可用。

## Requirements

### Requirement: 数据源注册

系统 SHALL 允许运营人员通过界面填写数据源连接信息（业务类型、主机、端口、数据库、用户名、密码及连接器所需属性）并注册为可查询数据源。系统 MUST 在注册时验证连接可用性，验证失败时拒绝注册并返回可读错误。

#### Scenario: 成功注册 OceanBase 数据源

- **WHEN** 运营人员选择业务类型“OceanBase”，填写可成功连接的连接信息并提交
- **THEN** 系统创建对应的动态 Catalog，并在数据源列表中显示该数据源，显示名称为“OceanBase”

#### Scenario: 成功注册 Greenplum 数据源

- **WHEN** 运营人员选择业务类型“Greenplum”，填写可成功连接的连接信息并提交
- **THEN** 系统创建对应的动态 Catalog，并在数据源列表中显示该数据源，显示名称为“Greenplum”

#### Scenario: 连接不可用导致注册失败

- **WHEN** 运营人员提交的数据源连接信息无法建立连接
- **THEN** 系统拒绝注册，不创建 Catalog，并返回可读的连接错误信息

### Requirement: 数据源业务别名

系统 SHALL 向用户展示业务别名而非底层连接器名称：以 MySQL 连接器承载的数据源显示为“OceanBase”，以 PostgreSQL 连接器承载的数据源显示为“Greenplum”，以自研达梦连接器承载的数据源显示为“达梦”。系统 MUST NOT 在面向用户的界面或 API 响应中暴露底层连接器名称。

#### Scenario: 列表展示业务别名

- **WHEN** 查询人员或运营人员查看数据源列表
- **THEN** MySQL 承载的数据源显示为“OceanBase”，PostgreSQL 承载的数据源显示为“Greenplum”，达梦连接器承载的数据源显示为“达梦”

#### Scenario: 达梦数据源注册

- **WHEN** 运营人员选择业务类型“达梦”并填写可成功连接的连接信息
- **THEN** 系统以 `jdbc:dm://host:port` 构造连接并创建 Catalog，列表显示名称为“达梦”

#### Scenario: API 响应不暴露连接器名称

- **WHEN** 客户端请求数据源列表接口
- **THEN** 响应中不包含 `mysql`、`postgresql` 等底层连接器标识

### Requirement: 数据源持久化

系统 SHALL 持久化已注册的数据源，使其在服务重启后仍然可用。

#### Scenario: 重启后数据源仍存在

- **WHEN** 平台或 Trino 服务重启后运营人员查看数据源列表
- **THEN** 之前注册的数据源仍然存在且可查询

### Requirement: 数据源变更与停用

系统 SHALL 允许运营人员更新数据源连接信息或停用数据源；停用后该数据源下的表 MUST NOT 出现在表目录中且不可查询。

#### Scenario: 停用数据源

- **WHEN** 运营人员停用某数据源
- **THEN** 系统移除对应动态 Catalog，该数据源下的表不再出现在表目录中且不可查询

### Requirement: 数据源管理权限

系统 SHALL 仅允许运营人员角色执行数据源的注册、更新与停用操作。

#### Scenario: 查询人员无权管理数据源

- **WHEN** 查询人员尝试调用数据源管理接口
- **THEN** 系统拒绝该操作并返回权限不足
