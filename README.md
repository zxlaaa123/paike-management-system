# 高校排课管理系统

基于 `Spring Boot 3 + Vue 3` 的高校排课管理系统。

当前仓库已经完成 `V1`、`V2` 两个阶段的主线功能，以及 `V3` 阶段 1-6 的开发。覆盖基础数据维护、教学任务管理、手动排课、自动排课、未排任务追踪、冲突报告、课表评分与课表导出、学期管理、多策略排课计划、计划对比与应用回滚等核心流程。

## 项目概览

本项目面向高校排课业务，目标不是做学术型最优解求解器，而是先落地一套可运行、可维护、可演示、可扩展的排课管理系统。

系统当前支持的核心闭环：

```text
基础数据维护
-> 教学任务配置
-> 手动排课 / 自动排课
-> 冲突检测与未排任务追踪
-> 课表查询与导出
-> 课表评分与优化建议
```

## 阶段状态

### V1 已完成

`V1` 解决“能把基础排课系统跑起来”的问题，已覆盖：

- 登录认证
- 教师、班级、教室、课程、时间段等基础数据管理
- 教学任务管理
- 手动排课
- 排课冲突检测
- 班级 / 教师 / 教室课表查询
- 首页统计与基础运行闭环

### V2 已完成

`V2` 在 V1 基础上补齐“排课增强能力”，已覆盖：

- 教师禁排时间管理
- 排课规则管理
- 自动排课批次运行
- 未排任务列表与失败原因追踪
- 排课冲突报告
- 课表评分报告
- Excel 课表导出

### V3 阶段 1-6 已完成

`V3` 在 V2 基础上引入"多策略排课计划"能力，阶段 1-6 已完成：

- 学期管理：学期 CRUD、当前学期切换、数据按学期隔离
- 排课计划：一次生成多个策略方案，支持查看、对比、应用、回滚
- 规则权重：4 种策略类型（综合/教师优先/班级均衡/教室利用），各配独立权重
- 评分详情：12 项指标（6 硬约束 + 6 软约束）逐项展示扣分明细
- 计划对比：多方案横向对比表格，推荐最优方案
- 计划应用/回滚：将计划写入正式排课表、支持回滚（阶段 6，含 E2E 测试）

### V2 算法定位

当前自动排课采用的是`规则驱动 + 启发式排序`方案，目标是先满足业务可用性和解释性，而不是引入复杂求解器。

当前版本明确不追求：

- 遗传算法
- 模拟退火
- Timefold Solver
- 全局最优排课求解

## 功能矩阵

| 模块 | 当前状态 | 说明 |
| --- | --- | --- |
| 登录认证 | ✅ 已完成 | JWT 登录、登录态校验、默认管理员初始化 |
| 基础数据管理 | ✅ 已完成 | 教师、班级、教室、课程、时间段 |
| 教学任务管理 | ✅ 已完成 | 维护课程-教师-班级任务与周课时 |
| 手动排课 | ✅ 已完成 | 创建排课记录、冲突校验 |
| 课表查询 | ✅ 已完成 | 班级 / 教师 / 教室三种视角 |
| 教师禁排时间 | ✅ 已完成 | 启停禁排规则、参与排课约束 |
| 排课规则 | ✅ 已完成 | 教师日上限、班级日上限、上午优先、避免周五下午等 |
| 自动排课 | ✅ 已完成 | 批次执行、结果落库 |
| 未排任务追踪 | ✅ 已完成 | 记录失败原因类型与详细说明 |
| 冲突报告 | ✅ 已完成 | 生成冲突明细与建议 |
| 评分报告 | ✅ 已完成 | 生成课表评分、扣分详情、优化建议 |
| Excel 导出 | ✅ 已完成 | 班级 / 教师 / 教室课表导出 |
| 学期管理 | ✅ V3 已完成 | 学期 CRUD、设置当前学期、数据按学期隔离 |
| 排课计划管理 | ✅ V3 已完成 | 多策略计划生成、计划列表/详情/删除 |
| 排课生成 | ✅ V3 已完成 | 支持 4 种策略（综合/教师优先/班级均衡/教室利用） |
| 规则权重配置 | ✅ V3 已完成 | 按策略类型配置 12 项评分指标权重 |
| 评分详情 | ✅ V3 已完成 | 按规则维度展示评分明细 |
| 计划对比 | ✅ V3 已完成 | 多方案横向对比、推荐最优方案 |
| 计划应用/回滚 | ✅ V3 已完成 | 将计划写入正式排课表、支持回滚 |

