# 高校排课管理系统（Spring Boot + Vue）

基于 `Spring Boot 3 + Vue 3 + MyBatis Plus + MySQL` 的高校排课管理系统。  
当前代码主线版本：`V5（约束驱动的局部重排与智能修复优化版）`。

## 1. 版本状态

- V1：基础数据管理、教学任务、手动排课、冲突检测、课表查询
- V2：自动排课、教师禁排、规则配置、未排任务、冲突报告、评分报告、Excel 导出
- V3：学期管理、排课方案、多方案生成、评分明细、方案对比、应用方案、历史回滚、排课日志
- V4：排课质量分析、评分解释、风险诊断、可视化图表、局部调整、课程锁定、报告导出、AI 辅助分析
- V5：智能修复建议、候选位置推荐、试算方案、局部重排、优化前后对比、一致性检查、回归测试增强

## 2. V5 开发硬约束（必须遵守）

1. 不重写 V1-V4 已稳定功能，只做增量扩展。
2. 自动排课与局部重排不能直接覆盖正式课表。
3. 修复/试算/重排结果必须先落到“排课方案或试算方案”。
4. 应用到正式课表必须由用户显式触发，且后端二次校验。
5. 锁定课程必须严格保护，局部重排不得改动锁定项。
6. 风险诊断、AI 分析、修复建议仅辅助决策，不得直接改正式课表。
7. 涉及正式课表写入必须事务化，并记录调整/修复日志。
8. 新功能必须兼容：当前学期、排课方案、正式课表来源方案机制。
9. 所有接口以后端校验为准，前端校验仅做体验增强。
10. 每阶段完成后必须回归测试，确认旧功能未破坏。

## 3. 当前代码中的关键机制（已存在）

- 方案应用正式课表：`SchedulePlanService.applyPlan()`（事务）
- 局部重排生成新方案：`V4ScheduleReplanService.createLocalReplanPlan()`
- 课程锁定：`V4ScheduleLockService` + `schedule_locked_item`
- 调整校验与应用：`V4ScheduleAdjustmentService`
- 调整日志：`schedule_adjust_log`
- 学期与历史兼容补表：`SemesterSchemaInitializer`

## 4. 技术栈

- 后端：Java 17、Spring Boot 3.3.x、MyBatis Plus、MySQL、JWT、Maven
- 前端：Vue 3、Vite、TypeScript、Element Plus、Pinia、Vue Router、Axios
- 测试：Playwright、Node API smoke test

## 5. 目录结构

```text
.
├─ backend/                         # Spring Boot 后端
│  ├─ src/main/java/com/paike/scheduler
│  │  ├─ controller/                # V1-V5 REST 接口
│  │  ├─ service/                   # 业务逻辑（含 V3/V4/V5 演进能力）
│  │  ├─ mapper/                    # MyBatis Plus Mapper
│  │  ├─ entity/                    # 实体模型
│  │  ├─ config/                    # 配置与启动迁移初始化
│  │  └─ common/                    # 响应/异常/枚举
│  └─ src/main/resources
│     ├─ application.yml
│     └─ db/                        # schema 与阶段 SQL
├─ frontend/                        # Vue 3 前端
│  └─ src
│     ├─ views/                     # 页面（含 v4 视图）
│     ├─ api/                       # API 封装
│     ├─ router/                    # 路由
│     ├─ stores/                    # Pinia
│     └─ components/                # 组件
├─ docs/                            # v1-v5 需求/设计/测试文档
├─ tests/                           # Playwright E2E
└─ scripts/                         # 脚本（如 API 冒烟）
```

## 6. 本地启动

### 6.1 环境要求

- JDK 17+
- Maven 3.9+
- Node.js 18+
- MySQL 8.x

### 6.2 初始化数据库

```sql
CREATE DATABASE paike CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

后端启动会按 `application.yml` 自动执行 `db/*.sql`（幂等容错开启）。

### 6.3 启动后端

```powershell
cd D:\paike\backend
mvn spring-boot:run
```

- 默认地址：`http://127.0.0.1:8090`
- 健康检查：`GET /api/health`

### 6.4 启动前端

```powershell
cd D:\paike\frontend
npm install
npm run dev
```

- 默认地址：`http://127.0.0.1:5173`

## 7. 默认账号

- 用户名：`admin`
- 密码：`123456`

## 8. 关键业务边界

- 正式课表来源于“已应用方案”，不是任意算法结果直写。
- 局部重排、智能修复、候选推荐默认只产出方案，不直接落正式课表。
- 锁定项必须在候选过滤、试算、应用前后全链路校验。
- 正式课表变更必须可审计（事务 + 日志 + 来源方案可追溯）。

## 9. 测试与回归

### 9.1 后端测试

```powershell
cd D:\paike\backend
mvn test
```

### 9.2 API 冒烟

```powershell
cd D:\paike
npm run smoke:api
```

### 9.3 E2E（Playwright）

```powershell
cd D:\paike
npx playwright test
```

建议每次 V5 功能合并前至少执行：

- 既有排课主链路回归（V1-V4）
- 方案生成/应用回归（V3）
- 锁定与局部重排回归（V4/V5）
- 修复建议与试算不落正式课表回归（V5）

## 10. 文档入口

- V1：`docs/v1/`
- V2：`docs/v2/`
- V3：`docs/v3/`
- V4：`docs/v4/`
- V5：`docs/v5/`

建议优先阅读：

- `docs/v5/V5_01_版本需求说明.md`
- `docs/v5/V5_04_API接口设计.md`
- `docs/v5/V5_06_局部重排与智能修复规则设计.md`
- `docs/v5/V5_08_测试与验收清单.md`

