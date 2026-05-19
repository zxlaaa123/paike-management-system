# V5_04_API接口设计

> 版本：V5 版本  
> 文档类型：API 接口设计  
> 适用项目：排课系统仿真 / 高校排课管理系统  
> V5 定位：约束驱动的局部重排与智能修复优化版  
> 前置基础：V3 学期与方案管理、V4 质量分析与风险诊断、V5 数据库扩展设计  
> 重要原则：V5 接口是对 V3/V4 的增量扩展，不重写旧接口，不允许修复建议或 AI 直接修改正式课表。

---

## 一、文档说明

本文档用于指导排课系统 V5 版本后端 API 接口开发、前端页面联调和 AI 辅助开发。

V5 的 API 设计重点不是重新设计排课系统接口，而是在 V3、V4 已有接口基础上，增加以下能力：

1. 约束规则分层查询与管理；
2. 创建排课修复任务；
3. 根据风险诊断生成修复建议；
4. 生成可替换时间、教室和组合候选位置；
5. 基于建议生成局部修复试算方案；
6. 基于锁定课程进行局部重排试算；
7. 对比修复前后评分、风险、负载变化；
8. 应用或废弃修复试算方案；
9. 执行排课数据一致性检查；
10. 记录回归测试结果；
11. 可选支持 AI 修复解释。

V5 接口的核心思想是：

```text
V4 发现风险
  ↓
V5 创建修复任务
  ↓
生成修复建议和候选位置
  ↓
生成试算方案
  ↓
展示优化前后对比
  ↓
用户确认后再应用
```

---

## 二、V5 与 V3/V4 接口关系

### 2.1 V3 接口继续负责方案主流程

V3 已经负责以下主流程：

```http
GET  /api/v3/semesters/current
GET  /api/v3/schedule-plans
GET  /api/v3/schedule-plans/{id}
POST /api/v3/schedule-generate
POST /api/v3/schedule-generate/multiple
POST /api/v3/schedule-plans/compare
POST /api/v3/schedule-plans/{id}/apply
POST /api/v3/schedule-plans/{id}/rollback
```

V5 不重写这些接口。

V5 只在这些能力之上增加“修复任务、修复建议、试算方案、优化对比、数据检查”等接口。

### 2.2 V4 接口继续负责分析和诊断

V4 已经负责以下能力：

```http
GET  /api/v4/schedule-analysis/plans/{planId}/summary
GET  /api/v4/schedule-analysis/plans/{planId}/score-details
GET  /api/v4/schedule-risks/plans/{planId}
POST /api/v4/schedule-risks/plans/{planId}/refresh
POST /api/v4/schedule-adjustments/check
POST /api/v4/schedule-adjustments/apply
POST /api/v4/schedule-locks/lock
POST /api/v4/schedule-locks/unlock
POST /api/v4/schedule-replan/plans/{planId}
```

V5 不替代 V4，而是复用 V4 的风险诊断、评分分析、课程锁定和局部调整能力。

### 2.3 V5 新增接口分组

V5 建议统一使用接口前缀：

```http
/api/v5
```

V5 新增接口建议分为以下分组：

| 分组 | 路径前缀 | 作用 |
|---|---|---|
| 约束规则接口 | `/api/v5/constraints` | 查询硬约束、软约束、偏好约束、修复约束 |
| 修复任务接口 | `/api/v5/schedule-repair-tasks` | 创建、查询、取消修复任务 |
| 修复建议接口 | `/api/v5/schedule-repair-suggestions` | 生成和管理修复建议 |
| 候选位置接口 | `/api/v5/schedule-repair-candidates` | 查询、刷新、评估候选位置 |
| 试算方案接口 | `/api/v5/schedule-repair-trials` | 生成局部修复或局部重排试算方案 |
| 优化对比接口 | `/api/v5/schedule-repair-compare` | 对比修复前后结果 |
| 课程保护接口 | `/api/v5/schedule-protection` | 检查锁定课程和修复范围 |
| 数据检查接口 | `/api/v5/schedule-data-checks` | 数据一致性和系统健康检查 |
| 回归测试接口 | `/api/v5/regression-tests` | 保存和查询回归测试结果 |
| AI 修复解释接口 | `/api/v5/ai/schedule-repair` | 生成修复建议解释和答辩摘要 |

---

## 三、统一接口规范

### 3.1 统一返回格式

所有 V5 接口建议继续使用统一响应格式：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

失败响应：

```json
{
  "code": 400,
  "message": "请求参数错误",
  "data": null
}
```

业务冲突响应：

```json
{
  "code": 409,
  "message": "存在冲突，无法生成试算方案",
  "data": {
    "conflictCount": 2,
    "conflicts": []
  }
}
```

### 3.2 常见状态码建议

| code | 含义 |
|---:|---|
| 200 | 成功 |
| 400 | 参数错误 |
| 401 | 未登录或 Token 无效 |
| 403 | 权限不足 |
| 404 | 数据不存在 |
| 409 | 业务冲突，例如存在硬冲突、方案状态不允许操作 |
| 500 | 系统内部错误 |

如果项目暂时没有登录系统，可以先不实现 401 和 403，但接口文档仍建议保留规范。

### 3.3 分页参数

列表接口统一使用：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| pageNum | number | 否 | 1 | 当前页 |
| pageSize | number | 否 | 10 | 每页数量 |
| keyword | string | 否 | 空 | 搜索关键词 |

分页返回结构：

```json
{
  "records": [],
  "total": 100,
  "pageNum": 1,
  "pageSize": 10
}
```

### 3.4 学期参数

涉及排课业务的数据查询统一支持：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| semesterId | number | 否 | 指定学期 ID，不传则默认当前学期 |

规则：

```text
前端传 semesterId，则按指定学期处理。
前端不传 semesterId，则后端读取当前学期。
不允许跨学期生成修复任务、候选位置、试算方案和对比结果。
```

### 3.5 常用枚举

#### 3.5.1 sourceType 来源类型

```text
PLAN        基于排课方案
SCHEDULE    基于正式课表
RISK        基于风险项
```

#### 3.5.2 repairMode 修复模式

```text
SINGLE_ISSUE_REPAIR     单风险修复
SINGLE_ITEM_REPAIR      单课程修复
CLASS_LOCAL_REPLAN      班级局部重排
TEACHER_LOCAL_REPLAN    教师局部重排
ROOM_LOCAL_REPLAN       教室局部重排
TIME_RANGE_REPLAN       时间范围局部重排
MULTI_RISK_REPAIR       多风险批量修复
```

