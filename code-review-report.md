# D:\paike 项目深度代码审查报告

**审查范围**：后端 `com.paike.scheduler` + 前端 `frontend/src/`  
**审查方法**：CodeGraph 符号搜索 + 逐文件精读 + Cypher 查询 + 子 agent 并行分析  
**审查日期**：2026-06-21  

---

## 项目概览

| 维度 | 数据 |
|------|------|
| 后端语言 | Java 17 + Spring Boot + MyBatis Plus |
| 前端语言 | Vue 3 + TypeScript + Element Plus + Pinia |
| 后端文件数 | 424 Java 文件 |
| 前端文件数 | 106 TS/Vue 文件 |
| 总节点数 | 11,732 |
| 总边数 | 21,368 |
| 发现问题数 | 40+ |

---

## 一、CRITICAL 级别（数据丢失 / 严重逻辑错误）

### 1. MyBatis Plus 物理删除与软删除系统性混用

**严重程度**：CRITICAL  
**文件**：`SchedulePlanService.java`、`V3ScheduleGenerateService.java`、`AutoScheduleService.java`、`V5SimulationService.java`  
**问题描述**：
项目大量使用 `@TableLogic` 实现软删除，但在多个 Service 中错误地使用 `BaseMapper.delete(Wrapper)` 执行物理删除。MyBatis Plus 中 `delete(Wrapper)` 仍执行 `DELETE FROM ...`，即使实体标注了 `@TableLogic`，仅 `deleteById(id)` 会执行软删除（`UPDATE ... SET deleted = 1`）。

**涉及位置**：
- `SchedulePlanService.java:116` — `SchedulePlanItem` 有 `@TableLogic`
- `SchedulePlanService.java:392, 395` — `Schedule` 有 `@TableLogic`
- `V3ScheduleGenerateService.java:229` — `SchedulePlanItem`
- `AutoScheduleService.java:123, 130` — `Schedule`
- `V5SimulationService.java:536` — `SchedulePlanItem`
- `V5SimulationService.java:543` — `ScheduleScoreDetail`

**改进建议**：
1. 建立代码规范：对带 `@TableLogic` 的实体，禁止使用 `delete(Wrapper)`，统一使用 `deleteById`
2. 对必须物理删除的场景显式添加注释说明原因，并确保有审计日志
3. 使用 SonarQube/CheckStyle 插件扫描 `delete(Wrapper)` 调用

---

### 2. 应用方案时物理删除手动排课

**严重程度**：CRITICAL  
**文件**：`SchedulePlanService.java:392-401`  
**问题描述**：
`applyPlanInternal` 执行了物理删除整个学期所有 Schedule 记录，**包括 sourceType=MANUAL 的手动排课**，不仅限于目标方案关联的排课。

```java
scheduleMapper.delete(new LambdaQueryWrapper<Schedule>()
        .eq(Schedule::getSemesterId, semesterId));
```

**改进建议**：
1. 区分 `sourceType`，仅删除关联旧方案的 schedule，保留手动排课
2. 或使用软删除
3. 如果业务确实需要清空所有课课，应在 UI 上明确警告并增加二次确认

---

### 3. ErrorBoundary 将错误栈写入 sessionStorage

**严重程度**：CRITICAL  
**文件**：`frontend/src/components/ErrorBoundary.vue:32-45`  
**问题描述**：
`recordLocalError` 函数将 `err.stack`（包含完整调用栈、变量名、文件路径等敏感信息）写入 `sessionStorage`。在共享设备或多标签场景下，攻击者可通过 XSS 或其他方式读取这些信息。

**改进建议**：
1. 移除 `stack` 字段，只保留 `message`、`path`、`time`
2. 如需上报错误，应在后端记录，前端只发送最小必要信息
3. 添加 CSP (Content-Security-Policy) 头防止 XSS

---

## 二、HIGH 级别（安全漏洞 / 严重一致性问题）

### 4. AuthInterceptor requiresAdmin 路径匹配在 context-path 部署下失效

**严重程度**：HIGH  
**文件**：`AuthInterceptor.java:99`  
**问题描述**：
使用 `request.getRequestURI()` 进行路径比较，在 Spring Boot 设置非根 context-path 时（如 `/scheduler`），logout 路径 `/scheduler/api/auth/logout` 与硬编码的 `"/api/auth/logout"` 不匹配，导致普通用户无法正常退出。

