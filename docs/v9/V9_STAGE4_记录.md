# V9 Stage 4 记录：benchmark 大档收口

## 目标

补齐 V9 weekType benchmark 大档（300 任务）数据，验证 R2 门槛：

- 回溯成功率 >= 95%：混合数据集排下率 >= 95%
- 退火耗时增幅 <= 50%：同等 optimizeTimeBudgetMs 下，混合数据退火步数 >= 全 ALL 基线 / 1.5

## Benchmark 参数化

`V9WeekTypeBenchmarkTest` 支持按规模和数据集单独跑：

```powershell
mvn "-Dtest=V9WeekTypeBenchmarkTest" "-Dv9.benchmark=true" "-Dv9.benchmark.scale=large" "-Dv9.benchmark.dataset=all" "-Dsurefire.useFile=false" "-DforkCount=0" test
mvn "-Dtest=V9WeekTypeBenchmarkTest" "-Dv9.benchmark=true" "-Dv9.benchmark.scale=large" "-Dv9.benchmark.dataset=mixed" "-Dsurefire.useFile=false" "-DforkCount=0" test
```

可选参数：

| 参数 | 值 |
|---|---|
| `v9.benchmark.scale` | `small` / `medium` / `large` / `all` / `default`（默认 small+medium） |
| `v9.benchmark.dataset` | `all` / `mixed` / `both`（默认 both） |

大档资源量沿用 V8 benchmark：300 tasks / 80 teachers / 60 classes / 60 rooms。

## 大档实测

运行日期：2026-06-15

退火预算：3000ms

raw 输出：

- `reports/v9-week-type-benchmark-large-all.txt`
- `reports/v9-week-type-benchmark-large-mixed.txt`

| 规模 | 数据集 | 未排 | 排下率 | 引擎ms | 回溯ms | 退火步数 |
|---|---|---:|---:|---:|---:|---:|
| 大（300任务） | 全 ALL 基线 | 0 | 100.0% | 4351 | 1351 | 260 |
| 大（300任务） | 混合 weekType | 0 | 100.0% | 4299 | 1299 | 265 |

## R2 结论

| 门槛 | 实测 | 结论 |
|---|---:|---|
| 回溯成功率 >= 95% | 100.0% | PASS |
| 退火步数比 >= 0.67 | 265 / 260 = 1.02 | PASS |

大档补跑通过。V9 weekType 阶段 4 benchmark 收口完成。

## 验证

- `V9WeekTypeBenchmarkTest` large/all：1 passed，BUILD SUCCESS
- `V9WeekTypeBenchmarkTest` large/mixed：1 passed，BUILD SUCCESS
- `mvn "-DskipTests" test`：testCompile 通过，BUILD SUCCESS
