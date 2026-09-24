## 1. 探路 Spike

- [x] 1.1 部署达梦 DM8 容器（5236），确认 `SYSDBA` 可连、可建库建表、授权文件有效
- [x] 1.2 纯 Java + `DmJdbcDriver18` 验证 `information_schema.tables/columns` 是否可用，记录标识符大小写行为
- [x] 1.3 结论固化：`TableCatalogService` 是否需调整；类型映射清单

## 2. 连接器实现

- [x] 2.1 创建 `data-fabric/trino-dameng` Maven 工程，依赖 `io.trino:*:483`
- [x] 2.2 `DmJdbcDriver18.jar` 用 `install-file` 装入 `.m2` 并声明依赖
- [x] 2.3 实现 `DamengPlugin` / `DamengConnectorFactory` / `DamengClient`（继承 `BaseJdbcClient`）/ `DamengClientModule` / 类型映射
- [x] 2.4 `META-INF/services/io.trino.spi.Plugin` 与插件目录组装
- [x] 2.5 单元测试（不依赖真实库的部分）

## 3. 部署接入

- [x] 3.1 `docker-compose.yml`：Trino 镜像固定 `483`，挂载 `./trino/plugin/trino-dameng`
- [x] 3.2 新增 `dm8` 服务接入 `data-fabric-net`，端口 5236
- [x] 3.3 验证 Trino 启动加载 `trino-dameng` 无 SPI 错误

## 4. 平台接入

- [x] 4.1 `BusinessType` 新增 `DAMENG("dameng","达梦")`
- [x] 4.2 `DataSourceService.connectorProperties` 增加 `jdbc:dm://host:port` 分支
- [x] 4.3 平台注册达梦数据源 → 发现表 → 注册 → 授权

## 5. 端到端验证

- [x] 5.1 `CREATE CATALOG ... USING dameng` 成功，`SHOW SCHEMAS`/`SELECT` 通
- [x] 5.2 跨 4 源 JOIN E2E 通过并纳入 `smoke_test.py`