## 技术栈

### 后端

- Java 17
- Spring Boot 3.3.5
- MyBatis Plus 3.5.7
- MySQL
- JWT
- Apache POI
- Maven

### 前端

- Vue 3
- Vite
- TypeScript
- Element Plus
- Vue Router
- Pinia
- Axios

### 测试与辅助

- Playwright
- Node.js 脚本化 API smoke test

## 架构设计

### 整体架构

```
[Vue 3 前端 :5173]  <--HTTP/JSON-->  [Spring Boot 后端 :8090]  <--JDBC-->  [MySQL]
   Vite + Element Plus                    Spring Boot 3.3.5
   Pinia (auth store)                     MyBatis Plus 3.5.7
   Vue Router (25 条路由)                  JWT Authentication
   Axios 拦截器                           21 个 REST Controller
   25 个视图页面                           15 个 Service
   22 个 API 模块                         20 个 Entity / Mapper
                                          12 张数据库表
```

### 后端分层

```
Controller 层  →  接收请求、参数校验、调用 Service、返回 Result<T>
    ↓
Service 层     →  业务逻辑、冲突检测、自动排课算法、评分计算
    ↓
Mapper 层      →  MyBatis Plus，注解式 CRUD，XML 自定义查询
    ↓
Entity 层      →  数据库表映射，@TableLogic 软删除，驼峰-下划线自动转换
```

### 关键设计约定

| 约定 | 说明 |
| --- | --- |
| **统一响应** | 所有接口返回 `Result<T>`：`{ code: 200, message: "success", data: T }` |
| **软删除** | 所有实体使用 `@TableLogic`，`deleteById` 触发 `SET deleted=1`，查询自动加 `WHERE deleted=0` |
| **驼峰映射** | MyBatis Plus 自动将 `createTime` ↔ `create_time`，实体类用驼峰，数据库用下划线 |
| **枚举映射** | `CourseType`(NORMAL/EXPERIMENT/COMPUTER/PE)、`RoomType`(NORMAL/MULTIMEDIA/LAB/COMPUTER) |
| **认证方式** | JWT Token，前端存 localStorage，请求头 `Authorization: Bearer <token>` |
| **数据库迁移** | 启动时自动执行 `classpath:db/` 下的 SQL 脚本，`continue-on-error: true` 保证幂等 |

### 核心业务流程

#### 手动排课
```
前端提交(teacherId, classId, courseId, classroomId, timeSlotId)
  → ScheduleController.create()
  → ScheduleConflictService.checkConflicts()  // 7 种冲突检测
  → 无冲突：写入 Schedule 表
  → 有冲突：返回冲突详情，前端展示
```

#### 自动排课（V2）
```
POST /api/auto-schedule/run
  → AutoScheduleService.generate()
  → 按启发式排序遍历教学任务
  → 对每个任务遍历可用(教室, 时间段)组合
  → ScheduleConflictService.checkConflicts() 检查约束
  → 成功：写入 Schedule(source_type=AUTO, batch_id=xxx)
  → 失败：写入 UnscheduledTask(失败原因)
```

