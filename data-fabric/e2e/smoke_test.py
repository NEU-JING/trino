#!/usr/bin/env python3
"""End-to-end smoke test for the data-fabric prototype.

Covers:
  1. Trino: cross-source JOIN across the `oceanbase` (mysql) and `greenplum`
     (postgresql) catalogs.
  2. Platform backend: health, login for both roles, and role enforcement on the
     operator-only admin API.
  3. Data source management and table catalog (with permission filtering).
  4. Table-level access enforcement in Trino (grant -> allowed, revoke -> denied,
     JOIN/subquery/CTE bypass attempts denied).

Environment:
    TRINO_URL     default http://localhost:8080
    BACKEND_URL   default http://localhost:8001
"""

import json
import os
import sys
import time
import urllib.error
import urllib.request

TRINO_URL = os.environ.get("TRINO_URL", "http://localhost:8080").rstrip("/")
BACKEND_URL = os.environ.get("BACKEND_URL", "http://localhost:8001").rstrip("/")
SERVICE_USER = "trino_service"
# Source-database passwords are provided out of band (never committed).
OB_PASSWORD = os.environ.get("E2E_OB_PASSWORD", "")
GP_PASSWORD = os.environ.get("E2E_GP_PASSWORD", "")
REFRESH_WAIT_SECONDS = 13


def trino_query(sql, user="admin"):
    request = urllib.request.Request(
        TRINO_URL + "/v1/statement",
        data=sql.encode("utf-8"),
        headers={"X-Trino-User": user, "Content-Type": "text/plain"},
        method="POST",
    )
    response = json.load(urllib.request.urlopen(request, timeout=120))
    columns = []
    rows = []
    while True:
        if "error" in response:
            raise RuntimeError(response["error"].get("message"))
        if response.get("columns"):
            columns = [column["name"] for column in response["columns"]]
        rows.extend(response.get("data", []))
        next_uri = response.get("nextUri")
        if not next_uri:
            return columns, rows
        time.sleep(0.05)
        response = json.load(
            urllib.request.urlopen(urllib.request.Request(next_uri, headers={"X-Trino-User": user}), timeout=120)
        )


def trino_query_denied(sql, user):
    try:
        trino_query(sql, user)
    except RuntimeError as error:
        assert "denied" in str(error).lower(), f"unexpected error: {error}"
        return
    raise AssertionError(f"expected access denial but query succeeded: {sql}")


def http(method, url, body=None, headers=None):
    request = urllib.request.Request(url, method=method)
    for key, value in (headers or {}).items():
        request.add_header(key, value)
    data = None
    if body is not None:
        data = body.encode("utf-8")
        request.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(request, data=data, timeout=60) as response:
            return response.status, response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode("utf-8")


def login(username, password):
    status, body = http(
        "POST",
        BACKEND_URL + "/api/auth/login",
        json.dumps({"username": username, "password": password}),
    )
    assert status == 200, f"login {username} failed: {status} {body}"
    return json.loads(body)["token"]


def verify_trino_cross_source():
    # Setup runs as the service account; reads run as an operator.
    setup = [
        "CREATE TABLE IF NOT EXISTS oceanbase.ob_source.orders (id integer, amount integer)",
        "CREATE TABLE IF NOT EXISTS greenplum.public.customers (id integer, name varchar)",
        "DELETE FROM oceanbase.ob_source.orders WHERE true",
        "DELETE FROM greenplum.public.customers WHERE true",
        "INSERT INTO oceanbase.ob_source.orders (id, amount) VALUES (1, 100), (2, 200), (3, 300)",
        "INSERT INTO greenplum.public.customers (id, name) VALUES (1, 'alice'), (2, 'bob'), (4, 'dan')",
    ]
    for statement in setup:
        trino_query(statement, SERVICE_USER)

    columns, rows = trino_query(
        """
        SELECT c.name, o.amount
        FROM oceanbase.ob_source.orders o
        JOIN greenplum.public.customers c ON o.id = c.id
        WHERE o.amount >= 100
        ORDER BY o.id
        """,
        "admin",
    )
    assert columns == ["name", "amount"], f"unexpected columns: {columns}"
    assert rows == [["alice", 100], ["bob", 200]], f"unexpected rows: {rows}"
    print("[trino] cross-source JOIN OK:", rows)


