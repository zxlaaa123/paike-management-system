# D3 A.2 normalizeStrategyType 调查记录

## 结论

`normalizeStrategyType` 当前存在，不需要补代码。

位置：

- `backend/src/main/java/com/paike/scheduler/service/V3ScheduleGenerateService.java`

当前实现：

- `null` 或 blank strategy -> `COMPREHENSIVE`
- 非空 strategy -> `trim()` 后原样返回

因此，D3 清单里“函数搜不到”的判断是历史调查误判，不是当前代码缺失。

## 证据

当前代码路径：

- `V3ScheduleGenerateService.generate(...)`
  - 调用 `normalizeStrategyType(request.getStrategyType())`
  - 归一化结果继续传给 `resolvePlanName(...)`
  - 归一化结果继续传给 `referenceLoader.loadForV3Generate(...)`

- `V3ScheduleGenerateService.generateMultiple(...)`
  - 默认策略列表为 `TEACHER_PRIORITY`、`CLASS_BALANCE`、`CLASSROOM_UTILIZATION`、`COMPREHENSIVE`
  - 生成默认 plan name 时调用 `strategyLabel(normalizeStrategyType(strategyType))`
  - 单个策略仍会进入 `generate(...)` 再归一化一次

Git 历史：

- `25d20f7 feat(v3): add phase 5 schedule generation flow`
  - 首次引入 `normalizeStrategyType`
  - 同时引入 V3 生成流程
- `887ee07 refactor(d2): 登记评分维度文档 + ScoringDimensions 常量表`
  - D2 实施计划里写了“normalizeStrategyType 没看到代码（搜不到）”
- `2895873 docs(d3): D3 议题清单（D2 留尾 3 项 + GPT-5.5 review 2 个 angle）`
  - 将该疑点落到 D3 A.2

## 关于“未知 strategy 落到 COMPREHENSIVE”

需要区分两个层面：

1. `V3ScheduleGenerateService.normalizeStrategyType`
   - 只把空值归一到 `COMPREHENSIVE`
   - 不把未知非空 strategy 改成 `COMPREHENSIVE`

2. `ScheduleRuleWeightService.getDefaultRules`
   - `switch (strategyType)` 的 `default` 分支注释为 `COMPREHENSIVE`
   - 如果某个非空未知 strategy 没有现成权重，`SchedulingReferenceLoader.loadWeights(...)` 会触发 `initDefaultRules(...)`
   - `initDefaultRules(...)` 会用 `getDefaultRules(...)` 的 default 分支生成一组 COMPREHENSIVE 形状的规则，但 `strategyType` 字段仍保留传入的未知值

所以准确说法不是“normalizeStrategyType 把未知 strategy 映射成 COMPREHENSIVE”，而是：

> 空 strategy 在 V3 入口归一为 `COMPREHENSIVE`；未知非空 strategy 会在默认权重初始化时走 `getDefaultRules` 的 default 分支，得到 COMPREHENSIVE 形状的规则集。

## 处理建议

不改代码。

理由：

- 当前函数存在并被 V3 生成入口使用。
- 当前行为已有历史来源。
- 本次 A.2 是调查项，不是 bugfix。

后续若要收紧未知 strategy，需要另开决策项：

- 方案 A：继续允许未知 strategy，沿用 default 权重。
- 方案 B：在入口校验 strategy 枚举，未知值直接拒绝。
- 方案 C：把 default 权重初始化逻辑显式改名/注释，避免误读。

这会改变现有容错行为，不属于 A.2 调查范围。

