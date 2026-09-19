## 1. 基线迁移

- [x] 1.1 确认工作区干净，记录待保留清单（`git ls-files data-fabric openspec`）
- [x] 1.2 从 `483` tag 创建新分支（如 `feature/data-fabric-on-483`）
- [x] 1.3 将 `data-fabric/` 与 `openspec/` 从旧分支签出到新分支，核对文件数一致
- [x] 1.4 核对根 `pom.xml` 版本为 483（不含 484-SNAPSHOT）
- [x] 1.5 本地提交迁移结果（不 push）

## 2. 版本锁定

- [x] 2.1 `data-fabric/deploy/docker-compose.yml` 的 `trinodb/trino:latest` 改为 `trinodb/trino:483`
- [x] 2.2 核对 `data-fabric/backend/pom.xml` 的 `trino.version=483`（已满足）
- [x] 2.3 在 `data-fabric/README.md` / `HANDOVER.md` 记录基线版本

## 3. 验证

- [x] 3.1 远端 Maven 容器执行后端 `mvn test` 全绿
- [x] 3.2 重建部署并跑全栈 E2E `smoke_test.py` 得到 `E2E_OK`
- [x] 3.3 确认跨源查询与动态 Catalog 在 483 行为一致