#### 3.5.3 repairScopeType 修复范围类型

```text
RISK_ONLY       仅修复指定风险
ITEM_ONLY       仅修复指定课表项
CLASS_SCOPE     班级范围
TEACHER_SCOPE   教师范围
ROOM_SCOPE      教室范围
TIME_SCOPE      时间范围
CUSTOM_SCOPE    自定义范围
```

#### 3.5.4 repairTaskStatus 修复任务状态

```text
CREATED             已创建
SUGGESTING          正在生成建议
SUGGESTED           已生成建议
TRIAL_GENERATING    正在生成试算方案
TRIAL_GENERATED     已生成试算方案
COMPARED            已生成对比结果
APPLIED             已应用
ABANDONED           已放弃
FAILED              失败
```

#### 3.5.5 suggestionType 修复建议类型

```text
CHANGE_ROOM          更换教室
CHANGE_TIME          更换时间
CHANGE_TIME_ROOM     更换时间和教室
SWAP_ITEM            与其他课程交换
LOCAL_REPLAN         局部重排
IGNORE_RISK          忽略风险
MANUAL_HANDLE        建议人工处理
```

#### 3.5.6 recommendationLevel 推荐等级

```text
HIGHLY_RECOMMENDED   强烈推荐
RECOMMENDED          推荐
NORMAL               一般
NOT_RECOMMENDED      不推荐
```

---

## 四、约束规则接口

约束规则接口用于支撑 V5 的规则分层管理。

V5 建议将规则分为：

```text
硬约束
软约束
偏好约束
修复约束
```

---

### 4.1 查询约束规则列表

```http
GET /api/v5/constraints/rules
```

#### 请求参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| semesterId | number | 否 | 学期 ID |
| category | string | 否 | HARD / SOFT / PREFERENCE / REPAIR |
| enabled | boolean | 否 | 是否启用 |
| keyword | string | 否 | 规则名称或编码 |
| pageNum | number | 否 | 页码 |
| pageSize | number | 否 | 每页数量 |

#### 响应示例

```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "records": [
      {
        "id": 1,
        "ruleCode": "TEACHER_TIME_CONFLICT",
        "ruleName": "教师时间冲突",
        "constraintCategory": "HARD",
        "severityLevel": "HIGH",
        "weight": 100,
        "enabled": true,
        "description": "同一教师同一时间不能安排多门课程"
      },
      {
        "id": 2,
        "ruleCode": "LOCKED_ITEM_PROTECT",
        "ruleName": "锁定课程保护",
        "constraintCategory": "REPAIR",
        "severityLevel": "HIGH",
        "weight": 100,
        "enabled": true,
        "description": "局部重排时不得移动锁定课程"
      }
    ],
    "total": 2,
    "pageNum": 1,
    "pageSize": 10
  }
}
```

---

### 4.2 初始化 V5 默认约束规则

```http
POST /api/v5/constraints/rules/init-default
```

#### 请求体

```json
{
  "semesterId": 1,
  "overrideExisting": false
}
```

#### 业务规则

1. 如果当前学期已存在规则，默认不覆盖；
2. `overrideExisting=true` 时可以覆盖默认规则，但不能删除历史方案；
3. 硬约束和修复约束默认启用；
4. 该接口只初始化规则，不生成方案，不修改课表。

---

### 4.3 修改约束规则

```http
PUT /api/v5/constraints/rules/{id}
```

#### 请求体

```json
{
  "ruleName": "教师时间冲突",
  "weight": 100,
  "enabled": true,
  "description": "同一教师同一时间不能安排多门课程"
}
```

#### 注意事项

1. 不建议关闭硬约束；
2. 不允许关闭锁定课程保护规则；
3. 修改规则不会自动改变历史方案评分；
4. 如果需要使用新规则重新分析，必须主动调用分析或修复接口。

---

### 4.4 检查某个位置是否满足约束

```http
POST /api/v5/constraints/check-position
```

#### 请求体

```json
{
  "semesterId": 1,
  "sourceType": "PLAN",
  "sourcePlanId": 12,
  "sourceItemId": 3001,
  "teachingTaskId": 101,
  "weekday": 3,
  "startPeriod": 5,
  "endPeriod": 6,
  "classroomId": 8,
  "teacherId": 5,
  "ignoreItemIds": [3001]
}
```

#### 响应示例

```json
{
  "code": 200,
  "message": "检查完成",
  "data": {
    "available": true,
    "hardConflictCount": 0,
    "softViolationCount": 1,
    "preferenceScore": 86.5,
    "violations": [
      {
        "ruleCode": "CLASS_DAILY_LOAD",
        "ruleName": "班级日负载偏高",
        "constraintCategory": "SOFT",
        "level": "MEDIUM",
        "message": "该班级当天课程较多，但不影响合法性"
      }
    ]
  }
}
```

#### 用途

该接口可供以下功能复用：

1. 修复候选位置生成；
2. 单条课表调整前检查；
3. 局部重排过程中的候选筛选；
4. 前端手动选择时间和教室时实时预检。

---

## 五、修复任务接口

修复任务表示一次局部修复、局部重排或试算操作。

---

### 5.1 创建修复任务

```http
POST /api/v5/schedule-repair-tasks
```

#### 请求体

```json
{
  "semesterId": 1,
  "sourceType": "PLAN",
  "sourcePlanId": 12,
  "sourceScheduleId": null,
  "sourceRiskId": 88,
  "taskName": "修复方案A中的教师时间冲突",
  "repairMode": "SINGLE_ISSUE_REPAIR",
  "repairScopeType": "RISK_ONLY",
  "description": "基于风险诊断结果生成修复建议"
}
```

#### 响应示例

```json
{
  "code": 200,
  "message": "修复任务创建成功",
  "data": {
    "repairTaskId": 1001,
    "status": "CREATED"
  }
}
```

#### 校验规则

1. `semesterId` 必须存在；
2. `sourceType=PLAN` 时 `sourcePlanId` 必填；
3. `sourceType=SCHEDULE` 时 `sourceScheduleId` 必填；
4. `sourceRiskId` 如果填写，必须属于同一学期；
5. 已废弃方案不能直接创建修复任务，除非仅用于查看或复制；
6. 修复任务创建不修改正式课表。

---

### 5.2 查询修复任务列表

```http
GET /api/v5/schedule-repair-tasks
```

