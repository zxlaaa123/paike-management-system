# V4_04_API接口设计.md

# V4 版本 API 接口设计

## 一、文档说明

本文档用于指导“排课系统仿真”V4 版本后端接口开发。

V4 版本定位为：

> 排课质量分析与智能辅助优化版。

V4 不重新设计 V3 的多方案自动排课流程，也不替代 V3 的方案生成、方案应用、方案回滚机制。

V4 的接口重点是：

1. 读取 V3 已生成的排课方案；
2. 对排课方案进行质量分析；
3. 对方案评分进行拆解解释；
4. 对冲突和风险进行诊断；
5. 为前端图表提供统计数据；
6. 支持局部调整前的冲突检测；
7. 支持课程锁定；
8. 支持基于锁定状态的局部重排；
9. 支持排课分析报告生成与导出；
10. 可选支持 AI 辅助生成文字分析建议。

---

## 二、接口设计原则

### 2.1 不破坏 V3 既有接口

V3 已有或规划接口包括：

```http
POST /api/v3/schedule-generate
POST /api/v3/schedule-generate/multiple
GET  /api/v3/schedule-plans/{id}
POST /api/v3/schedule-plans/compare
POST /api/v3/schedule-plans/{id}/apply
POST /api/v3/schedule-plans/{id}/rollback
```

V4 不重写这些接口。

V4 只在其基础上增加分析、诊断、可视化、报告、局部调整相关接口。

---

### 2.2 V4 分析接口默认只读

以下类型接口默认只读：

1. 排课质量分析；
2. 评分详情；
3. 风险诊断；
4. 图表统计；
5. 报告预览；
6. AI 分析建议。

这些接口不能修改：

1. `schedule` 正式课表；
2. `schedule_plan` 历史方案；
3. `schedule_plan_item` 历史方案明细。

---

### 2.3 修改类接口必须可追溯

V4 中涉及修改的接口包括：

1. 方案副本局部调整；
2. 正式课表局部调整；
3. 课程锁定；
4. 局部重排；
5. 报告生成记录。

这些接口必须：

1. 校验数据是否存在；
2. 校验学期是否一致；
3. 校验冲突；
4. 记录操作日志；
5. 不删除历史方案；
6. 不破坏 V3 应用和回滚机制。

---

## 三、统一返回格式建议

后端接口建议保持统一返回格式。

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

错误返回示例：

```json
{
  "code": 400,
  "message": "参数错误",
  "data": null
}
```

风险确认返回示例：

```json
{
  "code": 409,
  "message": "存在冲突风险，需要确认",
  "data": {
    "needConfirm": true,
    "riskCount": 2,
    "risks": []
  }
}
```

---

## 四、排课质量分析接口

### 4.1 获取方案质量分析总览

```http
GET /api/v4/schedule-analysis/plans/{planId}/summary
```

#### 用途

用于方案详情页、质量分析面板展示某个排课方案的总体质量。

#### 参数

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---:|---:|---|
| planId | path | Long | 是 | 排课方案 ID |

#### 返回字段

```json
{
  "planId": 12,
  "planName": "V3 自动排课方案 A",
  "termId": 1,
  "termName": "2025-2026 第一学期",
  "strategyCode": "BALANCED",
  "planStatus": "APPLIED",
  "isCurrent": true,

  "totalScore": 86.5,
  "scheduledCount": 120,
  "unscheduledCount": 3,
  "conflictCount": 1,

  "teacherCount": 26,
  "classCount": 12,
  "roomCount": 18,
  "courseCount": 40,

  "teacherAverageHours": 12.5,
  "teacherMaxHours": 20,
  "teacherMinHours": 4,

  "roomUtilizationRate": 72.3,
  "classAverageDailyLessons": 5.2,
  "highRiskCount": 2,
  "mediumRiskCount": 5,
  "lowRiskCount": 8,

  "createdAt": "2026-05-15 10:00:00",
  "appliedAt": "2026-05-15 10:30:00"
}
```

#### 注意事项

1. 本接口只读取方案数据；
2. 不允许修改正式课表；
3. 不允许重新生成方案；
4. 如果分析数据尚未生成，可以实时计算后返回；
5. 如果使用分析缓存表，可以读取缓存并支持刷新。

