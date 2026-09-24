#!/usr/bin/env bash
# =============================================================================
# Register the demo datasets with the platform: data sources, curated tables
# with business descriptions, and a viewer grant for the demo user.
#
# Idempotent: existing data sources are reused; table registration upserts the
# description; grants are no-ops when already present.
#
# Credentials are read from deploy/.env (not committed).
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

API="${DF_API:-http://localhost:8001}"
OP_USER="${DF_OPERATOR_USER:-admin}"
OP_PASSWORD="${DF_OPERATOR_PASSWORD:-admin}"

# Connection endpoints (container names on the `data-fabric-net` network).
OB_HOST="${OB_HOST:-data-fabric-mysql}"
OB_PORT="${OB_PORT:-3306}"
OB_USER="${OB_USER:-ob}"
GP_HOST="${GP_HOST:-postgres94}"
GP_PORT="${GP_PORT:-5432}"
GP_DB="${PG_SOURCE_DB:-gp_source}"
GP_USER="${GP_USER:-postgres}"
DM_HOST="${DM_HOST:-data-fabric-dm8}"
DM_PORT="${DM_PORT:-5236}"
DM_USER_DS="${DM_USER_DS:-SYSDBA}"

: "${MYSQL_PASSWORD:?MYSQL_PASSWORD must be set (deploy/.env)}"
: "${PG_SOURCE_PASSWORD:?PG_SOURCE_PASSWORD must be set (deploy/.env)}"
DM_PASSWORD="${DM_PASSWORD:-SYSDBA_dm001}"

token="$(curl -fsS -X POST "$API/api/auth/login" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$OP_USER\",\"password\":\"$OP_PASSWORD\"}" | jq -r .token)"

register_source() {
    local name="$1" business="$2" host="$3" port="$4" database="$5" user="$6" password="$7"
    local existing
    existing="$(curl -fsS "$API/api/data-sources" -H "Authorization: Bearer $token" \
        | jq -r --arg n "$name" '.[] | select(.name == $n) | .id' | head -n1)"
    if [[ -n "$existing" ]]; then
        echo "data source '$name' already registered (id=$existing)" >&2
        echo "$existing"
        return
    fi
    local body
    body="$(jq -n --arg name "$name" --arg bt "$business" --arg host "$host" --argjson port "$port" \
        --arg db "$database" --arg u "$user" --arg p "$password" \
        '{name:$name,businessType:$bt,host:$host,port:$port,database:$db,user:$u,password:$p}')"
    curl -fsS -X POST "$API/api/data-sources" -H "Authorization: Bearer $token" \
        -H 'Content-Type: application/json' -d "$body" | jq -r .id
}

register_table() {
    local data_source_id="$1" schema="$2" table="$3" description="$4"
    local body
    body="$(jq -n --argjson ds "$data_source_id" --arg s "$schema" --arg t "$table" --arg d "$description" \
        '{dataSourceId:$ds,schema:$s,table:$t,description:$d}')"
    curl -fsS -X POST "$API/api/tables" -H "Authorization: Bearer $token" \
        -H 'Content-Type: application/json' -d "$body" >/dev/null
    echo "  registered $schema.$table - $description" >&2
}

grant_viewer() {
    local catalog="$1" schema="$2" table="$3"
    local body
    body="$(jq -n --arg c "$catalog" --arg s "$schema" --arg t "$table" \
        '{principal:"viewer",principalType:"USER",catalog:$c,schema:$s,table:$t,permission:"SELECT"}')"
    curl -fsS -X POST "$API/api/permissions" -H "Authorization: Bearer $token" \
        -H 'Content-Type: application/json' -d "$body" >/dev/null
}

echo "==> data sources"
OB_ID="$(register_source ob_demo OceanBase "$OB_HOST" "$OB_PORT" "" "$OB_USER" "$MYSQL_PASSWORD")"
GP_ID="$(register_source gp_demo Greenplum "$GP_HOST" "$GP_PORT" "$GP_DB" "$GP_USER" "$PG_SOURCE_PASSWORD")"
DM_ID="$(register_source dm_demo 达梦 "$DM_HOST" "$DM_PORT" "" "$DM_USER_DS" "$DM_PASSWORD")"
echo "  ob_demo=$OB_ID gp_demo=$GP_ID dm_demo=$DM_ID" >&2

echo "==> tables with business descriptions"
register_table "$OB_ID" oa employee      "员工花名册：工号、姓名、部门、职位、入职日期、在职状态"
register_table "$OB_ID" oa attendance    "考勤记录：上下班打卡、工时、考勤状态"
register_table "$OB_ID" oa leave_request "请假申请：假别、起止日期、天数、审批人、审批状态"
register_table "$OB_ID" oa business_trip "出差申请：目的地、起止日期、预算金额、审批状态"
register_table "$OB_ID" oa approval      "审批流：业务类型、单据、审批动作与意见"

register_table "$GP_ID" hr department      "部门/组织：名称、上级部门、对应成本中心、负责人"
register_table "$GP_ID" hr position        "职位：名称、职位序列、职级"
register_table "$GP_ID" hr salary          "月度薪资：基本工资、奖金、津贴、社保、个税、实发"
register_table "$GP_ID" hr performance     "绩效评估：KPI 得分、评级、评估人、评语"
register_table "$GP_ID" hr social_security "社保公积金：养老/医疗/失业/公积金缴纳额与基数"
register_table "$GP_ID" hr contract        "劳动合同：类型、起止日期、签订日期、状态、约定薪资"

register_table "$DM_ID" finance cost_center     "成本中心：名称、归属部门、公司代码"
register_table "$DM_ID" finance account_subject "费用科目：科目编码、名称、类型"
register_table "$DM_ID" finance budget          "预算执行：成本中心 + 期间 + 科目的预算额与已用额"
register_table "$DM_ID" finance reimbursement   "费用报销：申请人、部门、成本中心、科目、期间、金额、状态"
register_table "$DM_ID" finance invoice         "发票：发票号、类型、金额、税额、开票日期、销方"

echo "==> grant viewer SELECT on demo tables"
for entry in "ob_demo oa employee" "ob_demo oa attendance" "ob_demo oa leave_request" \
             "ob_demo oa business_trip" "ob_demo oa approval" \
             "gp_demo hr department" "gp_demo hr position" "gp_demo hr salary" \
             "gp_demo hr performance" "gp_demo hr social_security" "gp_demo hr contract" \
             "dm_demo finance cost_center" "dm_demo finance account_subject" "dm_demo finance budget" \
             "dm_demo finance reimbursement" "dm_demo finance invoice"; do
    # shellcheck disable=SC2086
    grant_viewer $entry
done
echo "  viewer granted on all demo tables" >&2

echo "register done."