#### 请求参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| semesterId | number | 否 | 学期 ID |
| sourcePlanId | number | 否 | 来源方案 ID |
| status | string | 否 | 任务状态 |
| repairMode | string | 否 | 修复模式 |
| keyword | string | 否 | 任务名称关键词 |
| pageNum | number | 否 | 页码 |
| pageSize | number | 否 | 每页数量 |

#### 响应示例

```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "records": [
      {
        "id": 1001,
        "semesterId": 1,
        "sourceType": "PLAN",
        "sourcePlanId": 12,
        "sourcePlanName": "综合最优方案",
        "sourceRiskId": 88,
        "taskName": "修复方案A中的教师时间冲突",
        "repairMode": "SINGLE_ISSUE_REPAIR",
        "repairScopeType": "RISK_ONLY",
        "status": "SUGGESTED",
        "beforeScore": 82.5,
        "afterScore": null,
        "generatedPlanId": null,
        "lockedItemCount": 3,
        "affectedItemCount": 1,
        "movedItemCount": 0,
        "createdAt": "2026-05-18 14:30:00"
      }
    ],
    "total": 1,
    "pageNum": 1,
    "pageSize": 10
  }
}
```

---

### 5.3 查询修复任务详情

```http
GET /api/v5/schedule-repair-tasks/{taskId}
```

#### 响应示例

```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "id": 1001,
    "semesterId": 1,
    "sourceType": "PLAN",
    "sourcePlanId": 12,
    "sourceScheduleId": null,
    "sourceRiskId": 88,
    "taskName": "修复方案A中的教师时间冲突",
    "repairMode": "SINGLE_ISSUE_REPAIR",
    "repairScopeType": "RISK_ONLY",
    "status": "SUGGESTED",
    "beforeScore": 82.5,
    "afterScore": null,
    "generatedPlanId": null,
    "description": "基于风险诊断结果生成修复建议",
    "suggestionCount": 3,
    "candidateCount": 15,
    "compareResultId": null,
    "createdAt": "2026-05-18 14:30:00",
    "updatedAt": "2026-05-18 14:35:00"
  }
}
```

---

### 5.4 取消或放弃修复任务

```http
POST /api/v5/schedule-repair-tasks/{taskId}/abandon
```

#### 请求体

```json
{
  "reason": "用户决定暂不处理该风险"
}
```

#### 业务规则

1. 已应用任务不能放弃；
2. 已生成试算方案的任务可以放弃，但不删除试算方案；
3. 放弃任务不修改正式课表；
4. 任务状态更新为 `ABANDONED`。

---

## 六、修复建议接口

修复建议用于告诉用户“这个问题可以怎么改”。

---

### 6.1 根据修复任务生成建议

```http
POST /api/v5/schedule-repair-tasks/{taskId}/generate-suggestions
```

#### 请求体

```json
{
  "maxSuggestionCount": 5,
  "includeChangeRoom": true,
  "includeChangeTime": true,
  "includeChangeTimeRoom": true,
  "includeLocalReplan": false,
  "respectLockedItems": true
}
```

#### 响应示例

```json
{
  "code": 200,
  "message": "修复建议生成成功",
  "data": {
    "repairTaskId": 1001,
    "status": "SUGGESTED",
    "suggestionCount": 3,
    "suggestions": [
      {
        "id": 501,
        "suggestionType": "CHANGE_ROOM",
        "suggestionTitle": "保持时间不变，更换为 B203 教室",
        "suggestionDetail": "原教室存在占用冲突，B203 同时间空闲且容量满足要求。",
        "targetWeekday": 3,
        "targetStartPeriod": 5,
        "targetEndPeriod": 6,
        "targetClassroomId": 8,
        "targetClassroomName": "B203",
        "estimatedBeforeScore": 82.5,
        "estimatedAfterScore": 86.0,
        "estimatedScoreDelta": 3.5,
        "newConflictCount": 0,
        "resolvedRiskCount": 1,
        "recommendationLevel": "HIGHLY_RECOMMENDED"
      }
    ]
  }
}
```

#### 业务规则

1. 生成建议前必须重新读取当前风险和锁定状态；
2. 所有建议必须经过后端硬约束校验；
3. 不允许生成移动锁定课程的建议；
4. 建议只保存到修复建议表，不修改方案明细和正式课表；
5. 如果没有可行建议，应返回原因列表。

---

### 6.2 查询任务下的修复建议

```http
GET /api/v5/schedule-repair-tasks/{taskId}/suggestions
```

#### 请求参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| suggestionType | string | 否 | 建议类型 |
| recommendationLevel | string | 否 | 推荐等级 |
| onlyAvailable | boolean | 否 | 是否只看可用建议 |

#### 响应示例

```json
{
  "code": 200,
  "message": "查询成功",
  "data": [
    {
      "id": 501,
      "repairTaskId": 1001,
      "suggestionType": "CHANGE_ROOM",
      "suggestionTitle": "保持时间不变，更换为 B203 教室",
      "estimatedScoreDelta": 3.5,
      "newConflictCount": 0,
      "resolvedRiskCount": 1,
      "recommendationLevel": "HIGHLY_RECOMMENDED",
      "selectedFlag": false
    }
  ]
}
```

---

### 6.3 查询修复建议详情

```http
GET /api/v5/schedule-repair-suggestions/{suggestionId}
```

#### 响应示例

```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "id": 501,
    "repairTaskId": 1001,
    "riskId": 88,
    "sourceItemId": 3001,
    "suggestionType": "CHANGE_ROOM",
    "suggestionTitle": "保持时间不变，更换为 B203 教室",
    "suggestionDetail": "原教室存在占用冲突，B203 同时间空闲且容量满足要求。",
    "targetWeekday": 3,
    "targetStartPeriod": 5,
    "targetEndPeriod": 6,
    "targetClassroomId": 8,
    "targetClassroomName": "B203",
    "estimatedBeforeScore": 82.5,
    "estimatedAfterScore": 86.0,
    "estimatedScoreDelta": 3.5,
    "newConflictCount": 0,
    "resolvedRiskCount": 1,
    "conflictResult": {
      "hardConflictCount": 0,
      "softViolationCount": 1,
      "messages": []
    }
  }
}
```

---

### 6.4 选择修复建议

```http
POST /api/v5/schedule-repair-suggestions/{suggestionId}/select
```

#### 请求体

```json
{
  "selected": true
}
```

#### 业务规则

1. 同一个修复任务可以只允许一个建议被选中；
2. 选择建议不等于应用建议；
3. 选择建议后，后续可基于该建议生成试算方案；
4. 如果建议已经失效，应提示用户刷新建议。

