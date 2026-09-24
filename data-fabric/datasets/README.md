# Data Fabric 演示数据集（demo datasets）

面向「一个 SQL 跨多个办公系统查询」演示的可重放样例数据。数据按**业务域**拆分，
分别落在三个异构数据源上，通过**共用关联键**（`employee_id` / `dept_id` /
`cost_center_id` / 会计期间）支持跨源 JOIN。

```
 OceanBase（MySQL 模拟）        Greenplum（PostgreSQL 9.4 模拟）        达梦 DM8
 ┌────────────────────┐        ┌────────────────────┐        ┌────────────────────┐
 │ OA 办公域           │        │ 人力资源域          │        │ 财务域              │
 │  oa.employee       │◀员工──▶│  hr.department     │        │  finance.cost_center│
 │  oa.attendance     │        │  hr.position       │        │  finance.budget     │
 │  oa.leave_request  │        │  hr.salary         │        │  finance.reimburse  │
 │  oa.business_trip  │        │  hr.performance    │        │  finance.invoice    │
 │  oa.approval       │        │  hr.social_security│        │  finance.account_.. │
 │                    │        │  hr.contract       │        │                    │
 └────────────────────┘        └────────────────────┘        └────────────────────┘
        共用键：employee_id / dept_id / cost_center_id / period（会计期间）
```

## 1. 业务域与数据源映射

| 业务域 | 数据源 | 连接器（平台别名） | Schema | 表数 | 说明 |
|---|---|---|---|---|---|
| 办公 OA | OceanBase | `mysql`（平台显示为 OceanBase） | `oa` | 5 | 员工花名册、考勤、请假、出差、审批流 |
| 人力资源 | Greenplum | `postgresql`（平台显示为 Greenplum） | `hr` | 6 | 组织、职位、薪资、绩效、社保、合同 |
| 财务 | 达梦 DM8 | `dameng`（平台显示为 达梦） | `finance` | 5 | 成本中心、预算、报销、发票、费用科目 |

> 三个数据源与平台演示用的数据源名称（`ob_demo` / `gp_demo` / `dm_demo`）对应，
> 见 `register_tables.sh`。Trino 中的 catalog 名即平台数据源名。

## 2. 共用关联键与生成规则

数据不是随机堆砌：`employee_id` / `dept_id` / `cost_center_id` / `period` 在三个源里
用**同一套确定性公式**生成，因此跨源 JOIN 必然命中。

| 键 | 取值范围 | 生成规则（`n` 为员工序号 0..999，`p` 为期间序号 0..5） |
|---|---|---|
| `employee_id` | 1001..2000 | `1001 + n` |
| `dept_id` | 10..29 | `10 + (n % 20)` |
| `position_id` | 100..129 | `100 + ((n / 3) % 30)` |
| `cost_center_id` | 1000..1019 | `1000 + (n % 20)`（与 `dept_id` 一一对应：`1000 + dept_id - 10`） |
| `period` | 2026-01..2026-06 | `'2026-' \|\| LPAD(p + 1, 2, '0')` |

关系：

- `oa.employee.dept_id` → `hr.department.dept_id`（跨源）
- `oa.employee.position_id` → `hr.position.position_id`（跨源）
- `hr.department.cost_center_id` → `finance.cost_center.cost_center_id`（跨源）
- `oa.employee.employee_id` → `hr.salary.employee_id` / `finance.reimbursement.employee_id`（跨源）
- `hr.salary.period` / `hr.performance.period` / `finance.budget.period` / `finance.reimbursement.period` 使用同一 `period` 口径

## 3. 表清单（业务描述）

### 3.1 OA 办公域（`oceanbase.oa`）

| 表 | 业务含义 | 主键 | 关联键 | 量级 |
|---|---|---|---|---|
| `oa.employee` | 员工花名册：工号、姓名、性别、出生日期、联系方式、所属部门/职位、入职日期、在职状态 | `employee_id` | `employee_id`, `dept_id`, `position_id` | ~1,000 |
| `oa.attendance` | 考勤记录：上下班打卡、工时、考勤状态（正常/迟到/早退/缺勤/请假） | `attendance_id` | `employee_id` | ~5,000 |
| `oa.leave_request` | 请假申请：假别、起止日期、天数、审批人、审批状态 | `leave_id` | `employee_id` | ~2,000 |
| `oa.business_trip` | 出差申请：目的地、起止日期、天数、预算金额、审批状态 | `trip_id` | `employee_id` | ~1,000 |
| `oa.approval` | 审批流：业务类型（请假/出差/报销）、单据、审批动作与意见 | `approval_id` | `employee_id` | ~3,000 |

### 3.2 人力资源域（`greenplum.hr`）