**改进建议**：
1. 使用 `request.getServletPath()` 获取不包含 context-path 的路径
2. 或使用 `AntPathMatcher` 做后缀匹配

---

### 5. TimetableService 查询使用 Schedule 冗余字段导致数据不一致

**严重程度**：HIGH  
**文件**：`TimetableService.java:158-166`  
**问题描述**：
`Schedule` 实体中 `courseId/teacherId/classId` 是从 `TeachingTask` 复制来的冗余字段。`TimetableService.queryByClassId` 和 `queryByTeacherId` 直接通过 `Schedule::getClassId` 查询，如果 TeachingTask 的 `classId/teacherId` 发生变更，Schedule 表中的冗余字段不会同步更新，导致查询结果遗漏或错误。

**改进建议**：
1. 统一改为通过 `teachingTaskId` 关联 `TeachingTask` 表进行过滤
2. 或在 `TeachingTaskService.update` 中增加级联更新逻辑

---

### 6. ScheduleService.create 手动排课未设置 weekType/startWeek/endWeek

**严重程度**：HIGH  
**文件**：`ScheduleService.java:111-122`  
**问题描述**：
插入手动排课时，仅设置了基本字段，**未设置 `weekType`、`startWeek`、`endWeek`**。后续冲突检测会使用默认值（`ALL` / `1-20`），导致 ODD/EVEN 周类型的教学任务被错误检测为与全周课程冲突。

**改进建议**：
1. 从 `TeachingTask` 复制 `weekType/startWeek/endWeek` 到新创建的 `Schedule`
2. 或在数据库层面对字段设置 DEFAULT 值

---

### 7. 权限控制过于粗粒度，所有写操作强制 ADMIN

**严重程度**：HIGH  
**文件**：`AuthInterceptor.java:95-101`、`WebMvcConfig.java:28-36`  
**问题描述**：
所有 `/api/**` 下的 POST/PUT/DELETE/PATCH 请求（除 `/api/auth/logout` 外）强制要求 `ROLE_ADMIN`。系统实际只有"管理员"和"只读用户"两种角色，功能可用性受限。

**改进建议**：
1. 实现基于资源的细粒度权限控制（RBAC 或 ABAC）
2. 区分数据所有权：教师只能修改自己负责的教学任务

---

### 8. God 组件：SchedulePlanDetailView.vue（655 行）

**严重程度**：HIGH  
**文件**：`SchedulePlanDetailView.vue`  
**问题描述**：
单个组件承担了 8+ 职责：方案基本信息展示、课表明细表格、生成日志、未排任务统计、评分明细、调整记录、局部重排弹窗、修复任务创建。

**改进建议**：
拆分为：`SchedulePlanHeader.vue`、`ScheduleItemsTable.vue`、`ScheduleLogsPanel.vue`、`UnassignedTasksPanel.vue`、`ScoreDetailsPanel.vue`、`AdjustLogsPanel.vue`

---

### 9. 分页组件竞态条件

**严重程度**：HIGH  
**文件**：15+ 个 views（`SchedulePlanView.vue`、`ScheduleView.vue`、`TeacherView.vue` 等）  
**问题描述**：
Element Plus 分页组件同时使用 `v-model:current-page` 和 `@current-change`，快速点击时可能产生重复请求或数据错乱。

**改进建议**：
1. 移除 `@current-change` 和 `@size-change`，改用 `watch([currentPage, pageSize], fetchData)`
2. 或在事件处理函数中使用 AbortController 取消之前的请求

---

### 10. Cookie 读取正则存在注入风险

**严重程度**：HIGH  
**文件**：`frontend/src/utils/request.ts:7-15`  
**问题描述**：
`getCookie` 函数使用 `new RegExp('(?:^|; )' + name + '=([^;]*)')` 构建正则表达式，如果 `name` 参数包含正则元字符可能导致 ReDoS。

**改进建议**：
1. 对 `name` 参数进行转义：`name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')`
2. 或改用字符串查找方式解析 cookie

---

## 三、MEDIUM 级别（可维护性 / 潜在风险）

### 11. RateLimiterService 内存限流器集群不适用

**严重程度**：MEDIUM  
**文件**：`RateLimiterService.java:17-18`  
**问题描述**：
使用 `ConcurrentHashMap` 在内存中存储限流状态，集群环境下每个实例独立计数，攻击者可轮询不同实例绕过限流。

