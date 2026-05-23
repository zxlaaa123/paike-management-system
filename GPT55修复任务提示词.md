# GPT-5.5 修复任务提示词（分阶段）

> 本文档内每一节都是**可直接复制粘贴给 GPT-5.5 的完整提示词**，互相独立、不依赖上下文。
> 4 个阶段建议按顺序执行（A → B → C → D），每个阶段独立 commit / 独立 PR，便于 review。
> 每个阶段执行前，先把 `D:\paike\代码修改建议.md` 中对应批次的具体 diff 喂给 agent 作为补充参考。

---

## 使用说明（给操作者看，不喂给 agent）

- **阶段 A**：1-2 天可清掉的安全/数据风险，强制串行执行。
- **阶段 B**：本迭代功能正确性问题，建议串行。
- **阶段 C**：下迭代维护性问题，可拆分多 PR。
- **阶段 D**：架构级，建议拆出独立 ticket，不要一次性塞给 agent。

每次给 agent 任务时：
1. 复制对应阶段整段提示词；
2. 同时附上 `D:\paike\代码修改建议.md`（让 agent 直接读取，不要在提示词里复述 diff）；
3. agent 完成后，Claude 这边做 review，确认无误再 merge。

---

## 阶段 A 提示词：立即修复（A1-A5 整批 + 4 项 review 补丁，单次交付）

> 上一轮 GPT-5.5 已执行过 A 批次但工作区被 `git restore` 清空，未进 commit，整批改动已丢失。
> 当前 fix/audit-2026-05 分支 HEAD = 470e69c，工作区干净。本次按下面这份单一提示词从零重做，并把之前 Claude review 出的 4 项遗漏一并合并。

