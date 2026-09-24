# data-fabric 访问与使用指南

> 面向两类使用者：
> - **运营人员（OPERATOR）**：供给侧，接入数据、构建数据集、发布与治理。
> - **数据应用产品（消费侧）**：以 QUERY_USER/服务账号访问已发布数据集，做自助查询、看板与指标。
>
> 底座定位：data-fabric 整合数据后，以**逻辑数据集**为唯一对外原语提供服务；最终用户由应用产品触达，不直接关心数据来源。

## 0. 角色与认证

| 角色 | 说明 | 能力边界 |
|---|---|---|
| `OPERATOR` | 运营管理员 | 全部读写：数据源/表/数据集/关系/权限/用户，可查询全部数据 |
| `QUERY_USER` | 查询用户 | 只读：浏览并查询被授权/可消费的数据集与表 |

认证（平台 REST）：

```
POST /api/auth/login   {"username":"...","password":"..."}
  → {"token":"...","username":"...","role":"OPERATOR|QUERY_USER"}
```

后续请求带 `Authorization: Bearer <token>`（也支持 HTTP Basic）。`POST /api/auth/logout` 吊销 token；`GET /api/auth/me` 查看当前身份。

---

## A. 运营人员：供给侧

### A.1 工作流

```
接入数据源 → 发现/注册表 → 定义数据集 → 声明/推断关系 → 发布(fabric视图)
   → 配置物化 → 授权 → 观测用量 → 迭代(弃用/删除/重物化)
```

### A.2 能力与入口

| 阶段 | 能力 | 主要 API | 控制台页面 |
|---|---|---|---|
| 接入 | 注册/测试/更新/停用数据源（动态 Catalog，业务别名对外） | `POST/GET/PUT/DELETE /api/data-sources`、`POST /api/data-sources/test` | 数据源管理 |
| 注册表 | 发现 schema/表、单张/批量注册、写业务描述（支持描述模板） | `GET /api/data-sources/{id}/tables`、`POST/GET/DELETE /api/tables` | 表目录 |
| 建数据集 | 建 `BASE`/`DERIVED`/`AGGREGATE`、编辑草稿定义、标注字段语义角色 | `POST /api/datasets`、`PUT /api/datasets/{uid}/definition` | 数据集 → 新建 |
| 关系 | 自动**推断**候选 join、人工**确认**、手动**声明**、查看关系图 | `POST /api/model/relations/infer`、`POST /api/model/relations`、`POST /api/model/relations/{id}/confirm`、`GET /api/model/graph` | 数据集 → 关系图/关系清单 |
| 发布 | 发布生成 `fabric.<domain>.<name>` 视图（版本不可变）、弃用、删除 | `POST /api/datasets/{uid}/publish`、`.../deprecate`、`DELETE /api/datasets/{uid}` | 数据集 → 发布/弃用 |
| 物化 | 设 `virtual/materialized`、手动刷新、查看新鲜度 | `PUT /api/datasets/{uid}/materialization`、`POST .../materialization/refresh` | 数据集 → 刷新 |
| 授权 | 表级 grant/revoke（Trino 引擎强制）+ 用户管理 | `/api/permissions`、`/api/admin/users` | 权限管理、用户管理 |
| 观测 | 按 `dataset × 应用 × 用户` 看用量/延迟/失败 | `GET /api/usage/datasets`、`/datasets/{uid}` | 总览 → 数据集用量 |
| 总览 | KPI、数据源健康、拓扑、最近查询 | `GET /api/overview/stats`、`/topology` | 总览 |

### A.3 发布语义（运营需理解）

- 数据集有**稳定 `uid`**（不随版本变化），消费方按 `uid` 绑定。
- **发布 = 生成一个不可变的 `PUBLISHED` 版本**，并把定义编译为 `fabric` catalog 里的查询对象：
  - `virtual` → 视图（`CREATE OR REPLACE VIEW ... SECURITY INVOKER`）
  - `materialized` → 表（`CREATE TABLE AS SELECT`，由刷新触发）
- 破坏性变更（删字段/改类型）通过**发布新版本**完成，旧版本保持可用。
- `DEPRECATED` 后不再出现在面向应用的默认可消费列表。

### A.4 定义数据集的三类形态

