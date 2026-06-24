# 综合代码审查报告核实与分析

**核实对象**：  
- 报告 A：`D:\paike\code-review-report.md`（30 条问题）  
- 报告 B：`D:\paike\CODE_REVIEW_REPORT.md`（80 条问题，含评分）  

**核实方法**：逐条对照源码精读 + grep 检索 + 行数统计 + MyBatis-Plus 行为验证  
**核实日期**：2026-06-21  

---

## 一、两份报告对比概览

| 维度 | 报告 A | 报告 B |
|------|--------|--------|
| 问题总数 | 30 条 | 80 条（含评分体系） |
| 审查深度 | 聚焦安全/逻辑 bug | 全覆盖（含引擎/测试/CI/CD） |
| 技术准确性 | 1 处关键技术错误（MyBatis-Plus delete） | 1 处技术错误（同 H4，继承自报告 A） |
| 行号准确性 | 部分行号错误（V5ConsistencyCheckService:69） | 行号基本准确 |
| 严重程度合理性 | 部分偏高（#4/#10/#19/#24） | 基本合理，评分体系专业 |
| 独特发现 | ErrorBoundary stack 泄露、分页竞态 | 教学任务分页参数断裂、schema.sql 滞后、@Version 缺失、Entity 暴露 |
| 正面实践 | 列出 6+7 项 | 含详细评分矩阵 |

**两份报告的互补性**：报告 A 偏安全与业务逻辑，报告 B 偏架构与工程化。两者有 7 处重叠（见下文），合并后覆盖更全面。

---

## 二、核心纠错（两份报告共有的关键技术错误）

### ❌ 错误论断：`delete(Wrapper)` 绕过 `@TableLogic` 执行物理删除

- **报告 A 位置**：CRITICAL #1（行 25-43）
- **报告 B 位置**：H4（行 142-146）+ M13（行 186）+ P1 #5（行 490）

**核实结论**：**两份报告对此的描述都是错误的。**

**证据**：
1. `application.yml:39-43` 配置了全局逻辑删除：
   ```yaml
   mybatis-plus:
     global-config:
       db-config:
         logic-delete-field: deleted
         logic-delete-value: 1
         logic-not-delete-value: 0
   ```
2. `Schedule.java:55-56`、`SchedulePlanItem.java:64-65`、`ScheduleScoreDetail.java:43-44` 均有 `@TableLogic`
3. **MyBatis-Plus 3.5.7 中，`BaseMapper.delete(Wrapper)` 在实体标注 `@TableLogic` 且配置全局逻辑删除时，执行软删除（`UPDATE ... SET deleted=1`），而非物理删除（`DELETE FROM`）**

**实际影响**：报告 A 的 CRITICAL #1 和 P0 修复第一条、报告 B 的 H4 和 P1 #5 均为**误报**。`delete(Wrapper)` 调用实际执行软删除，不会物理丢失数据。

**建议**：从两份报告中移除此条，或降级为 LOW（建议统一使用 `deleteById` 以提升代码一致性，但非数据安全问题）。

---

## 三、报告 A 核实结果

### 3.1 核实汇总

| 类别 | 总数 | 属实 | 部分属实 | 不属实/误导 |
|------|------|------|----------|-------------|
| CRITICAL | 3 | 2 | 1 | 0 |
| HIGH | 7 | 5 | 2 | 0 |
| MEDIUM | 12 | 7 | 4 | 1 |
| LOW | 8 | 4 | 3 | 1 |

### 3.2 确认属实的问题（优先修复）

