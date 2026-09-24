-- =============================================================================
-- Data Fabric demo dataset - OA domain (OceanBase / MySQL dialect)
-- Source: deploy/docker-compose.yml service `mysql` (container data-fabric-mysql)
-- Schema: oa
--
-- Idempotent: safe to re-run. Drops the seed tables, recreates and reloads.
-- Deterministic: every run produces identical rows (formula-based generation).
--
-- Apply (see datasets/README.md):
--   docker exec -i data-fabric-mysql sh -c \
--     'MYSQL_PWD=<pwd> mysql -uroot' < oceanbase/seed_oa.sql
-- =============================================================================

CREATE DATABASE IF NOT EXISTS oa DEFAULT CHARACTER SET utf8mb4;

-- ---------------------------------------------------------------------------
-- Helper sequence 0..9999 (dropped at the end of the script).
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS oa.seq;
CREATE TABLE oa.seq (n INT NOT NULL PRIMARY KEY) ENGINE=InnoDB;

INSERT INTO oa.seq (n)
SELECT a.i + b.i * 10 + c.i * 100 + d.i * 1000
FROM (SELECT 0 i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a
CROSS JOIN (SELECT 0 i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b
CROSS JOIN (SELECT 0 i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) c
CROSS JOIN (SELECT 0 i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d;

-- ---------------------------------------------------------------------------
-- oa.employee : employee roster (~1,000 rows)
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS oa.employee;
CREATE TABLE oa.employee (
    employee_id   BIGINT       NOT NULL PRIMARY KEY,
    employee_no   VARCHAR(20)  NOT NULL,
    name          VARCHAR(50)  NOT NULL,
    gender        VARCHAR(4)   NOT NULL,
    birth_date    DATE,
    id_card       VARCHAR(20),
    phone         VARCHAR(20),
    email         VARCHAR(100),
    dept_id       BIGINT       NOT NULL,
    position_id   BIGINT       NOT NULL,
    manager_id    BIGINT,
    hire_date     DATE,
    employ_status VARCHAR(20),
    created_at    TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO oa.employee
    (employee_id, employee_no, name, gender, birth_date, id_card, phone, email,
     dept_id, position_id, manager_id, hire_date, employ_status, created_at)
SELECT 1001 + s.n,
       CONCAT('E', 1001 + s.n),
       CONCAT(
           ELT(1 + MOD(s.n, 20), '张','王','李','赵','陈','刘','杨','黄','周','吴',
                                 '徐','孙','马','朱','胡','郭','何','高','林','罗'),
           ELT(1 + MOD(FLOOR(s.n / 20), 30), '伟','芳','娜','敏','静','丽','强','磊','军','洋',
                                             '勇','艳','杰','娟','涛','明','超','秀英','霞','平',
                                             '刚','桂英','辉','建国','红','梅','斌','鑫','雪','飞')),
       CASE WHEN MOD(s.n, 2) = 0 THEN '男' ELSE '女' END,
       DATE_ADD('1978-01-01', INTERVAL MOD(s.n * 137, 7300) DAY),
       CASE WHEN MOD(s.n, 13) = 0 THEN NULL
            ELSE CONCAT('110101', LPAD(MOD(s.n * 977, 100000000), 8, '0')) END,
       CONCAT('138', LPAD(MOD(s.n * 7919, 100000000), 8, '0')),
       CASE WHEN MOD(s.n, 11) = 0 THEN NULL
            ELSE CONCAT('user', 1001 + s.n, '@example.com') END,
       10 + MOD(s.n, 20),
       100 + MOD(FLOOR(s.n / 3), 30),
       CASE WHEN MOD(s.n, 20) = 0 THEN NULL
            ELSE 1001 + MOD(s.n + 37, 1000) END,
       DATE_ADD('2015-01-01', INTERVAL MOD(s.n * 23, 3650) DAY),
       CASE WHEN MOD(s.n, 25) = 0 THEN '离职'
            WHEN MOD(s.n, 17) = 0 THEN '试用'
            ELSE '在职' END,
       TIMESTAMP '2026-06-30 09:00:00'
FROM oa.seq s
WHERE s.n < 1000;

-- ---------------------------------------------------------------------------
-- oa.attendance : attendance records (~5,000 rows)
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS oa.attendance;
CREATE TABLE oa.attendance (
    attendance_id BIGINT      NOT NULL PRIMARY KEY,
    employee_id   BIGINT      NOT NULL,
    work_date     DATE        NOT NULL,
    check_in      TIME,
    check_out     TIME,
    work_hours    DECIMAL(4,1),
    status        VARCHAR(20),
    created_at    TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO oa.attendance
    (attendance_id, employee_id, work_date, check_in, check_out, work_hours, status, created_at)
SELECT 1 + s.n,
       1001 + MOD(s.n, 1000),
       DATE_ADD('2026-01-01', INTERVAL MOD(FLOOR(s.n / 1000) * 7 + MOD(s.n, 7), 180) DAY),
       SEC_TO_TIME(8 * 3600 + 30 * 60 + MOD(s.n * 13, 70) * 60),
       SEC_TO_TIME(17 * 3600 + 30 * 60 + MOD(s.n * 17, 120) * 60),
       ROUND(7.0 + MOD(s.n, 30) / 10, 1),
       CASE WHEN MOD(s.n, 23) = 0 THEN '缺勤'
            WHEN MOD(s.n, 19) = 0 THEN '迟到'
            WHEN MOD(s.n, 29) = 0 THEN '早退'
            WHEN MOD(s.n, 31) = 0 THEN '请假'
            ELSE '正常' END,
       TIMESTAMP '2026-06-30 10:00:00'
FROM oa.seq s
WHERE s.n < 5000;

-- ---------------------------------------------------------------------------
-- oa.leave_request : leave requests (~2,000 rows)
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS oa.leave_request;
CREATE TABLE oa.leave_request (
    leave_id    BIGINT       NOT NULL PRIMARY KEY,
    employee_id BIGINT       NOT NULL,
    leave_type  VARCHAR(20)  NOT NULL,
    start_date  DATE         NOT NULL,
    end_date    DATE         NOT NULL,
    days        DECIMAL(4,1),
    reason      VARCHAR(200),
    approver_id BIGINT,
    status      VARCHAR(20),
    created_at  TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO oa.leave_request
    (leave_id, employee_id, leave_type, start_date, end_date, days, reason, approver_id, status, created_at)
SELECT 1 + s.n,
       1001 + MOD(s.n, 1000),
       ELT(1 + MOD(s.n, 5), '年假','事假','病假','婚假','产假'),
       DATE_ADD('2026-01-05', INTERVAL MOD(s.n * 3, 160) DAY),
       DATE_ADD(DATE_ADD('2026-01-05', INTERVAL MOD(s.n * 3, 160) DAY), INTERVAL FLOOR(MOD(s.n, 10) / 2) DAY),
       0.5 + MOD(s.n, 10) * 0.5,
       CASE WHEN MOD(s.n, 7) = 0 THEN NULL
            ELSE ELT(1 + MOD(s.n, 4), '家中有事','身体不适','外出旅行','处理私事') END,
       CASE WHEN MOD(s.n, 21) = 0 THEN NULL ELSE 1001 + MOD(s.n + 53, 1000) END,
       ELT(1 + MOD(s.n, 3), '已批准','待审批','已驳回'),
       DATE_ADD('2026-01-05', INTERVAL MOD(s.n * 3, 160) DAY)
FROM oa.seq s
WHERE s.n < 2000;

-- ---------------------------------------------------------------------------
-- oa.business_trip : business trips (~1,000 rows)
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS oa.business_trip;
CREATE TABLE oa.business_trip (
    trip_id       BIGINT       NOT NULL PRIMARY KEY,
    employee_id   BIGINT       NOT NULL,
    destination   VARCHAR(50)  NOT NULL,
    start_date    DATE         NOT NULL,
    end_date      DATE         NOT NULL,
    days          DECIMAL(4,1),
    budget_amount DECIMAL(12,2),
    approver_id   BIGINT,
    status        VARCHAR(20),
    created_at    TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO oa.business_trip
    (trip_id, employee_id, destination, start_date, end_date, days, budget_amount, approver_id, status, created_at)
SELECT 1 + s.n,
       1001 + MOD(s.n, 1000),
       ELT(1 + MOD(s.n, 10), '北京','上海','广州','深圳','成都','杭州','武汉','西安','南京','重庆'),
       DATE_ADD('2026-01-08', INTERVAL MOD(s.n * 5, 150) DAY),
       DATE_ADD(DATE_ADD('2026-01-08', INTERVAL MOD(s.n * 5, 150) DAY), INTERVAL MOD(s.n, 5) DAY),
       1.0 + MOD(s.n, 5),
       CASE WHEN MOD(s.n, 9) = 0 THEN NULL
            ELSE ROUND(1000 + MOD(s.n * 37, 9000), 2) END,
       1001 + MOD(s.n + 53, 1000),
       ELT(1 + MOD(s.n, 4), '已批准','已批准','待审批','已驳回'),
       DATE_ADD('2026-01-08', INTERVAL MOD(s.n * 5, 150) DAY)
FROM oa.seq s
WHERE s.n < 1000;

-- ---------------------------------------------------------------------------
-- oa.approval : approval workflow (~3,000 rows)
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS oa.approval;
CREATE TABLE oa.approval (
    approval_id BIGINT       NOT NULL PRIMARY KEY,
    biz_type    VARCHAR(20)  NOT NULL,
    biz_id      BIGINT       NOT NULL,
    employee_id BIGINT       NOT NULL,
    approver_id BIGINT,
    action      VARCHAR(20),
    comment     VARCHAR(200),
    approve_time TIMESTAMP,
    created_at  TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO oa.approval
    (approval_id, biz_type, biz_id, employee_id, approver_id, action, comment, approve_time, created_at)
SELECT 1 + s.n,
       ELT(1 + MOD(s.n, 3), '请假','出差','报销'),
       1 + MOD(s.n, 2000),
       1001 + MOD(s.n, 1000),
       CASE WHEN MOD(s.n, 21) = 0 THEN NULL ELSE 1001 + MOD(s.n + 53, 1000) END,
       ELT(1 + MOD(s.n, 4), '通过','通过','驳回','转交'),
       CASE WHEN MOD(s.n, 5) = 0
            THEN ELT(1 + MOD(s.n, 3), '同意','不符合规定','金额需复核')
            ELSE NULL END,
       DATE_ADD('2026-01-06', INTERVAL MOD(s.n * 5, 150) DAY),
       TIMESTAMP '2026-06-30 11:00:00'
FROM oa.seq s
WHERE s.n < 3000;

-- ---------------------------------------------------------------------------
-- Cleanup helper, expose OA schema to the platform's `ob` user.
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS oa.seq;
GRANT ALL PRIVILEGES ON oa.* TO 'ob'@'%';
FLUSH PRIVILEGES;
