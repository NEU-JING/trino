# 逻辑数据集模型与对外契约

> 对应变更：`openspec/changes/add-logical-dataset-model`
> 定位：data-fabric 是底座平台，向上为数据应用产品与未来指标平台提供统一的数据集服务。

## 1. 分层与边界

```
数据应用产品 / 指标平台      自助查询 · 拖拽看板 · 指标定义 · 智能问数
        ▲  模型 API / 写回 API / SQL 查询 / 用量
        │
data-fabric  逻辑数据集（BASE / DERIVED / AGGREGATE）+ 关系 + 字段语义 + 版本 + 物化 + 治理
        ▲  Trino 联邦 + 表级权限
        │
OceanBase / Greenplum / 达梦 / 湖仓
```

data-fabric 做**语义基质**，不做指标产品。指标表达为一种 `AGGREGATE` 数据集，由指标平台写回。

## 2. 数据集

| 字段 | 说明 |
|---|---|
| `uid` | 稳定标识，跨版本不变，消费方据此绑定 |
| `name` | 业务名，同时是 `fabric.<domain>.<name>` 视图名 |
| `kind` | `BASE` / `DERIVED` / `AGGREGATE` |
| `status` | `DRAFT` / `PUBLISHED` / `DEPRECATED` |
| `currentVersion` | 已发布版本号；PUBLISHED 版本不可变 |
| `materializationMode` | `VIRTUAL` / `MATERIALIZED` |
| `definitionJson` | 结构定义（见下） |

`DatasetDefinition`：`baseTableId`（BASE）、`inputs`（DERIVED/AGGREGATE 输入数据集 uid）、
`joins`（join 边）、`projections`、`filters`、`measures`、`groupBy`、`timeGrain`。
输入数据集在编译后的 SQL 中按顺序别名为 `t0`、`t1`……

字段（`dataset_field`）：`name`、`label`、`dataType`、`role`（语义角色）、`aggregation`、
`timeGrain`、`format`、`unit`、`nullable`、`ordinal`。

## 3. 关系与血缘

- **关系**：有向 join 边 `from(uid,field) → to(uid,field)`，含 `joinType`、`cardinality`、
  `origin`（`DECLARED` 运营确认 / `INFERRED` 命名与取值推断）。关系可被多个数据集复用。
- **推断**：按 `*_id` / `*_no` / `*_code` / `*_key` 等命名约定匹配；推断结果需确认后才用于生产建议。
- **血缘**：上游 = 输入数据集与 BASE 的物理表；下游 = 引用该数据集的数据集与应用。

## 4. 编译与同源

`DatasetSqlCompiler` 是唯一真源：同一份定义编译为
① `fabric` catalog 中的视图（`CREATE OR REPLACE VIEW ... SECURITY INVOKER`，物化时为 CTAS 表）；
② 模型 API 返回的字段与语义。视图采用 `INVOKER` 安全模式，源表授权仍由 Trino 引擎强制。

`fabric` catalog 由 `deploy/trino/catalog/fabric.properties` 提供（原型使用 Trino `memory` 连接器承载视图/物化表；生产环境可替换为 Hive/Iceberg 等持久化连接器）。

## 5. 契约 API

| 用途 | 方法 | 路径 |
|---|---|---|
| 列出可消费数据集 | GET | `/api/model/datasets` |
| 读取数据集模型 | GET | `/api/model/datasets/{uid}` |
| 版本列表 | GET | `/api/model/datasets/{uid}/versions` |
| 关系 / 关系图 / 血缘 | GET | `/api/model/relations`、`/graph`、`/lineage/{uid}` |
| 指标写回（AGGREGATE） | POST | `/api/model/datasets` |
| 发布新版本 | PUT | `/api/model/datasets/{uid}/versions` |
| 运营管理 | POST/PUT/DELETE | `/api/datasets` |
| 用量观测（仅运营） | GET | `/api/usage/datasets`、`/datasets/{uid}` |

写回仅接受 `AGGREGATE`，且同名数据集仅允许其 owner 覆盖（命名空间隔离）。

## 6. 物化与观测

第一阶段：`VIRTUAL` 视图实时计算；`MATERIALIZED` 由运营手动刷新（drop + CTAS）。
用量按 `数据集 × 应用 × 用户` 记录查询次数、失败与延迟，作为后续自动物化策略与数据集淘汰的输入。