| 表 | 业务含义 | 主键 | 关联键 | 量级 |
|---|---|---|---|---|
| `hr.department` | 部门/组织：部门名称、上级部门、对应成本中心、负责人、办公地点 | `dept_id` | `dept_id`, `cost_center_id` | 20 |
| `hr.position` | 职位：职位名称、职位序列、职级 | `position_id` | `position_id` | 30 |
| `hr.salary` | 月度薪资：基本工资、奖金、津贴、社保、个税、实发 | `salary_id` | `employee_id`, `dept_id`, `period` | ~6,000 |
| `hr.performance` | 绩效评估：KPI 得分、评级、评估人、评语 | `perf_id` | `employee_id`, `period` | ~3,000 |
| `hr.social_security` | 社保公积金：养老/医疗/失业/公积金缴纳额与基数 | `ss_id` | `employee_id`, `period` | ~6,000 |
| `hr.contract` | 劳动合同：合同类型、起止日期、签订日期、状态、约定薪资 | `contract_id` | `employee_id` | ~1,000 |

### 3.3 财务域（`finance`，达梦）

| 表 | 业务含义 | 主键 | 关联键 | 量级 |
|---|---|---|---|---|
| `finance.cost_center` | 成本中心：名称、归属部门、公司代码 | `cost_center_id` | `cost_center_id` | 20 |
| `finance.account_subject` | 费用科目：科目编码、名称、类型 | `subject_id` | `subject_id` | 30 |
| `finance.budget` | 预算执行：成本中心 + 期间 + 科目的预算额与已用额 | `budget_id` | `cost_center_id`, `period`, `subject_id` | ~360 |
| `finance.reimbursement` | 费用报销：申请人、部门、成本中心、科目、期间、金额、审批状态 | `reimb_id` | `employee_id`, `dept_id`, `cost_center_id`, `period` | ~5,000 |
| `finance.invoice` | 发票：发票号、类型、金额、税额、开票日期、销方 | `invoice_id` | `reimb_id` | ~4,000 |

## 4. 量级与真实性

- 每域千行级，总量约 **3.7 万行**；足以支撑 JOIN / 聚合 / 图表。
- 中文姓名（姓氏 + 名字组合）、跨季度日期（2026-01 ~ 2026-06）、合理金额量级。
- 少量 NULL：证件号、上级/审批人、奖金、评语、发票关联等按 `MOD(n, k)` 稀疏置空。

## 5. 目录与脚本

```
data-fabric/datasets/
  README.md                    本文件（域设计 + 业务描述 + seed 说明）
  seed_all.sh                  一键应用三域 seed（空环境可重复执行）
  register_tables.sh           通过平台 API 注册数据源 + 表 + 业务描述
  examples/sample_queries.sql  一键示例查询（跨源 JOIN）
  oceanbase/seed_oa.sql        OA 域建表 + 造数（MySQL 方言）
  greenplum/seed_hr.sql        HR 域建表 + 造数（PostgreSQL 9.4 方言）
  dm8/seed_finance.sql         财务域建表 + 造数（达梦 DM8 方言）
  dm8/seed_dm8.sql             e2e 冒烟测试用最小表 `DEMO.EMP`（保留）
```

## 6. 如何加载

前置：`data-fabric/deploy` 栈已启动（`docker compose up -d`），且
`deploy/.env` 中已有 MySQL 密码（`MYSQL_ROOT_PASSWORD`）。

```bash
cd data-fabric/datasets
./seed_all.sh            # 幂等：空环境建表造数，重复执行结果一致
./register_tables.sh     # 可选：把表注册进平台并写入业务描述
```

`seed_all.sh` 通过容器内的 `mysql` / `psql` / `disql` 客户端执行脚本，
**不落任何凭据到仓库**（密码从 `deploy/.env` 读取）。

单域手动执行：

```bash
# OA（OceanBase / MySQL）
docker exec -i data-fabric-mysql sh -c 'MYSQL_PWD=<pwd> mysql -uroot' < oceanbase/seed_oa.sql
# HR（Greenplum / PostgreSQL 9.4）
docker exec -i postgres94 psql -U postgres -d gp_source < greenplum/seed_hr.sql
# 财务（达梦 DM8；必须带 LANG=zh_CN.UTF-8，否则 DIsql 对含多个中文字符串常量的语句分词出错）
docker exec -i -e LANG=zh_CN.UTF-8 data-fabric-dm8 \
  /opt/dmdbms/bin/disql SYSDBA/SYSDBA_dm001@127.0.0.1:5236 < dm8/seed_finance.sql
```

### 幂等策略

- **OA**：先 `CREATE DATABASE IF NOT EXISTS oa`，再 `DROP TABLE IF EXISTS` + `CREATE TABLE` + `INSERT`，重复执行为全量重建。
- **HR**：先 `DROP SCHEMA IF EXISTS hr CASCADE` + `CREATE SCHEMA hr`，再建表造数。
- **财务**：先 `DROP SCHEMA IF EXISTS finance CASCADE`，再 `CREATE SCHEMA finance ...`（`/` 结束），然后建表造数。
- 三域均以确定性公式造数，**同一脚本任意次执行结果一致**，不产生重复或脏数据。
