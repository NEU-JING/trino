## Why

当前部署环境的样例数据仅存在于远端服务器，仓库内**没有任何 seed 脚本**（`e2e/smoke_test.py` 只建了 orders/customers 两张玩具表），演示环境一旦重建即不可复现。数据量极小、无业务语义、无跨源关联，无法支撑「跨源联邦查询」这一核心价值的演示。

## What Changes

- 按办公系统的真实业务域构建样例数据：**办公 OA、人力资源、财务**等。
- 将各业务域分布到不同数据源，天然制造跨源 JOIN 场景（如「人力成本 vs 财务预算」）。
- 用**共用关联键**打通：`employee_id` / `dept_id` / `cost_center_id` / 会计期间。
- 数据量提升到**几千至几万行**，中文姓名、跨季度日期、合理金额量级、少量 NULL。
- 提供**可重放的 seed 脚本**（随仓库版本化），保证演示环境可重建。
- 为每张表补充平台侧业务描述，使非技术观众可理解。

## Capabilities

### New Capabilities

- `demo-datasets`: 可重放、彼此关联、覆盖多办公业务域的样例数据集。

### Modified Capabilities

<!-- 无 requirement 级变更。 -->

## Impact

- `data-fabric/deploy/` 或 `data-fabric/datasets/`：新增建表与数据 seed 脚本（多数据源）。
- 远端部署流程：新增数据初始化步骤。
- 与 `add-dameng-connector` 协同：达梦作为其中一个业务域的数据源。
- 演示叙事依赖本数据集（一键示例查询）。
