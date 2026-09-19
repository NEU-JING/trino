# Data Fabric Prototype — 工作交接（断电/重启恢复用）

> 更新时间：2026-09-18 ｜ 分支：`feature/build-data-fabric-prototype`（尚未 commit）
> 进度：**59 / 59** tasks（Group 0–8 全部完成，见 `openspec/changes/build-data-fabric-prototype/tasks.md`）

---

## 1. 重启后如何继续

### 远端服务器
- 主机：`<remote-host>`（由你掌握），账号：`trino`，密码：**由你提供**（本文件及仓库中均不保存）
- 部署根目录：`/home/trino/data-fabric/`
- 服务用 Docker 运行，`trino/backend/mysql` 均 `restart: unless-stopped`，**服务器重启后会自动拉起**。

### 本机辅助脚本（位于 `C:\Users\Lenovo\AppData\Local\Temp\opencode\`）
> 若该目录被清理，需重建（依赖 `python -m pip install paramiko`）。脚本本身不含密码。

```powershell
$env:R_PW='<密码>'
# 在远端执行一段 bash 脚本：
python remote_exec.py --file <本地脚本.sh>        # 或 python remote_exec.py "<单行命令>"
# 上传单文件 / 整个目录：
python remote_put.py  <本地文件> <远端路径>
python remote_sync.py <本地目录> <远端目录>
# 对远端 Trino 执行 SQL（SQL 写在本地文件里）：
python remote_sql.py --file <本地.sql>
```
远端 Trino 的 SQL 执行器在 `/home/trino/df_sql.py`。

### 常用操作命令
```bash
# 后端测试（远端 Maven 容器）
docker run --rm -v /home/trino/data-fabric/backend:/workspace \
  -v /home/trino/.m2:/root/.m2 -w /workspace \
  maven:3.9-eclipse-temurin-25 mvn -B test

# 后端打包 + 部署
cd /home/trino/data-fabric/deploy
docker run --rm -v /home/trino/data-fabric/backend:/workspace -v /home/trino/.m2:/root/.m2 \
  -w /workspace maven:3.9-eclipse-temurin-25 mvn -B -q package -DskipTests
docker compose build backend && docker compose up -d backend

# 前端构建（node 容器）
cd /home/trino/data-fabric/frontend
docker run --rm -v /home/trino/data-fabric/frontend:/app -w /app \
  -e npm_config_registry=https://registry.npmmirror.com node:24-alpine \
  sh -c "npm install --no-audit --no-fund && npm run build"

# 全栈 E2E
cd /home/trino/data-fabric/e2e
TRINO_URL=http://localhost:8080 BACKEND_URL=http://localhost:8001 python3 smoke_test.py
```

---

## 2. 远端环境现状

| 容器 | 端口 | 说明 |
|---|---|---|
| `data-fabric-trino` | 8080 | Trino 483，动态 Catalog，`catalog.store=file` |
| `data-fabric-backend` | 8001 | Spring Boot 4.1.1 / Java 25（平台后端） |
| `data-fabric-mysql` | 3306 | 模拟 OceanBase（库 `ob_source`） |
| `pg-latest` | 5433 | PostgreSQL 18，平台应用支撑库（库 `datafabric`） |
| `postgres94` | 5432 | PostgreSQL 9.4，模拟 Greenplum（库 `gp_source`） |

- Compose 工程名 `data-fabric`，共享外部网络 `data-fabric-net`；两个 PG 为预置外部容器。
- 动态 Catalog 持久化目录：`/home/trino/data-fabric/deploy/trino/catalog/`。
- 远端 `.env`（**仅服务器，不入库**）：`/home/trino/data-fabric/deploy/.env`。
- Maven 缓存与阿里云镜像：`/home/trino/.m2`（`settings.xml` 已配 Aliyun，因为远端访问 Maven Central 仅 ~4kB/s）。
- `trino` 用户**无 sudo**；`/home/trino` 当初是用 root 容器创建的。
- 平台账号：`admin/admin`(OPERATOR)、`viewer/viewer`(QUERY_USER)（原型默认，可改）。