def verify_backend():
    status, body = http("GET", BACKEND_URL + "/api/health")
    assert status == 200 and "ok" in body, f"health failed: {status} {body}"
    print("[backend] health OK")

    operator_token = login("admin", "admin")
    query_token = login("viewer", "viewer")
    print("[backend] login OK (admin=OPERATOR, viewer=QUERY_USER)")

    status, _ = http("GET", BACKEND_URL + "/api/admin/users")
    assert status == 401, f"anonymous admin access should be 401, was {status}"
    print("[backend] anonymous admin access rejected (401)")

    status, _ = http("GET", BACKEND_URL + "/api/admin/users", headers={"Authorization": "Bearer " + query_token})
    assert status == 403, f"query user admin access should be 403, was {status}"
    print("[backend] query user admin access rejected (403)")

    status, body = http("GET", BACKEND_URL + "/api/admin/users", headers={"Authorization": "Bearer " + operator_token})
    assert status == 200, f"operator admin access should be 200, was {status}"
    assert "admin" in body and "viewer" in body, f"unexpected user list: {body}"
    print("[backend] operator admin access OK")


def disable_leftovers(token, prefix):
    status, body = http("GET", BACKEND_URL + "/api/data-sources", headers={"Authorization": "Bearer " + token})
    if status != 200:
        return
    for data_source in json.loads(body):
        if data_source["name"].startswith(prefix) and data_source["enabled"]:
            http("DELETE", BACKEND_URL + f"/api/data-sources/{data_source['id']}", None, {"Authorization": "Bearer " + token})


def verify_data_source_management():
    token = login("admin", "admin")
    auth = {"Authorization": "Bearer " + token}
    disable_leftovers(token, "e2e_ob_")

    # Clean up catalogs left behind by earlier failed runs.
    _, catalog_rows = trino_query("SHOW CATALOGS", SERVICE_USER)
    for row in catalog_rows:
        if str(row[0]).startswith("e2e_ob_"):
            trino_query(f"DROP CATALOG IF EXISTS {row[0]}", SERVICE_USER)

    name = "e2e_ob_" + str(int(time.time()))
    payload = json.dumps({
        "name": name,
        "businessType": "OceanBase",
        "host": "data-fabric-mysql",
        "port": 3306,
        "database": "",
        "user": "ob",
        "password": OB_PASSWORD,
    })
    status, body = http("POST", BACKEND_URL + "/api/data-sources", payload, auth)
    assert status == 201, f"register data source failed: {status} {body}"
    assert "OceanBase" in body, f"alias missing: {body}"
    assert "connector" not in body.lower() and '"mysql"' not in body, f"connector leaked: {body}"
    data_source_id = json.loads(body)["id"]
    print(f"[backend] registered data source '{name}' as OceanBase")

    _, rows = trino_query(f"SELECT count(*) FROM {name}.ob_source.orders", SERVICE_USER)
    assert rows and int(rows[0][0]) >= 1, f"platform catalog not queryable: {rows}"
    print(f"[backend] platform-created catalog queryable (orders count={rows[0][0]})")

    status, body = http("GET", BACKEND_URL + "/api/data-sources", headers=auth)
    assert status == 200 and name in body and "OceanBase" in body, f"list failed: {status} {body}"

    status, body = http("DELETE", BACKEND_URL + f"/api/data-sources/{data_source_id}", None, auth)
    assert status == 200 and '"enabled":false' in body, f"disable failed: {status} {body}"
    print("[backend] data source disabled (catalog dropped)")


