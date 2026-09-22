## Why

SQL 查询是平台的核心演示场景，但当前编辑器只是一个裸 `<textarea>`（`QueryPage.tsx:114`），默认仅 5–10 行、没有代码高亮、格式化与补全；结果表格也无法排序/分页，超过万行会卡顿。查询结果不落库，刷新即丢，无法回看历史或收藏常用查询。更关键的是，**表目录位于独立页面**（`TablesPage.tsx`），编写 SQL 时无法随手确认有哪些表可用、表的含义与列结构，只能在页面间来回切换，缺乏 DBeaver 这类数据库客户端的一体化体验。

## What Changes

- 将编辑器替换为 **CodeMirror 6**，提供 Trino SQL 语法高亮、自动格式化、以及基于平台元数据的表/列/关键字补全。
- **工作台布局重构与表目录整合**：查询页改为三区布局（左侧可搜索的对象树 catalog→schema→table→column、右侧上编辑器下结果），分隔可拖拽并记忆尺寸，编辑器默认高度充足；对象树展示表含义（描述/注释），可按需查看列结构（类型/可空/注释）与样例行，单击定位、双击将表名/列名插入编辑器。
- **结果栅格增强**：排序、分页/虚拟滚动、列宽、NULL 显示、长文本 tooltip、单元格复制、执行耗时与返回行数。
- **查询历史**：服务端持久化查询记录（SQL、用户、状态、耗时、行数），可回看与重跑。
- **收藏查询**：将常用 SQL 保存为可命名、可一键执行的收藏。
- **结果可视化**：数值结果可选柱状/折线/饼图展示。
- 编辑器快捷键（`Ctrl/Cmd+Enter` 执行）。

## Capabilities

### New Capabilities

- `sql-editor`: SQL 工作台——三区布局、表目录/对象浏览、编辑器高亮/格式化/补全与快捷键。
- `query-result-grid`: 查询结果栅格与可视化，含排序/分页/NULL 处理与图表。
- `query-history`: 查询历史与收藏查询的持久化、回看、重跑。

### Modified Capabilities

<!-- 见 `sql-query-execution` 既有 spec；若需变更其 requirement 将另附 delta。 -->

## Impact

- `data-fabric/frontend`：新增 `@codemirror/*` 依赖；重写 `QueryPage` 为三区工作台（对象树/编辑器/结果，AntD `Splitter`）。
- `data-fabric/backend`：新增查询历史/收藏表与接口；查询元数据（行数、耗时）记录。
- 依赖 `add-metadata-assist` 提供的 `metadata-discovery` 接口，作为对象树、表含义/结构/样例行与补全的统一数据源；对象树浏览范围与 `table-catalog` 的可见性规则一致。
