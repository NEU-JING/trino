-- =============================================================================
-- Data Fabric demo - one-click sample queries (cross-source federation)
--
-- Catalog names are the platform data-source names registered by
-- datasets/register_tables.sh:
--   ob_demo.oa.*        OA domain        (OceanBase / MySQL)
--   gp_demo.hr.*        HR domain        (Greenplum / PostgreSQL)
--   dm_demo.finance.*   Finance domain   (Dameng DM8)
--
-- Run any statement below through the SQL workbench, or directly against Trino.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- Q1. Data sanity: row counts across all three sources (one click, fast).
-- ---------------------------------------------------------------------------
SELECT 'OA employees'            AS dataset, COUNT(*) AS rows_loaded FROM ob_demo.oa.employee
UNION ALL
SELECT 'HR payroll rows',                     COUNT(*)              FROM gp_demo.hr.salary
UNION ALL
SELECT 'Finance reimbursements',              COUNT(*)              FROM dm_demo.finance.reimbursement;

-- ---------------------------------------------------------------------------
-- Q2. 跨源员工全貌: OA + HR (employee roster + org + contract + payroll).
-- ---------------------------------------------------------------------------
SELECT e.employee_id,
       e.name,
       e.dept_id,
       d.dept_name,
       p.position_name,
       c.contract_type,
       s.base_salary,
       s.net_pay
FROM ob_demo.oa.employee e
JOIN gp_demo.hr.department d ON e.dept_id = d.dept_id
JOIN gp_demo.hr.position p ON e.position_id = p.position_id
LEFT JOIN gp_demo.hr.contract c ON e.employee_id = c.employee_id
LEFT JOIN gp_demo.hr.salary s ON e.employee_id = s.employee_id AND s.period = '2026-06'
WHERE e.employ_status = '在职'
ORDER BY e.employee_id
LIMIT 50;

-- ---------------------------------------------------------------------------
-- Q3. 人力成本 vs 财务预算: HR payroll (Greenplum) JOIN finance budget (DM8).
-- Aggregated on both sides before the JOIN to avoid fan-out.
-- ---------------------------------------------------------------------------
WITH payroll AS (
    SELECT d.dept_name,
           d.cost_center_id,
           s.period,
           SUM(s.net_pay)                                AS payroll_cost,
           SUM(s.base_salary + COALESCE(s.bonus, 0) + s.allowance) AS gross_cost
    FROM gp_demo.hr.salary s
    JOIN gp_demo.hr.department d ON s.dept_id = d.dept_id
    GROUP BY d.dept_name, d.cost_center_id, s.period
),
budget AS (
    SELECT cost_center_id,
           period,
           SUM(budget_amount) AS budget_amount,
           SUM(used_amount)   AS used_amount
    FROM dm_demo.finance.budget
    GROUP BY cost_center_id, period
)
SELECT p.dept_name,
       p.period,
       ROUND(p.payroll_cost, 2)              AS payroll_cost,
       ROUND(p.gross_cost, 2)                AS gross_cost,
       ROUND(b.budget_amount, 2)             AS budget_amount,
       ROUND(b.used_amount, 2)               AS budget_used,
       ROUND(b.budget_amount - p.payroll_cost, 2) AS budget_surplus
FROM payroll p
JOIN budget b ON p.cost_center_id = b.cost_center_id AND p.period = b.period
ORDER BY p.dept_name, p.period
LIMIT 100;

-- ---------------------------------------------------------------------------
-- Q4. 三源联合: 人力成本 + 报销 (OceanBase + Greenplum + Dameng) vs 预算.
-- ---------------------------------------------------------------------------
WITH payroll AS (
    SELECT dept_id, period, SUM(net_pay) AS payroll
    FROM gp_demo.hr.salary
    GROUP BY dept_id, period
),
reimb AS (
    SELECT e.dept_id, r.period, SUM(r.amount) AS reimbursement
    FROM dm_demo.finance.reimbursement r
    JOIN ob_demo.oa.employee e ON r.employee_id = e.employee_id
    GROUP BY e.dept_id, r.period
),
budget AS (
    SELECT cost_center_id, period, SUM(budget_amount) AS budget
    FROM dm_demo.finance.budget
    GROUP BY cost_center_id, period
)
SELECT d.dept_name,
       p.period,
       ROUND(p.payroll, 2)                              AS payroll_cost,
       ROUND(COALESCE(r.reimbursement, 0), 2)           AS reimbursement,
       ROUND(b.budget, 2)                               AS budget_amount,
       ROUND(b.budget - p.payroll - COALESCE(r.reimbursement, 0), 2) AS remaining_budget
FROM gp_demo.hr.department d
JOIN payroll p ON d.dept_id = p.dept_id
JOIN budget b ON d.cost_center_id = b.cost_center_id AND p.period = b.period
LEFT JOIN reimb r ON r.dept_id = d.dept_id AND r.period = p.period
ORDER BY d.dept_name, p.period
LIMIT 100;

-- ---------------------------------------------------------------------------
-- Q5. 员工 360 画像: 花名册 + 组织 + 薪资 + 报销 + 绩效 (single employee).
-- ---------------------------------------------------------------------------
SELECT e.employee_id,
       e.name,
       d.dept_name,
       p.position_name,
       s.period,
       s.net_pay,
       COALESCE(r.reimb, 0) AS reimbursement,
       perf.kpi_score,
       perf.rating
FROM ob_demo.oa.employee e
JOIN gp_demo.hr.department d ON e.dept_id = d.dept_id
JOIN gp_demo.hr.position p ON e.position_id = p.position_id
JOIN gp_demo.hr.salary s ON e.employee_id = s.employee_id AND s.period = '2026-06'
LEFT JOIN (
    SELECT employee_id, period, SUM(amount) AS reimb
    FROM dm_demo.finance.reimbursement
    WHERE period = '2026-06'
    GROUP BY employee_id, period
) r ON r.employee_id = e.employee_id
LEFT JOIN gp_demo.hr.performance perf ON perf.employee_id = e.employee_id AND perf.period = '2026-06'
WHERE e.employee_id = 1100;

-- ---------------------------------------------------------------------------
-- Q6. OA-only check: 出差申请 + 预算金额 (OceanBase), a non-federated control.
-- ---------------------------------------------------------------------------
SELECT e.name,
       t.destination,
       t.start_date,
       t.end_date,
       t.budget_amount,
       t.status
FROM ob_demo.oa.business_trip t
JOIN ob_demo.oa.employee e ON t.employee_id = e.employee_id
ORDER BY t.budget_amount DESC NULLS LAST
LIMIT 20;
