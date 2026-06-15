# V9 Final 最终验收记录

日期：2026-06-15

最终分支：`main`

最终合并提交：`97f31a7` - `merge: v9 week type`

远端状态：本地 `main` 领先 `origin/main` 24 commits，尚未执行 `git push origin main`。

## 阶段提交

1. 可行性与阶段计划：`9a5cac8` - `merge: V9 week-type feasibility study + stage plan (docs only, reviewed)`
2. 阶段 0：`2ae2619` / merge `00b63b0` - 原型验证（方案详情页 weekType 展示与筛选）
3. 阶段 1：`533c669` + `93418c9` - 数据模型、输入源、冲突检测、落库闭环
4. 阶段 2：`0c81409` + `e52bb27` + `9a8929f` - 评分、导出、V4/V5 校验链
5. 阶段 1+2 汇总合并：`96b7ae9` - `merge: V9 stage1 + stage2 (data model + conflict + scoring + export + V4V5 validation)`
6. 阶段 3A：`c86f1ad` - 引擎时间模型扩展，slot 物理翻倍
7. 阶段 3B：`9e2216f` - 输出透传、β 激活、weekType 引擎冲突
8. 阶段 3C：`17c4045` - 端到端集成测试 + 小/中 benchmark
9. 阶段 3 汇总合并：`b289024` - `merge: v9 stage3 engine week type`
10. 阶段 4：`db60f4e` - 大档 benchmark 补跑与参数化
11. 阶段 4 汇总合并：`62a1aa3` - `merge: v9 stage4 benchmark large`
12. V9 总合并：`97f31a7` - `merge: v9 week type`

## 验收范围

V9 交付目标：系统全链路支持 `ALL` / `ODD` / `EVEN` 周次类型，并保持旧全周数据零回归。

覆盖链路：

| 链路 | 验收状态 |
|---|---|
| 数据模型与迁移 | ✅ `teaching_task` / `schedule` / `schedule_plan_item` weekType 字段与唯一键语义完成 |
| TeachingTask 输入源 | ✅ 后端 VO / API / 前端表单透传 weekType |
| 冲突检测 | ✅ DB 版、V3 版、引擎版 weekType overlap 语义统一 |
| 评分 | ✅ β 独立计数；Service 层与引擎层均激活 |
| 导出与课表展示 | ✅ 同时段 ODD/EVEN 不静默覆盖；VO 透传 |
| V4/V5 校验链 | ✅ 不再误报单双周共槽冲突 |
| V8/SOLVER_V8 引擎 | ✅ 移除阶段 1 stub，支持单双周任务 |
| 性能 benchmark | ✅ 小/中/大三档 R2 PASS |

## 验证命令与结果

### 1. 后端全量回归

```powershell
cd D:\paike\backend
mvn test
```

结果：`Tests run: 329, Failures: 0, Errors: 0, Skipped: 2`，BUILD SUCCESS。

Skipped：

- `V8BenchmarkComparisonTest`：benchmark gated，需显式 `-Dv8.benchmark=true`
- `V9WeekTypeBenchmarkTest`：benchmark gated，需显式 `-Dv9.benchmark=true`

### 2. 前端类型检查

```powershell
cd D:\paike\frontend
npx vue-tsc -b --pretty false
```

结果：exit code 0。

### 3. V9 benchmark 小/中档

```powershell
cd D:\paike\backend
mvn "-Dtest=V9WeekTypeBenchmarkTest" "-Dv9.benchmark=true" "-Dsurefire.useFile=false" "-DforkCount=0" test
```

结果：1 passed，BUILD SUCCESS。原始输出见 `reports/v9-week-type-benchmark-raw.txt`。

| 规模 | 数据集 | 未排 | 排下率 | 引擎ms | 回溯ms | 退火步数 |
|---|---|---:|---:|---:|---:|---:|
| 小（30任务） | 全 ALL 基线 | 0 | 100.0% | 3234 | 234 | 447 |
| 小（30任务） | 混合 weekType | 0 | 100.0% | 3119 | 119 | 499 |
| 中（120任务） | 全 ALL 基线 | 0 | 100.0% | 4054 | 1054 | 371 |
| 中（120任务） | 混合 weekType | 0 | 100.0% | 4255 | 1255 | 423 |

