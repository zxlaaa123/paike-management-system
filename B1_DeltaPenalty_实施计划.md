# B1 DeltaPenalty 评分双轨收口实施计划

日期：2026-05-26

## 1. 背景

当前 V3 评分是双轨制：

- 在线生成：`V3ScheduleGenerateService.scoreCandidate` 对每个候选 `(slot, room)` 做正向启发式加分，分高者优先。
- 离线评分：`ScheduleScoreService.rescore` 对完整方案计算 penalty，再写 `schedule_score_detail` 和 `schedule_plan.total_score`。

B1 的目标不是简单搬代码，而是把在线候选评分改成接近离线口径的边际罚分：

```text
candidateScore = -ΔPenalty + tieBreaker
```

也就是：放入这个候选后，完整方案的离线罚分会增加多少，增加越少越优。

## 2. 当前关键入口

- `V3ScheduleGenerateService.findBestCandidate`
  - 扫描 slot。
  - 调用 `evaluateRoomsForSlot`。
- `V3ScheduleGenerateService.evaluateRoomsForSlot`
  - 过滤资源冲突。
  - 调用 `scoreCandidate`。
  - 写候选评分日志。
- `V3ScheduleGenerateService.scoreCandidate`
  - 当前在线正向启发式：
    - `CLASSROOM_UTILIZATION`
    - `CLASS_DAILY_BALANCE`
    - `TEACHER_DAILY_LOAD`
    - `COURSE_DISTRIBUTION`
    - `CONTINUOUS_PERIOD_LIMIT`
    - `MORNING_THEORY_PRIORITY`
- `ScoringFunctions`
  - 当前同时放在线 `candidateXxx` 和离线 `penaltyXxx` 纯函数。
- `ScheduleScoreService.buildScoreContext`
  - 聚合完整方案统计。
  - 调用离线 `penaltyXxx`。
- `ScheduleScoreServiceTest`
  - 已锁定离线 rescore 的数值基线。

## 3. 现状差异

同名规则码在线/离线不是同一公式：

- `CLASSROOM_UTILIZATION`
  - 在线：单候选 `studentCount / capacity`，越贴近容量越高。
  - 离线：教室使用次数方差，越不均衡罚分越高。
- `CLASS_DAILY_BALANCE` / `TEACHER_DAILY_LOAD`
  - 在线：`1 / (1 + 当天已排数)`。
  - 离线：跨日课程数方差。
- `COURSE_DISTRIBUTION`
  - 在线：同班同课同日二值。
  - 离线：重复天数占比。
- `CONTINUOUS_PERIOD_LIMIT`
  - 在线：同教师或同班相邻二值。
  - 离线：教师每日连续链平均惩罚。
- `MORNING_THEORY_PRIORITY`
  - 在线：理论课且上午二值。
  - 离线：下午课占比，不区分课程类型。

因此 B1 会改变候选排序，排课结果和最终分数可能漂移。

## 4. 设计原则

1. 不直接把 `ScheduleScoreService.rescore` 塞进候选循环。
   - 每个候选全量 rescore 性能不可接受。
2. 不先切换主流程。
   - 先抽增量计算并加测试，确认数值和性能边界。
3. 离线 `rescore` 的现有基线先保持不变。
   - B1 先改在线候选评分，不改最终写库评分。
4. 每个 commit 可回滚。
   - 先测试，再抽 helper，再灰度接入，再移除旧路径。
5. 日志要能解释。
   - 候选日志至少保留总分；后续可追加主要 delta 来源。

## 5. 建议分阶段

### Phase 0：基线冻结

目标：确认当前分支未改行为。

动作：

- 跑 `mvn test -Dtest=ScheduleScoreServiceTest`。
- 跑 `mvn test -Dtest=SchedulingSupportTest`。
- 如用户能启动后端，再记录一次 V3 生成冒烟：
  - 策略：`CLASS_BALANCE` 或当前实际 UI 使用策略。
  - 记录 `totalScore`、scheduled、unscheduled、conflict。

产物：

- 在计划或单独 baseline 文档中记录当前数值。

### Phase 1：抽 DeltaPenalty 计算模型，不接主流程

目标：创建可单测的边际罚分函数，不影响生成。

建议新增：

- `ScoringFunctions.deltaPenaltyXxx(...)` 或独立 `DeltaPenaltyScorer`。

先支持软规则：

- `CLASS_DAILY_BALANCE`
- `TEACHER_DAILY_LOAD`
- `COURSE_DISTRIBUTION`
- `CONTINUOUS_PERIOD_LIMIT`
- `CLASSROOM_UTILIZATION`
- `MORNING_THEORY_PRIORITY`