```
你是一位资深 Java + Vue 全栈工程师。请在 D:\paike 这个 Spring Boot 3 + Vue 3 + MyBatis Plus 项目里完成 P0 安全/数据修复。当前分支 fix/audit-2026-05，HEAD 提交 470e69c，工作区干净，没有任何未提交改动。本次按下面任务清单一次性完成所有改动。

【项目关键信息】
- 后端：Spring Boot 3.x、Java 17、MyBatis Plus、jjwt、Lombok
- 前端：Vue 3 + TypeScript + Element Plus + Pinia + axios
- 数据库：MySQL 8.x，schema 通过 spring.sql.init 加载多个 .sql 脚本（v2-v7）
- 包名：com.paike.scheduler
- 端口：后端 8090，前端 5173
- 所有 shell 命令必须用 PowerShell（pwsh 7+）；不要用 bash 风格
- 不要尝试启动 Spring Boot，会卡死。需要联调时告诉操作者让他在独立终端跑 mvn spring-boot:run

【硬约束 —— 违反任何一条都视为失败】
- 不允许执行任何破坏性 git 命令：禁用 git restore / git reset / git checkout -- / git stash drop / git clean -f / git rm。即使为了"收敛 diff 范围"也禁止。任何想 revert 的冲动都要先停下来，把情况报告给操作者，让他来决定。
- 不允许自动 commit。改完留 working tree 给操作者 review。
- 不允许动本任务清单以外的文件。看到顺手能"优化"的代码也别动，包括重命名、调整 import 顺序、改空行、补缺失的 final 等。
- 不允许碰下列已被验证为误报的位置（哪怕觉得它们有问题）：
  · WebMvcConfig.java（误报：所有 v3 接口实际是 /api/v3/）
  · LoginResponse.java 的 @JsonIgnore（误报：HttpOnly Cookie 是 by design）
  · AutoScheduleService 的事务隔离逻辑（误报：MySQL 支持 read-own-writes）
  · SchedulePlanService.adjustPlanItem 的 adjustReason null 检查（已被 @Valid 挡住）

【任务清单】

A1. 修复 UnscheduledTaskService.clearAll 跨学期误删
- backend/src/main/java/com/paike/scheduler/service/UnscheduledTaskService.java
  · 把 clearAll() 改名为 clearBySemester(Long semesterId)，方法上加 @Transactional(rollbackFor = Exception.class)
  · semesterId 为 null 时抛 BusinessException("clearBySemester 必须传入 semesterId")
  · 删除条件改为 LambdaQueryWrapper<UnscheduledTask>().eq(UnscheduledTask::getSemesterId, semesterId)
  · addUnscheduledTask 方法签名加一个 Long semesterId 参数（放在 batchId 之后），把它 set 到 entity 上
- backend/src/main/java/com/paike/scheduler/service/AutoScheduleService.java
  · clearAllSchedule / clearOldAutoSchedule 两个分支里的 unscheduledTaskService.clearAll() 调用改成 clearBySemester(semesterId)
  · 所有 addUnscheduledTask 调用点（共 4 处：course/class/room 不匹配 + 排课失败兜底）补传 semesterId
- backend/src/main/java/com/paike/scheduler/entity/UnscheduledTask.java
  · 加字段 private Long semesterId;（放在 batchId 之后）
- backend/src/main/java/com/paike/scheduler/controller/UnscheduledTaskController.java
  · DELETE 端点的 clear() 方法补一个 @RequestParam(required = false) Long semesterId 参数
  · 原本 batchId == null 时调用 clearAll() 的分支改成调用 clearBySemester(semesterId)
- 新建 backend/src/main/resources/db/v8_unscheduled_task_semester.sql：
  · 用动态 SQL（SET @col_exists + PREPARE + EXECUTE 模式，参考 v2_alter_schedule.sql 但不要用 DELIMITER）幂等地：
    1. 检查 information_schema.COLUMNS，若 unscheduled_task.semester_id 列不存在则 ALTER TABLE 添加为 BIGINT NULL，位置 AFTER batch_id
    2. UPDATE unscheduled_task ut JOIN teaching_task tt ON tt.id = ut.task_id SET ut.semester_id = tt.semester_id WHERE ut.semester_id IS NULL AND tt.semester_id IS NOT NULL（回填历史数据）
    3. DELETE FROM unscheduled_task WHERE semester_id IS NULL（清扫无主孤儿行——teaching_task 已不存在或其 semester_id 为 NULL 的记录，这些行无法被 clearBySemester 命中，留着会无限堆积）
    4. 检查 information_schema.COLUMNS 的 IS_NULLABLE，若仍为 'YES' 则 ALTER TABLE MODIFY COLUMN semester_id BIGINT NOT NULL DEFAULT 0 COMMENT '所属学期ID'（强制后续插入必须显式带学期）
    5. 检查 information_schema.STATISTICS，若 idx_unscheduled_task_semester 不存在则 CREATE INDEX
  · 关键顺序：DELETE 必须在 ALTER MODIFY NOT NULL 之前，否则 NOT NULL 转换会失败
- backend/src/main/resources/application.yml
  · 在 spring.sql.init.schema-locations 末尾追加 ,classpath:db/v8_unscheduled_task_semester.sql

A2. 12 个 Controller 补 @Valid，并给 DTO 补校验
- 给以下端点的 @RequestBody 参数前补 @Valid（import jakarta.validation.Valid）：
  · AutoScheduleBatchController.java run 方法
  · ScheduleAiAnalysisController.java generatePlanAiAnalysis 方法
  · ScheduleRuleWeightController.java update(@PathVariable Long id, ...) 方法（line 60 那个 batchUpdate 由 A3 处理）
  · ScheduleRuleController.java update 方法：除了 @Valid @RequestBody List<@Valid RuleUpdateForm>，还要给 Controller 类加 @Validated（org.springframework.validation.annotation.Validated）
  · ScheduleLockController.java lock / unlock 两个方法
  · ScheduleGenerateController.java generate / generateMultiple 两个方法
  · ScheduleReplanController.java createLocalReplanPlan 方法
  · ScheduleReportController.java generatePlanReport 方法
  · V5CandidatePositionController.java generate 方法
  · V5RepairSuggestionController.java generate 方法
  · V5SimulationController.java localReplan 方法
- DTO 校验补齐：
  · AutoScheduleRequest.java：semesterId 字段必须同时有 @NotNull(message = "semesterId 不能为空") 和 @Positive(message = "semesterId 必须大于 0")，缺一不可（@Positive 对 null 是通过的，必须显式 @NotNull）；taskIds 加 @Size(max = 5000)
  · ScheduleLockRequest.java：targetType 加 @NotBlank + @Size(max = 20)；planId/planItemId/scheduleId 各加 @Positive；lockReason 加 @Size(max = 255)
  · ScheduleGenerateRequest.java：semesterId @Positive；strategyType @Size(max = 50)；planName @Size(max = 100)
  · MultipleScheduleGenerateRequest.java：semesterId @Positive；strategyTypes @Size(max = 8)
  · V4ScheduleAiAnalysisRequest.java：analysisType @Size(max = 32)
  · V4ScheduleReplanRequest.java：newPlanName @Size(max = 100)；strategyCode @Size(max = 50)
  · V4ScheduleReportGenerateRequest.java：reportType @Size(max = 50)；format @Size(max = 20)
  · V5CandidatePositionGenerateRequest.java：scheduleId/planItemId 各 @Positive；limit @Min(1) @Max(1000)
  · V5LocalReplanRequest.java：newPlanName @Size(max = 100)；candidateLimit @Min(1) @Max(2000)
  · V5RepairSuggestionGenerateRequest.java：candidateLimit @Min(1) @Max(1000)

A3. 裸实体 @RequestBody 换 Form DTO
- 新建 backend/src/main/java/com/paike/scheduler/service/dto/TeacherUnavailableTimeForm.java：
  · 字段：Long teacherId（@NotNull + @Positive）、Long timeSlotId（@NotNull + @Positive）、String reason（@Size(max = 255)）、Integer status（@Min(0) @Max(1)）、String remark（@Size(max = 255)）
  · 不能含 id / deleted / createTime
- 新建 backend/src/main/java/com/paike/scheduler/service/dto/ScheduleRuleWeightBatchForm.java：
  · 顶层字段：List<Item> rules（@NotEmpty + @Valid）
  · 内部 static class Item：Long id（@NotNull + @Positive）、BigDecimal weight（@NotNull + @DecimalMin("0.0")）、Integer enabled（@NotNull + @Min(0) + @Max(1)）、String description（@Size(max = 255)）
- 新建 backend/src/main/java/com/paike/scheduler/service/dto/V5RepairTaskCancelRequest.java：
  · 字段：String reason（@Size(max = 255)）
- 切换 Controller：
  · TeacherUnavailableTimeController.java create / update 两个端点的 @RequestBody 类型从 TeacherUnavailableTime 换成 TeacherUnavailableTimeForm
  · ScheduleRuleWeightController.java batchUpdate 端点从 @RequestBody List<ScheduleRuleWeight> 换成 @Valid @RequestBody ScheduleRuleWeightBatchForm，调用时传 form.getRules()
  · V5RepairTaskController.java cancel 端点从 @RequestBody(required = false) Map<String, Object> body 换成 @Valid @RequestBody(required = false) V5RepairTaskCancelRequest request，保留 required=false 语义
- 切换 Service：
  · TeacherUnavailableTimeService.java create / update 方法签名形参类型换成 TeacherUnavailableTimeForm，内部装配 TeacherUnavailableTime 实体（手动 set 各字段，不能用 BeanUtils.copyProperties 全量拷贝）
  · ScheduleRuleWeightService.java batchUpdate 方法签名换成 List<ScheduleRuleWeightBatchForm.Item>，循环里只 set existing 的 weight / enabled / description / updatedAt，禁止把 form 里的字段直接 setDeleted / setCreateTime

A4. 关键 Service 加 @Transactional
- TeacherUnavailableTimeService.java：create / update / updateStatus 三个方法各加 @Transactional(rollbackFor = Exception.class)
  · create 内 unavailableTimeMapper.insert(entity) 要套 try/catch DuplicateKeyException(org.springframework.dao.DuplicateKeyException)，catch 内抛 new BusinessException(409, teacher.getName() + "老师在" + timeSlot.getTimeLabel() + "已存在禁排时间")
- ScheduleRuleService.java：
  · updateRules 方法加 @Transactional(rollbackFor = Exception.class)
  · resetToDefault 方法也加 @Transactional(rollbackFor = Exception.class)（同样是循环 select/update/insert，性质完全一样，不能漏）
- AutoScheduleBatchService.java：updateBatchResult 方法加 @Transactional(rollbackFor = Exception.class)
- ScheduleRuleWeightService.java：batchUpdate 方法加 @Transactional(rollbackFor = Exception.class)（循环 update，半路抛异常会留下脏数据）

A5. JwtService 显式校验密钥长度
- backend/src/main/java/com/paike/scheduler/auth/JwtService.java
- 在构造器现有的 isBlank / DEFAULT_SECRET 校验之后、Keys.hmacShaKeyFor 调用之前，追加：
  · 取 secret.getBytes(StandardCharsets.UTF_8).length，记为 secretLength
  · 若 secretLength < 32，抛 IllegalStateException("JWT_SECRET 长度不足：当前仅 " + secretLength + " 字节，要求至少 32 字节。请配置新的强密钥后重启服务。")

【验证】
1. 在 D:\paike\backend 执行 mvn compile，编译必须通过；日志输出到 D:\paike\backend-compile.log，并贴出最后 50 行给操作者看
2. 跑 git status --short，确认改动文件集合大致是：
   - M 约 22 个 java 文件（13 个 controller + 6 个 service + 1 个 entity + 10 个 DTO，具体看你实际触碰的数量）
   - M backend/src/main/resources/application.yml
   - ?? backend/src/main/resources/db/v8_unscheduled_task_semester.sql
   - ?? 三个新 DTO 文件（TeacherUnavailableTimeForm / ScheduleRuleWeightBatchForm / V5RepairTaskCancelRequest）
   多动的文件需要解释清楚，不要 restore
3. 跑 git diff --stat 给操作者看
4. 不要 commit，等操作者 review

【输出格式】
按 A1 → A5 顺序，每完成一节简述触碰了哪些文件（只说文件名即可，不要贴 diff）。
全部完成后给一份汇总：
- mvn compile 是否通过
- git diff --stat 完整内容
- 新增的 v8_unscheduled_task_semester.sql 需要操作者在测试库手动跑一次确认，重点核查：
  · unscheduled_task.semester_id 列是否 NOT NULL DEFAULT 0
  · idx_unscheduled_task_semester 索引是否存在
  · 原 NULL 行是否已被回填或清除

参考文档：D:\paike\代码修改建议.md（详细 diff 在批次 A 章节，本提示词中的"4 项 review 补丁"已对里面的部分内容做了升级，以本提示词为准——具体差异是：v8 SQL 增加了清扫孤儿行 + ALTER MODIFY NOT NULL 两步、AutoScheduleRequest.semesterId 必须同时有 @NotNull 和 @Positive、ScheduleRuleService.resetToDefault 也要加 @Transactional、ScheduleRuleWeightService.batchUpdate 也要加 @Transactional）
```