---

### 4.2 刷新方案质量分析

```http
POST /api/v4/schedule-analysis/plans/{planId}/refresh
```

#### 用途

重新计算某个方案的质量分析数据，并写入 V4 分析汇总表。

#### 请求体

```json
{
  "forceRefresh": true
}
```

#### 返回字段

```json
{
  "planId": 12,
  "refreshed": true,
  "message": "方案质量分析已刷新"
}
```

#### 注意事项

1. 本接口可以写入 V4 分析表；
2. 不允许修改 V3 方案明细；
3. 不允许修改正式课表；
4. 如果方案不存在，应返回错误；
5. 如果方案没有明细，应返回空分析结果或错误提示。

---

## 五、评分详情接口

### 5.1 获取方案评分明细

```http
GET /api/v4/schedule-analysis/plans/{planId}/score-details
```

#### 用途

把 V3 的 `total_score` 拆解成多个评分项，方便前端展示和答辩说明。

#### 返回示例

```json
{
  "planId": 12,
  "totalScore": 86.5,
  "scoreItems": [
    {
      "scoreKey": "HARD_CONFLICT",
      "scoreName": "硬性冲突评分",
      "scoreValue": 98,
      "maxScore": 100,
      "weight": 0.35,
      "description": "教师、班级、教室同一时间冲突越少，分数越高"
    },
    {
      "scoreKey": "TEACHER_BALANCE",
      "scoreName": "教师课时均衡评分",
      "scoreValue": 82,
      "maxScore": 100,
      "weight": 0.2,
      "description": "教师课时分布越均衡，分数越高"
    },
    {
      "scoreKey": "ROOM_UTILIZATION",
      "scoreName": "教室利用率评分",
      "scoreValue": 78,
      "maxScore": 100,
      "weight": 0.15,
      "description": "教室使用越合理，分数越高"
    },
    {
      "scoreKey": "CLASS_LOAD",
      "scoreName": "班级负载评分",
      "scoreValue": 84,
      "maxScore": 100,
      "weight": 0.15,
      "description": "班级每日课程安排越均衡，分数越高"
    },
    {
      "scoreKey": "TIME_DISTRIBUTION",
      "scoreName": "时间分布评分",
      "scoreValue": 90,
      "maxScore": 100,
      "weight": 0.15,
      "description": "课程分布越合理，分数越高"
    }
  ]
}
```

#### 注意事项

1. 评分项应尽量来自 V3 方案数据；
2. 不要求 V3 阶段已经保存所有评分项；
3. V4 可以通过统计重新计算评分明细；
4. 总分不一定必须完全等于 V3 旧分数，但需要说明计算来源；
5. 如果 V3 已有规则权重，应优先复用。

---

## 六、冲突风险诊断接口

### 6.1 获取方案风险列表

```http
GET /api/v4/schedule-risks/plans/{planId}
```

#### 查询参数

| 参数 | 类型 | 必填 | 说明 |
|---|---:|---:|---|
| riskType | String | 否 | 风险类型 |
| level | String | 否 | 风险等级 |
| onlyUnresolved | Boolean | 否 | 是否只看未处理风险 |

#### 风险类型建议

```text
TEACHER_CONFLICT       教师时间冲突
CLASS_CONFLICT         班级时间冲突
ROOM_CONFLICT          教室时间冲突
ROOM_CAPACITY          教室容量不足
ROOM_TYPE              教室类型不匹配
TEACHER_UNAVAILABLE    教师禁排时间冲突
UNSCHEDULED_TASK       教学任务未排
TEACHER_OVERLOAD       教师课时过高
CLASS_DAILY_OVERLOAD   班级单日课程过多
ROOM_LOW_UTILIZATION   教室利用率偏低
ROOM_HIGH_UTILIZATION  教室利用率偏高
```

#### 风险等级建议

```text
HIGH
MEDIUM
LOW
```

#### 返回示例

