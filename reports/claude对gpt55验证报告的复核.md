# Claude 对 gpt55 验证报告的复核

复核时间：2026-05-21
复核对象：`D:\paike\gpt55对mimov25发现的bug验证报告.md`
原始 bug 清单：`D:\paike\mimov25发现的bug.md`

---

## 工作流前提（关键背景）

按用户说明，本次三份产物的产生顺序为：

1. 在 `main` 分支：mimov25 扫码生成 `mimov25发现的bug.md`
2. 仍在 `main` 分支：gpt5.5 阅读上面那份清单后逐项核对当前代码，写出 `gpt55对mimov25发现的bug验证报告.md`
3. 之后从 `main` 创建分支 `fix/mimov25-bugs`
4. 在分支上参考 gpt5.5 的报告进行了批量修复（提交 `4ee4741`）
5. 当前 HEAD = `ce1ddcc`，分支**未合并回 main**

所以 gpt5.5 的"验证基准"是当时的 main 状态，**不是**当前分支。复核 gpt5.5 报告的正确口径，是把它对照到**当时的 main**——也就是当前 main 分支代码（因为后续主线没动相关文件）。

我重新用 `git show main:<path>` 抽查了关键文件，下面给出复核结论。

---

## 复核结论汇总

把 gpt5.5 的每条结论对照到 main 时的代码状态：

| 编号 | gpt5.5 结论 | main 实际 | 复核判断 |
|---:|---|---|---|
| 1 | 确认存在 | bug 确实存在 | ✅ 正确 |
| 2 | 确认存在 | bug 确实存在 | ✅ 正确 |
| 3 | 确认存在 | bug 确实存在 | ✅ 正确 |
| 4 | 确认存在 | bug 确实存在 | ✅ 正确 |
| 5 | 确认存在 | bug 确实存在 | ✅ 正确 |
| 6 | 确认存在 | bug 确实存在 | ✅ 正确 |
| 7 | 部分存在（N+1 确认 / day 0 不成立） | N+1 确实存在；day 0 跳过逻辑也确实存在 | ✅ 正确 |
| 8 | 确认存在 | 多个 Service 确实未声明 rollbackFor | ✅ 正确 |
| 9 | 确认存在 | bug 确实存在 | ✅ 正确 |
| 10 | 确认存在 | bug 确实存在 | ✅ 正确 |
| 11 | 确认存在 | bug 确实存在 | ✅ 正确 |
| 12 | 不成立 | RoomType.LAB("LAB") 确实存在 | ✅ 正确 |
| 13 | 确认存在 | rescore 确实修改入参 | ✅ 正确 |
| 14 | 低风险成立 | 确实是字符串拼接，调用方硬编码 | ✅ 正确 |
| 15 | 确认存在 | uk_teacher_timeslot 确实含 deleted | ✅ 正确 |
| 16 | 确认存在 | schedule_plan_item 确实缺唯一约束 | ✅ 正确 |
| 17 | 需降级（声称已有 idx_schedule_semester） | **main 上 v6_schedule_index.sql 实际没有 idx_schedule_semester** | ❌ 错误 |
| 18 | 性能风险成立 | 确实是 SELECT DISTINCT | ✅ 正确 |
| 19 | 确认存在 | ClassroomView 确实缺 try/catch | ✅ 正确 |
| 20 | (未列入) | **CourseView 缺 try/catch 是真 bug** | ❗ 漏判 |
| 21 | 确认存在 | ClassInfoView 确实缺 try/catch | ✅ 正确 |
| 22 | 确认存在 | TeacherUnavailableTimeView 确实缺 try/catch | ✅ 正确 |
| 23 | 不成立 | async-validator 行为一致；定性偏维护者口径 | ⚠️ 理解偏差 |
| 24 | 未确认（查不到对应空 catch） | **bug 真实存在，gpt5.5 路径前缀写错（v5/ vs schedule/）漏掉** | ❌ 错误 |
| 25 | 确认存在 | RepairTaskListView 确实把 API 错误吞掉 | ✅ 正确 |
| 26 | 确认存在 | CandidatePositionView 模板拼接确实可能含 undefined | ✅ 正确 |
| 27 | 确认存在 | confirm/apply 非原子确实存在 | ✅ 正确 |
| 28 | 确认存在 | useCrudForm 确实只 try/finally | ✅ 正确 |

