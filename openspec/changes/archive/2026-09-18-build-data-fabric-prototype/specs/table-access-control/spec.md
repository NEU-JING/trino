## ADDED Requirements

### Requirement: 权限授予与回收

系统 SHALL 允许运营人员为查询人员（或角色）授予与回收对已注册表的读权限。

#### Scenario: 授予读权限

- **WHEN** 运营人员为查询人员 U 授予对表 T 的读权限
- **THEN** U 随后可以查询表 T

#### Scenario: 回收读权限

- **WHEN** 运营人员回收查询人员 U 对表 T 的读权限
- **THEN** U 对表 T 的查询被拒绝

### Requirement: 查询执行时强制鉴权

系统 SHALL 在查询执行阶段强制执行表级权限，独立于用户如何构造 SQL。

#### Scenario: 越权查询被拒绝

- **WHEN** 无表 T 权限的用户提交访问表 T 的 SQL
- **THEN** 查询在返回任何数据前被拒绝，并返回权限错误

#### Scenario: 通过 JOIN 绕过被拒绝

- **WHEN** 无表 T 权限的用户将表 T 与一张有权限的表 JOIN
- **THEN** 查询被拒绝

#### Scenario: 通过子查询绕过被拒绝

- **WHEN** 无表 T 权限的用户在子查询或 CTE 中引用表 T
- **THEN** 查询被拒绝

### Requirement: 表目录按权限过滤

系统 SHALL 使表目录仅展示当前用户有权限访问的表。

#### Scenario: 目录不含无权限表

- **WHEN** 用户 U 查看表目录
- **THEN** U 无权限的表不出现在结果中

### Requirement: 权限变更在线生效

系统 SHALL 在不重启服务的情况下使权限变更生效，生效延迟不超过配置的刷新周期。

#### Scenario: 权限变更在线生效

- **WHEN** 运营人员修改某用户的表权限
- **THEN** 在一个刷新周期内，新的权限在查询执行与表目录中生效

### Requirement: 权限管理权限

系统 SHALL 仅允许运营人员角色配置权限。

#### Scenario: 查询人员无权配置权限

- **WHEN** 查询人员尝试调用权限配置接口
- **THEN** 系统拒绝该操作并返回权限不足