#### 多策略排课生成（V3）
```
POST /api/v3/schedule-generate/generate
  → V3ScheduleGenerateService.generateMultiple()
  → 按 4 种策略权重分别执行排课
  → 每套方案写入 SchedulePlan + SchedulePlanItem
  → ScheduleScoreService.scorePlan() 计算评分
  → ScheduleRuleWeightService 提供策略权重
```

#### 冲突检测（7 种）
1. 教师时间冲突 — 同一教师同一时段已有排课
2. 班级时间冲突 — 同一班级同一时段已有排课
3. 教室时间冲突 — 同一教室同一时段已有排课
4. 教室容量不足 — 班级人数 > 教室容量
5. 教室类型不匹配 — 实验课需要实验室，机房课需要机房
6. 教师禁排时间 — 教师在禁排时段内
7. 实体已禁用 — 教师/班级/教室/课程被软删除

#### 评分规则（V3 — 12 项指标）
| 类型 | 规则 | 说明 |
| --- | --- | --- |
| 硬约束 | 教师冲突扣分 | 同一教师同一时段多门课 |
| 硬约束 | 班级冲突扣分 | 同一班级同一时段多门课 |
| 硬约束 | 教室冲突扣分 | 同一教室同一时段多门课 |
| 硬约束 | 容量超限扣分 | 人数超过教室容量 |
| 硬约束 | 类型不匹配扣分 | 课程类型与教室类型不符 |
| 硬约束 | 禁排时间扣分 | 教师在禁排时段排课 |
| 软约束 | 上午优先加分 | 上午时段排课奖励 |
| 软约束 | 课程分布均衡扣分 | 同一课程排课过于集中 |
| 软约束 | 教师日课时均衡扣分 | 教师每日课时差异大 |
| 软约束 | 教室利用率加分 | 教室使用率高 |
| 软约束 | 连排加分 | 同一课程连排奖励 |
| 软约束 | 空闲时段扣分 | 教师/班级空闲时段过多 |

## 项目结构