---

### 6.5 忽略修复建议

```http
POST /api/v5/schedule-repair-suggestions/{suggestionId}/ignore
```

#### 请求体

```json
{
  "reason": "用户决定手动处理"
}
```

说明：该接口只改变建议状态或备注，不修改课表。

---

## 七、修复候选位置接口

候选位置用于支撑“怎么改”的底层计算。

---

### 7.1 为修复任务生成候选位置

```http
POST /api/v5/schedule-repair-tasks/{taskId}/generate-candidates
```

#### 请求体

```json
{
  "maxCandidateCount": 20,
  "onlyAvailable": true,
  "preferSameClassroomType": true,
  "preferSameTime": false,
  "respectLockedItems": true
}
```

#### 响应示例

```json
{
  "code": 200,
  "message": "候选位置生成成功",
  "data": {
    "repairTaskId": 1001,
    "candidateCount": 12,
    "availableCount": 8,
    "topCandidates": [
      {
        "id": 9001,
        "weekday": 4,
        "startPeriod": 3,
        "endPeriod": 4,
        "classroomId": 8,
        "classroomName": "B203",
        "hardConflictCount": 0,
        "softViolationCount": 1,
        "preferenceScore": 86.5,
        "candidateScore": 92.0,
        "rankNo": 1,
        "available": true
      }
    ]
  }
}
```

---

### 7.2 查询任务候选位置列表

```http
GET /api/v5/schedule-repair-tasks/{taskId}/candidates
```

#### 请求参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| available | boolean | 否 | 是否只看可用候选 |
| teachingTaskId | number | 否 | 教学任务 ID |
| weekday | number | 否 | 星期几 |
| classroomId | number | 否 | 教室 ID |
| pageNum | number | 否 | 页码 |
| pageSize | number | 否 | 每页数量 |

#### 响应示例

```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "records": [
      {
        "id": 9001,
        "teachingTaskId": 101,
        "courseName": "高等数学",
        "teacherName": "张老师",
        "className": "软件工程1班",
        "candidateWeekday": 4,
        "candidateStartPeriod": 3,
        "candidateEndPeriod": 4,
        "candidateClassroomId": 8,
        "candidateClassroomName": "B203",
        "hardConflictCount": 0,
        "softViolationCount": 1,
        "candidateScore": 92.0,
        "rankNo": 1,
        "availableFlag": true,
        "unavailableReason": null
      }
    ],
    "total": 12,
    "pageNum": 1,
    "pageSize": 10
  }
}
```

---

### 7.3 重新评估单个候选位置

```http
POST /api/v5/schedule-repair-candidates/{candidateId}/evaluate
```

#### 请求体

```json
{
  "refreshConstraintCheck": true,
  "refreshScore": true
}
```

#### 响应示例

```json
{
  "code": 200,
  "message": "候选位置评估完成",
  "data": {
    "candidateId": 9001,
    "available": true,
    "hardConflictCount": 0,
    "softViolationCount": 1,
    "candidateScore": 92.0,
    "checkDetail": {
      "hardRules": [],
      "softRules": [
        {
          "ruleCode": "CLASS_DAILY_LOAD",
          "message": "该班级当天课程略多"
        }
      ]
    }
  }
}
```

---

### 7.4 基于候选位置生成修复建议

```http
POST /api/v5/schedule-repair-candidates/{candidateId}/to-suggestion
```

#### 请求体

```json
{
  "suggestionTitle": "调整到周四第3-4节 B203 教室",
  "recommendationLevel": "RECOMMENDED"
}
```

说明：该接口用于前端从候选位置中手动选择一个候选，并生成对应修复建议。

---

## 八、修复范围与课程保护接口

V5 局部重排必须明确修复范围和锁定保护。

---

### 8.1 保存修复范围

```http
POST /api/v5/schedule-repair-tasks/{taskId}/scope
```

#### 请求体

```json
{
  "items": [
    {
      "scopeType": "INCLUDE",
      "itemType": "PLAN_ITEM",
      "itemId": 3001,
      "reason": "需要参与本次修复"
    },
    {
      "scopeType": "LOCKED",
      "itemType": "PLAN_ITEM",
      "itemId": 3002,
      "reason": "用户已确认，不允许移动"
    },
    {
      "scopeType": "EXCLUDE",
      "itemType": "PLAN_ITEM",
      "itemId": 3003,
      "reason": "不参与本次局部重排"
    }
  ]
}
```

#### scopeType 建议

```text
INCLUDE     本次修复允许调整
EXCLUDE     本次修复排除
LOCKED      本次修复锁定
AFFECTED    本次修复可能影响
```

---

### 8.2 查询修复范围

```http
GET /api/v5/schedule-repair-tasks/{taskId}/scope
```

#### 响应示例

```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "repairTaskId": 1001,
    "includeItems": [],
    "excludeItems": [],
    "lockedItems": [],
    "affectedItems": []
  }
}
```

---

### 8.3 检查课程锁定影响

```http
POST /api/v5/schedule-protection/check-locked-impact
```

#### 请求体

```json
{
  "semesterId": 1,
  "sourcePlanId": 12,
  "repairMode": "CLASS_LOCAL_REPLAN",
  "targetClassId": 5,
  "targetWeekdays": [1, 2, 3],
  "respectLockedItems": true
}
```

#### 响应示例

```json
{
  "code": 200,
  "message": "检查完成",
  "data": {
    "lockedItemCount": 6,
    "movableItemCount": 18,
    "blockedRiskCount": 1,
    "canReplan": true,
    "warnings": [
      "存在 6 条锁定课程，局部重排时将保持不变"
    ]
  }
}
```

#### 说明

该接口只检查锁定影响，不生成试算方案。

---

## 九、试算方案接口

试算方案是 V5 的核心安全机制。

V5 的修复结果必须先生成 `isTrial=true` 的排课方案，不能直接改正式课表。

---

### 9.1 基于修复建议生成试算方案

```http
POST /api/v5/schedule-repair-trials/from-suggestion
```

#### 请求体

```json
{
  "repairTaskId": 1001,
  "suggestionId": 501,
  "trialPlanName": "教师冲突修复试算方案",
  "copyUnaffectedItems": true,
  "recalculateScore": true,
  "refreshRiskDiagnosis": true
}
```

#### 响应示例

