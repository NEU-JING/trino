-- =============================================================================
-- Data Fabric demo dataset - Finance domain (Dameng DM8 dialect)
-- Source: deploy/docker-compose.yml service `dm8` (container data-fabric-dm8)
-- Schema: finance
--
-- Idempotent: DROP SCHEMA ... CASCADE then recreate, so re-running is a clean
-- rebuild. DIsql needs a trailing "/" to terminate a bare CREATE SCHEMA.
-- Deterministic: same input -> same output on every run.
--
-- Apply (see datasets/README.md):
--   docker exec -i -e LANG=zh_CN.UTF-8 data-fabric-dm8 /opt/dmdbms/bin/disql \
--     SYSDBA/SYSDBA_dm001@127.0.0.1:5236 < dm8/seed_finance.sql
-- LANG=zh_CN.UTF-8 is required, otherwise DIsql mis-tokenizes any statement
-- that contains more than one multi-byte (Chinese) string literal.
-- =============================================================================

DROP SCHEMA IF EXISTS FINANCE CASCADE;
CREATE SCHEMA FINANCE AUTHORIZATION SYSDBA
/

-- ---------------------------------------------------------------------------
-- finance.cost_center : cost centers (20 rows)
-- ---------------------------------------------------------------------------
CREATE TABLE FINANCE.COST_CENTER (
    cost_center_id   INT          NOT NULL PRIMARY KEY,
    cost_center_name VARCHAR(50)  NOT NULL,
    owner_dept_id    INT          NOT NULL,
    company_code     VARCHAR(20),
    created_at       TIMESTAMP
);

INSERT INTO FINANCE.COST_CENTER (cost_center_id, cost_center_name, owner_dept_id, company_code, created_at)
SELECT 1000 + LEVEL - 1,
       DECODE(MOD(LEVEL - 1, 20),
              0, '总裁办成本中心', 1, '人力资源成本中心', 2, '财务成本中心', 3, '行政成本中心',
              4, '采购成本中心', 5, '销售一部成本中心', 6, '销售二部成本中心', 7, '市场成本中心',
              8, '研发成本中心', 9, '产品成本中心', 10, '技术支持成本中心', 11, '质量成本中心',
              12, '法务成本中心', 13, '审计成本中心', 14, '战略投资成本中心', 15, '客户成功成本中心',
              16, '供应链成本中心', 17, '数据智能成本中心', 18, '信息安全成本中心', 19, '国际业务成本中心'),
       10 + MOD(LEVEL - 1, 20),
       'CC' || LPAD(TO_CHAR(LEVEL), 3, '0'),
       TIMESTAMP '2026-06-30 09:00:00'
FROM DUAL CONNECT BY LEVEL <= 20;

-- ---------------------------------------------------------------------------
-- finance.account_subject : expense subjects (30 rows)
-- ---------------------------------------------------------------------------
CREATE TABLE FINANCE.ACCOUNT_SUBJECT (
    subject_id   INT          NOT NULL PRIMARY KEY,
    subject_code VARCHAR(20)  NOT NULL,
    subject_name VARCHAR(50)  NOT NULL,
    subject_type VARCHAR(20),
    created_at   TIMESTAMP
);

INSERT INTO FINANCE.ACCOUNT_SUBJECT (subject_id, subject_code, subject_name, subject_type, created_at)
SELECT LEVEL,
       'SUB' || LPAD(TO_CHAR(LEVEL), 3, '0'),
       DECODE(MOD(LEVEL - 1, 30),
              0, '差旅费', 1, '交通费', 2, '住宿费', 3, '餐饮费', 4, '办公用品',
              5, '通讯费', 6, '会议费', 7, '培训费', 8, '招待费', 9, '市场推广费',
              10, '广告费', 11, '软件服务费', 12, '硬件采购费', 13, '云资源费', 14, '外包服务费',
              15, '咨询费', 16, '审计费', 17, '法律费', 18, '水电费', 19, '物业费',
              20, '租赁费', 21, '车辆费', 22, '团队建设费', 23, '招聘费', 24, '福利费',
              25, '保险费', 26, '运输费', 27, '仓储费', 28, '样品费', 29, '其他费用'),
       CASE WHEN MOD(LEVEL - 1, 3) = 0 THEN '资产'
            WHEN MOD(LEVEL - 1, 3) = 1 THEN '费用'
            ELSE '费用' END,
       TIMESTAMP '2026-06-30 09:00:00'
FROM DUAL CONNECT BY LEVEL <= 30;

-- ---------------------------------------------------------------------------
-- finance.budget : budget vs actual (360 rows = 20 centers x 6 periods x 3 subjects)
-- ---------------------------------------------------------------------------
CREATE TABLE FINANCE.BUDGET (
    budget_id      INT          NOT NULL PRIMARY KEY,
    cost_center_id INT          NOT NULL,
    period         VARCHAR(7)   NOT NULL,
    subject_id     INT          NOT NULL,
    budget_amount  DECIMAL(12,2),
    used_amount    DECIMAL(12,2),
    created_at     TIMESTAMP
);