def verify_table_catalog():
    operator_token = login("admin", "admin")
    viewer_token = login("viewer", "viewer")
    auth = {"Authorization": "Bearer " + operator_token}
    disable_leftovers(operator_token, "e2e_tbl_")
    name = "e2e_tbl_" + str(int(time.time()))

    payload = json.dumps({
        "name": name,
        "businessType": "OceanBase",
        "host": "data-fabric-mysql",
        "port": 3306,
        "database": "",
        "user": "ob",
        "password": OB_PASSWORD,
    })
    status, body = http("POST", BACKEND_URL + "/api/data-sources", payload, auth)
    assert status == 201, f"register data source failed: {status} {body}"
    data_source_id = json.loads(body)["id"]

    status, body = http("GET", BACKEND_URL + f"/api/data-sources/{data_source_id}/tables", headers=auth)
    assert status == 200 and "orders" in body, f"discovery failed: {status} {body}"
    print("[backend] table discovery OK")

    status, body = http(
        "POST",
        BACKEND_URL + "/api/tables",
        json.dumps({"dataSourceId": data_source_id, "schema": "ob_source", "table": "orders", "description": "订单表"}),
        auth,
    )
    assert status == 201, f"register table failed: {status} {body}"
    table_id = json.loads(body)["id"]
    print("[backend] table registered")

    status, body = http("GET", BACKEND_URL + "/api/tables", headers=auth)
    assert status == 200 and "orders" in body and "OceanBase" in body, f"table list failed: {status} {body}"

    status, body = http("GET", BACKEND_URL + "/api/tables?q=%E8%AE%A2%E5%8D%95", headers=auth)
    assert status == 200 and "orders" in body, f"search failed: {status} {body}"
    print("[backend] table list + search OK")

    status, body = http("GET", BACKEND_URL + "/api/tables", headers={"Authorization": "Bearer " + viewer_token})
    assert status == 200 and name not in body, f"viewer should not see ungranted table from {name}: {status} {body}"
    print("[backend] table catalog filtered by permission OK")

    # Workbench object tree / table detail data sources.
    status, body = http("GET", BACKEND_URL + f"/api/tables/{table_id}/detail", headers=auth)
    assert status == 200 and "订单表" in body and name in body, (status, body)
    status, body = http("GET", BACKEND_URL + f"/api/tables/{table_id}/columns", headers=auth)
    assert status == 200 and "amount" in body, (status, body)
    status, body = http("GET", BACKEND_URL + f"/api/tables/{table_id}/sample", headers=auth)
    assert status == 200 and "columns" in body, (status, body)
    status, body = http(
        "GET",
        BACKEND_URL + f"/api/metadata/suggest?type=table&parent={name}.ob_source",
        headers=auth,
    )
    assert status == 200 and "orders" in body, (status, body)
    status, body = http(
        "GET",
        BACKEND_URL + f"/api/tables/{table_id}/columns",
        headers={"Authorization": "Bearer " + viewer_token},
    )
    assert status == 404, f"viewer must not read ungranted table structure: {status} {body}"
    print("[workbench] table meaning/structure/sample + object tree suggestion OK, ungranted hidden")

    assert http("DELETE", BACKEND_URL + f"/api/tables/{table_id}", None, auth)[0] == 204
    assert http("DELETE", BACKEND_URL + f"/api/data-sources/{data_source_id}", None, auth)[0] == 200
    print("[backend] table catalog cleanup OK")


def grant(token, catalog, schema, table):
    return http(
        "POST",
        BACKEND_URL + "/api/permissions",
        json.dumps({
            "principal": "viewer",
            "principalType": "USER",
            "catalog": catalog,
            "schema": schema,
            "table": table,
            "permission": "SELECT",
        }),
        {"Authorization": "Bearer " + token},
    )


def revoke(operator_token, catalog, schema, table):
    return http(
        "POST",
        BACKEND_URL + "/api/permissions/revoke",
        json.dumps({
            "principal": "viewer",
            "principalType": "USER",
            "catalog": catalog,
            "schema": schema,
            "table": table,
            "permission": "SELECT",
        }),
        {"Authorization": "Bearer " + operator_token},
    )


