# V7 API 接口设计

## 一、排课方案列表字段补全

### 1.1 影响接口

```http
GET /api/v3/schedule-plans
```

### 1.2 响应字段补全

在现有响应结构中补齐：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| semesterName | string | 学期名称 |
| strategyName | string | 策略中文名 |

兼容要求：

1. 不删除现有字段。
2. 不改变分页结构。
3. 关系字段查不到时允许返回 `null` 或 `-`，但应优先返回可读值。

## 二、教师工作量统计补全

### 2.1 影响接口

```http
GET /api/v3/statistics/teacher-workload
```

### 2.2 响应字段补全

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| maxContinuousPeriods | number | 单日最大连续节次数 |

兼容要求：

1. 字段名不变。
2. 原先恒为 `0`，V7 后返回真实统计值。

## 三、首页统计增强

### 3.1 影响接口

```http
GET /api/v3/statistics/dashboard
```

### 3.2 建议新增字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| unscheduledTaskCount | number | 未排任务数 |
| appliedPlanName | string | 当前应用方案名称 |
| recentAuditFailureCount | number | 最近审计失败数 |
| latestConsistencyIssueCount | number | 最新一致性问题数 |
| slowPerformanceCount | number | 慢操作数量 |

## 四、性能基线趋势接口

### 4.1 查询性能趋势

```http
GET /api/v6/performance/trends
```

查询参数：

| 参数 | 说明 |
| --- | --- |
| operationType | 操作类型，可选 |
| limit | 最近记录数量，默认 20 |

响应建议：

```json
[
  {
    "operationType": "AUTO_SCHEDULE",
    "createdAt": "2026-06-08T10:00:00",
    "durationMs": 1234,
    "success": true,
    "slow": false
  }
]
```

## 五、E2E 测试辅助

不新增生产 API。测试层统一使用：

```http
POST /api/auth/login
```

并从响应 `Set-Cookie` 中读取 `paike_token` 与 `XSRF-TOKEN`。