---

## 阶段 B 提示词：本迭代修复（7 项功能正确性问题）

```
你是一位资深 Java + Vue 全栈工程师。请在 D:\paike 这个 Spring Boot 3 + Vue 3 项目里完成 7 项 P1 功能正确性修复。

【前置条件】
- 阶段 A 的所有修改已合并到当前分支
- 启动方式参考：mvn -pl backend compile（请勿尝试启动服务）
- 项目用 PowerShell，所有 shell 命令请用 PS 语法

【任务清单】

B1. 排课循环 N+1 改批量查询
- AutoScheduleService.java:
  · run() 方法在加载 timeSlots 后追加 Map<Integer, List<Long>> slotIdsByDay = ... 按 dayOfWeek 分组
  · checkTeacherDailyLimit / checkClassDailyLimit / hasSameCourseSameDay 方法签名加 slotIdsByDay 参数
  · 方法体内把 getTimeSlotIdsByDay(dayOfWeek) 改成 slotIdsByDay.getOrDefault(dayOfWeek, List.of())
  · 调用点（198/205/214 行）相应传入
  · 删除/废弃旧的 getTimeSlotIdsByDay(int) 方法
- V3ScheduleGenerateService.java:
  · 在生成入口一次性加载 courseMap / classMap（参考 AutoScheduleService.java:122-129 的写法）
  · getCourseType / getClassStudentCount 方法签名加 Map 参数
  · 改造所有调用点（特别是 sortTasks 比较器内部）
- UnscheduledTaskService.fillRelationFields:
  · 进入循环前用 selectBatchIds 批量加载 task / course / teacher / classInfo，转 Map
  · 循环内只走 map.get(id)

B2. 统一错误处理模式
- ScheduleAdjustLogController.java:35-36：删掉 catch (BusinessException) { return 空 Page; } 块，直接让异常抛出
- ScheduleRuleWeightController.java:33-34：同上，删掉 catch + 返回 List.of() 块
- 执行 PowerShell：Select-String -Path 'backend/src/main/java/com/paike/scheduler/controller/*.java' -Pattern 'catch \(BusinessException' 找出其他类似位置，逐个判断：
  · 若是"业务允许的兜底"（如配置缺失返回空数据），保留但加上明确的 log.warn + ElMessage 类似的语义说明
  · 若是"应该让前端看到的失败"，删掉 catch 让异常上抛

B3. ScheduleController.create 加 task null 检查
- 文件：backend/.../controller/ScheduleController.java:99 后面
- selectById 后立即检查 task == null || deleted == 1，是则抛 BusinessException(400, "教学任务不存在或已删除")

B4. 前端列表 fetchData 补 catch + handleSubmit await
- 给以下 4 个文件的 fetchData / fetchReports 函数加 catch 块，**不要**在 catch 内调用 ElMessage.error（拦截器会弹，避免双弹），只做兜底数据清空：
  · frontend/src/views/schedule/ScheduleRuleWeightView.vue:41
  · frontend/src/views/schedule/ScheduleConflictReportView.vue:77
  · frontend/src/views/schedule/ScheduleView.vue:85
  · frontend/src/views/schedule/SchedulePlanView.vue:42
- frontend/src/composables/useCrudForm.ts:121：把 fetchData() 改成 await fetchData()

B5. handleDelete 错误提示去重
- 文件：frontend/src/composables/useCrudForm.ts:127-141
- catch 块内删掉 ElMessage.error(extractMessage(e, '删除失败'))，改为空 catch 或注释说明（"拦截器已弹错误提示"）
- handleDelete 内 fetchData() 也改为 await fetchData()

B6. 路由守卫加 in-flight 锁
- 文件：frontend/src/stores/auth.ts
- fetchCurrentUser 函数改成：内部维护 inflight 变量（模块级闭包），同时多次调用复用同一个 Promise，finally 清空
- router/index.ts:222 处不用动，自动复用 in-flight

B7. timeout 分级
- 修改 frontend/src/api/autoSchedule.ts：runAutoSchedule 等长操作的 axios 调用，第三个参数加 { timeout: 120_000 }
- 同样处理：
  · frontend/src/api/v4ScheduleReportApi.ts 报告生成接口
  · frontend/src/api/v4ScheduleAiApi.ts AI 分析接口（若存在）
  · frontend/src/api/v5SimulationApi.ts 仿真接口
  · frontend/src/api/scheduleGenerate.ts （若存在）
- 找不到对应文件时跳过并在报告中说明，不要新建文件

【验收标准】
1. mvn -pl backend compile 通过
2. 前端：cd frontend; npm run type-check（如果有该脚本）通过；若无脚本，至少 npx tsc --noEmit 通过
3. B1 改动后，肉眼检查所有循环内 selectById 调用应该都消失了
4. 不要碰这些误报项：
   · request.ts 的 blob 处理（已正确）
   · ScheduleScoreController.rescore 的 null 检查（getById 已抛异常）
   · SchedulePlanService.java:570 的 slot.getId() NPE（已有守卫）

【输出要求】
1. 按顺序执行 B1 → B7
2. 每完成一项简述改了哪些文件、关键改动是什么
3. 全部完成后给出 git diff --stat 摘要
4. 不要自动 commit

参考文档：D:\paike\代码修改建议.md（详细 diff 在批次 B 章节）
```