```json
{
  "planId": 12,
  "riskCount": 3,
  "risks": [
    {
      "id": 1,
      "riskType": "TEACHER_CONFLICT",
      "riskTypeName": "教师时间冲突",
      "level": "HIGH",
      "title": "张老师周一第 3-4 节存在时间冲突",
      "description": "张老师在同一时间被安排了两门课程",
      "relatedTeacherId": 5,
      "relatedTeacherName": "张老师",
      "relatedClassId": 2,
      "relatedClassName": "计算机 1 班",
      "relatedRoomId": 3,
      "relatedRoomName": "A101",
      "weekDay": 1,
      "period": "3-4",
      "suggestion": "建议调整其中一门课程到其他空闲时间段",
      "resolved": false
    }
  ]
}
```

#### 注意事项

1. 风险诊断可以实时计算，也可以从 V4 风险表读取；
2. 如果实时计算，应避免影响页面加载速度；
3. 风险接口不能自动修改课表；
4. 风险建议只作为提示，不直接执行修改。

---

### 6.2 刷新方案风险诊断

```http
POST /api/v4/schedule-risks/plans/{planId}/refresh
```

#### 用途

重新扫描某个方案的冲突和风险，并更新 V4 风险诊断表。

#### 返回示例

```json
{
  "planId": 12,
  "riskCount": 8,
  "message": "风险诊断已刷新"
}
```

#### 注意事项

1. 不允许修改正式课表；
2. 不允许删除 V3 历史方案；
3. 可以清理并重建 V4 风险诊断表中的旧风险记录；
4. 风险记录应绑定 `plan_id`。

---

## 七、图表统计接口

### 7.1 教师课时分布

```http
GET /api/v4/schedule-charts/plans/{planId}/teacher-hours
```

#### 返回示例

```json
{
  "planId": 12,
  "items": [
    {
      "teacherId": 1,
      "teacherName": "张老师",
      "totalHours": 16,
      "courseCount": 4
    },
    {
      "teacherId": 2,
      "teacherName": "李老师",
      "totalHours": 12,
      "courseCount": 3
    }
  ]
}
```

---

### 7.2 教室利用率排行

```http
GET /api/v4/schedule-charts/plans/{planId}/room-utilization
```

#### 返回示例

```json
{
  "planId": 12,
  "items": [
    {
      "roomId": 1,
      "roomName": "A101",
      "roomType": "普通教室",
      "capacity": 60,
      "usedPeriods": 30,
      "totalPeriods": 50,
      "utilizationRate": 60.0
    }
  ]
}
```

---

### 7.3 班级每日课程负载

```http
GET /api/v4/schedule-charts/plans/{planId}/class-daily-load
```

#### 返回示例

```json
{
  "planId": 12,
  "items": [
    {
      "classId": 1,
      "className": "计算机 1 班",
      "weekDay": 1,
      "lessonCount": 6
    },
    {
      "classId": 1,
      "className": "计算机 1 班",
      "weekDay": 2,
      "lessonCount": 4
    }
  ]
}
```

---

### 7.4 时间段课程密度热力图

```http
GET /api/v4/schedule-charts/plans/{planId}/time-density
```

#### 返回示例

```json
{
  "planId": 12,
  "items": [
    {
      "weekDay": 1,
      "period": 1,
      "courseCount": 10
    },
    {
      "weekDay": 1,
      "period": 2,
      "courseCount": 12
    }
  ]
}
```

---

### 7.5 方案评分雷达图

```http
GET /api/v4/schedule-charts/plans/{planId}/score-radar
```

#### 返回示例

```json
{
  "planId": 12,
  "items": [
    {
      "name": "硬性冲突",
      "value": 98
    },
    {
      "name": "教师均衡",
      "value": 82
    },
    {
      "name": "教室利用",
      "value": 78
    },
    {
      "name": "班级负载",
      "value": 84
    },
    {
      "name": "时间分布",
      "value": 90
    }
  ]
}
```

---

## 八、局部调整与冲突检测接口

V4 的局部调整需要特别注意数据安全。

建议优先支持两种模式：

1. 方案副本调整；
2. 正式课表调整。

为了避免破坏 V3 历史方案，推荐优先实现“方案副本调整”。

---

### 8.1 检查某节课调整是否冲突

```http
POST /api/v4/schedule-adjustments/check
```

#### 请求体