```json
{
  "code": 200,
  "message": "试算方案生成成功",
  "data": {
    "repairTaskId": 1001,
    "trialPlanId": 1201,
    "trialPlanName": "教师冲突修复试算方案",
    "isTrial": true,
    "planCategory": "LOCAL_REPAIR_TRIAL",
    "beforeScore": 82.5,
    "afterScore": 86.0,
    "improvementScore": 3.5,
    "movedItemCount": 1,
    "affectedItemCount": 1,
    "lockedItemCount": 6,
    "status": "TRIAL_GENERATED"
  }
}
```

#### 业务规则

1. 必须校验修复任务和建议存在；
2. 必须校验建议仍然可用；
3. 必须读取最新锁定状态；
4. 必须复制未受影响的方案明细；
5. 必须标记被移动的课程；
6. 必须重新计算评分和风险；
7. 生成试算方案不能修改正式课表；
8. 生成过程必须使用事务。

---

### 9.2 基于候选位置生成试算方案

```http
POST /api/v5/schedule-repair-trials/from-candidate
```

#### 请求体

```json
{
  "repairTaskId": 1001,
  "candidateId": 9001,
  "trialPlanName": "候选位置修复试算方案",
  "copyUnaffectedItems": true,
  "recalculateScore": true
}
```

说明：适用于用户直接从候选位置列表中选择某个候选进行试算。

---

### 9.3 创建局部重排试算方案

```http
POST /api/v5/schedule-repair-trials/local-replan
```

#### 请求体

```json
{
  "repairTaskId": 1002,
  "semesterId": 1,
  "sourcePlanId": 12,
  "trialPlanName": "软件工程1班局部重排试算方案",
  "repairMode": "CLASS_LOCAL_REPLAN",
  "repairScopeType": "CLASS_SCOPE",
  "targetClassId": 5,
  "targetTeacherId": null,
  "targetRoomId": null,
  "targetWeekdays": [1, 2, 3, 4, 5],
  "targetStartPeriod": null,
  "targetEndPeriod": null,
  "respectLockedItems": true,
  "maxMoveCount": 10,
  "recalculateScore": true,
  "refreshRiskDiagnosis": true
}
```

#### 响应示例

```json
{
  "code": 200,
  "message": "局部重排试算方案生成成功",
  "data": {
    "repairTaskId": 1002,
    "trialPlanId": 1202,
    "planCategory": "LOCAL_REPLAN_TRIAL",
    "beforeScore": 79.0,
    "afterScore": 87.5,
    "improvementScore": 8.5,
    "movedItemCount": 6,
    "affectedItemCount": 12,
    "lockedItemCount": 8,
    "resolvedRiskCount": 4,
    "newConflictCount": 0,
    "summary": "局部重排后解决 4 个风险项，方案评分提升 8.5 分。"
  }
}
```

#### 业务规则

1. 局部重排只能处理当前学期数据；
2. 不能移动锁定课程；
3. 不能直接覆盖来源方案；
4. 不能直接修改正式课表；
5. 必须生成新的 `schedule_plan` 和 `schedule_plan_item`；
6. 必须保留来源方案 ID；
7. 必须记录移动数量、影响数量和锁定数量；
8. 如果局部重排失败，必须返回失败原因。

---

### 9.4 查询试算方案结果

```http
GET /api/v5/schedule-repair-trials/{trialPlanId}
```

#### 响应示例

```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "trialPlanId": 1201,
    "trialPlanName": "教师冲突修复试算方案",
    "semesterId": 1,
    "sourcePlanId": 12,
    "repairTaskId": 1001,
    "isTrial": true,
    "planCategory": "LOCAL_REPAIR_TRIAL",
    "status": "DRAFT",
    "beforeScore": 82.5,
    "afterScore": 86.0,
    "improvementScore": 3.5,
    "movedItemCount": 1,
    "affectedItemCount": 1,
    "lockedItemCount": 6,
    "repairSummary": "更换教室后解决教师时间冲突风险",
    "createdAt": "2026-05-18 15:10:00"
  }
}
```

---

### 9.5 查询试算方案变更明细

```http
GET /api/v5/schedule-repair-trials/{trialPlanId}/changes
```

#### 请求参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| onlyMoved | boolean | 否 | 是否只看被移动课程 |
| moveType | string | 否 | 移动类型 |
| pageNum | number | 否 | 页码 |
| pageSize | number | 否 | 每页数量 |

#### 响应示例

```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "records": [
      {
        "trialItemId": 6001,
        "sourceItemId": 3001,
        "courseName": "高等数学",
        "teacherName": "张老师",
        "className": "软件工程1班",
        "oldWeekday": 3,
        "oldStartPeriod": 5,
        "oldEndPeriod": 6,
        "oldClassroomName": "A101",
        "newWeekday": 3,
        "newStartPeriod": 5,
        "newEndPeriod": 6,
        "newClassroomName": "B203",
        "isMoved": true,
        "moveType": "CHANGE_ROOM",
        "isLocked": false,
        "repairReason": "原教室存在冲突，B203 可用"
      }
    ],
    "total": 1,
    "pageNum": 1,
    "pageSize": 10
  }
}
```

---

### 9.6 废弃试算方案

```http
POST /api/v5/schedule-repair-trials/{trialPlanId}/abandon
```

#### 请求体

```json
{
  "reason": "用户选择不采用该试算方案"
}
```

#### 业务规则

1. 废弃试算方案不能删除历史记录；
2. 试算方案废弃后不能直接应用；
3. 废弃操作不影响来源方案和正式课表；
4. 对应修复任务可更新为 `ABANDONED` 或保持历史状态。

---

### 9.7 应用试算方案

```http
POST /api/v5/schedule-repair-trials/{trialPlanId}/apply
```

#### 请求体

```json
{
  "confirm": true,
  "applyMode": "AS_OFFICIAL_SCHEDULE",
  "remark": "采用局部修复试算方案作为正式课表"
}
```

#### 响应示例

```json
{
  "code": 200,
  "message": "试算方案已应用为正式课表",
  "data": {
    "trialPlanId": 1201,
    "applied": true,
    "officialScheduleUpdated": true,
    "repairTaskStatus": "APPLIED"
  }
}
```

#### 重要说明

该接口本质上应复用 V3 的应用方案逻辑。

应用前必须检查：

1. 试算方案存在；
2. 试算方案未废弃；
3. 试算方案属于当前学期；
4. 试算方案没有硬冲突；
5. 用户明确确认；
6. 应用过程使用事务；
7. 应用失败时回滚。

如果项目已经有稳定的 V3 应用接口，也可以让前端直接调用：

```http
POST /api/v3/schedule-plans/{trialPlanId}/apply
```