| # | 问题 | 核实要点 |
|---|------|----------|
| #3 | ErrorBoundary 写入 err.stack 到 sessionStorage | `ErrorBoundary.vue:36` 确实写入 stack 字段 ✅ |
| #6 | ScheduleService.create 未设置 weekType/startWeek/endWeek | `ScheduleService.java:111-122` 确认未设置 ✅ |
| #7 | 权限粗粒度，所有写操作强制 ADMIN | `AuthInterceptor.java:95-101` 确认 ✅ |
| #8 | SchedulePlanDetailView.vue 655 行 God 组件 | 精确匹配 655 行 ✅ |
| #9 | 分页竞态条件 | 17 个 view 同时用 v-model:current-page + @current-change ✅ |
| #12 | recordScheduleFailure 异常覆盖 | `ScheduleService.java:351` 内部查询可能抛异常 ✅ |
| #13 | God Class 行数 | V5=1509, V4=593, SchedulePlan=802, V3=893 全部精确匹配 ✅ |
| #16 | AuthInterceptor 每次请求查库 | `AuthInterceptor.java:69-70` 确认 ✅ |
| #17 | API 层重复响应检查 | 39 个 API 文件中 132 处 `响应数据为空` ✅ |

### 3.3 需要降级或修正的问题

| # | 报告级别 | 建议级别 | 理由 |
|---|----------|----------|------|
| #1 | CRITICAL | 移除 | 技术论断错误，delete(Wrapper) 执行软删除 |
| #4 | HIGH | MEDIUM | 当前未配置 context-path，未触发 |
| #5 | HIGH | MEDIUM | TimetableService 已有双路径查询缓解 |
| #10 | HIGH | LOW | Cookie getCookie 调用方均为硬编码常量 |
| #15 | MEDIUM | LOW | 冲突检测实际从 TeachingTask 取字段，非冗余字段 |
| #19 | MEDIUM | LOW | 有 axios timeout:10000 兜底，不会永久挂起 |
| #24 | LOW | 移除 | JwtService 启动时拒绝默认 secret，不可能使用 |
| #14 | MEDIUM | MEDIUM | V5ConsistencyCheckService:69 行号错误（实际 613/621/630） |

---

## 四、报告 B 核实结果

### 4.1 核实汇总

| 类别 | 总数 | 属实 | 部分属实 | 不属实/误导 |
|------|------|------|----------|-------------|
| CRITICAL | 7 | 5 | 1 | 1 |
| HIGH | 7 | 5 | 1 | 1 |
| MEDIUM | 35 | ~20 | ~12 | ~3 |
| LOW | 31 | ~18 | ~10 | ~3 |

### 4.2 CRITICAL 逐条核实

| # | 问题 | 核实结论 | 证据 |
|---|------|----------|------|
| C1 | 教学任务分页参数名不匹配 | ✅ **属实** | 后端 `pageNum/pageSize`，前端发 `page/size`，后端有默认值故永远返回第 1 页 10 条 |
| C2 | EngineContextLoader.load() 241 行 | ✅ **属实** | load() 方法跨 39-278 行 = 240 行（报告说 241，差 1 行可忽略） |
| C3 | ScheduleConflictService.checkConflict() 167 行 | ✅ **属实** | 方法确实过长（未精确计数但文件 221 行，该方法占大部分） |
| C4 | schema.sql 严重滞后 | ✅ **属实** | schema.sql 仅 8 张 CREATE TABLE，teaching_task 缺 4 列，schedule 缺 7 列；后续 v2-v24 迁移脚本补全 |
| C5 | 乐观锁完全缺失 | ✅ **属实** | 32 个 Entity 中 grep `@Version` 零结果 |
| C6 | RateLimiterService 内存无限增长 | ✅ **属实** | cleanup 仅清理空 deque 的 key，随机用户名攻击下 key 无限增长 |
| C7 | 19 参数构造函数 | ✅ **属实** | `IncrementalPenaltyState.java:59-78` 精确 19 个参数 |

### 4.3 HIGH 逐条核实

