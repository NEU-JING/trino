## Context

data-fabric 已具备：Trino 483 联邦底座、动态 Catalog、表级权限（引擎强制）、注册表目录（含描述）、列元数据/样例、SQL 工作台、历史与收藏、总览统计。定位已明确为**底座平台**：整合数据后为数据应用产品提供服务；最终用户经数据应用产品自助查询/看板/未来问数，不关心数据来源。

当前对外原语只有 `registered_table`（物理层）。缺口是"语义基质层"：下游无法获得统一的关系、字段语义与可消费单元，只能各自重新理解数据、重复定义 join。业界参照（Aloudata）将 fabric 与指标分为两个产品：AIR 承载逻辑建模（PDS/VDWD/VDWS/VADM），CAN 承载指标。本设计采纳该边界——**data-fabric 做语义基质，不做指标产品**；指标表达为一种聚合数据集，由下游写回。

约束：下游数据应用组进度不可控；信创/自主可控；私有化部署；运营单一角色；SQL 优先、API 兜底；物化分阶段。

## Goals / Non-Goals

**Goals:**

- 以**逻辑数据集（Dataset）**为唯一对外原语，承载结构、关系、字段语义、版本、物化与治理。
- 提供**稳定、版本化的服务契约**：模型 API + SQL 查询 + 指标写回 + 用量观测。
- 让 data-fabric **独立可用**（下游不交付也能支撑基础自助查询），同时让未来指标平台可无缝插入。
- 运营侧可高效完成"接入→注册→建模数据集→发布→观测"闭环。

**Non-Goals:**

- 不做完整指标平台：不实现指标定义管理、指标看板、NL 问数产品（由下游消费方承担）。
- 不做拖拽式重型建模器（前期以 SQL + 关系图定义数据集）。
- 不实现自动物化策略引擎（第二阶段）。
- 不替换或重构既有表注册/权限/查询工作台。

## Decisions

### D1. Dataset 作为唯一对外原语；指标 = AGGREGATE dataset + MetricProfile

**决策**：所有对外消费对象都是 Dataset。指标不是独立类型，而是 `kind=AGGREGATE` 且携带可选 `semantic.metric` 剖面的数据集。

**理由**：统一原语使治理、版本、物化、血缘、权限只需实现一次；下游指标平台用"同一种语言"写回指标，无需平台为其开专用通道。

**备选**：① 指标单独建类型/服务——会造成两套治理与血缘；② 只做 BASE/DERIVED 不做聚合——无法支撑指标写回与自动物化。

### D2. 三类 kind：BASE / DERIVED / AGGREGATE

**决策**：

- `BASE`：指向 `registered_table` 的直通数据集（物理层）。
- `DERIVED`：由关系图（join 边）+ 投影 + 过滤定义，输入为其它数据集。
- `AGGREGATE`：对 BASE/DERIVED 分组聚合，含 `measures[]`、`groupBy[]`、`timeGrain`。

**理由**：三类覆盖"直通 / 组合 / 汇总"全部消费形态，且与物化、语义角色自然对应。

**备选**：只做 BASE + DERIVED（聚合由下游表达）——但会丢失"指标可写回"和自动物化锚点。

### D3. 字段语义角色（semantic role）作为基质核心

**决策**：字段携带 `role ∈ {id, dimension, measure, time, geo, computed}`，可附 `aggregation`（度量默认聚合）、`timeGrain`、`format/unit`。

**理由**：这是"便宜且共享"的元数据——下游指标平台可直接将其作为可选维度/度量池，无需重新理解数据；也是未来 NL 问数可靠性的前提。

**备选**：只存类型与描述（最薄）——下游需自行推断语义，重复且易漂移。

### D4. 关系（Relation）作为可复用的一等对象，支持声明与推断

**决策**：关系为有向 join 边 `{from{dataset,field}, to{dataset,field}, joinType, cardinality, origin}`；`origin` 区分 `declared`（运营声明）与 `inferred`（命名约定 `*_id`/`*_no` + 样例值匹配）。关系可被多个 DERIVED 数据集复用。

**理由**：join 知识源自数据整合，只在 data-fabric 存一份；复用避免每个应用/指标重复定义。

**备选**：关系内嵌在 DERIVED 定义里——无法复用、无法出关系图。

### D5. 版本与生命周期：PUBLISHED 版本不可变，消费方绑定稳定 ID

**决策**：Dataset 有稳定 `id`（UUID）与递增 `version`；状态 `DRAFT → PUBLISHED → DEPRECATED`。发布后版本不可变；字段删除/改类型必须升版本；`latest` 为移动别名。消费方应绑定 `id`（可选 `version`）。

**理由**：下游不可控，必须保证契约稳定、破坏性变更显式化。

**备选**：原地可变——下游绑定会在无声中失效。

### D6. 模型与视图同源：一份定义，双向编译