但建议 V5 保留包装接口，方便更新修复任务状态和对比记录。

---

## 十、优化前后对比接口

优化对比用于回答：

> 修复以后到底变好了没有？

---

### 10.1 生成修复前后对比

```http
POST /api/v5/schedule-repair-compare/generate
```

#### 请求体

```json
{
  "repairTaskId": 1001,
  "sourcePlanId": 12,
  "trialPlanId": 1201,
  "compareScore": true,
  "compareRisks": true,
  "compareTeacherLoad": true,
  "compareRoomUsage": true,
  "compareClassLoad": true
}
```

#### 响应示例

```json
{
  "code": 200,
  "message": "对比结果生成成功",
  "data": {
    "compareId": 7001,
    "repairTaskId": 1001,
    "sourcePlanId": 12,
    "trialPlanId": 1201,
    "beforeScore": 82.5,
    "afterScore": 86.0,
    "scoreDelta": 3.5,
    "beforeConflictCount": 2,
    "afterConflictCount": 0,
    "conflictDelta": -2,
    "beforeRiskCount": 8,
    "afterRiskCount": 4,
    "riskDelta": -4,
    "movedItemCount": 1,
    "affectedItemCount": 1,
    "improvementLevel": "明显优化",
    "summary": "修复后硬冲突减少 2 个，总评分提升 3.5 分。"
  }
}
```

---

### 10.2 查询任务对比结果

```http
GET /api/v5/schedule-repair-tasks/{taskId}/compare
```

#### 响应示例

```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "compareId": 7001,
    "repairTaskId": 1001,
    "sourcePlanId": 12,
    "trialPlanId": 1201,
    "beforeScore": 82.5,
    "afterScore": 86.0,
    "scoreDelta": 3.5,
    "beforeConflictCount": 2,
    "afterConflictCount": 0,
    "beforeRiskCount": 8,
    "afterRiskCount": 4,
    "movedItemCount": 1,
    "affectedItemCount": 1,
    "improvementLevel": "明显优化",
    "summary": "修复后冲突减少，评分提升，建议采用。"
  }
}
```

---

### 10.3 查询对比明细指标

```http
GET /api/v5/schedule-repair-compare/{compareId}/details
```

#### 响应示例

```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "scoreCompare": {
      "before": 82.5,
      "after": 86.0,
      "delta": 3.5
    },
    "riskCompare": {
      "beforeHighRiskCount": 2,
      "afterHighRiskCount": 0,
      "beforeMediumRiskCount": 4,
      "afterMediumRiskCount": 2
    },
    "teacherLoadCompare": [],
    "roomUsageCompare": [],
    "classLoadCompare": []
  }
}
```

---

## 十一、数据一致性检查接口

V5 建议加入数据一致性检查，用来发现多版本迭代后遗留的数据问题。

---

### 11.1 执行数据一致性检查

```http
POST /api/v5/schedule-data-checks/run
```

#### 请求体

```json
{
  "semesterId": 1,
  "checkTypes": [
    "SEMESTER_BOUNDARY",
    "PLAN_ITEM_RELATION",
    "OFFICIAL_SCHEDULE_SOURCE",
    "LOCKED_ITEM_VALIDITY",
    "SCORE_RISK_CONSISTENCY"
  ],
  "autoFix": false
}
```

#### 响应示例

```json
{
  "code": 200,
  "message": "数据一致性检查完成",
  "data": {
    "checkBatchNo": "CHECK-20260518-001",
    "totalIssueCount": 3,
    "highIssueCount": 1,
    "mediumIssueCount": 2,
    "lowIssueCount": 0,
    "autoFix": false,
    "issues": [
      {
        "id": 8101,
        "checkType": "OFFICIAL_SCHEDULE_SOURCE",
        "checkLevel": "MEDIUM",
        "objectType": "SCHEDULE",
        "objectId": 2001,
        "title": "正式课表缺少来源方案",
        "description": "该课表记录 sourcePlanId 为空，无法追踪来源方案。",
        "suggestion": "建议补充来源方案或标记为人工创建。",
        "status": "OPEN"
      }
    ]
  }
}
```

#### checkTypes 建议

```text
SEMESTER_BOUNDARY          学期边界检查
PLAN_ITEM_RELATION         方案明细关联检查
OFFICIAL_SCHEDULE_SOURCE   正式课表来源检查
LOCKED_ITEM_VALIDITY       锁定项有效性检查
SCORE_RISK_CONSISTENCY     评分与风险一致性检查
ORPHAN_DATA                孤立数据检查
DUPLICATE_TIME_CONFLICT    重复时间冲突检查
```

#### 重要规则

1. 默认 `autoFix=false`；
2. 数据检查不能自动乱改业务数据；
3. 如果后续支持自动修复，只能处理低风险、可确定的问题；
4. 高风险问题必须用户确认。

---

### 11.2 查询数据检查结果列表

```http
GET /api/v5/schedule-data-checks
```

#### 请求参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| semesterId | number | 否 | 学期 ID |
| checkType | string | 否 | 检查类型 |
| checkLevel | string | 否 | 问题等级 |
| status | string | 否 | OPEN / RESOLVED / IGNORED |
| pageNum | number | 否 | 页码 |
| pageSize | number | 否 | 每页数量 |

---

### 11.3 标记数据检查问题状态

```http
POST /api/v5/schedule-data-checks/{checkId}/status
```

#### 请求体

```json
{
  "status": "IGNORED",
  "remark": "该记录为历史人工创建数据，可忽略"
}
```

说明：该接口只更新检查结果状态，不直接修复业务数据。

---

## 十二、回归测试记录接口

该接口用于记录 V1-V5 核心流程测试结果。

如果项目不准备做测试记录落库，可以先不实现本节接口。

---

### 12.1 创建回归测试记录

```http
POST /api/v5/regression-tests
```

#### 请求体

```json
{
  "semesterId": 1,
  "caseCode": "V3_PLAN_APPLY_ROLLBACK",
  "caseName": "V3 方案应用与回滚测试",
  "caseType": "V3_CORE_FLOW",
  "testStatus": "PASSED",
  "testerName": "系统管理员",
  "testResult": "方案应用和回滚均正常",
  "errorMessage": null
}
```

#### 响应示例

```json
{
  "code": 200,
  "message": "测试记录已保存",
  "data": {
    "id": 9001
  }
}
```

---

### 12.2 查询回归测试记录

```http
GET /api/v5/regression-tests
```