**改进建议**：
替换为 Redis + Lua 脚本实现的分布式滑动窗口限流

---

### 12. 审计日志记录方法在 catch 块中可能覆盖原始异常

**严重程度**：MEDIUM  
**文件**：`ScheduleService.java:347-356`  
**问题描述**：
`recordScheduleFailure` 在 catch 块中被调用，但方法内部又调用了 `teachingTaskMapper.selectById`，若查询失败会抛出新的 `RuntimeException`，覆盖原始异常。

**改进建议**：
1. 在 `recordScheduleFailure` 内部添加 try-catch，确保审计日志写入失败不影响原始异常传播
2. 或将 `teachingTaskId` 直接传入，避免二次查询

---

### 13. God Class：V5SimulationService 等方法过长

**严重程度**：MEDIUM  
**文件**：`V5SimulationService.java`（1509 行）、`V4ScheduleRiskService.java`（593 行）、`SchedulePlanService.java`（802 行）、`V3ScheduleGenerateService.java`（893 行）

**改进建议**：
按职责拆分：将冲突检测、评分计算、方案应用、回滚逻辑拆分为独立的 Service

---

### 14. 多处异常被静默吞没

**严重程度**：MEDIUM  
**文件**：`V5ConsistencyCheckService.java:69`、`SchedulePlanService.java:351`、`V5SimulationService.java:404,836,1388`、`SchedulePlanExplainService.java:327`

**改进建议**：
1. 即使不抛出异常，至少使用 `log.warn` 记录降级原因
2. `InterruptedException` 必须恢复中断状态：`Thread.currentThread().interrupt()`

---

### 15. Schedule 实体冗余字段与 TeachingTask 不一致风险

**严重程度**：MEDIUM  
**文件**：`Schedule.java:25-34`、`ScheduleConflictService.java:141-160`  
**问题描述**：
`Schedule` 实体中 `courseId/teacherId/classId` 为冗余字段，创建后不再同步。如果 TeachingTask 更新了 `weekType`，关联的 schedule 行不会同步更新，导致冲突检测误判。

**改进建议**：
1. 统一通过 `teachingTaskId` JOIN `TeachingTask` 获取最新字段
2. 或在 `TeachingTaskService.update` 中增加级联更新逻辑

---

### 16. AuthInterceptor 每次请求查询数据库获取用户信息

**严重程度**：MEDIUM  
**文件**：`AuthInterceptor.java:69-70`  
**问题描述**：
每次请求都执行 `sysUserMapper.selectById` 获取用户信息。在 QPS 较高时，这成为性能瓶颈。

**改进建议**：
1. JWT payload 中增加 `role/status` 等非敏感字段，避免查库
2. 使用 Redis 缓存用户会话信息

---

### 17. API 层大量重复的响应数据检查代码

**严重程度**：MEDIUM  
**文件**：所有 `frontend/src/api/*.ts`（共 30+ 文件）  
**问题描述**：
每个 API 函数都重复以下模式：
```typescript
if (!r.data) throw new Error('响应数据为空')
return r.data.data
```

**改进建议**：
在 `utils/request.ts` 的 response interceptor 中统一处理，直接返回 `response.data.data`

---

### 18. 7 个 CRUD View 未迁移到 useCrudForm，重复代码严重

**严重程度**：MEDIUM  
**文件**：`TeacherView.vue`、`ClassInfoView.vue`、`ClassroomView.vue`、`CourseView.vue`、`SemesterView.vue`、`TeacherUnavailableTimeView.vue`、`ScheduleView.vue`  
**问题描述**：
虽然提供了 `useCrudForm` composable，但仍有 7 个视图保留了大量重复的 CRUD 样板代码。

**改进建议**：
逐步迁移到 `useCrudForm`，先从最简单的 `CourseView` 开始

---

### 19. 路由守卫并发控制缺陷

**严重程度**：MEDIUM  
**文件**：`frontend/src/router/index.ts:302-332`、`frontend/src/stores/auth.ts:25-37`  
**问题描述**：
如果 `getCurrentUserApi` 请求超时或永远挂起，`fetchCurrentUserInflight` 永远不会被清除，后续所有导航都会永久挂起。

