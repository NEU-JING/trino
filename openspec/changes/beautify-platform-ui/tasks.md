## 1. 设计系统与依赖

- [ ] 1.1 前端 React 19→18（`react`/`react-dom`/`@types/*`），移除 React 19 补丁需求
- [ ] 1.2 引入 `antd`、`@ant-design/icons`、`echarts`、`echarts-for-react`、`react-router-dom`
- [ ] 1.3 建立主题 token（浅色科技蓝 / 深色科幻）与 CSS 变量，封装 `ThemeProvider` + 持久化

## 2. 平台外壳

- [ ] 2.1 设计 Banner：平台名、Slogan、Logo、背景（网格/渐变/玻璃拟态）
- [ ] 2.2 重构 `App.tsx`：AntD `Layout`、导航、用户区、主题切换开关
- [ ] 2.3 全局反馈：应用级 message/notification、路由级 loading 与错误边界
- [ ] 2.4 迁移 `DataSourcesPage`/`TablesPage`/`PermissionsPage`/`QueryPage` 到 AntD 组件与统一空状态

## 3. 总览 Dashboard

- [ ] 3.1 后端统计接口：数据源数、注册表数、用户数、查询量、数据源健康
- [ ] 3.2 后端拓扑接口：数据源→注册表→用户/授权的节点与边
- [ ] 3.3 前端 KPI 卡片 + 最近查询 + 健康状态
- [ ] 3.4 前端数据编织拓扑图（ECharts graph），节点点击联动跳转

## 4. 验证

- [ ] 4.1 `npm run build`（tsc + vite）通过
- [ ] 4.2 双主题切换无花屏，关键页面浅/深色均可读
- [ ] 4.3 浏览器级走查：登录→总览→各页面
