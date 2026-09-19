## MODIFIED Requirements

### Requirement: 数据源业务别名

系统 SHALL 向用户展示业务别名而非底层连接器名称：以 MySQL 连接器承载的数据源显示为「OceanBase」，以 PostgreSQL 连接器承载的数据源显示为「Greenplum」，以自研达梦连接器承载的数据源显示为「达梦」。系统 MUST NOT 在面向用户的界面或 API 响应中暴露底层连接器名称。

#### Scenario: 列表展示业务别名

- **WHEN** 查询人员或运营人员查看数据源列表
- **THEN** MySQL 承载的显示为「OceanBase」，PostgreSQL 承载的显示为「Greenplum」，达梦连接器承载的显示为「达梦」

#### Scenario: 达梦数据源注册

- **WHEN** 运营人员选择业务类型「达梦」并填写可成功连接的连接信息
- **THEN** 系统以 `jdbc:dm://host:port` 构造连接并创建 Catalog，列表显示名称为「达梦」