```text
.
├─ backend/                                   # Spring Boot 后端
│  ├─ src/main/java/com/paike/scheduler
│  │  ├─ SchedulerBackendApplication.java     # 启动类
│  │  ├─ PasswordTest.java                    # 密码哈希生成工具
│  │  ├─ auth/                                # 登录认证与 JWT
│  │  │  ├─ AuthInterceptor.java              # 认证拦截器
│  │  │  ├─ AuthService.java                  # 认证业务逻辑
│  │  │  ├─ JwtService.java                   # JWT 签发与校验
│  │  │  ├─ AuthUserContext.java              # 当前用户上下文
│  │  │  └─ dto/                              # 登录/用户 DTO、VO
│  │  ├─ common/                              # 公共组件
│  │  │  ├─ enums/                            # 枚举：CourseType、RoomType、ScheduleSourceType
│  │  │  ├─ exception/                        # BusinessException、GlobalExceptionHandler
│  │  │  └─ response/                         # 统一响应 Result<T>
│  │  ├─ config/                              # 配置类
│  │  │  ├─ AdminUserInitializer.java         # 默认管理员初始化
│  │  │  ├─ CorsConfig.java                   # 跨域配置
│  │  │  ├─ MybatisPlusConfig.java            # MyBatis Plus 配置
│  │  │  ├─ SecurityConfig.java               # Spring Security 配置
│  │  │  ├─ SemesterSchemaInitializer.java    # V3 数据库迁移初始化
│  │  │  └─ WebMvcConfig.java                 # Web MVC 配置
│  │  ├─ controller/                          # REST 控制器（21 个 + vo/）
│  │  │  ├─ AuthController                    # 登录/登出/当前用户
│  │  │  ├─ HealthController                  # 健康检查
│  │  │  ├─ TeacherController                 # 教师管理
│  │  │  ├─ ClassInfoController               # 班级管理
│  │  │  ├─ ClassroomController               # 教室管理
│  │  │  ├─ CourseController                  # 课程管理
│  │  │  ├─ TeachingTaskController            # 教学任务管理
│  │  │  ├─ TimeSlotController                # 时间段查询
│  │  │  ├─ ScheduleController                # 手动排课 CRUD + 冲突检测
│  │  │  ├─ AutoScheduleBatchController       # V2 自动排课批次
│  │  │  ├─ TeacherUnavailableTimeController  # V2 教师禁排时间
│  │  │  ├─ ScheduleRuleController            # V2 排课规则配置
│  │  │  ├─ UnscheduledTaskController         # V2 未排任务
│  │  │  ├─ ScheduleConflictReportController  # V2 冲突报告
│  │  │  ├─ ScheduleScoreReportController     # V2 评分报告
│  │  │  ├─ TimetableController               # 课表查询 + Excel 导出
│  │  │  ├─ SemesterController               # V3 学期管理
│  │  │  ├─ SchedulePlanController            # V3 排课计划 CRUD、对比、应用、回滚
│  │  │  ├─ ScheduleGenerateController        # V3 排课生成
│  │  │  ├─ ScheduleRuleWeightController      # V3 规则权重管理
│  │  │  └─ ScheduleScoreController           # V3 评分详情
│  │  ├─ entity/                              # 实体模型（20 个）
│  │  │  ├─ SysUser                           # 系统用户
│  │  │  ├─ Teacher                           # 教师
│  │  │  ├─ ClassInfo                         # 班级
│  │  │  ├─ Classroom                         # 教室
│  │  │  ├─ Course                            # 课程
│  │  │  ├─ TeachingTask                      # 教学任务
│  │  │  ├─ TimeSlot                          # 时间段
│  │  │  ├─ Schedule                          # 排课记录
│  │  │  ├─ TeacherUnavailableTime            # 教师禁排时间（V2）
│  │  │  ├─ ScheduleRuleConfig                # 排课规则配置（V2）
│  │  │  ├─ AutoScheduleBatch                 # 自动排课批次（V2）
│  │  │  ├─ UnscheduledTask                   # 未排任务（V2）
│  │  │  ├─ ScheduleConflictReport            # 冲突报告（V2）
│  │  │  ├─ ScheduleScoreReport               # 评分报告（V2）
│  │  │  ├─ Semester                          # 学期（V3）
│  │  │  ├─ SchedulePlan                      # 排课计划（V3）
│  │  │  ├─ SchedulePlanItem                  # 排课计划明细（V3）
│  │  │  ├─ ScheduleRuleWeight                # 规则权重（V3）
│  │  │  └─ ScheduleScoreDetail               # 评分详情（V3）
│  │  ├─ mapper/                              # MyBatis Plus Mapper（20 个）
│  │  └─ service/                             # 业务服务（15 个 + dto/）
│  │     ├─ AuthService                       # 认证服务
│  │     ├─ AutoScheduleService               # V2 自动排课引擎
│  │     ├─ AutoScheduleBatchService          # V2 批次管理
│  │     ├─ ScheduleConflictService           # 冲突检测
│  │     ├─ ScheduleRuleService               # 规则配置管理
│  │     ├─ SchedulePlanService               # V3 计划 CRUD、应用/回滚
│  │     ├─ ScheduleCompareService            # V3 计划对比
│  │     ├─ ScheduleScoreService              # V3 评分计算
│  │     ├─ ScheduleRuleWeightService         # V3 规则权重管理
│  │     ├─ ScheduleScoreReportService        # V2 评分报告生成
│  │     ├─ ScheduleConflictReportService     # V2 冲突报告生成
│  │     ├─ SemesterService                   # 学期管理
│  │     ├─ TeacherUnavailableTimeService     # 教师禁排时间管理
│  │     ├─ UnscheduledTaskService            # 未排任务追踪
│  │     ├─ V3ScheduleGenerateService         # V3 排课生成引擎
│  │     └─ dto/                              # 请求/结果 DTO
│  │        ├─ AutoScheduleRequest/Result     # 自动排课
│  │        ├─ ScheduleGenerateRequest/Result # 排课生成
│  │        ├─ MultipleScheduleGenerateRequest # 多计划生成
│  │        └─ SemesterRequest                # 学期请求
│  └─ src/main/resources
│     ├─ application.yml                      # 后端配置（端口、DB、JWT、SQL init）
│     └─ db/                                  # 数据库迁移脚本
│        ├─ schema.sql                        # V1 核心表 + 时间段种子数据
│        ├─ v2_schema.sql                     # V2 新表 + 默认规则配置
│        ├─ v2_alter_schedule.sql             # V2 排课表加列（source_type、batch_id）
│        ├─ v2_alter_score_report.sql        # V2 评分报告加列（grade_name）
│        ├─ v3_semester.sql                   # V3 学期表 + 默认学期种子
│        ├─ v3_semester_data_bind.sql        # V3 教学任务/排课绑定学期
│        ├─ v3_schedule_plan.sql              # V3 排课计划表 + 明细表
│        └─ v3_score.sql                      # V3 规则权重表 + 评分详情表
├─ frontend/                                  # Vue 3 前端
│  └─ src/
│     ├─ api/                                 # API 封装（22 个模块）
│     │  ├─ auth.ts                           # 认证
│     │  ├─ teacher.ts                        # 教师
│     │  ├─ classInfo.ts                      # 班级
│     │  ├─ classroom.ts                      # 教室
│     │  ├─ course.ts                         # 课程
│     │  ├─ teachingTask.ts                   # 教学任务
│     │  ├─ timeSlot.ts                       # 时间段
│     │  ├─ schedule.ts                       # 排课
│     │  ├─ timetable.ts                      # 课表查询
│     │  ├─ teacherUnavailableTime.ts         # 教师禁排时间
│     │  ├─ scheduleRule.ts                   # 排课规则
│     │  ├─ autoSchedule.ts                   # 自动排课
│     │  ├─ unscheduledTask.ts                # 未排任务
│     │  ├─ scheduleConflictReport.ts         # 冲突报告
│     │  ├─ scheduleScoreReport.ts            # 评分报告
│     │  ├─ scheduleScore.ts                  # V3 评分详情
│     │  ├─ semester.ts                       # 学期
│     │  ├─ schedulePlan.ts                   # 排课计划
│     │  ├─ scheduleGenerate.ts               # 排课生成
│     │  ├─ scheduleRuleWeight.ts             # 规则权重
│     │  ├─ types.ts                          # 公共类型
│     │  └─ index.ts                          # 统一导出
│     ├─ assets/                              # 静态资源
│     ├─ components/                          # 公共组件
│     │  └─ TimetableGrid.vue                 # 课表网格组件
│     ├─ layout/                              # 布局
│     │  └─ BaseLayout.vue                    # 侧边栏导航 + 学期选择器
│     ├─ router/                              # 路由定义
│     │  └─ index.ts                          # 25 条路由
│     ├─ stores/                              # Pinia 状态管理
│     │  └─ auth.ts                           # 认证状态（token、登录、当前用户）
│     ├─ utils/                               # 工具类
│     │  └─ request.ts                        # Axios 实例（拦截器、错误处理）
│     └─ views/                               # 页面视图（25 个）
│        ├─ login/LoginView.vue               # 登录页
│        ├─ dashboard/DashboardView.vue       # 仪表盘
│        ├─ semester/SemesterView.vue         # 学期管理
│        ├─ teacher/TeacherView.vue           # 教师管理
│        ├─ teacher/TeacherUnavailableTimeView.vue # 教师禁排时间
│        ├─ classInfo/ClassInfoView.vue       # 班级管理
│        ├─ classroom/ClassroomView.vue       # 教室管理
│        ├─ course/CourseView.vue             # 课程管理
│        ├─ teachingTask/TeachingTaskView.vue # 教学任务管理
│        ├─ schedule/ScheduleView.vue         # 手动排课
│        ├─ schedule/AutoScheduleView.vue     # 自动排课
│        ├─ schedule/UnscheduledTaskView.vue  # 未排任务
│        ├─ schedule/ScheduleConflictReportView.vue # 冲突报告
│        ├─ schedule/ScheduleScoreReportView.vue    # 评分报告
│        ├─ schedule/ScheduleRuleView.vue     # 排课规则
│        ├─ schedule/ScheduleGenerateView.vue # V3 排课生成
│        ├─ schedule/SchedulePlanView.vue     # V3 排课计划列表
│        ├─ schedule/SchedulePlanDetailView.vue # V3 排课计划详情
│        ├─ schedule/ScheduleRuleWeightView.vue # V3 规则权重配置
│        ├─ schedule/ScheduleCompareView.vue  # V3 计划对比
│        ├─ timetable/ClassTimetableView.vue  # 班级课表
│        ├─ timetable/TeacherTimetableView.vue # 教师课表
│        ├─ timetable/ClassroomTimetableView.vue # 教室课表
│        └─ PlaceholderView.vue               # 占位页
├─ docs/                                      # 项目文档
│  ├─ v1/                                    # V1 阶段文档
│  ├─ v2/                                    # V2 阶段文档
│  └─ v3/                                    # V3 阶段文档
├─ tests/                                     # Playwright E2E 测试
│  ├─ stage6.spec.ts                          # V3 计划对比与应用回滚（20 个用例）
│  ├─ stage7.spec.ts                          # 手动排课与冲突检测（22 个用例）
│  └─ stage9.spec.ts                          # 课表查询（8 个用例）
├─ scripts/                                   # 辅助脚本
│  └─ api-smoke-test.js                       # API 冒烟测试
├─ test-results/                              # 测试结果输出
├─ output/                                    # 构建产物输出
├─ CLAUDE.md                                  # AI 协作规范
├─ playwright.config.ts                       # Playwright 配置
├─ package.json                               # Node.js 项目配置
└─ README.md                                  # 本文件
```

