## Context

`QueryPage.tsx` 使用 `Input.TextArea`（`autoSize={{minRows:5,maxRows:10}}`）与原生 `<table>`；表目录是独立页面 `TablesPage.tsx`，与查询页割裂。后端 `QueryService` 在内存中保存执行态（`MAX-ROWS=10000`），不持久化。`add-metadata-assist` 已规划统一 suggestion 接口与列/样例/表详情接口。

## Goals / Non-Goals

**Goals:**

- 专业化 SQL 工作台体验（高亮/格式化/补全/快捷键）。
- 单页一体化工作台：表目录、含义/结构查看与 SQL 编写同屏，接近 DBeaver 体验。
- 大结果集可浏览、可导出、可可视化。
- 历史与收藏可回看、可重跑。

**Non-Goals:**

- 不做查询执行的分布式/多引擎改造。
- 不做结果集服务端无限分页（首版仍受 `max-rows` 约束）。
- 不做完整的数据库客户端能力（如 DDL 编辑、事务、ER 图），对象树只读。

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

### D5. 工作台采用三区可拖拽布局

左侧对象树、右上编辑器、右下结果/历史/收藏，使用 AntD `Splitter`（antd ≥ 5.21，当前 5.22）实现可拖拽分隔；分隔比例持久化到本地，默认编辑器高度不少于 12 行。**理由**：在不牺牲结果区可用的前提下解决「查询框过小」，并让表目录与编写动作同屏。

### D6. 对象树/表详情复用 metadata-discovery 接口

对象树的 catalog→schema→table→column 层级、搜索，以及表含义（描述/注释）、列结构、样例行，全部复用 `add-metadata-assist` 的统一 suggestion 与 columns/sample 接口，不在本 change 内另造元数据接口。可见范围由服务端按「已注册 ∩ 已授权」过滤，与 `table-catalog` 的浏览规则保持一致，避免两处可见性定义漂移。

### D7. 目录交互约定

单击对象树节点在右侧下方展示表详情（含义/结构/样例）；双击表或列将限定名插入编辑器光标处；插入仅产生文本，不自动执行。**理由**：贴近 DBeaver 的手感，同时保持动作可预期、不做隐式查询。

## Risks / Trade-offs

- [大结果集前端渲染] → 采用虚拟滚动/分页，避免一次性渲染全部行。
- [补全/对象树信息越权] → 补全候选与对象树均严格按可见表过滤。
- [历史表增长] → 设定保留策略（如按用户上限/时间清理）。
- [对象树元数据加载性能] → 层级懒加载、按需拉取列/样例，复用 `add-metadata-assist` 的超时与缓存。
- [窄屏三区布局拥挤] → 提供对象树折叠与结果区最大化，保证小屏可用。
