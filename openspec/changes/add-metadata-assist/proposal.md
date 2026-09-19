## Why

所有涉及输入的地方都缺乏联想能力：权限配置页的 `catalog/schema/table` 是**纯文本输入**（`PermissionsPage.tsx:117-119`），管理员必须手打 `oceanbase.hr.employees`，拼错即静默授权失败；注册表必须「选数据源→点发现表→在一张全量列表里逐行手填描述」。注册前也无法查看表的列结构或样例数据。此外，后端已有用户管理 API（`UserAdminController`），前端却无对应页面。

## What Changes

- 新增统一的**元数据联想接口**：跨 catalog/schema/table/column 的搜索与候选返回。
- 新增后端**列元数据与样例行接口**（基于 `information_schema.columns` 与采样查询）。
- **全局联想输入**：表注册、权限配置（catalog→schema→table **级联**）、查询编辑器等所有输入点接入候选。
- **表详情**：查看列名/类型/注释、前 N 行样例、行数、被授权主体。
- **数据源连接测试**：注册/编辑前可单独测试连通性。
- **表注册流程增强**：schema 筛选、搜索、已注册标记、批量注册、描述模板。
- **用户管理页**：补全 OPERATOR 的用户增删/启停/改角色界面。
- **错误可诊断**：非法输入给出「可用候选」提示而非仅报错。

## Capabilities

### New Capabilities

- `metadata-discovery`: 列元数据、样例行、表详情与跨对象联想候接口。
- `guided-input`: 所有输入点提供联想/级联/校验候选，避免手输错误。
- `user-administration`: 运营人员的用户管理界面。

### Modified Capabilities

- `table-catalog`: 注册流程增加筛选/搜索/已注册标记/批量注册。
- `table-access-control`: 授权输入改为级联选择，禁止无效对象。
- `data-source-management`: 增加连接测试。

## Impact

- `data-fabric/backend`：新增元数据/建议/详情/测试接口；`TableCatalogService` 扩展列与样例查询。
- `data-fabric/frontend`：新增共享 `EntitySelect`/`AutoComplete` 组件；重构三个管理页与注册流程。
- 依赖 `beautify-platform-ui` 的 AntD 组件与主题。