---

## 阶段 C 提示词：下一迭代修复（5 个主题，可拆 PR）

```
你是一位资深 Java + Vue 全栈工程师。请在 D:\paike 项目完成 5 个主题的维护性改造。每个主题独立可提交，建议拆成 5 个 PR。

【前置条件】
- 阶段 A、B 已合并
- 项目用 PowerShell，所有 shell 命令请用 PS 语法
- 改造涉及数据库迁移，每次改 schema 都要在本地测试库验证一次

【主题列表，逐个完成，每个完成后停下来等 review】

C1. 数据库 schema 治理（最大风险，单独 PR）
- 目标：合并 schema.sql + v2-v7 所有迁移脚本到 db/migration/V1__baseline.sql，启用 Flyway 取代 spring.sql.init
- 步骤：
  1. 读取 db/ 目录所有 .sql 文件，整合为一份完整 V1__baseline.sql
  2. 所有 DELIMITER + 存储过程写法改成 SET @ddl + PREPARE + EXECUTE 动态 SQL（参考代码修改建议.md C1.2 的模板）
  3. 创建 db/migration/V2__teaching_task_index.sql，给 teaching_task 表的 course_id / teacher_id / class_id / semester_id 加索引
  4. 修改 application.yml：spring.flyway.enabled=true、baseline-on-migrate=true、baseline-version=0；同时 spring.sql.init.mode=never
  5. AutoScheduleBatch.java:39 补 @TableField("create_time") 和 updateTime 字段；V2 脚本里给 auto_schedule_batch 加 update_time 列
  6. 确认 pom.xml 有 flyway-core 依赖（spring-boot-starter 通常已包含，否则手动加）
- 注意：
  · baseline-on-migrate 让已有线上库被视为 V1 已完成，不会重跑
  · 本地测试时建议用全新 schema 验证 V1 + V2 能跑通
  · 改完后 mvn -pl backend compile 通过即可，不启动服务

C2. 安全加固（独立 PR）
- CorsConfig.java:32：allowedHeaders 从 List.of("*") 改成具体列表：Content-Type / Authorization / X-CSRF-Token / X-Requested-With / Accept
- SecurityConfig.java：在现有 BCryptPasswordEncoder bean 之外，新增 SecurityFilterChain bean，配置：
  · 禁用 Spring Security 默认 CSRF（现有 AuthInterceptor 自己实现）
  · 禁用 formLogin / httpBasic
  · sessionCreationPolicy = STATELESS
  · authorizeHttpRequests anyRequest permitAll（鉴权交给 AuthInterceptor）
  · headers 启用：contentTypeOptions / frameOptions DENY / HSTS / referrerPolicy SAME_ORIGIN
  · 确认 pom.xml 有 spring-boot-starter-security
- LoginRequest.java：username 加 @Size(max=64)，password 加 @Size(max=128)
- Result.java：类上加 @JsonInclude(JsonInclude.Include.NON_NULL)
- GlobalExceptionHandler.java:25：BusinessException 处理用 Map<Integer, HttpStatus> 显式映射 401/403/404/409/429，其他统一 400；不要再用 HttpStatus.resolve(code)
- RBAC（如果工时允许，否则单独排期）：
  · 新建 V3__rbac.sql：sys_role + sys_user_role 两张表，预置 ADMIN / SCHEDULER / READONLY 三角色
  · 新建 @RequirePermission 自定义注解（com.paike.scheduler.common.annotation）
  · 新建 RequirePermissionAspect（com.paike.scheduler.common.aspect），@Before 拦截，从 AuthUserContext 读用户角色
  · 修改 AuthInterceptor，解析 JWT 后从 sys_user_role 查角色塞进 user.roles
  · 给 AutoScheduleBatchController.run、SchedulePlanController 的 delete/apply/rollback 端点加 @RequirePermission(roles = {"ADMIN", "SCHEDULER"})

C3. 空指针 / 死代码清理（独立 PR）
- ScheduleConflictReportService.java:658 getDayOfWeek：slot 为 null 时返回 -1（而不是 null），调用方在分组前 filter 掉 dayOfWeek == null 或 == -1 的项
- V4ScheduleAdjustmentService.java:361-363：在调用 getPeriodNo() * 2 - 1 之前显式校验 periodNo != null，是则抛 BusinessException
- CourseType / RoomType / ScheduleSourceType 的 fromCode：把 return null 改成 throw new IllegalArgumentException(...)
  · 注意：先 grep 调用点（Select-String -Path 'backend/**/*.java' -Pattern '\.fromCode\('），AutoScheduleService 等地方可能依赖 null 兜底回退到 NORMAL，那部分逻辑保留
- 删除死代码：
  · V5RepairExplanationService.java:443-447 的 __keepObjects() 方法
  · frontend/src/stores/auth.ts 里的 token ref、setToken、clearToken（保留 userInfo / isLoggedIn / login / logout / fetchCurrentUser）
  · 同步更新 router/index.ts:226 移除 authStore.clearToken() 调用

C4. 前端死代码 / 一致性清理（独立 PR）
- 新建 frontend/src/utils/asyncHelpers.ts，定义并 export fallback<T> 函数
- 删除以下 5 处文件里各自的 fallback 函数定义，改为 import { fallback } from '../../utils/asyncHelpers'：
  · views/teachingTask/TeachingTaskView.vue:63
  · views/schedule/ScheduleView.vue:78
  · views/schedule/ScheduleStatisticsView.vue:35
  · views/schedule/ScheduleRuleWeightView.vue:13
  · views/schedule/SchedulePlanView.vue:35
- utils/request.ts:5-9 删掉本地 ApiResponse 接口定义，改为 import type { ApiResponse } from '../api/types'
- 给 api/types.ts 的 ApiResponse 加默认泛型参数 <T = unknown>
- api/index.ts 补 re-export（用 export * as v4Analysis from '...' 命名空间形式，避免同名冲突）：
  · v4ScheduleAnalysisApi / v4ScheduleAdjustmentApi / v4ScheduleLockApi / v4ScheduleReplanApi / v4ScheduleReportApi / v4ScheduleAiApi / v5SimulationApi / scheduleConflictReport / scheduleScoreReport
- ScheduleView.vue 的 10 处内联 style 抽 scoped CSS class（其他文件先不动，作为示范）

C5. 硬编码常量配置化（独立 PR）
- 新建 backend/.../config/ScheduleThresholdProperties.java，用 @ConfigurationProperties(prefix = "app.schedule.thresholds")，含字段：
  · teacherOverloadMedium (默认 18)
  · teacherOverloadHigh (默认 22)
  · classDailyOverloadMedium (默认 8)
  · classDailyOverloadHigh (默认 10)
  · roomLowUtilization (默认 BigDecimal 30)
  · roomHighUtilization (默认 BigDecimal 85)
  · totalAvailablePeriods (默认 20)
  · afternoonStartPeriod (默认 5)
- application.yml 追加 app.schedule.thresholds.* 节点（值与默认一致）
- 改造调用点：把以下静态常量改成注入 ScheduleThresholdProperties + getter：
  · V4ScheduleRiskService.java:49-54（6 个常量）
  · V4ScheduleChartService.java:42（TOTAL_AVAILABLE_PERIODS）
  · V4ScheduleAnalysisService.java:90, 96, 106, 108（4 处阈值）
  · ScheduleScoreService.java:315（下午起始节次）

【验收标准】
1. 每个主题完成后 mvn -pl backend compile 通过
2. C1 改完，本地新库执行 Flyway 能跑通 V1 + V2，无 SQL 异常
3. C2 改完，老的 JWT 接口仍能登录，响应头能看到 X-Content-Type-Options / X-Frame-Options 等
4. C4 改完，前端 npx tsc --noEmit 通过
5. 不要碰这些误报项：
   · ScheduleRuleView.vue 的 rules 变量（无冲突）
   · errors.ts isCancel（有引用）
   · SimulationPlanDetailView.vue 行数（662 行不算大）

【输出要求】
1. 严格按 C1 → C5 顺序，每个主题完成后停下来等 review，不要连贯执行
2. 每个主题完成时输出：改了哪些文件、关键 diff 概述、下一步建议
3. 不要自动 commit

参考文档：D:\paike\代码修改建议.md（详细 diff 在批次 C 章节）
```

