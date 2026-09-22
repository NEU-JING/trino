# platform-shell Specification

## Purpose

定义数据编织平台已登录页面的统一外壳：品牌 Banner 与 Slogan、浅色/深色双主题切换与持久化，以及全局加载态、消息反馈与空状态，确保各页面视觉与交互一致。

## Requirements

### Requirement: 品牌外壳与 Slogan

系统 SHALL 在所有已登录页面提供统一外壳，包含平台名称、Slogan、品牌 Logo 与一致的视觉风格（背景、强调色）。

#### Scenario: 登录后可见 Banner

- **WHEN** 用户登录后进入任一页面
- **THEN** 顶部显示包含平台名称与 Slogan 的 Banner

### Requirement: 双主题切换

系统 SHALL 支持浅色与深色两套主题，用户可切换，且选择在刷新或再次登录后保留。

#### Scenario: 切换到深色主题

- **WHEN** 用户切换主题为深色
- **THEN** 整个应用（含图表与自定义组件）切换为深色配色，无明显花屏或不可读文本

#### Scenario: 主题持久化

- **WHEN** 用户选择主题后刷新页面
- **THEN** 应用以先前选择的主题呈现

### Requirement: 全局加载与反馈

系统 SHALL 在数据加载时提供加载态、在操作成功/失败时提供全局消息提示、在无数据时提供空状态。

#### Scenario: 请求失败提示

- **WHEN** 某接口请求失败
- **THEN** 界面以非阻塞消息提示错误，且不破坏当前页面
