## ADDED Requirements

### Requirement: 用户管理

系统 SHALL 为运营人员提供用户管理界面，支持查看用户列表、新增用户、启用/停用用户与调整角色。

#### Scenario: 新增用户

- **WHEN** 运营人员创建新用户并指定角色
- **THEN** 该用户可登录并具有相应角色权限

#### Scenario: 停用用户

- **WHEN** 运营人员停用某用户
- **THEN** 该用户无法再登录/访问

#### Scenario: 角色受限

- **WHEN** 非运营人员访问用户管理接口或页面
- **THEN** 系统拒绝并返回权限不足
