## Context

`QueryPage.tsx` 使用 `<textarea>` 与原生 `<table>`；后端 `QueryService` 在内存中保存执行态（`MAX-ROWS=10000`），不持久化。

## Goals / Non-Goals

**Goals:**

- 专业化 SQL 工作台体验（高亮/格式化/补全/快捷键）。
- 大结果集可浏览、可导出、可可视化。
- 历史与收藏可回看、可重跑。

**Non-Goals:**

- 不做查询执行的分布式/多引擎改造。
- 不做结果集服务端无限分页（首版仍受 `max-rows` 约束）。

## Decisions

### D1. 编辑器用 CodeMirror 6（不用 Monaco）

| 维度 | CodeMirror 6 | Monaco |
|---|---|---|
| 体积/构建 | 轻，node 容器构建快 | 重 |
| SQL 方言补全 | `@codemirror/lang-sql` + 自定义补全源 | 需较多样板配置 |
| 演示需求满足度 | 足够 | 过剩 |

### D2. 补全数据来自后端元数据接口

编辑器补全（表/列/关键字）复用 `add-metadata-assist` 的 suggestion 接口，按当前用户可见（已注册 ∩ 已授权）范围返回，避免越权信息泄露。

### D3. 历史/收藏持久化在平台元数据库

新增表（如 `query_history`、`saved_query`），记录 SQL、执行用户、状态、耗时、行数；历史可重跑。

### D4. 结果可视化用 ECharts

与 Dashboard 共用 ECharts；仅对数值列提供基础图表。

## Risks / Trade-offs

- [大结果集前端渲染] → 采用虚拟滚动/分页，避免一次性渲染全部行。
- [补全信息越权] → 补全候选严格按可见表过滤。
- [历史表增长] → 设定保留策略（如按用户上限/时间清理）。
