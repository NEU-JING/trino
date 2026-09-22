## Context

现有前端仅 8 个文件、无组件库/路由、全部内联样式（见 `data-fabric/frontend/src/`）。

## Goals / Non-Goals

**Goals:**

- 汇报演示级观感：品牌外壳、双主题、总览大屏、编织拓扑图。
- 保持并提升功能完整性（加载/空状态/反馈）。
- 后续 `enhance-query-workbench` / `add-metadata-assist` 共享同一套设计 token。

**Non-Goals:**

- 不为炫技牺牲功能完整性。
- 不做移动端适配（仅桌面演示）。

## Decisions

### D1. 组件库选 Ant Design 5（+ ECharts + CodeMirror 6）

双主题用 `ConfigProvider` 的 `theme.algorithm` 切换成本最低；表格/Select/Cascader/日期等复杂组件开箱即用；中文企业观感与生态最稳。科技感由自研外壳/Dashboard/拓扑图承担，与组件库无关。

- **备选**：Tailwind + shadcn/ui（科技感上限高，但 DataTable/Combobox 需自拼，完整打磨工期长）；Arco Design（生态较弱）。

### D2. React 降级至 18

AntD v5 官方支持 React 16–18；现有代码未用 React 19 专有 API，降级避免补丁包。

### D3. 浅色为主、深色为辅

投屏演示浅色不反光；深色为加分项，二者由同一套 CSS 变量/token 驱动。

### D4. 拓扑图用 ECharts graph 系列

复用已引入的 ECharts，力导向布局表达「编织」；布局要求提高时再评估 `@antv/g6`。

## Risks / Trade-offs

- [AntD 默认偏管理系统观感] → 需额外主题定制（token + 外壳）才能达成科技感。
- [拓扑/统计接口性能] → 后端聚合接口需控制查询规模与缓存。
- [双主题花屏] → 自定义组件必须使用同一套 token。
