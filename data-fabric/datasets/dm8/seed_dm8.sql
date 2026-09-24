-- DM8 demo seed for the data-fabric federation demo.
-- Apply once per DM8 instance (e.g. with disql or a small JDBC seeder).
-- This minimal table is used by e2e/smoke_test.py for the cross-source JOIN.
-- The fuller office-domain dataset is planned in the `enrich-demo-datasets` change.

CREATE SCHEMA DEMO AUTHORIZATION SYSDBA;

CREATE TABLE DEMO.EMP (
    ID INT PRIMARY KEY,
    NAME VARCHAR(50),
    SALARY DECIMAL(10,2),
    HIRE_DATE DATE,
    CREATED_TS TIMESTAMP,
    NOTE CLOB,
    FLAG BIT
);

INSERT INTO DEMO.EMP VALUES (1, 'alice', 100.50, DATE'2026-01-02', TIMESTAMP'2026-01-02 03:04:05', 'hello', 1);
INSERT INTO DEMO.EMP VALUES (2, 'bob', 200.00, DATE'2026-02-03', TIMESTAMP'2026-02-03 04:05:06', NULL, 0);
COMMIT;