**统计：**
- 完全判对：24 项
- 判错：2 项（#17、#24）
- 漏判：1 项（#20）
- 理解偏差：1 项（#23）

---

## 三条 gpt5.5 出错的细节

### #17 idx_schedule_semester 实际并没建

gpt5.5 在报告里写：

> 证据：项目后续迁移 `add_schedule_indexes_v6` 已添加 `idx_schedule_semester`。
> 说明：以当前仓库完整迁移集合为准，该问题已被后续迁移覆盖。

但用 `git show main:backend/src/main/resources/db/v6_schedule_index.sql` 看 main 的实际内容，`add_schedule_indexes_v6` 存储过程只创建：

- `idx_schedule_time_slot`、`idx_schedule_teacher`、`idx_schedule_class`、`idx_schedule_classroom`、`idx_schedule_task`
- 唯一键 `uk_schedule_teacher_slot`、`uk_schedule_class_slot`、`uk_schedule_classroom_slot`

**没有** `idx_schedule_semester`。gpt5.5 这里编造了不存在的事实——可能是被 `idx_schedule_class` 之类的近形索引名误导，或者是在没读完文件的情况下做的推断。

幸运的是分支上的 `4ee4741` 修复提交补上了 `idx_schedule_semester (semester_id, deleted)`（v6_schedule_index.sql 里现在多了对应分支）。但**这并不等于 gpt5.5 当时的"需降级"结论是对的**——那只是后来用户主动补的。

### #20 CourseView.vue 漏掉了一个文件

mimov25 原报告的 #19-22 在表格里列了 4 个文件：

```
19 | classroom/ClassroomView.vue
20 | course/CourseView.vue        ← gpt5.5 没提
21 | classInfo/ClassInfoView.vue
22 | teacher/TeacherUnavailableTimeView.vue
```

gpt5.5 报告的 #19-22 节正文只列了 3 个文件：

> - `frontend/src/views/classInfo/ClassInfoView.vue`
> - `frontend/src/views/classroom/ClassroomView.vue`
> - `frontend/src/views/teacher/TeacherUnavailableTimeView.vue`

CourseView 直接被丢了。后果是分支上的修复也只改了这 3 个，**CourseView.vue 至今仍是裸 await**：

```javascript
// frontend/src/views/course/CourseView.vue:129-134（main 与 branch 完全一致）
async function handleDelete(row: Course) {
  await ElMessageBox.confirm(`确定删除课程「${row.courseName}」吗？`, '提示', { type: 'warning' })
  await deleteCourse(row.id)
  ElMessage.success('删除成功')
  fetchData()
}
```

`git diff main HEAD -- frontend/src/views/course/CourseView.vue` 为空，证实该文件 main / branch 完全一致，bug 一直在。

### #24 SchedulePlanDetailView 找错了目录

gpt5.5 说"未确认，未找到 `frontend/src/views/v5/SchedulePlanDetailView.vue` 中对应空 catch"。

实际路径是 `frontend/src/views/schedule/SchedulePlanDetailView.vue`（`Glob frontend/src/views/**/SchedulePlanDetailView.vue` 在项目里**只有这一个匹配**）。该文件 line 69-146 是这样的：

```javascript
async function fetchData()        { ... } catch (e) { console.error(e) } ...
async function fetchScoreData()   { ... } catch (e) { console.error(e) }
async function loadLogs()         { ... } catch (e) { console.error(e) }
async function loadUnassigned()   { ... } catch (e) { console.error(e) }
async function loadAdjustLogs()   { ... } catch (e) { console.error(e) }
```

5 个加载函数全部静默吞错，与 mimov25 描述完全一致。gpt5.5 的报告里只去 `views/v5/` 找，**漏掉了真实位置**。该文件在 main 和当前分支也完全一致，bug 仍在。

---

## #23 的理解偏差

