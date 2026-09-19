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