def verify_permission_enforcement():
    operator_token = login("admin", "admin")
    # start from a clean slate for the target table regardless of seeded state
    revoke(operator_token, "oceanbase", "ob_source", "orders")
    time.sleep(REFRESH_WAIT_SECONDS)

    print("[perm] viewer denied before grant")
    trino_query_denied("SELECT count(*) FROM oceanbase.ob_source.orders", "viewer")

    status, body = grant(operator_token, "oceanbase", "ob_source", "orders")
    assert status == 201, f"grant failed: {status} {body}"
    time.sleep(REFRESH_WAIT_SECONDS)

    _, rows = trino_query("SELECT count(*) FROM oceanbase.ob_source.orders", "viewer")
    print("[perm] viewer allowed after grant:", rows)

    print("[perm] other table still denied")
    trino_query_denied("SELECT count(*) FROM greenplum.public.customers", "viewer")

    print("[perm] JOIN bypass denied")
    trino_query_denied(
        "SELECT c.name FROM oceanbase.ob_source.orders o JOIN greenplum.public.customers c ON o.id = c.id",
        "viewer",
    )

    print("[perm] subquery bypass denied")
    trino_query_denied(
        "SELECT count(*) FROM oceanbase.ob_source.orders WHERE id IN (SELECT id FROM greenplum.public.customers)",
        "viewer",
    )

    print("[perm] CTE bypass denied")
    trino_query_denied(
        "WITH c AS (SELECT id FROM greenplum.public.customers) SELECT count(*) FROM c",
        "viewer",
    )

    status, _ = revoke(operator_token, "oceanbase", "ob_source", "orders")
    assert status == 204, f"revoke failed: {status}"
    time.sleep(REFRESH_WAIT_SECONDS)
    print("[perm] viewer denied after revoke")
    trino_query_denied("SELECT count(*) FROM oceanbase.ob_source.orders", "viewer")


def run_query(token, sql, attempts=120):
    status, body = http(
        "POST",
        BACKEND_URL + "/api/queries",
        json.dumps({"sql": sql}),
        {"Authorization": "Bearer " + token},
    )
    assert status == 202, f"start query failed: {status} {body}"
    query_id = json.loads(body)["queryId"]
    for _ in range(attempts):
        status, body = http(
            "GET", BACKEND_URL + f"/api/queries/{query_id}", headers={"Authorization": "Bearer " + token}
        )
        assert status == 200, f"poll failed: {status} {body}"
        view = json.loads(body)
        if view["state"] != "RUNNING":
            return view
        time.sleep(0.5)
    raise AssertionError("query did not finish")


def http_raw(url, headers):
    request = urllib.request.Request(url, method="GET")
    for key, value in (headers or {}).items():
        request.add_header(key, value)
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            return response.status, response.read(), response.headers.get("Content-Type", "")
    except urllib.error.HTTPError as error:
        return error.code, error.read(), ""


def verify_query_execution():
    operator_token = login("admin", "admin")
    viewer_token = login("viewer", "viewer")
    operator_auth = {"Authorization": "Bearer " + operator_token}

    view = run_query(
        operator_token,
        "SELECT c.name, o.amount FROM oceanbase.ob_source.orders o "
        "JOIN greenplum.public.customers c ON o.id = c.id WHERE o.amount >= 100 ORDER BY o.id",
    )
    assert view["state"] == "FINISHED", view
    assert view["columns"] == ["name", "amount"], view
    assert view["rows"] == [["alice", 100], ["bob", 200]], view
    print("[query] cross-source query via platform OK")

    query_id = view["queryId"]
    status, body, content_type = http_raw(BACKEND_URL + f"/api/queries/{query_id}/export?format=csv", operator_auth)
    assert status == 200 and "text/csv" in content_type, (status, content_type)
    csv = body.decode("utf-8")
    assert csv.startswith("\ufeff") and "name,amount" in csv and "alice" in csv, csv[:120]
    print("[query] CSV export OK (UTF-8 BOM + header + rows)")

    status, body, content_type = http_raw(BACKEND_URL + f"/api/queries/{query_id}/export?format=xlsx", operator_auth)
    assert status == 200 and "spreadsheetml" in content_type, (status, content_type)
    assert body[:2] == b"PK" and len(body) > 100, "xlsx magic bytes missing"
    print("[query] Excel export OK")

    failed = run_query(operator_token, "SELECT * FROM")
    assert failed["state"] == "FAILED" and failed["error"], failed
    status, _, _ = http_raw(BACKEND_URL + f"/api/queries/{failed['queryId']}/export?format=csv", operator_auth)
    assert status == 409, status
    print("[query] failed query is not exportable (409)")

    view = run_query(operator_token, "SELECT CAST(NULL AS varchar) AS n, '' AS e")
    assert view["state"] == "FINISHED" and view["rows"] == [[None, ""]], view
    print("[query] NULL and empty string preserved in result")

    status, _, _ = http_raw(
        BACKEND_URL + f"/api/queries/{query_id}/export?format=csv",
        {"Authorization": "Bearer " + viewer_token},
    )
    assert status == 404, status
    print("[query] another user's result is not exportable (404)")

    view = run_query(viewer_token, "SELECT count(*) FROM oceanbase.ob_source.orders")
    assert view["state"] == "FAILED" and "denied" in (view["error"] or "").lower(), view
    print("[query] viewer denied through platform query API")

    view = run_query(operator_token, "EXPLAIN SELECT * FROM oceanbase.ob_source.orders WHERE id = 1")
    assert view["state"] == "FINISHED", view
    plan = "\n".join(str(cell) for row in view["rows"] for cell in row)
    assert "constraint on [id]" in plan, plan
    assert "Filter[" not in plan, plan
    print("[query] predicate pushdown confirmed (constraint on [id], no Filter node)")


