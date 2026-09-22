## Why

原型功能闭环已满足需求，但前端为纯内联样式的原生控件、无组件库、无总览页，观感粗糙、缺少品牌感，达不到向领导汇报演示的标准。

## What Changes

- 引入 **Ant Design 5** 设计系统替换全部原生控件与内联样式；前端 **React 19 降级至 18** 以匹配组件库。
- **双主题**（浅色科技蓝 / 深色科幻）可切换并持久化。
- **平台外壳**：顶部 Banner（平台名 + Slogan）、导航、用户信息与退出、品牌视觉（Logo/背景网格/玻璃拟态）。
- **总览 Dashboard**：KPI 卡片（数据源数、注册表数、用户数、查询量）、最近查询、数据源健康状态。
- **数据编织拓扑图**：力导向图展示「数据源 → 注册表 → 用户/授权」，节点点击联动跳转。
- 统一的加载态（skeleton/spin）、空状态、消息提示（message/notification）与错误呈现。

## Capabilities

### New Capabilities

- `platform-shell`: 平台整体外壳与品牌视觉，含 Banner/Slogan、导航、双主题切换与全局反馈。
- `platform-dashboard`: 登录后总览页，含 KPI、最近查询、数据源健康与数据编织拓扑图。

### Modified Capabilities

<!-- 其余页面的行为不变，仅视觉/交互升级由实施任务承担。 -->

## Impact

- `data-fabric/frontend`：新增 `antd`、`@ant-design/icons`、`echarts`、`echarts-for-react`、`react-router-dom`；React 19→18；新增主题/布局/图表模块。
- `data-fabric/backend`：新增只读统计与拓扑接口。
- 现有页面（DataSources/Tables/Permissions/Query）迁移到 AntD 组件。