```json
{
  "targetType": "PLAN",
  "planId": 12,
  "planItemId": 1001,
  "scheduleId": null,
  "newWeekDay": 2,
  "newPeriodStart": 3,
  "newPeriodEnd": 4,
  "newRoomId": 5
}
```

#### targetType 说明

```text
PLAN      检查方案明细调整
SCHEDULE  检查正式课表调整
```

#### 返回示例

```json
{
  "canAdjust": false,
  "conflictCount": 2,
  "conflicts": [
    {
      "conflictType": "TEACHER_CONFLICT",
      "message": "张老师在该时间已有课程安排"
    },
    {
      "conflictType": "ROOM_CONFLICT",
      "message": "A101 教室在该时间已被占用"
    }
  ]
}
```

#### 注意事项

1. 本接口只检查，不保存；
2. 必须检查教师冲突；
3. 必须检查班级冲突；
4. 必须检查教室冲突；
5. 必须检查教师禁排；
6. 必须检查教室容量；
7. 必须检查教室类型；
8. targetType 为 PLAN 时检查同一方案内的冲突；
9. targetType 为 SCHEDULE 时检查正式课表内的冲突。

---

### 8.2 应用局部调整

```http
POST /api/v4/schedule-adjustments/apply
```

#### 请求体

```json
{
  "targetType": "PLAN",
  "planId": 12,
  "planItemId": 1001,
  "scheduleId": null,
  "newWeekDay": 2,
  "newPeriodStart": 3,
  "newPeriodEnd": 4,
  "newRoomId": 5,
  "forceAdjust": false,
  "remark": "人工调整课程时间"
}
```

#### 返回示例

```json
{
  "adjusted": true,
  "targetType": "PLAN",
  "planId": 12,
  "planItemId": 1001,
  "adjustmentLogId": 88,
  "message": "局部调整成功"
}
```

#### 强制确认逻辑

如果调整存在冲突，并且：

```json
{
  "forceAdjust": false
}
```

后端应返回：

```json
{
  "needConfirm": true,
  "message": "调整后存在冲突，是否继续保存？",
  "conflictCount": 2,
  "conflicts": []
}
```

前端二次确认后，再传：

```json
{
  "forceAdjust": true
}
```

#### 注意事项

1. 建议优先只允许调整未应用方案或方案副本；
2. 如果允许调整正式课表，必须记录调整日志；
3. 调整正式课表后，正式课表来源方案显示可以增加“已人工调整”标识；
4. 不允许直接修改已废弃方案；
5. 不允许修改生成失败方案；
6. 调整后应刷新分析数据和风险数据；
7. 不允许删除历史方案。

---

## 九、课程锁定接口

### 9.1 锁定课程安排

```http
POST /api/v4/schedule-locks/lock
```

#### 请求体

```json
{
  "targetType": "PLAN",
  "planId": 12,
  "planItemId": 1001,
  "scheduleId": null,
  "lockReason": "该课程时间已人工确认，不参与后续重排"
}
```

#### 返回示例

```json
{
  "locked": true,
  "lockId": 10,
  "message": "课程已锁定"
}
```

#### 注意事项

1. 支持锁定方案项；
2. 可选支持锁定正式课表项；
3. 锁定后局部重排不得修改该课程；
4. 锁定记录应可追溯；
5. 不要直接依赖前端状态判断锁定，后端必须校验。

---

### 9.2 取消课程锁定

```http
POST /api/v4/schedule-locks/unlock
```

#### 请求体

```json
{
  "targetType": "PLAN",
  "planId": 12,
  "planItemId": 1001,
  "scheduleId": null
}
```

#### 返回示例

```json
{
  "unlocked": true,
  "message": "课程已取消锁定"
}
```

---

### 9.3 查询方案锁定列表

```http
GET /api/v4/schedule-locks/plans/{planId}
```

#### 返回示例

```json
{
  "planId": 12,
  "lockedCount": 3,
  "items": [
    {
      "lockId": 10,
      "planItemId": 1001,
      "courseName": "高等数学",
      "teacherName": "张老师",
      "className": "计算机 1 班",
      "weekDay": 1,
      "period": "1-2",
      "roomName": "A101",
      "lockReason": "人工确认课程",
      "createdAt": "2026-05-15 10:20:00"
    }
  ]
}
```

