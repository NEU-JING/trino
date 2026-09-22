## ADDED Requirements

### Requirement: SQL 语法高亮与格式化

系统 SHALL 提供带 SQL 语法高亮与自动格式化的编辑器。

#### Scenario: 高亮显示

- **WHEN** 用户在 SQL 编辑器中输入查询
- **THEN** 关键字、字符串、标识符等按语法着色

#### Scenario: 格式化

- **WHEN** 用户触发格式化
- **THEN** SQL 被格式化为缩进规范、可读的形式

### Requirement: SQL 补全

系统 SHALL 基于当前用户可见的已注册表与列，为编辑器提供表名、列名与关键字补全。

#### Scenario: 表名补全

- **WHEN** 用户开始输入表名
- **THEN** 编辑器提示当前用户可见的表名候选

#### Scenario: 不泄露越权对象

- **WHEN** 某表未被授权给当前用户
- **THEN** 该表不出现在补全候选中

### Requirement: 执行快捷键

系统 SHALL 支持 `Ctrl/Cmd+Enter` 执行当前 SQL。

#### Scenario: 快捷键执行

- **WHEN** 用户在编辑器中按下 `Ctrl/Cmd+Enter`
- **THEN** 当前 SQL 被提交执行

### Requirement: 工作台布局与自适应

系统 SHALL 以可调整的一体化工作区呈现表目录、SQL 编辑器与结果，避免编辑区过小，并避免在页面间切换。

#### Scenario: 默认高度充足

- **WHEN** 用户进入 SQL 查询页
- **THEN** 编辑器默认展示不少于 12 行的高度

#### Scenario: 可调整分区

- **WHEN** 用户拖动对象树与编辑器/编辑器与结果之间的分隔
- **THEN** 各分区尺寸相应变化，其余分区保持可用

#### Scenario: 布局记忆

- **WHEN** 用户调整过分区尺寸后再次进入 SQL 查询页
- **THEN** 系统沿用上次的分区尺寸

### Requirement: 表目录浏览

系统 SHALL 在工作台内提供按 catalog→schema→table 分组的对象树，仅展示当前用户可见（已注册 ∩ 已授权）的表，并支持按关键字搜索。

#### Scenario: 浏览可用表

- **WHEN** 用户展开对象树
- **THEN** 系统仅展示其有权访问的已注册表，并按 catalog/schema 分组

#### Scenario: 搜索表

- **WHEN** 用户在对象树搜索框输入关键字
- **THEN** 对象树过滤出表名或含义匹配的表

#### Scenario: 对象树不泄露越权对象

- **WHEN** 某表未被授权给当前用户
- **THEN** 该表不出现在对象树中

### Requirement: 表含义与结构查看

系统 SHALL 允许用户在不离开工作台的情况下查看表的含义（描述/注释）与结构（列名、类型、可空、注释），并按需查看样例行。

#### Scenario: 查看表结构与含义

- **WHEN** 用户选中对象树中的某张表
- **THEN** 系统展示该表的名称、含义与列结构

#### Scenario: 查看样例行

- **WHEN** 用户请求查看某张表的样例行
- **THEN** 系统返回受限行数的样例数据，且不因源端异常影响编辑器使用

### Requirement: 从对象树插入标识符

系统 SHALL 允许用户从对象树将表名或列名插入编辑器当前光标处。

#### Scenario: 插入表名

- **WHEN** 用户双击对象树中的表或选择「插入」
- **THEN** 该表的限定名被插入到编辑器光标位置，且不自动执行