```jsonc
// BASE：指向一张已注册表
{"name":"customers_base","kind":"BASE","domain":"oa","baseTableId":123,
 "fields":[{"name":"customer_id","role":"ID"},{"name":"name","role":"DIMENSION"}]}

// DERIVED：对多个数据集 join + 投影 + 过滤（inputs 顺序别名 t0,t1,...）
{"name":"orders_enriched","kind":"DERIVED","domain":"oa",
 "inputs":["<orders_uid>","<customers_uid>"],
 "joins":[{"leftDatasetUid":"<orders_uid>","leftField":"customer_id",
           "rightDatasetUid":"<customers_uid>","rightField":"customer_id",
           "joinType":"LEFT","cardinality":"N:1"}],
 "projections":["t0.order_id","t1.name AS customer_name"],
 "filters":["t0.status = 'paid'"]}

// AGGREGATE：度量 + 维度 +（可选）时间粒度；指标即带业务剖面的 AGGREGATE
{"name":"monthly_dept_sales","kind":"AGGREGATE","domain":"finance",
 "inputs":["<orders_enriched_uid>"],
 "groupBy":["t0.dept_name","date_trunc('month', t0.paid_at) AS month"],
 "measures":[{"name":"sales","expression":"sum(t0.amount)","aggregation":"SUM"}],
 "filters":["t0.status = 'paid'"],
 "fields":[{"name":"dept_name","role":"DIMENSION"},
           {"name":"month","role":"TIME","timeGrain":"month"},
           {"name":"sales","role":"MEASURE","aggregation":"SUM","unit":"元"}]}
```

字段语义角色：`id` / `dimension` / `measure` / `time` / `geo` / `computed`，度量可带默认聚合，时间可带粒度。

---

## B. 数据应用产品：消费侧

### B.1 两条访问路径

```
   数据应用产品
        ├─ A. 平台 REST（推荐）：认证/鉴权/用量/导出统一走平台
        └─ B. SQL 直连 Trino：查 fabric.<domain>.<name>，用 X-Trino-User
```

- **A（推荐）**：应用持 token 调平台查询 API，平台以该用户身份执行 Trino 查询，权限/结果/导出/用量闭环。
- **B**：已有 JDBC/BI 工具直连 Trino `:8080`，查询数据集视图；身份靠 `X-Trino-User`，权限由平台下发的 access-control 规则控制。

### B.2 消费侧 API

| 用途 | 方法 & 路径 |
|---|---|
| 列出可消费数据集 | `GET /api/model/datasets` |
| 读取数据集模型（字段/语义角色/版本） | `GET /api/model/datasets/{uid}` |
| 版本历史 | `GET /api/model/datasets/{uid}/versions` |
| 关系 / 关系图 / 血缘 | `GET /api/model/relations[?dataset=uid]`、`/graph`、`/lineage/{uid}` |
| 提交查询 | `POST /api/queries` `{"sql":"..."}`（可加 `X-Data-Fabric-Application` 头做用量归属） |
| 轮询结果 | `GET /api/queries/{id}` |
| 取消查询 | `POST /api/queries/{id}/cancel` |
| 导出结果 | `GET /api/queries/{id}/export?format=csv\|xlsx` |
| 指标写回（OPERATOR） | `POST /api/model/datasets`（kind=AGGREGATE）、`PUT /api/model/datasets/{uid}/versions` |

### B.3 示例

```bash
# 登录
TOKEN=$(curl -s -X POST http://host:8001/api/auth/login \
  -H 'Content-Type: application/json' -d '{"username":"viewer","password":"viewer"}' | jq -r .token)

# 发现数据集
curl -s http://host:8001/api/model/datasets -H "Authorization: Bearer $TOKEN"
curl -s http://host:8001/api/model/datasets/<uid> -H "Authorization: Bearer $TOKEN"

# 查询（SQL-first）
QID=$(curl -s -X POST http://host:8001/api/queries \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -H 'X-Data-Fabric-Application: my-dashboard' \
  -d '{"sql":"SELECT * FROM fabric.finance.monthly_dept_sales"}' | jq -r .queryId)
curl -s http://host:8001/api/queries/$QID -H "Authorization: Bearer $TOKEN"
curl -s "http://host:8001/api/queries/$QID/export?format=xlsx" -H "Authorization: Bearer $TOKEN" -o out.xlsx
```

```
// B 路径：JDBC 直连
jdbc:trino://host:8080/fabric/finance?user=viewer
SELECT * FROM monthly_dept_sales;
```

---

## C. 治理模型

- **数据集是治理单元**（设计目标）：权限/行过滤/列脱敏最终挂在数据集上。
- **当前实现**：数据集视图为 `SECURITY INVOKER`，查询权限**继承底层源表授权**（表级 grant，Trino 引擎强制）；`fabric` catalog 对所有已认证用户开放只读。
- **运营**可查询全部数据；**应用/查询用户**只能查到被授权表所支撑的数据集结果。

## D. 当前边界与限制

- **fabric catalog 使用 Trino `memory` 连接器**（原型）：Trino 重启后视图/物化表丢失（需重新发布）；物化数据受 `memory.max-data-per-node=128MB` 限制，仅适合演示/小表。虚拟视图不受此限（仅存定义，不存数据）。
- **无数据集级独立授权**：查询权限继承源表（见 C）。
- **直连 Trino（B 路径）无平台 token 鉴权**：靠 `X-Trino-User` 声明身份；生产建议统一走 A 路径或为 Trino 配置认证。
- **用量统计**按 SQL 文本是否包含数据集名匹配，可能误配；后续可改为解析查询计划/视图依赖。
- **写回**仅支持 `AGGREGATE`；同名数据集仅 owner 可覆盖（命名空间隔离）。
- 暂无 SDK / OpenAPI 文档。