---

## 十、局部重排接口

局部重排是 V4 的中高级功能，建议不要一开始就做复杂。

推荐实现方式：

> 基于某个已有方案，复制一个新方案，只重排未锁定课程，原方案不删除。

---

### 10.1 基于方案创建局部重排方案

```http
POST /api/v4/schedule-replan/plans/{planId}
```

#### 请求体

```json
{
  "newPlanName": "方案 A 的局部优化版",
  "keepLocked": true,
  "strategyCode": "LOCAL_REPLAN",
  "forceGenerate": false
}
```

#### 返回示例

```json
{
  "newPlanId": 20,
  "sourcePlanId": 12,
  "scheduledCount": 118,
  "unscheduledCount": 2,
  "conflictCount": 0,
  "totalScore": 89.2,
  "message": "局部重排方案已生成"
}
```

#### 注意事项

1. 不能覆盖原方案；
2. 不能删除原方案；
3. 锁定课程必须保留原时间、原教室；
4. 未锁定课程可以重新安排；
5. 生成结果写入新的 `schedule_plan` 和 `schedule_plan_item`；
6. 不允许直接写入正式课表；
7. 是否应用新方案仍然走 V3 的 apply 接口；
8. 不要引入复杂求解器；
9. 可以复用 V3 阶段 5 的贪心候选评分逻辑；
10. 如果 V3 算法不好复用，可以先实现最小版本：复制方案并标记局部重排来源。

---

## 十一、报告接口

### 11.1 生成方案分析报告

```http
POST /api/v4/schedule-reports/plans/{planId}/generate
```

#### 请求体

```json
{
  "reportType": "ANALYSIS",
  "format": "HTML",
  "includeCharts": true,
  "includeRisks": true,
  "includeSuggestions": true
}
```

#### reportType 建议

```text
ANALYSIS      方案质量分析报告
COMPARE       方案对比报告
RISK          冲突风险报告
TEACHER_LOAD  教师课时统计报告
ROOM_USAGE    教室使用率报告
```

#### format 建议

```text
HTML
EXCEL
PDF
```

V4 初期建议优先实现：

```text
HTML
EXCEL
```

PDF 可以作为后续扩展。

#### 返回示例

```json
{
  "reportId": 33,
  "planId": 12,
  "reportType": "ANALYSIS",
  "format": "HTML",
  "status": "GENERATED",
  "downloadUrl": "/api/v4/schedule-reports/33/download",
  "createdAt": "2026-05-15 11:00:00"
}
```

---

### 11.2 下载报告

```http
GET /api/v4/schedule-reports/{reportId}/download
```

#### 注意事项

1. 报告必须绑定方案；
2. 下载前校验报告是否存在；
3. 如果文件不存在，应返回明确错误；
4. 不要将导出文件放到前端目录；
5. 建议后端统一放到 `data/reports/` 或项目约定目录。

---

### 11.3 查询方案报告列表

```http
GET /api/v4/schedule-reports/plans/{planId}
```

#### 返回示例

```json
{
  "planId": 12,
  "items": [
    {
      "reportId": 33,
      "reportType": "ANALYSIS",
      "format": "HTML",
      "status": "GENERATED",
      "downloadUrl": "/api/v4/schedule-reports/33/download",
      "createdAt": "2026-05-15 11:00:00"
    }
  ]
}
```

---

## 十二、AI 辅助分析接口（可选）

AI 辅助分析只用于生成文字建议，不允许直接修改课表。

---

### 12.1 生成 AI 方案分析建议

```http
POST /api/v4/ai/schedule-analysis/plans/{planId}
```

#### 请求体

```json
{
  "analysisType": "SUMMARY",
  "includeRisks": true,
  "includeSuggestions": true
}
```

#### 返回示例

```json
{
  "planId": 12,
  "analysisText": "本方案整体完成率较高，教师课时分布较为均衡，但部分教室利用率偏低。建议优先检查低利用率教室是否存在类型限制或容量不匹配问题。",
  "suggestions": [
    "适当调整部分课程到利用率较低的教室",
    "关注张老师周一上午课程较集中的问题",
    "优先处理 3 个未排任务"
  ]
}
```

#### 注意事项

