## Context

`TableCatalogService.discover`（`TableCatalogService.java:44`）仅返回 schema/table/type，无列与样例；`PermissionsPage` 对 catalog/schema/table 使用裸文本输入。

## Goals / Non-Goals

**Goals:**

- 消除所有「凭记忆手输标识符」的输入点。
- 注册前可查看表结构与样例，降低误注册。
- 只暴露当前用户有权看到/管理的对象。

**Non-Goals:**

- 不做企业级数据目录/血缘（本期仅联想与详情）。
- 不在源库创建视图或元数据回写。

## Decisions

### D1. 统一 suggestion 接口而非前端各页各自拼数据

后端提供单一 `GET /api/metadata/suggest`（按 `type` = catalog/schema/table/column、`parent` 过滤），前端封装共享组件复用。

- **理由**：一致性与权限过滤集中在一处，避免各页逻辑漂移。

### D2. 权限过滤在服务端完成

候选严格限于「当前用户可见（已注册 ∩ 已授权）或可管理（OPERATOR）」的对象，前端不做安全假设。

### D3. 表详情按需拉取

列与样例通过独立接口（如 `GET /api/tables/{id}/columns`、`/sample`）按需加载，避免列表页 N+1 查询。

### D4. 级联选择优先于自由文本

权限配置的 catalog/schema/table 改为 `Cascader`/联动 `Select`；仅在数据源发现失败等特殊场景保留手工输入兜底。

## Risks / Trade-offs

- [样例数据查询性能] → 限制行数（如 20）、加超时与缓存。
- [DM 等源 `information_schema` 行为差异] → 见 `add-dameng-connector` 的 spike，接口层做容错。
- [越权信息泄露] → 服务端强制过滤，E2E 断言越权对象不可见。
