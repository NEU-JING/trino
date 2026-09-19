# Data Fabric Prototype

A user-facing **data fabric / federation prototype** built on top of [Trino](https://trino.io).
It lets operators register heterogeneous databases as data sources, curate a queryable table
catalog, and manage table-level access; query users browse/search the catalog, run SQL across
sources, and export results to CSV/Excel.

```
React (operator + query user views)
        │ REST
Spring Boot backend ── StatementClient ──▶ Trino coordinator ──▶ MySQL (OceanBase sim)
        │  ▲                                     ▲               PostgreSQL 9.4 (Greenplum sim)
        │  └── access-control rules over HTTP ────┘               PostgreSQL (app support DB)
        └── platform metadata (users, data sources, tables, grants)
```

`data-fabric/` is an **independent project** and is intentionally not part of the Trino Maven
reactor (`data-fabric/backend/pom.xml` uses `spring-boot-starter-parent`).

## Layout

```
data-fabric/
  backend/    Spring Boot 4.1.1 / Java 25 service
  frontend/   React 19 + Vite 7 + TypeScript
  deploy/     docker-compose + Trino configuration
  e2e/        cross-source + platform end-to-end smoke test
  HANDOVER.md session handover notes
```

Backend packages: `security`, `user`, `auth`, `datasource`, `table`, `permission`,
`accesscontrol`, `query`, `export`, `trino`, `api`.

## Platform model

- **Data sources** are Trino dynamic catalogs created with `CREATE CATALOG`. The business type is
  mapped to a connector (OceanBase → `mysql`, Greenplum → `postgresql`) and only the business
  alias is exposed by the API.
- **Table catalog** is a curated registry (`registered_table`). Query users see
  *registered ∩ enabled source ∩ granted* tables only.
- **Access control** is enforced by Trino. The backend generates a `FileBasedSystemAccessControl`
  rules document from platform users and grants and serves it at
  `GET /api/access-control/rules`; Trino polls it (`security.refresh-period`), so changes apply
  without a restart.
- **Queries** run as the platform user (`X-Trino-User`), so Trino's rules apply directly. Results
  are held by the backend and can be exported.

Roles: `OPERATOR` (manage sources/tables/grants, see everything) and `QUERY_USER` (browse and
query granted tables only).

## Local development

Requirements: JDK 25, Maven (or use the Maven Docker image), Node/Bun for the frontend.

Backend:

```bash
cd data-fabric/backend
mvn test            # unit + integration tests (H2 for unit tests)
mvn spring-boot:run # starts on :8001
```

Frontend:

```bash
cd data-fabric/frontend
npm install
npm run dev         # Vite dev server on :5173, proxies /api to :8001
npm run build       # type-check + production build
```

Configuration (environment variables):

| Variable | Default | Purpose |
|---|---|---|
| `APP_DB_URL` | `jdbc:postgresql://localhost:5432/datafabric` | platform metadata DB |
| `APP_DB_USER` / `APP_DB_PASSWORD` | `postgres` / `postgres` | metadata DB credentials |
| `TRINO_URL` | `http://localhost:8080` | Trino coordinator |
| `TRINO_SERVICE_USER` | `trino_service` | account used for catalog management/probes |
| `BOOTSTRAP_USERS` | `true` | seed local accounts on first start |
| `BOOTSTRAP_OPERATOR_USER/PASSWORD` | `admin` / `admin` | operator account |
| `BOOTSTRAP_QUERY_USER/PASSWORD` | `viewer` / `viewer` | query account |
| `QUERY_MAX_ROWS` / `QUERY_TIMEOUT` / `QUERY_THREADS` | `10000` / `30s` / `4` | query limits |

## Remote deployment

Deployment is a docker-compose stack under `data-fabric/deploy/`. It manages Trino, MySQL
(OceanBase simulator) and the backend; the app-support PostgreSQL and the Greenplum-sim
PostgreSQL 9.4 instances are expected to be pre-existing and attached to the external
`data-fabric-net` network.

```bash
cd data-fabric/deploy
cp .env.example .env      # fill in credentials (never commit .env)
docker compose up -d
```

The frontend is packaged into the backend image (static resources) and served on `:8001`, so the
whole UI + API is one origin.

`data-fabric/deploy/trino/` holds `config.properties`, `catalog-store.properties` (dynamic catalog
persistence) and `access-control.properties` (HTTP rules endpoint + refresh period).

## Tests

- Backend: `mvn test` — unit and integration tests (JUnit 5 + AssertJ, hand-written fakes, no
  mocking libraries).
- End-to-end: `data-fabric/e2e/smoke_test.py` exercises a running stack:

```bash
TRINO_URL=http://localhost:8080 BACKEND_URL=http://localhost:8001 \
  E2E_OB_PASSWORD=<mysql-password> E2E_GP_PASSWORD=<postgres-password> \
  python3 e2e/smoke_test.py
```

The source-database passwords are read from `E2E_OB_PASSWORD` / `E2E_GP_PASSWORD` and are never
stored in the repository.

It covers the cross-source JOIN, authentication/role enforcement, data-source lifecycle, table
catalog with permission filtering, Trino-side table-grant enforcement (grant/revoke, JOIN/subquery/CTE
bypass attempts), query execution, predicate-pushdown verification, and CSV/Excel export.

## Security notes

- Credentials live only in `deploy/.env` on the deployment host and are never committed.
- Data source passwords are stored in the platform metadata DB and in Trino's catalog store in
  plain text — acceptable for the prototype, tracked as technical debt.
- The access-control rules endpoint is intentionally unauthenticated (it is consumed by Trino);
  it must only be reachable from the Trino cluster network.