#### 请求参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| semesterId | number | 否 | 学期 ID |
| caseType | string | 否 | 用例类型 |
| testStatus | string | 否 | PASSED / FAILED / BLOCKED |
| pageNum | number | 否 | 页码 |
| pageSize | number | 否 | 每页数量 |

---

### 12.3 获取测试概览

```http
GET /api/v5/regression-tests/summary
```

#### 响应示例

```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "totalCount": 30,
    "passedCount": 26,
    "failedCount": 3,
    "blockedCount": 1,
    "passRate": 86.67,
    "lastTestTime": "2026-05-18 17:20:00"
  }
}
```

---

## 十三、AI 修复解释接口（可选）

AI 只能辅助解释，不能直接修改课表。

---

### 13.1 生成修复建议解释

```http
POST /api/v5/ai/schedule-repair/explain-suggestion
```

#### 请求体

```json
{
  "repairTaskId": 1001,
  "suggestionId": 501,
  "style": "NORMAL"
}
```

#### 响应示例

```json
{
  "code": 200,
  "message": "AI 修复解释生成成功",
  "data": {
    "summary": "该建议通过更换教室解决原有教室冲突，同时保持教师、班级和上课时间不变，对整体课表影响较小。",
    "advantages": [
      "解决了原教室占用冲突",
      "不改变上课时间",
      "不影响锁定课程"
    ],
    "risks": [
      "新教室距离可能与原教学楼不同，需要人工确认"
    ],
    "recommendation": "建议优先采用该修复建议。"
  }
}
```

---

### 13.2 生成试算方案分析说明

```http
POST /api/v5/ai/schedule-repair/explain-trial-plan
```

#### 请求体

```json
{
  "repairTaskId": 1001,
  "trialPlanId": 1201,
  "compareId": 7001,
  "style": "DEFENSE_SUMMARY"
}
```

#### 响应示例

```json
{
  "code": 200,
  "message": "AI 试算分析生成成功",
  "data": {
    "summary": "本次局部修复在不影响已锁定课程的前提下，将存在冲突的课程调整到可用教室，修复后方案评分由 82.5 提升至 86.0，硬冲突数量降为 0。",
    "defenseText": "V5 版本通过试算方案机制实现了安全的局部修复。系统不会直接覆盖正式课表，而是先生成可对比的试算方案，由管理员确认后再应用。"
  }
}
```

---

### 13.3 AI 接口安全规则

AI 接口必须遵守：

1. AI 输入只能使用结构化统计数据、风险列表、建议摘要；
2. 不要把整个数据库原始数据直接传给 AI；
3. AI 输出只作为文字解释；
4. AI 不得直接调用应用方案、修改课表、删除方案接口；
5. AI 输出落库前应做长度和格式校验；
6. AI 失败不能影响核心修复流程。

---

## 十四、辅助选项接口

为了方便前端筛选和弹窗展示，V5 可以提供以下选项接口。

---

### 14.1 获取修复模式选项

```http
GET /api/v5/options/repair-modes
```

### 14.2 获取修复范围类型选项

```http
GET /api/v5/options/repair-scope-types
```

### 14.3 获取建议类型选项

```http
GET /api/v5/options/suggestion-types
```

### 14.4 获取约束规则类型选项

```http
GET /api/v5/options/constraint-categories
```

响应示例：

```json
{
  "code": 200,
  "message": "查询成功",
  "data": [
    {
      "label": "单风险修复",
      "value": "SINGLE_ISSUE_REPAIR"
    },
    {
      "label": "班级局部重排",
      "value": "CLASS_LOCAL_REPLAN"
    }
  ]
}
```

---

## 十五、接口安全与校验规则

### 15.1 通用校验

所有 V5 写操作接口必须校验：

1. 当前学期是否存在；
2. 来源方案或正式课表是否存在；
3. 来源数据是否属于同一学期；
4. 方案状态是否允许修复；
5. 锁定课程是否被移动；
6. 是否存在硬冲突；
7. 用户是否确认执行写操作；
8. 参数枚举值是否合法。

### 15.2 跨学期限制

以下对象必须属于同一学期：

```text
repairTask.semesterId
sourcePlan.semesterId
sourceRisk.semesterId
trialPlan.semesterId
suggestion.semesterId
candidate.semesterId
lockedItem.semesterId
```

禁止把 A 学期的修复建议应用到 B 学期课表。

### 15.3 事务要求

以下接口必须使用事务：

```http
POST /api/v5/schedule-repair-tasks
POST /api/v5/schedule-repair-tasks/{taskId}/generate-suggestions
POST /api/v5/schedule-repair-tasks/{taskId}/generate-candidates
POST /api/v5/schedule-repair-trials/from-suggestion
POST /api/v5/schedule-repair-trials/from-candidate
POST /api/v5/schedule-repair-trials/local-replan
POST /api/v5/schedule-repair-trials/{trialPlanId}/apply
POST /api/v5/schedule-repair-compare/generate
POST /api/v5/schedule-data-checks/run
```

事务要求：

1. 任意一步失败，整体回滚；
2. 生成试算方案和方案明细必须在同一事务；
3. 应用试算方案和更新修复任务状态必须在同一事务；
4. 不能只生成方案主表却缺少方案明细；
5. 不能只应用部分正式课表记录。

### 15.4 幂等与重复提交

建议处理方式：

1. 创建修复任务可以允许多次创建，但前端应提示已有类似任务；
2. 生成建议可以重复执行，后端可先清理旧建议或保留历史版本；
3. 生成试算方案默认允许多次，但每次都生成新方案；
4. 应用试算方案必须防止重复应用；
5. 已废弃试算方案不能应用。

### 15.5 锁定课程保护

以下操作必须读取锁定状态：

1. 生成修复建议；
2. 生成候选位置；
3. 生成试算方案；
4. 局部重排；
5. 应用试算方案前复检。

规则：

```text
被锁定课程不得移动。
被锁定课程如果必须移动，用户必须先显式解锁。
```

---

## 十六、接口与前端页面对应关系

