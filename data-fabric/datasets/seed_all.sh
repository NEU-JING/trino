#!/usr/bin/env bash
# =============================================================================
# Data Fabric demo dataset loader.
#
# Applies the OA (OceanBase/MySQL), HR (Greenplum/PostgreSQL) and Finance
# (Dameng DM8) seed scripts to a running `data-fabric/deploy` stack.
#
# Idempotent: every script is a full, deterministic rebuild, so this can be run
# on an empty environment or repeatedly without producing duplicates.
#
# Credentials are read from deploy/.env (not committed), never hard-coded.
# =============================================================================
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_ENV="${HERE}/../deploy/.env"

if [[ -f "$DEPLOY_ENV" ]]; then
    set -a
    # shellcheck disable=SC1090
    . "$DEPLOY_ENV"
    set +a
fi

MYSQL_CONTAINER="${MYSQL_CONTAINER:-data-fabric-mysql}"
PG_CONTAINER="${PG_CONTAINER:-postgres94}"
PG_DB="${PG_SOURCE_DB:-gp_source}"
DM_CONTAINER="${DM_CONTAINER:-data-fabric-dm8}"
DM_USER="${DM_USER:-SYSDBA}"
DM_PASSWORD="${DM_PASSWORD:-SYSDBA_dm001}"

: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD must be set (deploy/.env)}"

echo "==> OA domain (OceanBase / MySQL) ..."
docker exec -i "$MYSQL_CONTAINER" sh -c "MYSQL_PWD='$MYSQL_ROOT_PASSWORD' mysql -uroot" \
    < "$HERE/oceanbase/seed_oa.sql"

echo "==> HR domain (Greenplum / PostgreSQL) ..."
docker exec -i "$PG_CONTAINER" psql -U postgres -d "$PG_DB" -v ON_ERROR_STOP=1 \
    < "$HERE/greenplum/seed_hr.sql"

echo "==> Finance domain (Dameng DM8) ..."
# LANG=zh_CN.UTF-8 is required: without it DIsql mis-tokenizes statements that
# contain more than one multi-byte (Chinese) string literal.
docker exec -i -e LANG=zh_CN.UTF-8 "$DM_CONTAINER" \
    /opt/dmdbms/bin/disql "$DM_USER/$DM_PASSWORD@127.0.0.1:5236" \
    < "$HERE/dm8/seed_finance.sql"

echo "==> row counts"
docker exec -i "$MYSQL_CONTAINER" sh -c "MYSQL_PWD='$MYSQL_ROOT_PASSWORD' mysql -uroot -t" <<'SQL'
SELECT 'oa.employee'      AS table_name, COUNT(*) AS rows_loaded FROM oa.employee
UNION ALL SELECT 'oa.attendance',      COUNT(*) FROM oa.attendance
UNION ALL SELECT 'oa.leave_request',   COUNT(*) FROM oa.leave_request
UNION ALL SELECT 'oa.business_trip',   COUNT(*) FROM oa.business_trip
UNION ALL SELECT 'oa.approval',        COUNT(*) FROM oa.approval;
SQL

docker exec -i "$PG_CONTAINER" psql -U postgres -d "$PG_DB" -v ON_ERROR_STOP=1 <<'SQL'
SELECT 'hr.department'      AS table_name, COUNT(*) AS rows_loaded FROM hr.department
UNION ALL SELECT 'hr.position',         COUNT(*) FROM hr.position
UNION ALL SELECT 'hr.salary',           COUNT(*) FROM hr.salary
UNION ALL SELECT 'hr.performance',      COUNT(*) FROM hr.performance
UNION ALL SELECT 'hr.social_security',  COUNT(*) FROM hr.social_security
UNION ALL SELECT 'hr.contract',         COUNT(*) FROM hr.contract;
SQL

docker exec -i -e LANG=zh_CN.UTF-8 "$DM_CONTAINER" \
    /opt/dmdbms/bin/disql "$DM_USER/$DM_PASSWORD@127.0.0.1:5236" <<'SQL'
SELECT 'finance.cost_center'   AS table_name, COUNT(*) AS rows_loaded FROM FINANCE.COST_CENTER
UNION ALL SELECT 'finance.account_subject',  COUNT(*) FROM FINANCE.ACCOUNT_SUBJECT
UNION ALL SELECT 'finance.budget',           COUNT(*) FROM FINANCE.BUDGET
UNION ALL SELECT 'finance.reimbursement',    COUNT(*) FROM FINANCE.REIMBURSEMENT
UNION ALL SELECT 'finance.invoice',          COUNT(*) FROM FINANCE.INVOICE;
EXIT
SQL

echo "seed done."