**改进建议**：
为 `getCurrentUserApi` 添加超时处理和重试逻辑，在 `fetchCurrentUser` 中添加最大等待时间

---

### 20. 弹窗 destroy-on-close 导致状态管理复杂

**严重程度**：MEDIUM  
**文件**：11 个 views  
**问题描述**：
所有 `el-dialog` 都使用了 `destroy-on-close`，对于包含表单的弹窗，用户输入的内容在关闭后完全丢失。

**改进建议**：
对于复杂表单弹窗，考虑使用 `v-if` 控制显示/隐藏而非 `destroy-on-close`，或添加 `before-close` 钩子提示用户保存

---

### 21. 请求取消机制缺失

**严重程度**：MEDIUM  
**文件**：`frontend/src/utils/request.ts`  
**问题描述**：
使用 axios 创建了全局 request 实例，但没有使用 `AbortController`。快速切换页面时旧请求仍会返回并修改已卸载组件的状态。

**改进建议**：
使用 `AbortController` 在组件卸载时取消请求

---

### 22. 数据一致性：缺少唯一约束和乐观锁

**严重程度**：MEDIUM  
**文件**：`Schedule` 表、`SchedulePlan` 表  
**问题描述**：
1. `Schedule` 表应在组合字段上建立唯一约束，防止重复排课
2. `SchedulePlan.status` 状态流转在并发场景下可能丢失更新

**改进建议**：
1. 确认数据库层面已对关键业务表建立唯一约束
2. 对 `SchedulePlan.status` 状态流转使用乐观锁
3. 对方案应用操作增加分布式锁

---

## 四、LOW 级别（优化建议）

### 23. AuthService BCrypt matches 执行两次

**严重程度**：LOW  
**文件**：`AuthService.java:48-53`  
**问题描述**：
用户不存在时执行一次 `passwordEncoder.matches`，用户存在时又执行一次。BCrypt 计算密集型，两次执行使登录耗时翻倍。

**改进建议**：
统一使用 `passwordEncoder.matches(rawPassword, userOrDummyHash)` 一次执行

---

### 24. JWT 默认 secret 硬编码在源码中

**严重程度**：LOW  
**文件**：`JwtService.java:16`

**改进建议**：
将 `DEFAULT_SECRET` 改为空字符串或随机字符串，避免在代码库中保留可读的默认值

---

### 25. 大类方法过多，职责不清

**严重程度**：LOW  
**文件**：`V4ScheduleRiskService.java`（593 行）、`V5RepairSuggestionService.java`（470 行）、`SchedulePlanExplainService.java`

**改进建议**：
应用单一职责原则，按领域边界拆分

---

### 26. 表单验证规则不完整

**严重程度**：MEDIUM  
**文件**：`TeachingTaskView.vue`、`ScheduleView.vue`、`TeacherView.vue` 等  
**问题描述**：
表单验证只检查了 `required`，没有检查数值范围、周次范围等。

**改进建议**：
为所有数值字段添加 `type: 'number'`、`min`、`max` 验证规则

---

### 27. 类型安全不足：ApiResponse 的 data 字段为 unknown

**严重程度**：MEDIUM  
**文件**：`frontend/src/api/types.ts:8-12`

**改进建议**：
在 request interceptor 中统一处理响应，确保 `response.data` 始终为有效数据

---

### 28. 深色模式不兼容的内联样式

**严重程度**：LOW  
**文件**：`ScheduleCompareView.vue`、`AutoScheduleView.vue`、`ScheduleScoreReportView.vue`

**改进建议**：
使用 CSS 变量或 Element Plus 的 token 系统

---

### 29. Console.error 可能泄露敏感信息

**严重程度**：LOW  
**文件**：多个 views（共 20+ 处）

**改进建议**：
使用统一的错误处理工具函数，在发送前脱敏

---

### 30. EChartPanel 深度监听 option 可能导致性能问题

**严重程度**：LOW  
**文件**：`frontend/src/components/v4/EChartPanel.vue:83-95`

**改进建议**：
移除 `deep: true`，改用 `shallowRef` 管理 option

---

## 五、跨模块综合问题

### 31. 幂等性缺失

**严重程度**：MEDIUM  
**文件**：`ScheduleService.create`、`TeachingTaskService.create`、`SchedulePlanService.applyPlan`

**改进建议**：
对创建类接口支持幂等键（Idempotency-Key）

---