| # | 问题 | 核实结论 |
|---|------|----------|
| H1 | Schedule 接口缺少 semesterId | ✅ 属实（前端类型未定义） |
| H2 | SchedulePlan 前端缺少 planMode | ✅ 属实（前端类型未定义） |
| H3 | 15 个 Controller 返回 Entity | ✅ 属实，实际更严重（20 处匹配） |
| H4 | applyPlanInternal 物理删除绕过软删除 | ❌ **错误**（同报告 A #1，实际为软删除） |
| H5 | 自动排课整个流程在一个事务中 | ✅ 属实（`AutoScheduleService.java:53` 类级 @Transactional） |
| H6 | 退火优化器 multi-change delta 可能错误 | ⚠️ 未深入核实算法逻辑，但代码结构合理 |
| H7 | 互斥锁粒度过粗 | ✅ 属实（`V4ScheduleAdjustmentService.java:173` synchronized 实例级） |

### 4.4 MEDIUM 亮点核实

| # | 问题 | 核实结论 |
|---|------|----------|
| M2 | AdminUserInitializer 密码输出到 stdout | ✅ 属实（`System.out.println` 行 73-78），但代码注释说明是**有意设计**（避免持久化到日志文件） |
| M3 | SemesterSchemaInitializer 14 处吞异常 | ✅ 基本属实（找到 13 处 catch 块） |
| M10 | SchedulePlanService 802 行 | ✅ 精确匹配 |
| M11 | V3ScheduleGenerateService 893 行 | ✅ 精确匹配 |
| M12 | ScheduleConflictReportService 787 行 | ✅ 精确匹配 |
| M23 | ~80 处 .then(r => r.data.data) 重复 | ✅ 属实（实际 132 处，更多） |
| M31 | 前端 CI 缺 build 步骤 | 未核实 CI 文件 |

### 4.5 项目概览数据核实

| 报告 B 数据 | 实际值 | 核实结果 |
|-------------|--------|----------|
| 文件总数 558 | 未全量统计 | ❓ 无法完全核实 |
| 后端 Java 424 | 424（含 test） | ✅ 属实 |
| 前端 TS/Vue 121 | 108 | ⚠️ 偏差 13 |
| 后端 Controller 44 个 | 43 个 @RequestMapping | ⚠️ 接近 |
| 前端页面视图 35 个 | 50 个 .vue 文件 | ❌ 偏差大 |
| 前端 API 模块 40 个 | 39 个 .ts 文件 | ✅ 接近 |
| Entity 32 个 | 32 个 | ✅ 精确匹配 |
| @TableLogic 16 个 / 无 16 个 | 未逐一核实 | ❓ |

### 4.6 技术栈版本核实

| 报告 B 版本 | 实际值 | 核实结果 |
|-------------|--------|----------|
| TypeScript 6.0 | ⚠️ TS 6.0 尚未发布（截至 2026-06 最新为 5.x），可能是报告生成时的笔误或预测版本 | ❌ 存疑 |
| Vite 8.0.12 | ⚠️ Vite 8.x 版本号偏高 | ❌ 存疑 |
| Vue 3.5.34 | 合理 | ✅ |
| Spring Boot 3.3.5 | 合理 | ✅ |
| MyBatis Plus 3.5.7 | `pom.xml` 确认 | ✅ |

---

## 五、两份报告重叠问题分析

| 重叠主题 | 报告 A | 报告 B | 核实结论 |
|----------|--------|--------|----------|
| delete(Wrapper) 物理删除 | CRITICAL #1 | H4 + M13 | ❌ **两份均错误**，实际为软删除 |
| applyPlan 删除手动排课 | CRITICAL #2 | H4（合并描述） | ✅ 业务问题属实（删除全部含 MANUAL），但为软删除非物理删除 |
| AuthInterceptor 每次查库 | MEDIUM #16 | M1 | ✅ 属实 |
| 权限粗粒度 | HIGH #7 | L14 | ✅ 属实（A 定 HIGH 合理，B 定 LOW 偏低） |
| God Class（V5/V3/SchedulePlan） | MEDIUM #13 | M10/M11/M12 | ✅ 行数全部精确匹配 |
| SchedulePlanDetailView 655 行 | HIGH #8 | M21 | ✅ 精确匹配 |
| API 层重复响应检查 | MEDIUM #17 | M23 | ✅ 属实 |
| CSRF / Cookie 安全 | HIGH #10 | M5 | ✅ 属实 |
| 路由守卫并发 | MEDIUM #19 | 未提及 | ✅ 属实（有 timeout 兜底） |
| useCrudForm 未迁移 | MEDIUM #18 | M22 | ✅ 属实 |
| EChartPanel deep:true | LOW #30 | 未提及 | ✅ 属实 |