---

## 阶段 D 提示词：架构级改造（4 项，不要一次性塞给 agent）

> 每项都是独立立项，建议分别开 ticket，每次只给 agent 一项。下面列出每项各自的提示词。

### D1 提示词：去掉 applyPlan 复制粘贴（小风险，可先做）

```
你是一位资深 Java 工程师。在 D:\paike 项目修复一个代码重复问题。

【任务】
SchedulePlanService.java:267-356 (applyPlan) 与 :373-446 (applyPlanInternal) 几乎是逐行复制粘贴，仅入口校验不同。

改造目标：
1. applyPlan 保留方法签名和入口校验（line 267-287 检查 plan 状态的所有 throw）
2. applyPlan 末尾改成 return applyPlanInternal(plan);
3. 删除 applyPlan 原本的 line 288-355 实现（已经在 applyPlanInternal 里有）
4. applyPlanInternal 改成 private（如果 applySimulationPlan 还在调用它就保留可见性，否则降为 private）
5. 不要修改 applyPlanInternal 内部逻辑

【验收】
- mvn -pl backend compile 通过
- 静态扫描确认 applyPlan 的代码量缩减 60+ 行
- 行为不变：所有原本经过 applyPlan 的状态校验和入参依然存在

【边界】
- 不要重构 applySimulationPlan
- 不要修改其它 SchedulePlanService 方法
- 不要碰排课主算法
```

