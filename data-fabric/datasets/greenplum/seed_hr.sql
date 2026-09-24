-- =============================================================================
-- Data Fabric demo dataset - HR domain (Greenplum / PostgreSQL 9.4 dialect)
-- Source: pre-existing container `postgres94` (database gp_source)
-- Schema: hr
--
-- Idempotent: DROP SCHEMA ... CASCADE then recreate, so re-running is a clean
-- rebuild (PostgreSQL 9.4 has no ON CONFLICT / upsert).
-- Deterministic: same input -> same output on every run.
--
-- Apply (see datasets/README.md):
--   docker exec -i postgres94 psql -U postgres -d gp_source < greenplum/seed_hr.sql
-- =============================================================================

DROP SCHEMA IF EXISTS hr CASCADE;
CREATE SCHEMA hr;

-- ---------------------------------------------------------------------------
-- hr.department : organization units (20 rows)
-- ---------------------------------------------------------------------------
CREATE TABLE hr.department (
    dept_id        INTEGER      NOT NULL PRIMARY KEY,
    dept_name      VARCHAR(50)  NOT NULL,
    parent_dept_id INTEGER,
    cost_center_id INTEGER      NOT NULL,
    manager_id     BIGINT,
    location       VARCHAR(50),
    created_at     TIMESTAMP
);

INSERT INTO hr.department
    (dept_id, dept_name, parent_dept_id, cost_center_id, manager_id, location, created_at)
SELECT 10 + g,
       (ARRAY['总裁办公室','人力资源部','财务部','行政部','采购部','销售一部','销售二部','市场部',
              '研发中心','产品部','技术支持部','质量管理部','法务部','审计部','战略投资部',
              '客户成功部','供应链部','数据智能部','信息安全部','国际业务部'])[1 + g],
       CASE WHEN g < 3 THEN NULL ELSE 10 + (g % 3) END,
       1000 + g,
       CASE WHEN g % 5 = 0 THEN NULL ELSE 1001 + ((g * 47) % 1000) END,
       (ARRAY['北京','上海','广州','深圳','成都','杭州','武汉','西安'])[1 + (g % 8)],
       TIMESTAMP '2026-06-30 09:00:00'
FROM generate_series(0, 19) AS g;

-- ---------------------------------------------------------------------------
-- hr.position : job positions (30 rows)
-- ---------------------------------------------------------------------------
CREATE TABLE hr.position (
    position_id   INTEGER     NOT NULL PRIMARY KEY,
    position_name VARCHAR(50) NOT NULL,
    job_family    VARCHAR(30),
    job_level     VARCHAR(10),
    created_at    TIMESTAMP
);

INSERT INTO hr.position
    (position_id, position_name, job_family, job_level, created_at)
SELECT 100 + g,
       (ARRAY['软件工程师','高级软件工程师','技术专家','架构师','测试工程师','运维工程师',
              '产品经理','高级产品经理','产品总监','项目经理','数据分析师','数据工程师',
              '算法工程师','UI设计师','交互设计师','销售代表','销售经理','大客户经理',
              '市场专员','市场经理','品牌经理','招聘专员','HRBP','薪酬绩效专员',
              '会计','财务分析师','财务经理','法务专员','采购专员','行政专员'])[1 + g],
       (ARRAY['技术','技术','技术','技术','技术','技术','产品','产品','产品','技术','数据','数据',
              '算法','设计','设计','销售','销售','销售','市场','市场','市场','职能','职能','职能',
              '职能','财务','财务','职能','供应链','职能'])[1 + g],
       'P' || (4 + (g % 6)),
       TIMESTAMP '2026-06-30 09:00:00'
FROM generate_series(0, 29) AS g;

-- ---------------------------------------------------------------------------
-- hr.salary : monthly payroll (~6,000 rows = 1,000 employees x 6 months)
-- ---------------------------------------------------------------------------
CREATE TABLE hr.salary (
    salary_id       BIGINT       NOT NULL PRIMARY KEY,
    employee_id     BIGINT       NOT NULL,
    dept_id         INTEGER      NOT NULL,
    period          VARCHAR(7)   NOT NULL,
    base_salary     DECIMAL(12,2),
    bonus           DECIMAL(12,2),
    allowance       DECIMAL(12,2),
    social_security DECIMAL(12,2),
    tax             DECIMAL(12,2),
    net_pay         DECIMAL(12,2),
    pay_date        DATE,
    created_at      TIMESTAMP
);

INSERT INTO hr.salary
    (salary_id, employee_id, dept_id, period, base_salary, bonus, allowance,
     social_security, tax, net_pay, pay_date, created_at)
SELECT 1 + e,
       1001 + (e % 1000),
       10 + ((e % 1000) % 20),
       '2026-' || LPAD(((e / 1000) + 1)::text, 2, '0'),
       ROUND((8000 + ((e % 1000) % 40) * 250)::numeric, 2),
       CASE WHEN e % 12 = 0 THEN NULL
            ELSE ROUND((((e % 1000) % 20) * 300 + (e / 1000) * 137)::numeric, 2) END,
       ROUND((500 + ((e % 1000) % 10) * 100)::numeric, 2),
       ROUND(((8000 + ((e % 1000) % 40) * 250) * 0.105)::numeric, 2),
       ROUND(((8000 + ((e % 1000) % 40) * 250) * 0.12)::numeric, 2),
       ROUND(((8000 + ((e % 1000) % 40) * 250)
              + COALESCE(CASE WHEN e % 12 = 0 THEN NULL
                              ELSE ((((e % 1000) % 20) * 300 + (e / 1000) * 137)::numeric) END, 0)
              + (500 + ((e % 1000) % 10) * 100)
              - ((8000 + ((e % 1000) % 40) * 250) * 0.105)
              - ((8000 + ((e % 1000) % 40) * 250) * 0.12))::numeric, 2),
       (DATE '2026-01-01' + (((e / 1000))::text || ' month')::interval + interval '24 days')::date,
       TIMESTAMP '2026-06-30 09:00:00'
