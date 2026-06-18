# V10 Stage 0 记录：周模式工具与红线测试

## 目标

先把 V10 连续周段的最小语义钉死，不改数据库、不改前端、不接入排课主链。

本阶段只新增：

- `WeekPatternSupport`
- `WeekPatternSupportTest`

## 关键裁决

V10 冲突判断不再只看 V9 的 `ALL / ODD / EVEN` 三值矩阵，也不能只看 `startWeek/endWeek` 区间端点。

统一规则：

> 同资源同物理时段下，只有实际自然周集合相交才算冲突。

实现策略：

- 用 `weekType + startWeek + endWeek` 表达周模式。
- 内部用 `long` bit mask 表达第 1-63 周。
- 当前默认学期周数仍为 20。
- null 周段默认 `1-20`，兼容 V9 存量语义。

## 已覆盖红线

| A | B | 预期 |
|---|---|---|
| `ALL 1-8` | `ALL 9-16` | 不冲突 |
| `ALL 1-8` | `ODD 5-12` | 冲突 |
| `ODD 1-8` | `EVEN 1-8` | 不冲突 |
| `ODD 1-8` | `EVEN 8-12` | 不冲突 |
| `ALL 8-8` | `ODD 1-9` | 不冲突 |
| `ODD 1-9` | `ODD 8-12` | 冲突 |
| `EVEN 2-2` | `ALL 1-3` | 冲突 |

## 验证

命令：

```powershell
$env:DB_URL='jdbc:mysql://127.0.0.1:3306/paike?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
$env:DB_USERNAME='root'
$env:DB_PASSWORD='123456'
$env:JWT_SECRET='dev_local_secret_please_change_32_chars_minimum'
cd D:\paike\backend
mvn "-Dtest=WeekPatternSupportTest,WeekTypeConflictMatrixTest" test
```

结果：

- `WeekPatternSupportTest`: 14 passed
- `WeekTypeConflictMatrixTest`: 17 passed
- Total: 31 passed
- `BUILD SUCCESS`

日志：

- `C:\Users\zxl\AppData\Local\Temp\paike-v10-stage0-test-20260618-124606.log`

## 边界

本阶段未做：

- 数据库字段 `start_week/end_week`
- Entity / DTO / VO 字段透传
- `ScheduleConflictService` 接入
- `SchedulePlanService` 接入
- V8 引擎接入
- 评分链接入
- 导出链接入
- 前端表单

下一阶段应进入 `V10_02_开发阶段计划.md` 的阶段 1：数据模型与输入源。