## 主要页面与接口

### 前端页面

| 路由 | 名称 | 说明 |
| --- | --- | --- |
| `/login` | 登录页 | JWT 登录 |
| `/dashboard` | 仪表盘 | 首页统计 |
| `/semesters` | 学期管理 | V3 学期 CRUD |
| `/teachers` | 教师管理 | 教师 CRUD |
| `/classes` | 班级管理 | 班级 CRUD |
| `/classrooms` | 教室管理 | 教室 CRUD |
| `/courses` | 课程管理 | 课程 CRUD |
| `/teaching-tasks` | 教学任务管理 | 教学任务 CRUD |
| `/teacher-unavailable-times` | 教师禁排时间 | 禁排时段管理 |
| `/schedule-rules` | 排课规则 | V2 规则配置 |
| `/auto-schedule` | 自动排课 | V2 批次执行 |
| `/schedule` | 手动排课 | 手动排课 + 冲突检测 |
| `/unscheduled-tasks` | 未排任务 | V2 未排任务追踪 |
| `/schedule-conflict-reports` | 冲突报告 | V2 冲突明细 |
| `/schedule-score` | 课表评分 | V2 评分报告 |
| `/v3/schedule-generate` | 排课生成 | V3 多策略方案生成 |
| `/v3/schedule-plans` | 排课计划 | V3 计划列表 |
| `/v3/schedule-plans/:id` | 计划详情 | V3 计划明细 + 评分 |
| `/v3/schedule-rules` | 规则权重 | V3 策略权重配置 |
| `/v3/schedule-compare` | 计划对比 | V3 多方案对比 |
| `/timetable/class` | 班级课表 | 班级视角课表 |
| `/timetable/teacher` | 教师课表 | 教师视角课表 |
| `/timetable/classroom` | 教室课表 | 教室视角课表 |

