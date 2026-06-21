# V10 Stage 1 记录：数据模型与输入源

## 目标

让教学任务、方案项、正式课表都能保存连续周段 `start_week / end_week`。
本阶段只动数据模型、输入入口、序列化守卫，不改冲突检测、引擎、评分、导出链路。

## 改动清单

### 1. 数据库迁移

新增 `backend/src/main/resources/db/v24_continuous_week_range.sql`：

- `teaching_task` 加 `start_week INT NOT NULL DEFAULT 1` / `end_week INT NOT NULL DEFAULT 20`
- `schedule` 加同名字段
- `schedule_plan_item` 加同名字段
- 全部用 `information_schema.COLUMNS` 探测，幂等
- `application.yml` schema-locations 末尾追加 `v24_continuous_week_range.sql`

存量数据回填为 1-20，与 V9 整学期语义等价。唯一键不改：区间重叠由服务层 `WeekPatternSupport.overlap` 判定，不依赖数据库唯一键。

### 2. Entity 字段

- `TeachingTask`：`weekType` 后追加 `startWeek` / `endWeek`
- `Schedule`：同上
- `SchedulePlanItem`：同上

### 3. VO 透传

- `TeachingTaskVo`：加字段 + `fromEntity` 补 setter
- `ScheduleVo`：加字段 + `fromEntity` 补 setter
- `SchedulePlanItemVo`：加字段 + `fromEntity` 补 setter
- `TimetableVo`：加字段（导出/网格展示用，阶段 6 填充）

### 4. 输入入口

`TeachingTaskController.TaskForm`：

- 加 `startWeek` / `endWeek`，`@Min(1)` 校验
- create / update 把周段透传给 service

`TeachingTaskService.create / update`：

- 签名加 `startWeek` / `endWeek`
- 调用 `WeekPatternSupport.validateRange` 校验，非法值抛 400
- null 用默认值 1/20（`normalizeStartWeek` / `normalizeEndWeek`）
- update 在 weekType/startWeek/endWeek 全 null 时保留原值

### 5. 序列化守卫

更新三个 M16 序列化测试：

- `M16TeachingTaskVoSerializationTest`：EXPECTED_FIELDS 加 `startWeek` / `endWeek`，构造参数补 1/8，字段数 21→23
- `M16ScheduleVoSerializationTest`：同上，构造参数补 5/12，字段数 25→27
- `M16PlanItemVoSerializationTest`：同上，构造参数补 1/20，字段数 24→26

### 6. 前端

`frontend/src/api/teachingTask.ts`：

- `TeachingTask` 接口加 `startWeek` / `endWeek`
- `TeachingTaskForm` 接口加可选 `startWeek` / `endWeek`

`frontend/src/views/teachingTask/TeachingTaskView.vue`：

- form 默认值 `startWeek: 1, endWeek: 20`
- 新增/编辑 reset 回填周段
- 表格加「周段」列，默认 1-20 显示「整学期」，否则「1-8周」
- 表单加「起止周」输入项，两个 `el-input-number`，min/max 互相约束

## 验证

### 后端

命令：

```powershell
$env:DB_URL='jdbc:mysql://127.0.0.1:3306/paike?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
$env:DB_USERNAME='root'
$env:DB_PASSWORD='123456'
$env:JWT_SECRET='dev_local_secret_please_change_32_chars_minimum'
cd D:\paike\backend
mvn -q "-Dtest=M16TeachingTaskVoSerializationTest,M16ScheduleVoSerializationTest,M16PlanItemVoSerializationTest,WeekPatternSupportTest,WeekTypeConflictMatrixTest,ControllerRequestStringValidationTest,ControllerPaginationValidationTest,ScheduleConflictServiceTest" test
```

结果：

- 全部通过（BUILD SUCCESS，无 FAILED）
- 序列化守卫字段数与预期一致
- V9 单双周冲突测试无回退
- controller 校验测试无回退

### 前端

命令：

```powershell
cd D:\paike\frontend
npx vue-tsc --noEmit
```

结果：通过，无类型错误。

## 边界

本阶段未做（留给后续阶段）：

- `ScheduleConflictService` 接入 `WeekPatternSupport.overlap`（阶段 2）
- `SchedulePlanService` 方案冲突刷新（阶段 2）
- `ScheduleConflictReportService` 各 detect 方法（阶段 2）
- V4/V5/V6 校验链（阶段 3）
- V8 引擎 `EngineTask` / `InMemoryConflictDetector`（阶段 4）
- 评分链（阶段 5）
- `TimetableService` 导出填充 `startWeek` / `endWeek`（阶段 6）
- Mapper XML 中 `week_type` 相关 SQL 分支（阶段 2）
- E2E / Playwright 验证（阶段 7）

## 完成定义核对

| 完成定义 | 状态 |
|---|---|
| 旧请求不传周段仍成功 | ✅ null 走默认 1/20，V9 语义等价 |
| 新请求能保存并返回周段 | ✅ TaskForm → Service → Entity → VO 全链路透传 |
| 非法周段返回 400 | ✅ `WeekPatternSupport.validateRange` 抛 IAE → service 转 BusinessException(400) |
| `M16*SerializationTest` 对新增字段有覆盖 | ✅ 三个测试均补字段 + 构造参数 + 断言 |
| 前端类型检查通过 | ✅ `vue-tsc --noEmit` 通过 |

下一阶段应进入 `V10_02_开发阶段计划.md` 的阶段 2：手动排课与方案冲突链。
