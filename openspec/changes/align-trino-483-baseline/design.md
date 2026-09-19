## Context

`git rev-list HEAD...483` 为 `793 0`，即 HEAD = 483 + 793 个提交（484-SNAPSHOT 开发），且 `data-fabric/`（107 文件）提交在其上。483 是 HEAD 的祖先。后端/前端/e2e 均为独立工程，不依赖 484 引擎源码。

## Goals / Non-Goals

**Goals:**

- 源码基线、运行时镜像、后端客户端、连接器 SPI 统一为 483。
- 完整保留 `data-fabric/` 与 `openspec/` 的既有成果与历史。
- 部署不再受 `latest` 漂移影响。

**Non-Goals:**

- 不改变平台功能行为。
- 不调整后端 Spring Boot / 前端依赖版本。
- 不引入 484 之后的新引擎特性。

## Decisions

### D1. 以 `483` tag 为基线重建分支，而非逐提交回退

```
git checkout -b feature/data-fabric-on-483 483
git checkout <旧分支> -- data-fabric openspec
```

保留完整历史，避免对 793 个提交做 revert/reset。

- **备选**：`git revert`/`reset` 回退（否决，噪声大且易漏）。

### D2. 镜像锁定 `483` 而非 `latest`

避免 484 正式发布后 `latest` 漂移，导致与连接器 SPI 不匹配。

## Risks / Trade-offs

- [`data-fabric` 可能隐含依赖 484 引擎] → 当前后端/前端均独立，风险低；迁移后重跑测试与 E2E 确认。
- [分支历史分叉] → 明确数据迁移命令与文件清单，迁移后核对文件数一致。