### 关键后端接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auth/login` | 登录 |
| GET | `/api/health` | 健康检查 |
| CRUD | `/api/teachers` | 教师管理 |
| CRUD | `/api/classes` | 班级管理 |
| CRUD | `/api/classrooms` | 教室管理 |
| CRUD | `/api/courses` | 课程管理 |
| CRUD | `/api/teaching-tasks` | 教学任务管理 |
| GET | `/api/time-slots` | 时间段列表 |
| CRUD | `/api/schedules` | 手动排课 + 冲突检测 |
| CRUD | `/api/teacher-unavailable-times` | 教师禁排时间 |
| CRUD | `/api/schedule-rules` | 排课规则配置 |
| POST | `/api/auto-schedule/run` | 执行自动排课 |
| GET | `/api/unscheduled-tasks` | 未排任务列表 |
| POST | `/api/schedule-conflict-reports/generate` | 生成冲突报告 |
| POST | `/api/schedule-score/generate` | 生成评分报告 |
| GET | `/api/timetables/*/export` | Excel 课表导出 |
| CRUD | `/api/v3/semesters` | V3 学期管理 |
| CRUD | `/api/v3/schedule-plans` | V3 排课计划管理 |
| POST | `/api/v3/schedule-generate/generate` | V3 多策略排课生成 |
| CRUD | `/api/v3/schedule-rule-weights` | V3 规则权重管理 |
| GET | `/api/v3/schedule-score` | V3 评分详情 |
| POST | `/api/v3/schedule-plans/compare` | V3 计划对比 |
| POST | `/api/v3/schedule-plans/{id}/apply` | V3 应用计划 |
| POST | `/api/v3/schedule-plans/{id}/rollback` | V3 回滚计划 |