### D2 提示词：评分体系收口（需先和产品对齐，agent 不能独立完成）

```
（暂不交给 agent，需先做产品决策：ScheduleScoreReportService 和 ScheduleScoreService 哪个对外可见。）
决策完成后再分两步：
  D2.1 改名 / 注释：明确各自适用场景，文档化
  D2.2 写一次性脚本：把所有 APPLIED plan 的 total_score 重算为对外口径
每步独立交给 agent，提示词单独编写。
```

### D3 提示词：前端错误边界 + BaseLayout 修复 + SimulationPlanDetailView 拆分（拆 3 个 PR）

```
你是一位资深 Vue 3 + TypeScript 工程师。在 D:\paike\frontend 完成 3 个前端改造，每项单独成 PR。

【任务 1：错误边界】
1. 新建 frontend/src/components/ErrorBoundary.vue，使用 onErrorCaptured 钩子捕获子组件异常
   · 错误状态显示友好兜底页面（含返回首页按钮）
   · console.error 输出 [ErrorBoundary] 前缀，便于调试
   · onErrorCaptured 返回 false 阻止冒泡
2. 修改 frontend/src/App.vue，用 <ErrorBoundary> 包裹 <RouterView>
3. 验收：手动在 RouterView 内某个组件 throw new Error 后，浏览器不应白屏

【任务 2：BaseLayout 菜单高亮】
1. 文件：frontend/src/layout/BaseLayout.vue:62
2. el-menu :default-active 改为 :active，绑定 computed(() => route.path)
3. 用 useRoute() 替代 router.currentRoute.value 直接访问
4. 验收：浏览器切换路由时，菜单高亮项应当同步变化

【任务 3：SimulationPlanDetailView 拆分】
1. 文件：frontend/src/views/v5/SimulationPlanDetailView.vue（当前 662 行）
2. 按模块拆为：
   · 保留 SimulationPlanDetailView.vue 作为容器（路由 + 顶层数据加载）
   · 新建 frontend/src/components/v5/SimulationOverviewCard.vue（评分/状态/操作按钮）
   · 新建 frontend/src/components/v5/SimulationConflictTable.vue（冲突明细列表）
   · 新建 frontend/src/components/v5/SimulationItemTable.vue（排课明细）
   · 新建 frontend/src/components/v5/SimulationActionPanel.vue（接受/放弃/调整按钮）
3. 通过 props / emits 通信，禁止子组件直接调 API
4. 验收：拆完每个 .vue 文件不超过 250 行；功能行为完全不变

【边界】
- 不要修改路由配置
- 不要修改任何 backend 代码
- npx tsc --noEmit 必须通过
```

