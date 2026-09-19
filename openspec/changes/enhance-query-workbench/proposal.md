## Why

SQL 查询是平台的核心演示场景，但当前编辑器只是一个裸 `<textarea>`（`QueryPage.tsx:84`），没有代码高亮、格式化与补全；结果表格也无法排序/分页，超过万行会卡顿。查询结果不落库，刷新即丢，无法回看历史或收藏常用查询。

## What Changes

- 将编辑器替换为 **CodeMirror 6**，提供 Trino SQL 语法高亮、自动格式化、以及基于平台元数据的表/列/关键字补全。
- **结果栅格增强**：排序、分页/虚拟滚动、列宽、NULL 显示、长文本 tooltip、单元格复制、执行耗时与返回行数。
- **查询历史**：服务端持久化查询记录（SQL、用户、状态、耗时、行数），可回看与重跑。
- **收藏查询**：将常用 SQL 保存为可命名、可一键执行的收藏。
- **结果可视化**：数值结果可选柱状/折线/饼图展示。
- 编辑器快捷键（`Ctrl/Cmd+Enter` 执行）。

## Capabilities

### New Capabilities

- `sql-editor`: SQL 工作台编辑器，含高亮、格式化、补全与快捷键。
- `query-result-grid`: 查询结果栅格与可视化，含排序/分页/NULL 处理与图表。
- `query-history`: 查询历史与收藏查询的持久化、回看、重跑。

### Modified Capabilities

<!-- 见 `sql-query-execution` 既有 spec；若需变更其 requirement 将另附 delta。 -->

## Impact

- `data-fabric/frontend`：新增 `@codemirror/*` 依赖；重写 `QueryPage`。
- `data-fabric/backend`：新增查询历史/收藏表与接口；查询元数据（行数、耗时）记录。
- 依赖 `add-metadata-assist` 提供的补全数据源（表/列元数据）。