## 环境要求

- JDK 17
- Maven 3.9+
- Node.js 18+
- MySQL 8.x

### Java 版本说明

- 项目编译目标为 `Java 17`
- 本地开发使用 `Java 21` 通常也能运行 Spring Boot 3
- 但提交前仍应保证代码不依赖 `Java 21` 独占语法

## 快速启动

### 1. 准备数据库

先创建数据库：

```sql
CREATE DATABASE paike CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

后端启动时会自动执行以下迁移脚本（`continue-on-error: true` 保证幂等）：

| 脚本 | 说明 |
| --- | --- |
| `schema.sql` | V1 核心表（teacher, class_info, classroom, course, teaching_task, time_slot, schedule）+ 时间段种子数据 |
| `v2_schema.sql` | V2 新表（teacher_unavailable_time, schedule_rule_config, auto_schedule_batch, unscheduled_task, schedule_conflict_report, schedule_score_report）+ 默认规则配置 |
| `v2_alter_schedule.sql` | V2 排课表加列：source_type、batch_id |
| `v2_alter_score_report.sql` | V2 评分报告加列：grade_name |
| `v3_semester.sql` | V3 学期表 + 默认学期种子 |
| `v3_semester_data_bind.sql` | V3 教学任务/排课绑定学期 |
| `v3_schedule_plan.sql` | V3 排课计划表 + 明细表 |
| `v3_score.sql` | V3 规则权重表 + 评分详情表 + 初始化 4 套策略权重 |

### 2. 配置后端

参考 [backend/.env.example](backend/.env.example)：

```env
DB_URL=jdbc:mysql://127.0.0.1:3306/paike?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
DB_USERNAME=root
DB_PASSWORD=your_password
JWT_SECRET=replace_with_a_strong_secret
JWT_EXPIRATION_MS=86400000
```

默认后端端口来自 [backend/src/main/resources/application.yml](backend/src/main/resources/application.yml)：

- `http://127.0.0.1:8090`

### 3. 启动后端

```bash
cd backend
mvn spring-boot:run
```

健康检查：

```text
GET http://127.0.0.1:8090/api/health
```

### 4. 配置前端