### 32. 分层混乱：Service 层直接依赖 Mapper 且无接口隔离

**严重程度**：MEDIUM  
**文件**：所有 Service

**改进建议**：
对复杂查询场景，抽取独立的 Query Service 或 Repository

---

### 33. 缺少统一的 DTO/Command 层

**严重程度**：LOW  
**文件**：多个 Controller

**改进建议**：
建立独立的 `dto` 和 `command` 包

---

## 六、按模块总结

| 模块 | 主要问题 | 严重程度 |
|------|----------|----------|
| **Auth / Security** | requiresAdmin 路径匹配 context-path 问题；权限过于粗粒度；内存限流器集群不适用；每次请求查库；ErrorBoundary sessionStorage 泄露；Cookie 正则注入 | HIGH / MEDIUM / CRITICAL |
| **Service (Schedule)** | 物理删除误用；手动排课缺失 weekType；recordScheduleFailure 异常覆盖；冗余字段不一致 | CRITICAL / HIGH / MEDIUM |
| **Service (Plan)** | applyPlan 物理删除手动排课；物理删除 planItem；God Class | CRITICAL / MEDIUM |
| **Service (Engine)** | V5SimulationService 过长；异常吞没 | MEDIUM |
| **Controller** | 无独立安全问题，但依赖 AuthInterceptor 粗粒度权限 | HIGH |
| **Mapper / XML** | SQL 参数化安全；delete(Wrapper) 物理删除问题 | CRITICAL |
| **Entity** | 冗余字段一致性风险；weekType/startWeek/endWeek 未正确设置 | HIGH / MEDIUM |
| **前端 Views** | God 组件；分页竞态；弹窗状态丢失；重复代码 | HIGH / MEDIUM |
| **前端 API** | 响应检查重复；请求取消缺失；类型安全不足 | MEDIUM |
| **前端 Components** | ErrorBoundary 敏感信息泄露；EChartPanel 性能问题 | CRITICAL / LOW |

---

## 七、优先修复建议

### P0（立即修复，1-2 天）
1. 审查所有 `delete(Wrapper)` 调用，对带 `@TableLogic` 的实体改为软删除
2. 修复 `ScheduleService.create` 未设置 `weekType/startWeek/endWeek`
3. 修复 `AuthInterceptor.requiresAdmin` 路径匹配问题
4. 修复 `SchedulePlanService.applyPlanInternal` 物理删除手动排课
5. 移除 ErrorBoundary 中的 `err.stack` 字段

### P1（短期修复，1-2 周）
6. 实现 `TeachingTask` 到 `Schedule` 的级联更新或废弃冗余字段
7. 统一异常处理规范，消灭空 catch 块
8. 修复分页竞态条件（统一使用 watch 监听）
9. 引入 AbortController 取消机制
10. 修复 Cookie 正则注入风险

### P2（中期重构，1个月）
11. 拆分 God Class（V5SimulationService、SchedulePlanService 等）
12. 实现细粒度权限控制（RBAC）
13. 替换内存限流器为 Redis 分布式限流
14. 7 个 CRUD View 迁移到 useCrudForm
15. API 层统一响应处理

### P3（长期优化）
16. 深色模式兼容
17. 代码重复提取（formatDateTime 等）
18. 国际化支持（vue-i18n）
19. 建立代码扫描规则防止物理删除误用

---

## 八、正面实践（值得保持）

### 后端
- ✅ 使用 `@TableLogic` 实现软删除（设计合理）
- ✅ `DuplicateKeyException` 处理并发冲突
- ✅ `Semester.setCurrent` 通过乐观锁处理并发
- ✅ 审计日志使用 `Propagation.REQUIRES_NEW` 独立事务
- ✅ JWT + BCrypt 密码加密
- ✅ httpOnly Cookie 传递 JWT

### 前端
- ✅ 使用 httpOnly Cookie 传递 JWT，前端无法读取
- ✅ CSRF 防护通过 XSRF-TOKEN 实现
- ✅ 无 `v-html` / `innerHTML` / `eval()` 等危险 API
- ✅ TypeScript 类型使用良好，无 `any` 滥用
- ✅ 统一的 `extractMessage` 错误处理工具函数
- ✅ 提供了 `useCrudForm` composable 抽象

---

**审查完成**。如需对某个具体问题进行深入分析或提供修复代码示例，请告知。