1. AI 只读取统计数据；
2. AI 不读取数据库敏感信息；
3. AI 不直接修改方案；
4. AI 不直接修改正式课表；
5. API Key 不允许写死；
6. 前端不直接调用 AI；
7. 所有 AI 调用必须经过后端；
8. AI 返回内容仅作为辅助建议。

---

## 十三、正式课表来源增强接口

V3 阶段 6 已要求正式课表记录来源方案。

V4 可以增加一个更适合前端展示的接口。

---

### 13.1 获取当前正式课表来源信息

```http
GET /api/v4/schedules/current/source
```

#### 查询参数

| 参数 | 类型 | 必填 | 说明 |
|---|---:|---:|---|
| termId | Long | 是 | 学期 ID |

#### 返回示例

```json
{
  "termId": 1,
  "termName": "2025-2026 第一学期",
  "sourcePlanId": 12,
  "sourcePlanName": "V3 自动排课方案 A",
  "strategyCode": "BALANCED",
  "totalScore": 86.5,
  "appliedAt": "2026-05-15 10:30:00",
  "hasManualAdjustments": true,
  "manualAdjustmentCount": 2
}
```

#### 注意事项

1. 本接口只读；
2. 不替代 V3 apply；
3. 如果没有来源方案，返回空来源信息；
4. 如果正式课表发生人工调整，应显示调整标识。

---

## 十四、操作日志接口

### 14.1 查询方案调整日志

```http
GET /api/v4/schedule-adjustments/plans/{planId}/logs
```

#### 返回示例

```json
{
  "planId": 12,
  "items": [
    {
      "id": 88,
      "targetType": "PLAN",
      "operationType": "ADJUST_TIME_ROOM",
      "courseName": "高等数学",
      "beforeWeekDay": 1,
      "beforePeriod": "1-2",
      "beforeRoomName": "A101",
      "afterWeekDay": 2,
      "afterPeriod": "3-4",
      "afterRoomName": "A102",
      "remark": "人工调整课程时间",
      "createdAt": "2026-05-15 10:40:00"
    }
  ]
}
```

#### 注意事项

1. 所有局部调整必须有日志；
2. 课程锁定和解锁也可以记录日志；
3. 日志只追加，不建议物理删除；
4. 日志用于后续追溯和答辩展示。

---

## 十五、接口安全与校验规则

### 15.1 所有接口通用校验

1. ID 不能为空；
2. 方案必须存在；
3. 学期必须存在；
4. 方案和学期必须匹配；
5. 方案明细必须属于对应方案；
6. 正式课表记录必须属于当前学期；
7. 已废弃方案不能被调整、锁定、重排；
8. 生成失败方案不能被调整、锁定、重排；
9. 所有写操作必须使用事务；
10. 前端校验不能替代后端校验。

---

### 15.2 跨学期限制

以下操作必须限制在同一学期内：

1. 方案分析；
2. 方案对比；
3. 局部调整；
4. 课程锁定；
5. 局部重排；
6. 报告生成；
7. AI 分析。

禁止将 A 学期的方案数据写入 B 学期正式课表。

---

### 15.3 写操作事务要求

以下接口必须使用事务：

```http
POST /api/v4/schedule-analysis/plans/{planId}/refresh
POST /api/v4/schedule-risks/plans/{planId}/refresh
POST /api/v4/schedule-adjustments/apply
POST /api/v4/schedule-locks/lock
POST /api/v4/schedule-locks/unlock
POST /api/v4/schedule-replan/plans/{planId}
POST /api/v4/schedule-reports/plans/{planId}/generate
```

事务要求：

1. 任意一步失败，整体回滚；
2. 不允许只修改一半；
3. 调整课表和记录日志必须在同一事务；
4. 局部重排生成新方案和新方案明细必须在同一事务。

---

## 十六、接口与前端页面对应关系