mimov25 把 "`required: true` 对 0 通过校验" 视为 bug；gpt5.5 用 async-validator 实测确认 `0:pass`，结论是 bug **不成立**（async-validator 设计如此）。

两边在事实层面一致（`0` 确实通过），分歧只在**该不该叫它 bug**：

- mimov25：业务上希望选择 ID 类字段必须 > 0，所以这是 bug
- gpt5.5：这是 validator 设计语义，要业务约束就补 `min: 1` 或自定义校验器，不算 bug

两边修复建议**指向同一个方向**（补 `min: 1` 或 `value > 0`），只是结论标签不同。这条不算 gpt5.5 出错，但可以记为"定义之争"。

---

## 当前分支真实未修的 bug 清单

把 mimov25 清单与当前分支 (`ce1ddcc`) 实际状态对账后，仍然待办的项：

| 编号 | 项 | 文件 | 状态原因 |
|---:|---|---|---|
| 20 | CourseView.handleDelete 无 try/catch | `frontend/src/views/course/CourseView.vue:129-134` | gpt5.5 漏判 → 4ee4741 漏修 |
| 24 | SchedulePlanDetailView 5 个加载函数静默吞错 | `frontend/src/views/schedule/SchedulePlanDetailView.vue:69-146` | gpt5.5 找错路径 → 4ee4741 漏修 |
| 23 | 数字字段 0 通过 required 校验 | `TeachingTaskView.vue:48-53`、`ScheduleView.vue:49-53` 等 | 双方对 bug 定义有分歧，未在 4ee4741 中处理 |
| 27 | 试算 confirm/apply 数据层非原子 | `backend` + `SimulationPlanDetailView.vue` | 分支上只加了 UX 提示（`confirmedBeforeApply` 标志改了文案），后端没新增原子接口 |
| 13 | rescore 副作用 | `ScheduleScoreService.java:70-72` | 分支上加了 doc 注释明确为契约，未做拆分 |

---

## 对 gpt5.5 验证质量的整体评价

按"对照 main 是否准确"的口径来看，gpt5.5 报告**整体可靠**：28 项里 24 项判断正确，1 项理解偏差。但有 3 个具体失误值得记录：

1. **#17 编造了不存在的迁移内容**。这是最危险的一类——结论(降级)显著影响修复优先级。后续如果不是用户在分支里主动补了索引，这条 bug 会被错误地降低优先级而不修。
2. **#20 漏列文件**。mimov25 给了 4 个文件，gpt5.5 在正文里只展开 3 个，导致后续修复也只修 3 个。这种"清单核对不完整"在批量审计场景里直接转化为漏修。
3. **#24 路径前缀错误**。`views/v5/` vs `views/schedule/` 一个字之差直接放过一个真 bug。

### 启示

- gpt5.5 的"判断准确率"虽然高，但**漏判和路径错误**对最终修复结果的危害比"误判"更大——误判会被修复时再次复核，漏判会直接静默通过。
- 后续如果再做这种"基于 AI 审查 + AI 验证"的流水线，建议在验证报告后加一个 **mimov25 清单逐条 ID 反向勾选**的步骤：每个原始 ID 必须在验证报告里出现一次，缺一就报错。
- 对于"已被后续迁移覆盖"这种声称，应该要求验证报告**贴出实际文件路径 + 关键行**，而不是只说"项目已有"。

---

## 后续建议

1. 把本复核里列的 5 项真实未修内容（#20、#24、#23、#27、#13）合到 `fix/mimov25-bugs` 分支再做一次补修，然后合回 main。其中 #20、#24 是最低成本就能修掉的前端遗漏。
2. 同步更新 `mimov25发现的bug.md`，给已修复的项标上 `[FIXED in 4ee4741]`，剩下的保留为 TODO。
3. 更新 `gpt55对mimov25发现的bug验证报告.md` 里 #17 的结论（实际应为"确认存在"，而非"需降级"）；同时在 #19-22 段补上漏列的 CourseView；#24 路径更正为 `views/schedule/`。

---

复核人：Claude (Opus 4.7)
复核基准：`fix/mimov25-bugs` @ `ce1ddcc`、`main` 当前 HEAD
