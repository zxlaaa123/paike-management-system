# V9 阶段 0-1 记录（原型验证 + 数据模型 + 冲突检测 + 落库）

日期：2026-06-14（阶段 0-1 实际完成于更早对话，本文件为补记，依据提交历史与 V9_04 验收项还原）

分支：`feature/v9-stage1-data-conflict`（阶段 0-1 已合入，阶段 2 继续在此分支）

## 阶段 0：原型验证（止损门）—— commit `2ae2619`，merge `00b63b0`

**目的**：投入全链路改造前，先用最小改动验证"单双周明细的前端展示与筛选交互"，作为 Go/No-Go 闸门。

**范围限制**：只动前端方案详情页展示 + plan_item 查询透传，**不碰 V8 引擎、不碰数据模型、不碰唯一键、不接 TeachingTask 输入、不碰课表网格页**。

| 文件 | 改动 |
|---|---|
| `frontend/.../SchedulePlanDetailView.vue` | 明细列表加 weekType 列 + "单周/双周/全部"筛选控件，按 weekType 过滤 items |
| `frontend/.../utils/status.ts` | weekType 状态映射工具 |
| `frontend/components.d.ts` | 组件声明 |
| `reports/v9-stage0-filter-all.png` / `-even.png` | 原型截图（验证产物） |

**验收（Go 判定）**：明细列表正确展示 weekType 列（ALL/ODD/EVEN），单/双/全部筛选控件能正确过滤，用户确认交互体验可接受 → **Go，进入阶段 1**。

**能力边界声明**：本阶段不是"单双周功能"的任何一部分交付，只是交互原型。真实单双周能力从阶段 1 开始构建。

## 阶段 1：数据模型 + 输入源 + 冲突检测统一 —— commits `533c669` + `93418c9`

V9.1 的核心，最危险的阶段（R1 三套冲突检测语义漂移、R8 TeachingTask 输入源、R9 正式课表唯一键）。

### 1A：数据模型与输入源

| 文件 | 改动 |
|---|---|
| `db/v23_week_type_support.sql`（新增） | `ALTER TABLE schedule ADD COLUMN week_type VARCHAR(20) NOT NULL DEFAULT 'ALL'`；DROP + ADD schedule 三个唯一键（含 week_type）；DROP + ADD schedule_plan_item 唯一键 `uk_plan_task_slot` 及 3 个时间索引 |
| `entity/Schedule.java` | 加 `private String weekType;` |
| `entity/TeachingTask.java` | 加 `private String weekType;`（DEFAULT 'ALL'） |
| `mapper/ScheduleMapper.xml` / `ScheduleMapper.java` | result map 与查询字段加 week_type |
| `TeachingTaskService.java` / `TeachingTaskController.java` | create/update 读写 weekType，校验 ∈ {ALL, ODD, EVEN} |
| `service/vo/TeachingTaskVo.java` | 加 weekType 透传 |
| 前端 `api/teachingTask.ts` / `views/teachingTask/TeachingTaskView.vue` | interface + 表单加"周次类型"下拉（ALL全周/ODD单周/EVEN双周） |

**验收**：空库重建执行 v23 成功，schema 校验唯一键含 week_type；TeachingTask CRUD 正确存取 weekType，非法值拒绝；`DatabaseSchemaScriptTest` 通过；前端表单能选择并提交。

### 1B：冲突检测统一 + 两路对拍（R1 主战场）

| 文件 | 改动 |
|---|---|
| `WeekTypeSupport.java`（新增） | 统一工具类：`normalize`（null/空→ALL）+ `overlap`（3×3 冲突矩阵：ALL∩任意冲突，ODD∩EVEN不冲突） |
| `ScheduleConflictService.java`（DB 版） | `checkConflict` 加 weekType 参数，teacher/class/room 三处判定用 `WeekTypeSupport.overlap` |
| `V3ScheduleGenerateService.java`（V3 贪心版） | `hasConflict` 纳入 weekType；`checkTeacherDailyLimit`/`checkClassDailyLimit`/`hasSameCourseSameDay` 按周次过滤 |