硬规则暂不纳入 delta：

- V3 候选前置过滤已经跳过资源冲突、教师禁排、日上限等。
- 若把硬规则也纳入，需重新定义“允许冲突但高罚分”还是“继续过滤”，范围会扩大。

验收：

- 新增单测验证 delta 与全量 penalty 差值一致：

```text
delta(rule, generatedItems, candidate)
  == penalty(generatedItems + candidate) - penalty(generatedItems)
```

### Phase 2：性能优化

目标：避免每个候选都复制列表并全量聚合。

优先做简单安全版：

- 小 fixture 单测先用全量差值保证正确性。
- 生产实现允许先复制 `generatedItems + candidate`，但必须跑实际规模冒烟测耗时。

若性能不足，再做增量结构：

- owner/day count map。
- course/day count map。
- teacher/day startPeriod list。
- room use count map。

不要一开始就写复杂 Welford。先让正确性落地，再优化热点。

### Phase 3：灰度接入 V3 在线评分

目标：让 `scoreCandidate` 改为 `-weightedDeltaPenalty + tieBreaker`。

建议保留旧实现一段时间：

- `scoreCandidateLegacy(...)`
- `scoreCandidateDeltaPenalty(...)`

切换方式：

- 最好先用局部常量或私有方法固定切换，避免引入配置系统。
- 不加 Map 反查，不改 `ScoringDimensions` 的纯文档定位。

候选评分：

```text
score = -sum(weight(rule) * deltaPenalty(rule)) + stableTieBreaker
```

注意：

- 离线 `penaltyXxx` 返回 `[0,1]`，当前 soft metric 实际扣分为 `weight * penalty`。
- delta 可能为负数，表示候选改善当前分布；此时 `-ΔPenalty` 为正。
- tieBreaker 要保留更早时段微偏好，但权重要远小于 delta 主体。

### Phase 4：回归与基线决策

必须跑：

- `mvn compile`
- `mvn test -Dtest=ScheduleScoreServiceTest`
- `mvn test -Dtest=SchedulingSupportTest`
- 新增 delta 单测。

需要用户配合的冒烟：

- V3 生成策略。
- V4 自动排课基线。
- 至少记录：
  - total tasks
  - scheduled
  - unscheduled
  - conflict count
  - final totalScore
  - 生成耗时

若结果漂移：

- 不直接改 baseline。
- 先输出候选排序变化和主要 delta 来源。
- 用户确认“新算法行为可接受”后再更新基线。

## 6. 风险点

### 数值风险

候选顺序改变后，最终方案很可能变化。即使离线公式没改，最终 `totalScore` 也会漂移。

### 性能风险

候选数约等于：

```text
tasks × slots × matchedRooms
```

如果每个候选都全量扫描 `generatedItems`，后期任务会变慢。

### 语义风险

`MORNING_THEORY_PRIORITY` 在线公式区分理论课，离线公式不区分课程类型。B1 若按离线 delta，会改变这个规则的业务含义。

### 日志风险

当前生成日志写的是单个候选分数。切成 delta 后，分数可能为负，需要文案解释为『边际罚分越低越好』或继续显示转换后的候选得分。

## 7. 不建议做的事

- 不建议一次性删除所有 `candidateXxx`。
- 不建议修改 `ScheduleScoreService.rescore` 主流程。
- 不建议同时改 DB、前端、评分解释页。
- 不建议把硬规则从过滤改成可排但扣分。
- 不建议在没有基线记录的情况下更新 expected 分数。

## 8. 推荐 commit 切分

1. `test(b1): 锁定 DeltaPenalty 等价性基线`
   - 新增 delta 单测 fixture。
   - 不接生成流程。
2. `refactor(b1): 抽 DeltaPenalty 候选评分器`
   - 新增 helper/service。
   - 仍不改变 `scoreCandidate`。
3. `refactor(b1): V3 候选评分切换为边际罚分`
   - 修改 `scoreCandidate`。
   - 保留稳定 tieBreaker。
4. `test(b1): 更新生成冒烟记录`
   - 仅在用户确认新行为后更新 baseline 文档或测试。

## 9. 当前建议

先做 Phase 0 + Phase 1。也就是：

1. 冻结现有测试基线。
2. 写 delta 计算单测。
3. 抽出 delta 计算，但不接入 V3 主流程。

这样即使后续发现 B1 方向不合适，也能保留一组有价值的评分等价性测试，回滚成本低。