### D4 提示词：软删除补齐（数据风险高，谨慎）

```
你是一位资深 Java + MySQL 工程师。在 D:\paike 项目给 3 张关键业务表补软删除。

【风险提示】
本任务会改 production 数据表结构 + 修改物理删除调用点为软删除。请：
- 所有 SQL 写在 db/migration/V4__add_soft_delete.sql（如果 Flyway 已启用，否则放 db/v8_*.sql）
- SQL 用动态 PREPARE + EXECUTE 模式，幂等可重跑
- 改完后必须在本地测试库跑一遍验证

【任务】
1. 表结构：schedule_locked_item / schedule_adjust_log / schedule_unassigned_task 加 deleted TINYINT NOT NULL DEFAULT 0 列
2. Entity 补 @TableLogic：
   · ScheduleLockedItem.java 加 @TableLogic private Integer deleted
   · ScheduleAdjustLog.java 同上
   · ScheduleUnassignedTask.java 同上
3. 调用点回归：执行 PowerShell 命令
   Select-String -Path 'backend/src/main/java/**/*.java' -Pattern 'scheduleLockedItemMapper\.delete|scheduleAdjustLogMapper\.delete|scheduleUnassignedTaskMapper\.delete'
   每个命中点逐一确认：从物理删除变成软删除后业务语义是否仍然正确
4. 输出一份回归列表：哪些调用点改成了软删除、哪些保留物理删除（如果有不该被软删除的场景）

【边界】
- 不要给其他无 @TableLogic 的 entity 一次性补齐，本任务只动这 3 张
- 不要修改 MyBatis Plus 全局 logic-delete-field 配置
- 不要启动服务测试，告知操作者在测试环境验证

【验收】
1. mvn -pl backend compile 通过
2. V4 / v8 SQL 在本地空库执行成功
3. Entity 改动 + 调用点回归列表交付完整
```

