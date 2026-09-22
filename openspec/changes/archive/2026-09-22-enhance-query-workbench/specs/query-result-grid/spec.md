## ADDED Requirements

### Requirement: 结果栅格交互

系统 SHALL 支持对查询结果按列排序、分页或虚拟滚动、列宽调整与单元格复制。

#### Scenario: 排序

- **WHEN** 用户点击某列的表头
- **THEN** 结果按该列排序

#### Scenario: 大结果集浏览

- **WHEN** 结果行数较大（万行级）
- **THEN** 通过分页或虚拟滚动流畅浏览，不一次性渲染全部行

### Requirement: NULL 与长文本呈现

系统 SHALL 明确区分 NULL 与空字符串，并对超长文本提供完整查看方式。

#### Scenario: 长文本

- **WHEN** 单元格文本超长
- **THEN** 单元格以截断显示并提供 tooltip/展开查看完整内容

### Requirement: 结果可视化

系统 SHALL 允许将数值结果以基础图表（柱状/折线/饼图）展示。

#### Scenario: 切换图表

- **WHEN** 查询结果包含数值列，用户选择图表视图
- **THEN** 结果以所选图表类型呈现