INSERT INTO FINANCE.BUDGET (budget_id, cost_center_id, period, subject_id, budget_amount, used_amount, created_at)
SELECT LEVEL,
       1000 + FLOOR((LEVEL - 1) / 18),
       '2026-' || LPAD(TO_CHAR(MOD(FLOOR((LEVEL - 1) / 3), 6) + 1), 2, '0'),
       1 + MOD(LEVEL - 1, 3),
       ROUND(250000 + MOD((LEVEL - 1) * 137, 350000), 2),
       ROUND((250000 + MOD((LEVEL - 1) * 137, 350000)) * (0.45 + MOD(LEVEL, 55) / 100), 2),
       TIMESTAMP '2026-06-30 09:00:00'
FROM DUAL CONNECT BY LEVEL <= 360;

-- ---------------------------------------------------------------------------
-- finance.reimbursement : expense reimbursement (~5,000 rows)
-- ---------------------------------------------------------------------------
CREATE TABLE FINANCE.REIMBURSEMENT (
    reimb_id       BIGINT       NOT NULL PRIMARY KEY,
    employee_id    BIGINT       NOT NULL,
    dept_id        INT          NOT NULL,
    cost_center_id INT          NOT NULL,
    subject_id     INT          NOT NULL,
    period         VARCHAR(7)   NOT NULL,
    apply_date     DATE,
    amount         DECIMAL(12,2),
    status         VARCHAR(20),
    approver_id    BIGINT,
    created_at     TIMESTAMP
);

INSERT INTO FINANCE.REIMBURSEMENT
    (reimb_id, employee_id, dept_id, cost_center_id, subject_id, period,
     apply_date, amount, status, approver_id, created_at)
SELECT LEVEL,
       1001 + MOD(LEVEL - 1, 1000),
       10 + MOD(LEVEL - 1, 20),
       1000 + MOD(LEVEL - 1, 20),
       1 + MOD(LEVEL - 1, 30),
       '2026-' || LPAD(TO_CHAR(MOD(FLOOR((LEVEL - 1) / 20), 6) + 1), 2, '0'),
       DATE '2026-01-03' + MOD((LEVEL - 1) * 3, 175),
       ROUND(100 + MOD((LEVEL - 1) * 17, 9800) + MOD(LEVEL, 100) * 0.5, 2),
       DECODE(MOD(LEVEL - 1, 4), 0, '已支付', 1, '已通过', 2, '审批中', 3, '已驳回'),
       CASE WHEN MOD(LEVEL - 1, 21) = 0 THEN NULL ELSE 1001 + MOD(LEVEL + 52, 1000) END,
       TIMESTAMP '2026-06-30 10:00:00'
FROM DUAL CONNECT BY LEVEL <= 5000;

-- ---------------------------------------------------------------------------
-- finance.invoice : invoices (~4,000 rows)
-- ---------------------------------------------------------------------------
CREATE TABLE FINANCE.INVOICE (
    invoice_id   BIGINT       NOT NULL PRIMARY KEY,
    reimb_id     BIGINT,
    invoice_no   VARCHAR(30)  NOT NULL,
    invoice_type VARCHAR(30),
    amount       DECIMAL(12,2),
    tax_amount   DECIMAL(12,2),
    issue_date   DATE,
    seller_name  VARCHAR(100),
    created_at   TIMESTAMP
);

INSERT INTO FINANCE.INVOICE
    (invoice_id, reimb_id, invoice_no, invoice_type, amount, tax_amount, issue_date, seller_name, created_at)
SELECT LEVEL,
       CASE WHEN MOD(LEVEL - 1, 9) = 0 THEN NULL ELSE 1 + MOD(LEVEL - 1, 5000) END,
       'INV' || LPAD(TO_CHAR(LEVEL), 8, '0'),
       DECODE(MOD(LEVEL - 1, 3), 0, '增值税专用发票', 1, '增值税普通发票', 2, '电子发票'),
       ROUND(100 + MOD((LEVEL - 1) * 29, 9000), 2),
       ROUND((100 + MOD((LEVEL - 1) * 29, 9000)) * 0.06, 2),
       DATE '2026-01-04' + MOD((LEVEL - 1) * 2, 175),
       DECODE(MOD(LEVEL - 1, 8),
              0, '北京华信科技有限公司', 1, '上海云启信息技术有限公司', 2, '广州速达物流有限公司',
              3, '深圳恒通办公用品有限公司', 4, '成都锦程会务服务有限公司', 5, '杭州云栖软件有限公司',
              6, '武汉长江广告传媒有限公司', 7, '西安博远咨询有限公司'),
       TIMESTAMP '2026-06-30 11:00:00'
FROM DUAL CONNECT BY LEVEL <= 4000;
