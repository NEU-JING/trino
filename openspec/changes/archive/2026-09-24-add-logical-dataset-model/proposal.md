## Why

data-fabric 目前对外只暴露"注册表"（物理层原语），下游数据应用产品与未来指标平台无法获得统一的**关系、字段语义与可消费单元**，只能各自重新理解数据、重复定义 join 与口径。为支撑自助查询、拖拽看板与智能问数，data-fabric 需要补齐"语义基质层"：以**逻辑数据集（Dataset）**为唯一对外原语，承载关系、字段语义角色、版本、物化与治理，并以稳定契约对下游提供服务。指标不做成独立产品，而是表达为一种**聚合数据集剖面**，可由下游指标平台写回，从而在"下游不可控"的前提下仍保证平台独立可用。

## What Changes

- 新增**逻辑数据集**能力：定义 `BASE`（基表直通）、`DERIVED`（对其它数据集的 join/投影/过滤）、`AGGREGATE`（度量+维度+时间粒度聚合）三类数据集，字段携带语义角色（id/dimension/measure/time/geo/computed）。
- 新增**数据集关系**能力：声明与推断数据集之间的 join 边（join 类型、基数、来源），提供关系图与血缘。
- 新增**数据集服务契约**能力：模型 API（读元数据与关系）、SQL 优先查询（数据集落成 Trino `fabric` catalog 视图）、指标写回（`AGGREGATE` dataset）、用量观测。
- 数据集具备**版本与生命周期**（DRAFT → PUBLISHED 不可变 → DEPRECATED）与**物化策略**（virtual | cache | materialized，先手动后自动）。
- 扩展**元数据建议**：建议候选覆盖 dataset / 字段语义角色 / 关系。
- 扩展**平台总览**：增加按 dataset × 应用 × 用户的用量观测，闭合"供给-消费"飞轮。

## Capabilities

### New Capabilities

- `logical-dataset`: 逻辑数据集的种类、字段与语义角色、版本与生命周期、物化策略及运营侧定义流程。
- `dataset-relations`: 数据集之间关系（join 边）的声明与推断、关系图、血缘追溯。
- `dataset-serving`: 面向下游的稳定契约——模型 API、SQL 视图暴露、指标写回、用量观测。

### Modified Capabilities

- `metadata-discovery`: 建议候选由 catalog/schema/table/column 扩展到 dataset、字段语义角色与关系。
- `platform-dashboard`: 总览增加按 dataset × 应用 × 用户的用量与性能观测。

## Impact

- **后端**：新增 `dataset`（定义/版本/发布）、`relation`（关系图/血缘）、`serving`（模型 API/视图编译/写回/观测）等领域模块；`metadata` 扩展建议类型；`stats`/`overview` 扩展用量维度；数据库新增数据集、字段、关系、版本、用量等表。
- **Trino**：新增 `fabric` catalog，将 dataset 编译为视图（AGGREGATE 物化时落为物化表）；复用既有动态 Catalog 与表级权限机制。
- **前端**：运营控制台新增数据集建模（SQL + 关系图）与发布/物化操作；总览新增数据集用量视图。
- **契约**：新增对外模型 API 与写回 API，需版本化与稳定 ID；对下游构成公开接口。
- **非目标**：不做完整指标平台（指标定义管理/看板/NL 问数产品），不做拖拽式重型建模器；这些由下游消费方承担。