def verify_query_history_and_favorites():
    operator_token = login("admin", "admin")
    viewer_token = login("viewer", "viewer")
    operator_auth = {"Authorization": "Bearer " + operator_token}
    viewer_auth = {"Authorization": "Bearer " + viewer_token}

    marker = "e2e_history_" + str(int(time.time()))
    view = run_query(operator_token, f"SELECT 42 AS {marker}")
    assert view["state"] == "FINISHED", view

    status, body = http("GET", BACKEND_URL + "/api/query-history", headers=operator_auth)
    assert status == 200 and marker in body, (status, body)
    assert '"state":"FINISHED"' in body and '"rowCount":1' in body, body
    print("[history] executed query recorded with state and row count")

    status, body = http("GET", BACKEND_URL + "/api/query-history", headers=viewer_auth)
    assert status == 200 and marker not in body, f"history leaked across users: {body}"
    print("[history] history is isolated per user")

    favorite = "e2e_saved_" + str(int(time.time()))
    status, body = http(
        "POST",
        BACKEND_URL + "/api/saved-queries",
        json.dumps({"name": favorite, "sql": "SELECT 1"}),
        operator_auth,
    )
    assert status == 201 and favorite in body, (status, body)
    saved_id = json.loads(body)["id"]
    print("[favorites] named query saved")

    status, body = http("GET", BACKEND_URL + "/api/saved-queries", headers=operator_auth)
    assert status == 200 and favorite in body and "SELECT 1" in body, (status, body)

    status, body = http("GET", BACKEND_URL + "/api/saved-queries", headers=viewer_auth)
    assert favorite not in body, f"saved queries leaked across users: {body}"
    print("[favorites] saved queries are isolated per user")

    rerun = run_query(operator_token, "SELECT 1")
    assert rerun["state"] == "FINISHED", rerun
    print("[favorites] saved query executes")

    assert http("DELETE", BACKEND_URL + f"/api/saved-queries/{saved_id}", None, operator_auth)[0] == 204
    status, body = http("GET", BACKEND_URL + "/api/saved-queries", headers=operator_auth)
    assert favorite not in body, body
    print("[favorites] saved query deleted")


def verify_large_result():
    operator_token = login("admin", "admin")

    view = run_query(operator_token, "SELECT x FROM UNNEST(sequence(1, 10000)) AS t(x)")
    assert view["state"] == "FINISHED", view
    assert len(view["rows"]) == 10000, len(view["rows"])
    assert view["truncated"] is False, view["truncated"]
    print("[perf] 10000-row result returned untruncated")

    view = run_query(
        operator_token,
        "SELECT a.x FROM UNNEST(sequence(1, 10000)) AS a(x) "
        "CROSS JOIN UNNEST(sequence(1, 2)) AS b(y)",
    )
    assert view["state"] == "FINISHED", view
    assert view["truncated"] is True and len(view["rows"]) == 10000, (view["truncated"], len(view["rows"]))
    print("[perf] large result truncated at max-rows")


def main():
    verify_trino_cross_source()
    verify_backend()
    verify_data_source_management()
    verify_table_catalog()
    verify_permission_enforcement()
    verify_query_execution()
    verify_large_result()
    verify_query_history_and_favorites()
    print("E2E_OK")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:  # noqa: BLE001 - CLI entrypoint
        print(f"E2E_FAILED: {error}", file=sys.stderr)
        sys.exit(1)