---

## 3. 仓库内已实现的代码

```
data-fabric/
  deploy/            docker-compose.yml, trino/*.properties, .env.example
  backend/           Spring Boot 后端（独立 Maven 工程，不入 Trino reactor）
  frontend/          React 19 + Vite 7 + TS
  e2e/smoke_test.py  全栈端到端测试
openspec/changes/build-data-fabric-prototype/   proposal/design/specs/tasks
```

后端包结构：
- `security/`：`Role`、`PasswordHasher`(PBKDF2)
- `user/`：`User`、`UserRepository`、`BootstrapUsers`
- `auth/`：`TokenStore`、`AuthService`、`AuthInterceptor`、`@RequireRole`/`@RequireAuthenticated`
- `api/`：`AuthController`、`UserAdminController`、`DataSourceController`、`HealthController`、`ApiExceptionHandler`
- `datasource/`：`BusinessType`、`DataSource(+View/Request/Connection)`、`DataSourceRepository`、`DataSourceService`
- `trino/`：`TrinoGateway`(接口)、`HttpTrinoGateway`(基于 `io.trino:trino-client`)
- 资源：`schema.sql`（用户/数据源/注册表/权限四表）

---

## 4. 已验证 / 测试状态

- 后端单测+集成测试：**24 个全绿**（口令、仓储、鉴权/越权、数据源 CRUD）。
- 前端：`npm run build`（tsc + vite）通过。
- **全栈 E2E `E2E_OK`**，覆盖：跨源 JOIN、后端健康、双角色登录、越权 401/403、数据源注册（别名 OceanBase、隐藏连接器名）、平台创建的 Catalog 可被 Trino 查询、停用后 Catalog 被删除。
- 持久化实测：重启 backend + trino 后，平台元数据仍在、Catalog 仍可查询。

---

## 5. 现状与后续

所有 Group 已完成：数据源管理、表目录、表级权限（Trino 引擎强制）、SQL 查询、结果导出。
后端单测/集成测试 54 个全绿，全栈 E2E `E2E_OK`。

后续可选：
- 用 `/opsx-archive` 归档本变更。
- 处理 `design.md` 中列出的技术债（明文密码、内存态 Token、规则端点鉴权、仅 SELECT 限制、浏览器级 E2E 等）。
- 接入真实达梦 / OceanBase(MySQL/Oracle) / Greenplum 连接器（平台层无需改动交互模型）。

---

## 6. 踩过的坑（避免重复）

- Trino 动态 Catalog 开关是 `catalog.management=DYNAMIC`；文件存储目录要写在 **`etc/catalog-store.properties`** 的 `catalog.config-dir`，**不能**放在 `config.properties`（否则启动校验报 `catalog.config-dir was not used`）。
- `CREATE CATALOG`：**连接器名不能加引号**（`USING "mysql"` 会失败，因为 `toString()` 带引号）；属性名含 `-` 必须加引号；catalog 名已用正则校验为简单标识符。
- `io.trino.client.ClientSession` 必须设置 `timeZone` 与 `locale`，否则请求构造时报 `timeZone is null`。
- MySQL 连接器的 JDBC URL **不能带库名**（否则报 `Database must not be specified`）；平台 URL 构建已按此处理。
- 后端端口 `8001`（可用端口段 8001–8004）；Trino `8080`。
- 在本机（Windows，仅 Java 11）**无法本地构建**，一律在远端 Maven 容器中构建/测试。

---

## 7. 安全提醒

- 远端服务器密码、数据库密码只存在于远端 `.env`；仓库已扫描确认无泄漏。
- `.gitignore` 已忽略 `data-fabric/deploy/.env` 与动态 catalog 生成物。
- 尚未 commit；如要提交，请确认先移除/忽略任何本地临时产物。