**决策**：Dataset 定义是唯一真源；**同一份定义**同时编译为：① Trino `fabric` catalog 中的视图/物化表（供 SQL 查询）；② 模型 API 返回的元数据（供下游建模）。禁止维护两条独立路径。

**理由**：这是本设计的核心——避免"元数据描述"与"实际可查结构"漂移；AGGREGATE 的度量/维度语义在视图中会退化为普通列，必须由模型 API 补充。

**备选**：只出视图（丢失语义）/ 只出模型 API（下游需自行生成 SQL）。

### D7. 服务契约：SQL 优先 + API 兜底 + 写回 + 观测

**决策**：

- **查询**：SQL 优先——`fabric.<domain>.<dataset>` 视图可被 Trino 客户端直连；API 模式由查询服务包一层。
- **模型 API**：`GET /model/datasets`、`/model/datasets/{id}`、`/versions`、`/model/relations`、`/model/lineage/{id}`。
- **写回 API**：`POST /model/datasets`（`kind=AGGREGATE`）、`PUT /model/datasets/{id}/versions`（发布）。
- **物化 API**：`POST /materializations/{id}/refresh`。
- **观测 API**：`GET /usage/datasets/{id}`。

**理由**：SQL 是应用与引擎的通用语言，API 覆盖建模/写回/观测；稳定 ID + 版本使契约可依赖。

**备选**：仅 REST 语义查询（应用不碰 SQL）——与"SQL 优先"定位不符。

### D8. 物化分阶段：先手动，后由观测驱动自动

**决策**：第一阶段 `materialization.mode ∈ {virtual, materialized}`，仅支持手动 `refresh`；第二阶段引入 `cache` 与基于用量/延迟/陈旧度的自动策略。

**理由**：观测服务是自动物化策略的输入，必须先在位；避免过早优化。

**备选**：一开始就做自动策略——缺少输入信号，易误判热点。

### D9. 治理挂在 Dataset 上

**决策**：权限、行过滤、列脱敏挂在 Dataset（而非表）上；复用既有 `FileBasedSystemAccessControl` 生成机制。

**理由**：应用只需理解 dataset 级权限，比表级简单；同时保留引擎强制。

**备选**：沿用表级——应用需理解物理表，泄漏实现细节。

### D10. 服务边界：目录/关系/数据集/服务为语义基质，指标平台为下游

**决策**：微服务按业务边界拆分，`目录服务`（表/列/关系）+ `数据集服务`（定义/版本/发布/物化）+ `查询服务` + `治理服务` + `观测服务`。指标平台只依赖"目录 + 数据集"的模型 API。

**理由**：明确 data-fabric 是上游基质而非指标竞争者，化解与下游的边界冲突。

**备选**：data-fabric 也做指标——范围膨胀、域知识缺口、与下游撞车。

## Risks / Trade-offs

- [写回导致 dataset 膨胀] → 按 `domain/app` 命名空间隔离；`DRAFT→PUBLISHED→DEPRECATED` 生命周期；用量观测驱动淘汰；写回需鉴权与可选审核。
- [模型/视图漂移] → D6 同源编译，单一编译器与契约测试（同一定义产出的视图结构必须与模型 API 一致）。
- [Trino 嵌套视图性能与下推] → 优先下推；热点物化；结果行数/超时上限沿用既有配置。
- [语义角色过度建模] → 仅最小角色集，不引入完整逻辑建模分层；不在一期做重型 UI。
- [下游长期不交付] → 平台独立可用；数据集经 SQL/API 直接可消费；必要时补一个"参考级"轻指标消费者（可插拔，不进内核）。
- [版本破坏下游] → 发布版本不可变；破坏性变更升版本并保留弃用窗口；消费方可绑定 `version`。
- [关系推断误判] → `origin` 标记为 `inferred`，需运营确认后转 `declared` 才用于生产查询建议。

## Migration Plan

1. 增量新增，不改既有表注册/权限/查询行为；`BASE` dataset 可由既有 `registered_table` 迁移/映射生成。
2. 引入 `fabric` catalog 承载视图/物化表；既有 `sql-query-execution`、`table-access-control` 不变。
3. 新增数据库表：dataset、dataset_field、dataset_version、dataset_relation、materialization、usage。
4. 前端新增运营侧数据集建模与发布、总览用量视图；既有页面保留。
5. 回滚：停用 `fabric` catalog 与新增模块即可，物理层与既有查询不受影响。

## Open Questions

- MetricProfile 的深度：只声明度量/维度，还是含业务定义/单位/格式/owner 的完整剖面？
- 关系推断算法：命名约定 + 样例值匹配的具体阈值与人工确认流程？
- 是否提供轻量"语义查询"端点（按 dataset 选字段/过滤/分组），还是仅 SQL？
- 自动物化策略：基于用量/延迟/陈旧度的规则形态与触发频率？
- 数据集命名空间与下游指标平台的 domain 划分是否对齐？