参考 [frontend/.env.example](frontend/.env.example)：

```env
VITE_API_BASE_URL=http://127.0.0.1:8090/api
```

如果你使用默认 `Vite proxy` 开发模式，也可以不额外配置环境变量。

### 5. 启动前端

```bash
cd frontend
npm install
npm run dev
```

默认访问地址：

- `http://127.0.0.1:5173`

## 默认账号

系统会初始化默认管理员：

- 用户名：`admin`
- 密码：`123456`

建议首次登录后尽快修改。

## 测试与验证

### 后端单测

```bash
cd backend
mvn test
```

> 当前后端仅有基础 context load 测试，业务逻辑主要通过 E2E 测试覆盖。

### API 冒烟测试

```bash
npm run smoke:api
```

执行 `scripts/api-smoke-test.js`，快速验证核心接口是否可用。

### Playwright E2E 测试

```bash
npx playwright test
```

测试配置（`playwright.config.ts`）：
- 测试目录：`./tests`
- 前端 baseURL：`http://127.0.0.1:5173`
- 超时：60 秒
- 单 worker 串行执行，Chromium headed 模式
- 失败自动截图

| 测试文件 | 覆盖范围 | 用例数 |
| --- | --- | --- |
| `tests/stage6.spec.ts` | V3 计划对比与应用回滚 | ~20 |
| `tests/stage7.spec.ts` | 手动排课与冲突检测（7 种冲突类型） | ~22 |
| `tests/stage9.spec.ts` | 课表查询（班级/教师/教室） | ~8 |

**运行测试前确保**：后端已启动（`:8090`），前端已启动（`:5173`），数据库已初始化。

## 文档导航

如果你想按阶段理解项目，建议优先阅读：

- [docs/v1/V1_README.md](docs/v1/V1_README.md) — V1 阶段总览
- [docs/v2/V2_README.md](docs/v2/V2_README.md) — V2 阶段总览

### V1 文档（`docs/v1/`）

V1 PRD、架构设计、数据库设计、接口设计、页面设计、测试清单

### V2 文档（`docs/v2/`）

V2 功能设计、自动排课算法说明、测试清单

### V3 文档（`docs/v3/`）

| 文件 | 内容 |
| --- | --- |
| `01_V3版本需求说明.md` | V3 需求背景与目标 |
| `02_V3功能模块设计.md` | 功能模块详细设计 |
| `03_V3数据库设计.md` | 数据库表结构设计 |
| `04_V3接口设计.md` | REST API 接口定义 |
| `05_V3前端页面设计.md` | 前端页面与交互设计 |
| `06_V3自动排课与评分规则设计.md` | 评分规则与权重设计 |
| `07_V3开发阶段任务.md` | 开发阶段拆分与任务清单 |
| `08_V3测试与验收清单.md` | 测试用例与验收标准 |
| `09_V3_AI开发规则与提示词.md` | AI 开发协作规则 |

### 其他文档

| 文件 | 内容 |
| --- | --- |
| `docs/手动测试流程.md` | 手动测试步骤 |
| `docs/测试报告.md` | 测试执行报告 |
| `docs/修复记录.md` | Bug 修复记录 |
| `docs/代码设计问题审查报告.md` | 代码审查问题清单 |
| `docs/API冒烟测试脚本说明.md` | API 冒烟测试说明 |

## 当前边界与说明

- 当前自动排课已经具备业务可用性，但仍属于规则型实现，不是全局最优求解器
- 当前 README 以“本地开发和阶段交付”为主，不是面向生产部署的运维文档
- 仓库中保留了阶段文档、测试产物和调试文件，便于回溯开发过程

## 后续可扩展方向

- 更细粒度的排课优先级策略
- 更强的自动排课搜索与回溯能力
- 更完整的报表与导出模板
- 更规范的 CI / 自动化测试流水线
- 更接近生产环境的部署文档与容器化方案