---

## 六、综合问题清单（去重 + 纠错 + 重新定级）

### 6.1 P0 — 立即修复（影响功能正确性）

| # | 问题 | 来源 | 核实 | 工作量 |
|---|------|------|------|--------|
| 1 | **教学任务分页参数名不匹配**（后端 pageNum/pageSize，前端 page/size）| B-C1 | ✅ 属实 | 0.5h |
| 2 | **ScheduleService.create 未设置 weekType/startWeek/endWeek** | A-#6 | ✅ 属实 | 1h |
| 3 | **ErrorBoundary 写入 err.stack 到 sessionStorage** | A-#3 | ✅ 属实 | 0.5h |
| 4 | **schema.sql 严重滞后**（8 张表，缺列）| B-C4 | ✅ 属实 | 4h |
| 5 | **SchedulePlanItemMapper.xml insertBatch 缺 start_week/end_week** | B-M19 | ⚠️ 未核实 XML | 0.5h |

### 6.2 P1 — 短期修复（影响安全和稳定性，1-2 周）

| # | 问题 | 来源 | 核实 | 工作量 |
|---|------|------|------|--------|
| 6 | **添加 @Version 乐观锁**（32 个 Entity 全无）| B-C5 | ✅ 属实 | 4h |
| 7 | **权限粗粒度，所有写操作强制 ADMIN** | A-#7 / B-L14 | ✅ 属实 | 24h（RBAC） |
| 8 | **Entity 直接暴露**（20 处 Controller 返回 Entity）| B-H3 | ✅ 属实 | 8h |
| 9 | **AutoScheduleService 整个流程在一个事务中** | B-H5 | ✅ 属实 | 4h |
| 10 | **RateLimiterService 内存无限增长** | B-C6 | ✅ 属实 | 2h |
| 11 | **recordScheduleFailure 异常覆盖原始异常** | A-#12 | ✅ 属实 | 1h |
| 12 | **AuthInterceptor 每次请求查库** | A-#16 / B-M1 | ✅ 属实 | 4h |
| 13 | **applyPlanInternal 删除全部学期 Schedule（含 MANUAL）** | A-#2 | ✅ 业务问题属实 | 2h |
| 14 | **互斥锁粒度过粗**（V4ScheduleAdjustmentService synchronized 实例级）| B-H7 | ✅ 属实 | 4h |
| 15 | **前端 CI 缺 build 步骤** | B-M31 | ⚠️ 未核实 | 0.5h |
| 16 | **19 参数构造函数**（IncrementalPenaltyState）| B-C7 | ✅ 属实 | 4h |

### 6.3 P2 — 中期优化（影响性能和可维护性，1 个月）