---

## 通用尾注

每次给 GPT-5.5 任务时，建议**额外附上**以下信息（作为系统提示或附加上下文）：

```
【项目约束】
- 工作目录：D:\paike
- 操作系统：Windows + PowerShell 7
- 严禁调用 bash tool，所有 shell 命令必须用 PowerShell 语法
- 严禁尝试启动 Spring Boot 服务（mvn spring-boot:run）；如需联调，告知操作者人工启动
- 用 codegraph MCP 做符号检索优于 grep
- 改动文件后不要立即查询 codegraph，watcher 有约 500ms 延迟

【git 约束】
- 严禁执行任何破坏性 git 命令（restore / reset / checkout -- / stash drop / clean -f / rm）；想 revert 之前必须先报告操作者
- 不要 git push 或 git commit，最终由操作者审核
- 不要 git add -A，明确指定文件
- 改动较大时建议保留 git diff 摘要

【上下文文档】
- 代码修改建议：D:\paike\代码修改建议.md
- 验证报告（含误报项）：D:\paike\探查报告验证报告.md
- 原探查报告（不要按此整改）：D:\paike\深度代码探查报告.md

【完成后】
- 给出 git status / git diff --stat 摘要
- 报告未完成项 / 遇到的边界情况 / 需要操作者决策的事项
- 不要自我总结"做得很好"，只列事实
```

执行完一个阶段后，把 agent 的 PR 给 Claude（即我）做 review，确认无误再合并到主分支，然后进入下一阶段。