| 前端页面 | 主要接口 |
|---|---|
| 排课质量分析总览页 | `GET /api/v4/schedule-analysis/plans/{planId}/summary` |
| 评分详情页 | `GET /api/v4/schedule-analysis/plans/{planId}/score-details` |
| 风险诊断中心 | `GET /api/v4/schedule-risks/plans/{planId}` |
| 可视化图表页 | `/api/v4/schedule-charts/plans/{planId}/...` |
| 局部调整弹窗 | `POST /api/v4/schedule-adjustments/check` |
| 局部调整保存 | `POST /api/v4/schedule-adjustments/apply` |
| 课程锁定管理 | `/api/v4/schedule-locks/...` |
| 局部重排页面 | `POST /api/v4/schedule-replan/plans/{planId}` |
| 报告管理页 | `/api/v4/schedule-reports/...` |
| AI 分析页 | `POST /api/v4/ai/schedule-analysis/plans/{planId}` |
| 正式课表来源展示 | `GET /api/v4/schedules/current/source` |
| 调整日志页 | `GET /api/v4/schedule-adjustments/plans/{planId}/logs` |

---

## 十七、开发顺序建议

建议按以下顺序开发 V4 API：

```text
1. 方案质量分析 summary 接口
2. 评分详情 score-details 接口
3. 风险诊断 risks 接口
4. 图表统计 charts 接口
5. 正式课表来源 current/source 接口
6. 局部调整 check 接口
7. 局部调整 apply 接口
8. 课程锁定 lock/unlock 接口
9. 调整日志接口
10. 局部重排接口
11. 报告生成和下载接口
12. AI 辅助分析接口
```

不建议第一步就做局部重排。

---

## 十八、V4 API 开发禁止事项

请严格禁止：

1. 不要重写 V3 自动排课接口；
2. 不要重写 V3 应用方案接口；
3. 不要重写 V3 回滚方案接口；
4. 不要让分析接口修改正式课表；
5. 不要让图表接口修改数据库；
6. 不要让 AI 接口直接修改课表；
7. 不要删除历史方案；
8. 不要删除历史方案明细；
9. 不要覆盖原方案生成局部重排结果；
10. 不要跨学期读取和写入数据；
11. 不要只靠前端判断冲突；
12. 不要绕过事务执行写操作；
13. 不要引入复杂求解器作为 V4 主线；
14. 不要破坏旧课表页面接口；
15. 不要把导出文件放进前端源码目录。

---

## 十九、验收清单

完成 V4 API 后，需要验证：

### 19.1 分析类接口

- [ ] 能查询方案质量分析；
- [ ] 能查询评分详情；
- [ ] 能查询风险列表；
- [ ] 能刷新风险诊断；
- [ ] 能返回图表数据；
- [ ] 分析接口不会修改正式课表。

### 19.2 调整类接口

- [ ] 能检查局部调整是否冲突；
- [ ] 能提示教师冲突；
- [ ] 能提示班级冲突；
- [ ] 能提示教室冲突；
- [ ] 能保存无冲突调整；
- [ ] 有冲突时需要二次确认；
- [ ] 调整后有操作日志。

### 19.3 锁定类接口

- [ ] 能锁定课程；
- [ ] 能取消锁定；
- [ ] 能查询锁定列表；
- [ ] 局部重排不会修改锁定课程。

### 19.4 局部重排接口

- [ ] 能基于历史方案生成新方案；
- [ ] 原方案不会被删除；
- [ ] 原方案明细不会被覆盖；
- [ ] 新方案不会直接写入正式课表；
- [ ] 新方案仍然需要通过 V3 apply 才能成为正式课表。

### 19.5 报告接口

- [ ] 能生成分析报告；
- [ ] 能下载报告；
- [ ] 能查询报告列表；
- [ ] 报告文件路径合理；
- [ ] 报告不会影响排课数据。

### 19.6 AI 分析接口

- [ ] AI 只生成文字建议；
- [ ] AI 不直接修改课表；
- [ ] API Key 不写死；
- [ ] 前端不直接调用 AI；
- [ ] AI 失败时不影响基础功能。

---

## 二十、总结

V4 API 的核心不是重新做自动排课，而是围绕 V3 已有的排课方案，提供更完整的分析、解释、诊断、调整和报告能力。

本阶段接口设计应始终遵守：

```text
V3 负责生成、应用、回滚；
V4 负责分析、解释、辅助调整、报告展示；
V4 不破坏 V3 的历史方案机制；
V4 不让 AI 或分析接口直接修改正式课表。
```