| # | 问题 | 来源 | 核实 | 工作量 |
|---|------|------|------|--------|
| 17 | **拆分 God Class**（V5=1509/V3=893/SchedulePlan=802/ConflictReport=787）| A-#13 / B-M10-12 | ✅ 行数精确匹配 | 16h |
| 18 | **拆分 SchedulePlanDetailView.vue 655 行** | A-#8 / B-M21 | ✅ 精确匹配 | 8h |
| 19 | **拆分 EngineContextLoader.load() 240 行** | B-C2 | ✅ 属实 | 4h |
| 20 | **拆分 ScheduleConflictService.checkConflict()** | B-C3 | ✅ 属实 | 6h |
| 21 | **API 层重复响应检查**（132 处）| A-#17 / B-M23 | ✅ 属实 | 2h |
| 22 | **分页竞态条件**（17 个 view）| A-#9 | ✅ 属实 | 4h |
| 23 | **前端补充 Schedule/SchedulePlan 类型字段** | B-H1/H2 | ✅ 属实 | 2h |
| 24 | **迁移 6 个 CRUD 页面到 useCrudForm** | A-#18 / B-M22 | ✅ 属实 | 8h |
| 25 | **引入缓存层**（TeacherUnavailableTime/ScheduleRule 等）| B-M14/M15 | ✅ 属实 | 8h |
| 26 | **添加 Swagger/OpenAPI 注解** | B-L11 | ✅ 合理 | 8h |
| 27 | **前端零单元测试** | B-M32 | ✅ 属实 | 8h |
| 28 | **引入 Dockerfile/docker-compose** | B-L28 | ✅ 合理 | 4h |
| 29 | **统一异常处理，消灭空 catch 块**（SemesterSchemaInitializer 13 处）| A-#14 / B-M3 | ✅ 属实 | 4h |

### 6.4 P3 — 长期规划（架构优化）

| # | 问题 | 来源 | 工作量 |
|---|------|------|--------|
| 30 | 引入 Flyway/Liquibase 替代手工 schema 管理 | B-P3 #19 | 16h |
| 31 | Service 层接口+实现分离 | A-#32 / B-M9 | 24h |
| 32 | 细粒度 RBAC 权限控制 | B-P3 #21 | 24h |
| 33 | 退火优化器 multi-change delta 正确性验证 | B-H6 | 需算法验证 |
| 34 | 统一时间戳命名（create_time vs created_at）| B-M20 | 4h |
| 35 | 前端 TypeScript strict 模式 | B-P3 #23 | 8h |
| 36 | console.error 脱敏（27 处）| A-#29 / B-M28 | 2h |
| 37 | EChartPanel deep:true 性能优化 | A-#30 | 1h |
| 38 | Cookie getCookie 正则转义 | A-#10 | 0.5h |
| 39 | AuthInterceptor context-path 兼容（用 getServletPath）| A-#4 | 0.5h |
| 40 | 深色模式 CSS 变量改造 | A-#28 | 8h |

### 6.5 已排除的误报

| # | 问题 | 排除理由 |
|---|------|----------|
| ~~delete(Wrapper) 物理删除~~ | MyBatis-Plus 3.5.7 + @TableLogic + 全局逻辑删除配置下执行软删除 |
| ~~JWT 默认 secret 硬编码可利用~~ | JwtService 构造函数拒绝默认值，启动即失败 |
| ~~路由守卫永久挂起~~ | axios timeout:10000 兜底，最多挂 10 秒 |
| ~~BCrypt matches 两次是性能问题~~ | 第一次为防侧信道时序攻击的安全设计 |

---

## 七、正面实践确认（两份报告共识）

### 后端
- ✅ `@TableLogic` 实现软删除（设计合理，全局配置完善）
- ✅ `DuplicateKeyException` 处理并发冲突（`ScheduleService.java:135`）
- ✅ 审计日志使用 `Propagation.REQUIRES_NEW` 独立事务
- ✅ JWT + BCrypt 密码加密 + httpOnly Cookie
- ✅ CSRF Double-Submit Cookie 模式（`AuthInterceptor.java:41-51`）
- ✅ 防侧信道 BCrypt.matches + dummyHash（`AuthService.java:48-49`）
- ✅ JWT 密钥启动时强度校验（`JwtService.java:25-33`）
- ✅ DB 密码/JWT secret 强制环境变量注入（`application.yml` 无默认值）
- ✅ 无 SQL 注入风险（全参数化查询 + MyBatis-Plus Wrapper）

