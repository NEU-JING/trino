## ADDED Requirements

### Requirement: CSV 导出

系统 SHALL 允许将当前查询结果导出为 CSV 文件，使用 UTF-8 编码并写入 BOM，以保证 Excel 正确识别中文。

#### Scenario: 导出 CSV

- **WHEN** 查询人员对查询结果点击导出 CSV
- **THEN** 系统下载包含表头与全部结果行的 UTF-8(BOM) CSV 文件

### Requirement: Excel 导出

系统 SHALL 允许将当前查询结果导出为 Excel（.xlsx）文件。

#### Scenario: 导出 Excel

- **WHEN** 查询人员对查询结果点击导出 Excel
- **THEN** 系统下载包含表头与全部结果行的 .xlsx 文件

### Requirement: 导出遵守权限

系统 SHALL 仅导出当前用户有权访问的结果数据，导出行为 MUST 受与查询相同的权限约束。

#### Scenario: 无权限数据不可导出

- **WHEN** 用户尝试导出包含其无权限数据的查询结果
- **THEN** 查询阶段即被拒绝，不产生导出文件

### Requirement: 大数据量流式导出

系统 SHALL 以流式方式生成导出文件，避免将整个结果集加载进内存。

#### Scenario: 导出超过内存阈值的结果

- **WHEN** 查询结果行数超过内存阈值
- **THEN** 系统仍能成功导出且不因内存不足失败