### 4. V9 benchmark 大档

```powershell
cd D:\paike\backend
mvn "-Dtest=V9WeekTypeBenchmarkTest" "-Dv9.benchmark=true" "-Dv9.benchmark.scale=large" "-Dv9.benchmark.dataset=all" "-Dsurefire.useFile=false" "-DforkCount=0" test
mvn "-Dtest=V9WeekTypeBenchmarkTest" "-Dv9.benchmark=true" "-Dv9.benchmark.scale=large" "-Dv9.benchmark.dataset=mixed" "-Dsurefire.useFile=false" "-DforkCount=0" test
```

结果：两次均 1 passed，BUILD SUCCESS。原始输出：

- `reports/v9-week-type-benchmark-large-all.txt`
- `reports/v9-week-type-benchmark-large-mixed.txt`

| 规模 | 数据集 | 未排 | 排下率 | 引擎ms | 回溯ms | 退火步数 |
|---|---|---:|---:|---:|---:|---:|
| 大（300任务） | 全 ALL 基线 | 0 | 100.0% | 4351 | 1351 | 260 |
| 大（300任务） | 混合 weekType | 0 | 100.0% | 4299 | 1299 | 265 |

## R2 性能门槛判定

口径：

- 回溯成功率 >= 95% = 混合数据集排下率 >= 95%
- 退火耗时增幅 <= 50% = 同等预算下，混合数据退火步数 >= 全 ALL 基线 / 1.5，即步数比 >= 0.67

| 规模 | 回溯成功率 | 退火步数比 | 结论 |
|---|---:|---:|---|
| 小 | 100.0% >= 95% | 499 / 447 = 1.12 >= 0.67 | PASS |
| 中 | 100.0% >= 95% | 423 / 371 = 1.14 >= 0.67 | PASS |
| 大 | 100.0% >= 95% | 265 / 260 = 1.02 >= 0.67 | PASS |

## E2E 状态

本轮未执行 `npm test`，原因：项目约束要求后端由用户在独立 PowerShell 终端手动启动，AI 不负责启动/等待 Spring Boot。

历史阶段记录：

- 阶段 1：现有 57 E2E 全绿 + V9 阶段 1 E2E 8 passed
- 阶段 2：新增 E2E 标记为待跑

建议推送前若需要完整前端验收，由用户手动启动：

```powershell
cd D:\paike\backend
mvn spring-boot:run
```

另开前端终端后运行：

```powershell
cd D:\paike
npm test
```

## 清理记录

- `V9WeekTypeBenchmarkTest` 每个 dataset 独立创建 semester / teachers / classes / courses / rooms / tasks / plan，并在 `finally` 中清理 plan、plan_item、unassigned_task、performance_record、rule_weight、teaching_task、classroom、course、class_info、teacher、semester。
- `EngineWeekTypeIntegrationTest` 使用独立测试数据并清理创建记录。
- benchmark raw 文件保留为验收证据，不属于数据库残留。

## 偏离与裁决

1. **R2 退火耗时口径**：退火按 `optimizeTimeBudgetMs` 墙钟预算停机，直接比较耗时无意义；最终裁决为同等预算下比较退火步数比，门槛为 mixed/all >= 1/1.5。
2. **大档拆分运行**：300 任务全 ALL 与 mixed 分两次跑，避免 harness 600s 超时；测试已支持 `v9.benchmark.scale` / `v9.benchmark.dataset` 参数化。
3. **V9 benchmark gated**：`V9WeekTypeBenchmarkTest` 默认跳过，避免日常 `mvn test` 被分钟级 benchmark 阻塞。
4. **E2E 未在最终轮执行**：后端启动受项目规则限制，最终轮只补跑后端全量与前端类型检查。

## 最终结论

V9 weekType 主功能链路已完成并合入 `main`。后端全量回归通过，前端类型检查通过，小/中/大 benchmark 全部满足 R2 门槛。

发布前剩余动作：

1. 可选：用户手动启动前后端后补跑 `npm test`
2. 推送：`git push origin main`