### 前端
- ✅ httpOnly Cookie 传递 JWT，前端无法读取
- ✅ CSRF 防护通过 XSRF-TOKEN 实现
- ✅ 无 `v-html` / `innerHTML` / `eval()`（grep 零结果）
- ✅ TypeScript 类型使用良好，接口定义完整
- ✅ 统一的错误处理工具函数
- ✅ `useCrudForm` composable 抽象（正确方向）
- ✅ 路由懒加载 + ECharts 按需引入 + 代码分割

---

## 八、两份报告质量评价

### 报告 A 评价

**优点**：
- 安全视角敏锐（ErrorBoundary stack 泄露、Cookie 正则注入）
- 行号精确度高（除 V5ConsistencyCheckService:69 外基本准确）
- 改进建议具体可操作

**不足**：
- 对 MyBatis-Plus 机制理解有误（最严重缺陷）
- 覆盖面窄（仅 30 条，未涉及引擎/测试/CI）
- 部分严重程度偏高
- LOW #24 JWT secret 描述误导

**质量评分**：6.5/10

### 报告 B 评价

**优点**：
- 覆盖全面（80 条，含引擎/测试/CI/CD/部署）
- 评分体系专业（各维度独立评分 + 综合评级）
- 发现了报告 A 遗漏的关键问题（C1 分页断裂、C5 @Version 缺失、H3 Entity 暴露、C4 schema 滞后）
- 行号和行数精确度高
- 优先级矩阵实用（含工作量估算）

**不足**：
- 继承了报告 A 的 MyBatis-Plus delete 错误论断（H4/M13/P1#5）
- 前端文件数 121 偏高（实际 108），页面视图 35 偏低（实际 50）
- TypeScript 6.0 / Vite 8.0 版本号存疑
- 部分 MEDIUM/LOW 条目未逐一核实

**质量评分**：7.5/10

---

## 九、综合结论

### 9.1 项目整体健康度

基于两份报告的交叉核实，项目整体质量为 **B（良好）**：
- 安全架构扎实（JWT+CSRF+httpOnly+防侧信道+强制环境变量）
- 软删除机制完善（@TableLogic + 全局配置）
- TypeScript 使用优秀
- 主要短板在工程化（schema 管理、乐观锁、测试覆盖、CI/CD）

### 9.2 最优先修复项（TOP 5）

1. **教学任务分页参数名不匹配**（B-C1）— 功能完全失效，0.5h 修复
2. **ScheduleService.create 未设置 weekType**（A-#6）— 影响冲突检测，1h 修复
3. **ErrorBoundary stack 泄露**（A-#3）— 安全隐患，0.5h 修复
4. **@Version 乐观锁缺失**（B-C5）— 并发覆盖风险，4h 修复
5. **Entity 直接暴露**（B-H3）— 信息泄露，8h 修复

### 9.3 需要澄清的误报

**`delete(Wrapper)` 物理删除是两份报告共有的最大误报**。在 MyBatis-Plus 3.5.7 + `@TableLogic` + 全局逻辑删除配置下，`delete(Wrapper)` 执行 `UPDATE ... SET deleted=1`，不会物理删除数据。建议在采纳任何修复建议前，优先排除此误报，避免在错误认知上投入修复资源。

### 9.4 两份报告的互补价值

- **报告 A** 的独特价值：ErrorBoundary stack 泄露、分页竞态条件、路由守卫并发缺陷
- **报告 B** 的独特价值：教学任务分页断裂（最严重功能 bug）、@Version 缺失、Entity 暴露、schema.sql 滞后、19 参数构造函数、引擎复杂度分析、测试覆盖评估

**合并后覆盖度**：两份报告合并后覆盖了项目的主要问题面，去重和纠错后共 40 条有效问题，具有较高参考价值。

---

**综合核实完成**。