**冲突矩阵（所有冲突类测试基准）**：
```
         ALL    ODD    EVEN
ALL      冲突   冲突    冲突
ODD      冲突   冲突    不冲突
EVEN     冲突   不冲突  冲突
```

**测试**：
- `WeekTypeConflictMatrixTest`（17 用例）：9 组合 + 对称性 + null/空白/大小写 + ODD/EVEN 共槽
- `ConflictDetectorWeekTypePairTest`：DB 版 vs V3 版逐格对拍
- `ScheduleConflictServiceTest`、`V3ScheduleGenerateServiceTest` 扩展单双周用例

### 1C：Service 落库链路 + 正式课表应用

| 文件 | 改动 |
|---|---|
| `V3ScheduleGenerateService.java` | `generatePlanItems` 读 task.weekType 生成；`toPlanItem` 写真实值（非硬编码 ALL）；四策略 daily limit 按周次独立计数 |
| `AutoScheduleService.java` | `saveSchedule` 写 weekType |
| `SchedulePlanService.java` | `applyPlan` plan_item → schedule 透传 weekType；`refreshPlanConflictState` 纳入 weekType |
| `EngineContextLoader.java` | V8 引擎 stub：装载时检测 task.weekType != ALL → 该任务落 unassigned，reasonCode=`WEEK_TYPE_NOT_SUPPORTED_BY_SOLVER_V8`（显式拒绝，非静默降级） |
| `SchedulePlanExplainService.java` | 配套 reasonCode 处理 |

**阶段 1 引擎 stub 行为**：V8 引擎显式拒绝非 ALL 任务（落 unassigned，不崩、不静默降级），是个可交付的中间态。旧四策略完整支持单双周。

### 阶段 1 E2E + VO 透传 —— commit `93418c9`

| 文件 | 改动 |
|---|---|
| 阶段 1 E2E 测试（8 用例） | 创建单双周教学任务 → 旧策略生成方案 → 课表单/双/全部切换查看 → apply → 正式课表验证 weekType |
| `ScheduleVo` 等 VO | weekType passthrough（补漏，踩了 M16ScheduleVoSerializationTest） |

## 阶段 1 整体验收（V9.1 DoD 对照 V9_04:132-153）

| 项 | 状态 |
|---|---|
| 旧四策略完整支持单双周 | ✅ |
| V8 引擎显式拒绝非 ALL 任务（落 unassigned，reasonCode 正确） | ✅ |
| 数据模型迁移幂等，空库可重建 | ✅ |
| 两路冲突对拍（DB vs V3）全绿 | ✅ |
| 现有 57 E2E 全绿 + 后端单测全绿 | ✅ |
| 新增 E2E：单双周任务创建 → 生成 → 切换 → apply → 验证 | ✅（8 用例） |

## 测试结果（阶段 1 结束时）

- mvn test 303+ passed / 现有 E2E 57 passed / V9 阶段1 E2E 8 passed，全绿

## 踩过的坑（@AllArgsConstructor 回归，供后续参考）

1. **M16TeachingTaskVoSerializationTest**：1A 给 TeachingTaskVo 加 weekType 字段时踩了全参构造器调用点。
2. **M16ScheduleVoSerializationTest**：1C 补漏 ScheduleVo passthrough 时又踩了一次。
- **教训**（已写入 AGENTS.md 约束）：给任何用 @AllArgsConstructor 的 VO/Entity 加字段，必须立即 grep 全参构造器调用点。

## 阶段 1 边界声明（当时已明确）

| 类别 | 阶段 1 状态 | 留待 |
|---|---|---|
| 评分链（ScoringFunctions/ScoreService） | ❌ 未改，留阶段 2 | 2A |
| 导出链（TimetableService cellKey） | ❌ 未改，留阶段 2 | 2B |
| V4/V5 校验链 | ❌ 未改，留阶段 2 | 2C |
| V8 引擎 weekType 支持 | ❌ stub 拒绝 | 阶段 3 |

> 阶段 2 记录见 `V9_STAGE2_记录.md`。