FROM generate_series(0, 5999) AS e;

-- ---------------------------------------------------------------------------
-- hr.performance : performance reviews (~3,000 rows = 1,000 x 3 cycles)
-- ---------------------------------------------------------------------------
CREATE TABLE hr.performance (
    perf_id     BIGINT       NOT NULL PRIMARY KEY,
    employee_id BIGINT       NOT NULL,
    period      VARCHAR(7)   NOT NULL,
    kpi_score   DECIMAL(5,1),
    rating      VARCHAR(4),
    reviewer_id BIGINT,
    review_date DATE,
    comment    VARCHAR(200),
    created_at  TIMESTAMP
);

INSERT INTO hr.performance
    (perf_id, employee_id, period, kpi_score, rating, reviewer_id, review_date, comment, created_at)
SELECT 1 + e,
       1001 + (e % 1000),
       '2026-0' || (2 * (e / 1000) + 2),
       ROUND((62 + ((e % 1000) % 38) + (e / 1000) * 0.3)::numeric, 1),
       CASE WHEN (62 + ((e % 1000) % 38)) >= 92 THEN 'A'
            WHEN (62 + ((e % 1000) % 38)) >= 82 THEN 'B+'
            WHEN (62 + ((e % 1000) % 38)) >= 72 THEN 'B'
            ELSE 'C' END,
       CASE WHEN e % 17 = 0 THEN NULL ELSE 1001 + ((e % 1000 + 53) % 1000) END,
       (DATE '2026-01-01' + ((2 * (e / 1000) + 2)::text || ' month')::interval + interval '27 days')::date,
       CASE WHEN e % 4 = 0 THEN NULL
            ELSE (ARRAY['目标达成良好，继续保持','超额完成季度目标','需加强协作与主动性',
                        '工作质量稳定','建议提升交付效率'])[1 + (e % 5)] END,
       TIMESTAMP '2026-06-30 09:00:00'
FROM generate_series(0, 2999) AS e;

-- ---------------------------------------------------------------------------
-- hr.social_security : social insurance & housing fund (~6,000 rows)
-- ---------------------------------------------------------------------------
CREATE TABLE hr.social_security (
    ss_id        BIGINT       NOT NULL PRIMARY KEY,
    employee_id  BIGINT       NOT NULL,
    period       VARCHAR(7)   NOT NULL,
    pension      DECIMAL(12,2),
    medical      DECIMAL(12,2),
    unemployment DECIMAL(12,2),
    housing_fund DECIMAL(12,2),
    base_amount  DECIMAL(12,2),
    created_at   TIMESTAMP
);

INSERT INTO hr.social_security
    (ss_id, employee_id, period, pension, medical, unemployment, housing_fund, base_amount, created_at)
SELECT 1 + e,
       1001 + (e % 1000),
       '2026-' || LPAD(((e / 1000) + 1)::text, 2, '0'),
       ROUND(((8000 + ((e % 1000) % 40) * 250) * 0.08)::numeric, 2),
       ROUND(((8000 + ((e % 1000) % 40) * 250) * 0.02)::numeric, 2),
       ROUND(((8000 + ((e % 1000) % 40) * 250) * 0.005)::numeric, 2),
       ROUND(((8000 + ((e % 1000) % 40) * 250) * 0.07)::numeric, 2),
       ROUND((8000 + ((e % 1000) % 40) * 250)::numeric, 2),
       TIMESTAMP '2026-06-30 09:00:00'
FROM generate_series(0, 5999) AS e;

-- ---------------------------------------------------------------------------
-- hr.contract : labor contracts (1,000 rows)
-- ---------------------------------------------------------------------------
CREATE TABLE hr.contract (
    contract_id   BIGINT       NOT NULL PRIMARY KEY,
    employee_id   BIGINT       NOT NULL,
    contract_type VARCHAR(20)  NOT NULL,
    start_date    DATE         NOT NULL,
    end_date      DATE,
    sign_date     DATE,
    status        VARCHAR(20),
    salary_agreed DECIMAL(12,2),
    created_at    TIMESTAMP
);

INSERT INTO hr.contract
    (contract_id, employee_id, contract_type, start_date, end_date, sign_date, status, salary_agreed, created_at)
SELECT 1 + n,
       1001 + n,
       (ARRAY['固定期限','无固定期限','实习协议'])[1 + (n % 3)],
       (DATE '2015-01-01' + ((n * 23) % 3650)),
       CASE WHEN n % 3 = 1 THEN NULL
            ELSE (DATE '2015-01-01' + ((n * 23) % 3650) + 365 * 3) END,
       (DATE '2015-01-01' + ((n * 23) % 3650) - 7),
       CASE WHEN n % 4 = 0 THEN '已到期'
            WHEN n % 4 = 1 THEN '生效中'
            WHEN n % 4 = 2 THEN '生效中'
            ELSE '待续签' END,
       ROUND((8000 + (n % 40) * 250)::numeric, 2),
       TIMESTAMP '2026-06-30 09:00:00'
FROM generate_series(0, 999) AS n;