| 前端页面 | 主要接口 |
|---|---|
| V5 修复任务列表页 | `GET /api/v5/schedule-repair-tasks` |
| 创建修复任务弹窗 | `POST /api/v5/schedule-repair-tasks` |
| 修复任务详情页 | `GET /api/v5/schedule-repair-tasks/{taskId}` |
| 修复建议页面 | `POST /api/v5/schedule-repair-tasks/{taskId}/generate-suggestions`、`GET /api/v5/schedule-repair-tasks/{taskId}/suggestions` |
| 候选位置页面 | `POST /api/v5/schedule-repair-tasks/{taskId}/generate-candidates`、`GET /api/v5/schedule-repair-tasks/{taskId}/candidates` |
| 局部修复试算页面 | `POST /api/v5/schedule-repair-trials/from-suggestion`、`GET /api/v5/schedule-repair-trials/{trialPlanId}` |
| 局部重排页面 | `POST /api/v5/schedule-repair-trials/local-replan` |
| 优化前后对比页面 | `POST /api/v5/schedule-repair-compare/generate`、`GET /api/v5/schedule-repair-tasks/{taskId}/compare` |
| 试算方案变更明细页 | `GET /api/v5/schedule-repair-trials/{trialPlanId}/changes` |
| 数据一致性检查页 | `POST /api/v5/schedule-data-checks/run`、`GET /api/v5/schedule-data-checks` |
| 回归测试记录页 | `GET /api/v5/regression-tests`、`POST /api/v5/regression-tests` |
| AI 修复解释页 | `POST /api/v5/ai/schedule-repair/explain-suggestion`、`POST /api/v5/ai/schedule-repair/explain-trial-plan` |

---

## 十七、推荐开发顺序

建议按以下顺序开发 V5 API：

```text
1. 约束规则查询与位置约束检查接口
2. 修复任务创建、列表、详情接口
3. 修复建议生成与查询接口
4. 候选位置生成与查询接口
5. 基于建议生成试算方案接口
6. 试算方案结果与变更明细查询接口
7. 优化前后对比接口
8. 试算方案废弃与应用接口
9. 局部重排试算接口
10. 数据一致性检查接口
11. 回归测试记录接口
12. AI 修复解释接口
```

第一阶段最小闭环：

```text
创建修复任务
↓
生成修复建议
↓
生成试算方案
↓
查看优化前后对比
↓
应用或放弃试算方案
```

---

## 十八、V5 API 开发禁止事项

开发 V5 API 时必须禁止：

1. 不允许重写 V3 自动排课接口；
2. 不允许重写 V3 应用方案接口；
3. 不允许重写 V3 回滚接口；
4. 不允许让修复建议直接修改 `schedule`；
5. 不允许让候选位置直接写入正式课表；
6. 不允许让局部重排直接覆盖来源方案；
7. 不允许移动锁定课程；
8. 不允许跨学期应用试算方案；
9. 不允许删除历史方案和历史方案明细；
10. 不允许 AI 接口调用写课表接口；
11. 不允许数据一致性检查接口默认自动修复高风险数据；
12. 不允许接口返回结构随意变化，导致前端无法联调。

---

## 十九、V5 API 验收清单

### 19.1 基础接口验收

- [ ] 所有接口路径符合 `/api/v5/...` 分组；
- [ ] 所有接口返回格式统一；
- [ ] 所有写操作有参数校验；
- [ ] 所有涉及学期的接口能按当前学期处理；
- [ ] 参数错误能返回清晰错误信息；
- [ ] 业务冲突能返回 409 或明确业务错误。

### 19.2 修复任务验收

- [ ] 能基于方案创建修复任务；
- [ ] 能基于风险创建修复任务；
- [ ] 能查看修复任务列表；
- [ ] 能查看修复任务详情；
- [ ] 能放弃未应用的修复任务。

### 19.3 修复建议验收

- [ ] 能根据风险生成修复建议；
- [ ] 修复建议能显示预计评分变化；
- [ ] 修复建议能显示新增冲突数；
- [ ] 修复建议能按推荐等级排序；
- [ ] 不会生成移动锁定课程的建议。

### 19.4 试算方案验收

- [ ] 能基于建议生成试算方案；
- [ ] 能基于候选位置生成试算方案；
- [ ] 能生成局部重排试算方案；
- [ ] 试算方案不会直接修改正式课表；
- [ ] 试算方案能查看变更明细；
- [ ] 试算方案能废弃；
- [ ] 试算方案能确认应用。

### 19.5 优化对比验收

- [ ] 能生成修复前后对比；
- [ ] 能展示评分变化；
- [ ] 能展示冲突变化；
- [ ] 能展示风险变化；
- [ ] 能展示移动课程数量和受影响课程数量。

### 19.6 数据安全验收

- [ ] 不能跨学期修复；
- [ ] 不能移动锁定课程；
- [ ] 不能应用已废弃试算方案；
- [ ] 应用失败时事务回滚；
- [ ] 不破坏 V1-V4 旧功能。

---

## 二十、给 AI 开发工具的接口开发提示词

后续可以将以下提示词交给 AI 编程工具：

```text
现在开发排课系统 V5 的 API 接口。V5 定位为“约束驱动的局部重排与智能修复优化版”。

请严格遵守 docs/V5_04_API接口设计.md。

开发要求：
1. 不要重写 V1-V4 已有接口。
2. V5 新接口统一使用 /api/v5 前缀。
3. 修复建议、候选位置、AI 分析都不能直接修改正式课表。
4. 所有自动修复和局部重排结果必须先生成试算方案。
5. 试算方案仍然写入 schedule_plan 和 schedule_plan_item，不要单独发明一套正式课表结构。
6. 应用试算方案时复用 V3 应用方案逻辑，并更新修复任务状态。
7. 任何写操作都必须校验 semesterId，禁止跨学期操作。
8. 局部重排必须读取课程锁定状态，锁定课程不能移动。
9. 所有写操作必须有事务，失败时回滚。
10. 所有接口返回格式统一为 { code, message, data }。
11. 每完成一个接口模块后，说明新增和修改的文件，并给出自测结果。

优先开发顺序：
1. 修复任务接口；
2. 修复建议接口；
3. 候选位置接口；
4. 试算方案接口；
5. 优化对比接口；
6. 数据一致性检查接口。
```

---

## 二十一、总结

V5 API 的核心不是“再写一套自动排课接口”，而是围绕已有 V3/V4 能力，增加安全、可解释、可试算的局部修复流程。

V5 API 主线是：

```text
V4 风险诊断
  ↓
V5 创建修复任务
  ↓
生成修复建议和候选位置
  ↓
生成试算方案
  ↓
对比优化前后结果
  ↓
用户确认后应用
```

开发时必须记住：

```text
修复建议不等于修改课表。
候选位置不等于正式安排。
试算方案不等于正式课表。
只有用户确认应用后，才能通过正式流程写入 schedule。
```

只要这条边界守住，V5 就可以在不破坏 V1-V4 的前提下，为系统增加更高层次的智能修复和局部优化能力。
