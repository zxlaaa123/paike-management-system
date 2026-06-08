# V6_04_API接口设计

## 一、接口设计原则

V6 接口只提供治理能力，不替代 V1-V5 业务接口。

当前状态校准（2026-06-07）：

1. 当前代码中已经存在 `/api/v6/audit-logs` 与 `/api/v6/audit-logs/{id}`。
2. 本文件接口均为目标契约，不代表已实现。
3. V5 已有修复任务、试算方案、一致性检查相关接口；V6 不应重复实现同一业务接口。
4. V6 第一阶段审计日志查询接口已落地；后续应补关键写路径审计覆盖和前端只读页面，其他接口按阶段推进。

原则：

1. 所有接口使用 `/api/v6` 前缀。
2. 所有危险操作需要登录和 CSRF 校验。
3. 所有检查类接口必须返回结构化结果。
4. 不提供直接修改正式课表的接口。
5. 不提供绕过 V3/V5 方案机制的接口。

## 二、回归测试接口

当前状态校准（2026-06-08）：

1. 不直接新增 `regression_test_run` / `regression_test_case_result`。
2. V6 第二阶段最小可交付版复用 V5 已有 `schedule_regression_test`。
3. 当前只提供只读查询，不提供一键运行回归测试。

### 2.1 查询回归测试记录

```http
GET /api/v6/regression-tests
```

查询参数：

| 参数 | 说明 |
|---|---|
| testStage | 测试阶段 |
| testSuite | 测试套件 |
| status | PASS / FAIL / BLOCKED / RUNNING |
| semesterId | 学期 ID |
| planId | 方案 ID |
| page | 页码 |
| size | 每页数量 |

### 2.2 查询回归测试详情

```http
GET /api/v6/regression-tests/{id}
```

## 三、数据一致性接口

当前状态校准（2026-06-08）：

1. V6 第三阶段最小可交付版复用 `schedule_consistency_check`。
2. 不直接新增 `data_consistency_check_run` / `data_consistency_issue`。
3. 触发检查复用 `V5ConsistencyCheckService`。

### 3.1 触发一致性检查

```http
POST /api/v6/consistency-checks/run?taskId=1&planId=2
```

### 3.2 查询一致性检查列表

```http
GET /api/v6/consistency-checks
```

查询参数：

| 参数 | 说明 |
|---|---|
| status | PASS / WARN / FAIL |
| checkType | 检查类型 |
| semesterId | 学期 ID |
| planId | 方案 ID |
| page | 页码 |
| size | 每页数量 |

### 3.3 查询一致性检查详情

```http
GET /api/v6/consistency-checks/{id}
```

详情响应包含持久化记录、报告和问题清单。

## 四、性能基线接口

### 4.1 查询性能基线

```http
GET /api/v6/performance/baselines
```

查询参数：

| 参数 | 说明 |
|---|---|
| operationType | 操作类型 |
| semesterId | 学期 ID |
| planId | 方案 ID |
| startTime | 开始时间 |
| endTime | 结束时间 |

### 4.2 查询性能摘要

```http
GET /api/v6/performance/summary
```

响应字段：

| 字段 | 说明 |
|---|---|
| operationType | 操作类型 |
| avgDurationMs | 平均耗时 |
| maxDurationMs | 最大耗时 |
| successRate | 成功率 |
| sampleCount | 样本数量 |

## 五、审计日志接口

### 5.1 查询审计日志

```http
GET /api/v6/audit-logs
```

查询参数：

| 参数 | 说明 |
|---|---|
| actionType | 操作类型 |
| semesterId | 学期 ID |
| planId | 方案 ID |
| operatorId | 操作人 ID |
| success | 是否成功 |

### 5.2 查询审计日志详情

```http
GET /api/v6/audit-logs/{id}
```

## 六、数据库迁移接口

### 6.1 查询迁移状态

```http
GET /api/v6/migrations/status
```

响应：

```json
{
  "enabled": true,
  "currentVersion": "V6_005",
  "failedCount": 0
}
```

### 6.2 查询迁移历史

```http
GET /api/v6/migrations/history
```

## 七、错误码接口

### 7.1 查询错误码列表

```http
GET /api/v6/error-codes
```

### 7.2 查询错误码详情

```http
GET /api/v6/error-codes/{code}
```

## 八、统一响应建议

成功响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

失败响应：

```json
{
  "code": 400,
  "errorCode": "PLAN_STATUS_INVALID",
  "message": "当前方案状态不允许应用",
  "data": null
}
```
